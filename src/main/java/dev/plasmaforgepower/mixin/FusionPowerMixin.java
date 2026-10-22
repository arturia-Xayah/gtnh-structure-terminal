package dev.plasmaforgepower.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.plasmaforgepower.api.FusionPowerAccess;
import dev.plasmaforgepower.core.PowerNbt;
import dev.plasmaforgepower.core.PowerSettings;
import goodgenerator.blocks.tileEntity.base.MTELargeFusionComputer;
import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.IDataCopyable;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTRecipe;
import gregtech.common.tileentities.machines.multi.MTEFusionComputer;

/** Adds configuration only. Native recipe iteration, startup taxation and tick execution remain in GT. */
@Mixin(value = { MTEFusionComputer.class, MTELargeFusionComputer.class }, remap = false)
public abstract class FusionPowerMixin extends MTEMultiBlockBase implements FusionPowerAccess, IDataCopyable {

    @Unique
    private PowerSettings pfp$settings = new PowerSettings();
    @Unique
    private int pfp$inputColor = -1;
    @Unique
    private boolean pfp$dualInput;
    @Unique
    private int pfp$attemptColor = -1;
    @Unique
    private int pfp$activeColor = -1;
    @Unique
    private long pfp$attemptBudget = -1;
    @Unique
    private long pfp$runningBudget = -1;

    protected FusionPowerMixin(String name) {
        super(name);
    }

    @Override
    public PowerSettings pfp$getSettings() {
        return pfp$settings.copy();
    }

    @Override
    public boolean pfp$isFormed() {
        return mMachine;
    }

    @Override
    public int pfp$activeColor() {
        return pfp$activeColor;
    }

    @Override
    public long pfp$attemptPower() {
        return pfp$attemptBudget;
    }

    @Override
    public boolean pfp$sourceAllowed(GTRecipe recipe) {
        return true;
    }

    @Override
    public long pfp$ratedVoltage() {
        Object machine = this;
        return GTValues.V[machine instanceof MTEFusionComputer ? ((MTEFusionComputer) machine).tier()
            : ((MTELargeFusionComputer) machine).tier()];
    }

    @Unique
    private long pfp$nativeAmps() {
        Object machine = this;
        if (machine instanceof MTEFusionComputer) return 1;
        MTELargeFusionComputer compact = (MTELargeFusionComputer) machine;
        long voltage = pfp$ratedVoltage();
        long perHatch = voltage * compact.getMaxPara() * compact.extraPara(100) / 32;
        return perHatch * 32 / voltage;
    }

    @Override
    public long pfp$ratedPower() {
        return pfp$ratedVoltage() * pfp$nativeAmps();
    }

    @Override
    public int pfp$applySettings(PowerSettings settings) {
        pfp$settings = settings.copy();
        int changes = mMachine ? pfp$settings.clamp(pfp$ratedPower(), pfp$ratedVoltage()) : 0;
        if (getBaseMetaTileEntity() != null) getBaseMetaTileEntity().markDirty();
        // Never stop/restart or recheck an in-flight recipe: native completion must retain its hot handoff.
        return changes;
    }

    @Override
    public void pfp$inputGroup(int color, boolean dual) {
        pfp$inputColor = PowerSettings.validColor(color) ? color : -1;
        pfp$dualInput = dual;
    }

    @Override
    public CheckRecipeResult pfp$prepareRecipe(ProcessingLogic logic, GTRecipe recipe) {
        if (pfp$settings.isAllAuto() && !(mRunningOnLoad && pfp$runningBudget >= 0)) {
            pfp$attemptColor = pfp$inputColor;
            pfp$attemptBudget = -1;
            return null;
        }
        int color = pfp$inputColor;
        if (!pfp$dualInput && color >= 0 && !mRunningOnLoad) {
            List<ItemStack> plain = new ArrayList<>(getStoredInputsForColor(Optional.of((byte) -1)));
            if (canUseControllerSlotForRecipe() && getControllerSlot() != null) plain.add(getControllerSlot());
            FluidStack[] fluids = (FluidStack[]) getStoredFluidsForColor(Optional.of((byte) -1))
                .toArray(new FluidStack[0]);
            // A complete unpainted recipe must not escape its cap through a painted group sharing its ingredients.
            if (recipe.maxParallelCalculatedByInputs(1, fluids, plain.toArray(new ItemStack[0])) >= 1) color = -1;
        }
        long budget = mRunningOnLoad ? pfp$runningBudget
            : pfp$settings.get(color)
                .isAuto() ? -1
                    : Math.min(
                        pfp$ratedPower(),
                        pfp$settings.get(color)
                            .eut());
        pfp$attemptColor = mRunningOnLoad ? pfp$activeColor : color;
        pfp$attemptBudget = budget;
        boolean normal = (Object) this instanceof MTEFusionComputer;
        if (budget < 0) {
            // Restore native parameters for every candidate, including an automatic group after a rejected manual one.
            logic.setAvailableVoltage(pfp$ratedVoltage())
                .setAvailableAmperage(pfp$nativeAmps())
                .setAmperageOC(!normal);
        } else {
            logic.setAvailableVoltage(budget)
                .setAvailableAmperage(1)
                .setAmperageOC(!normal);
            if (recipe.mEUt > budget) return CheckRecipeResultRegistry.insufficientPower(recipe.mEUt);
        }
        // Preserve recipe voltage eligibility even when a compact machine has a large total parallel budget.
        if (recipe.mEUt > pfp$ratedVoltage()) return CheckRecipeResultRegistry.insufficientPower(recipe.mEUt);
        return null;
    }

    @Override
    public void pfp$finishAttempt(boolean success) {
        if (success) {
            pfp$activeColor = pfp$attemptColor;
            pfp$runningBudget = pfp$attemptBudget;
        }
    }

    @Inject(method = "checkMachine", at = @At("RETURN"))
    private void pfp$clampFusion(IGregTechTileEntity base, ItemStack controller, List<StructureError> errors,
        CallbackInfo ci) {
        if (errors.isEmpty() && pfp$settings.clamp(pfp$ratedPower(), pfp$ratedVoltage()) > 0) base.markDirty();
    }

    @Override
    public NBTTagCompound pfp$saveFusionState() {
        NBTTagCompound tag = PowerNbt.write(pfp$settings);
        tag.setInteger("active_color", pfp$activeColor);
        tag.setLong("running_budget", pfp$runningBudget);
        return tag;
    }

    @Override
    public void pfp$loadFusionState(NBTTagCompound tag) {
        PowerSettings saved = PowerNbt.read(tag);
        pfp$settings = saved == null ? new PowerSettings() : saved;
        int color = tag.hasKey("active_color") ? tag.getInteger("active_color") : -1;
        pfp$activeColor = PowerSettings.validColor(color) ? color : -1;
        long budget = tag.hasKey("running_budget") ? tag.getLong("running_budget") : -1;
        pfp$runningBudget = budget > 0 ? budget : -1;
    }

    @Override
    public NBTTagCompound getCopiedData(EntityPlayer player) {
        return PowerNbt.write(pfp$settings);
    }

    @Override
    public String getCopiedDataIdentifier(EntityPlayer player) {
        return PowerNbt.TYPE;
    }

    @Override
    public boolean pasteCopiedData(EntityPlayer player, NBTTagCompound tag) {
        PowerSettings saved = PowerNbt.read(tag);
        if (saved == null) return false;
        pfp$applySettings(saved);
        return true;
    }
}

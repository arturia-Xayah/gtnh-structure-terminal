package dev.plasmaforgepower.mixin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.plasmaforgepower.api.ForgePowerAccess;
import dev.plasmaforgepower.core.PowerLimit;
import dev.plasmaforgepower.core.PowerNbt;
import dev.plasmaforgepower.core.PowerSettings;
import dev.plasmaforgepower.ui.ForgePowerGui;
import gregtech.api.interfaces.IDataCopyable;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTRecipe;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;
import gregtech.common.tileentities.machines.IDualInputInventoryWithPattern;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;
import gregtech.common.tileentities.machines.multi.MTEPlasmaForge;

@Mixin(value = MTEPlasmaForge.class, remap = false)
public abstract class PlasmaForgeMixin extends MTEExtendedPowerMultiBlockBase<MTEPlasmaForge>
    implements ForgePowerAccess, IDataCopyable {

    @Unique
    private PowerSettings pfp$settings = new PowerSettings();
    @Unique
    private long pfp$attemptBudget = -1;
    @Unique
    private int pfp$activeColor = -1;
    @Unique
    private boolean pfp$requiresColoredIngredient;
    @Unique
    private ItemStack[] pfp$plainItems = new ItemStack[0];
    @Unique
    private FluidStack[] pfp$plainFluids = new FluidStack[0];

    @Shadow
    protected abstract GTRecipe recipeAfterAdjustments(GTRecipe recipe, FluidStack[] fluids);

    protected PlasmaForgeMixin(String name) {
        super(name);
    }

    @Override
    public PowerSettings pfp$getSettings() {
        return pfp$settings.copy();
    }

    @Override
    public long pfp$ratedPower() {
        return super.getMaxInputEu();
    }

    @Override
    public long pfp$ratedVoltage() {
        return super.getAverageInputVoltage();
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
        if (!pfp$requiresColoredIngredient) return true;
        GTRecipe adjusted = recipeAfterAdjustments(recipe, pfp$plainFluids);
        return adjusted.maxParallelCalculatedByInputs(1, pfp$plainFluids, pfp$plainItems) < 1;
    }

    @Override
    public int pfp$applySettings(PowerSettings settings) {
        pfp$settings = settings.copy();
        int changes = mMachine ? pfp$settings.clamp(pfp$ratedPower(), pfp$ratedVoltage()) : 0;
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
            scheduleRecipeCheckImmediate();
        }
        return changes;
    }

    @Inject(method = "checkMachine", at = @At("RETURN"))
    private void pfp$clampAfterSuccessfulCheck(IGregTechTileEntity base, ItemStack controller,
        List<StructureError> errors, CallbackInfo ci) {
        if (errors.isEmpty() && pfp$settings.clamp(pfp$ratedPower(), pfp$ratedVoltage()) > 0) base.markDirty();
    }

    @Inject(method = "saveNBTData", at = @At("TAIL"))
    private void pfp$save(NBTTagCompound tag, CallbackInfo ci) {
        tag.setTag(PowerNbt.KEY, PowerNbt.write(pfp$settings));
    }

    @Inject(method = "loadNBTData", at = @At("TAIL"))
    private void pfp$load(NBTTagCompound tag, CallbackInfo ci) {
        PowerSettings saved = PowerNbt.read(tag.getCompoundTag(PowerNbt.KEY));
        pfp$settings = saved == null ? new PowerSettings() : saved;
        // Hatches are not necessarily known during load; wait for an actual successful structure check.
    }

    @Inject(method = "getGui", at = @At("HEAD"), cancellable = true)
    private void pfp$gui(CallbackInfoReturnable<MTEMultiBlockBaseGui<?>> cir) {
        cir.setReturnValue(new ForgePowerGui((MTEPlasmaForge) (Object) this));
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
        PowerSettings pasted = PowerNbt.read(tag);
        if (pasted == null) return false;
        pfp$applySettings(pasted);
        return true;
    }

    /** The DTPF's native catalyst adjustment also calls this method during recipe calculation. */
    @Override
    public long getMaxInputEu() {
        long power = super.getMaxInputEu();
        return pfp$attemptBudget < 0 ? power : Math.min(power, pfp$attemptBudget);
    }

    @Override
    protected void setProcessingLogicPower(ProcessingLogic logic) {
        super.setProcessingLogicPower(logic);
        if (pfp$attemptBudget < 0) return;
        // Preserve the vanilla single-normal-hatch rule: a limit may never increase native processing power.
        boolean single = !debugEnergyPresent && mEnergyHatches.size() == 1 && mExoticEnergyHatches.isEmpty();
        long nativeVoltage = super.getAverageInputVoltage();
        long amps = single ? 1 : super.getMaxInputAmps();
        long nativePower = nativeVoltage > 0 && amps > Long.MAX_VALUE / nativeVoltage ? Long.MAX_VALUE
            : nativeVoltage * amps;
        long budget = Math.max(0, Math.min(nativePower, pfp$attemptBudget));
        pfp$attemptBudget = budget;
        logic.setAvailableVoltage(budget)
            .setAvailableAmperage(1)
            .setAmperageOC(true);
    }

    /**
     * Keep the native processing path untouched when no group has a manual limit. With limits, each input candidate
     * receives a fresh native ProcessingLogic context before its ingredients may be consumed.
     */
    @Override
    protected CheckRecipeResult doCheckRecipe() {
        if (pfp$settings.isAllAuto()) return super.doCheckRecipe();
        CheckRecipeResult last = CheckRecipeResultRegistry.NO_RECIPE;
        for (IDualInputHatch hatch : mDualInputHatches) {
            if (hatch.getBaseMetaTileEntity() == null || hatch.getBaseMetaTileEntity()
                .getMetaTileEntity() != hatch) continue;
            int color = hatch.getBaseMetaTileEntity()
                .getColorization();
            Iterator<? extends IDualInputInventory> inventories = hatch.inventories();
            while (inventories.hasNext()) {
                IDualInputInventory inventory = inventories.next();
                if (inventory.isEmpty()) continue;
                List<ItemStack> items = new ArrayList<>();
                if (hatch.getSharedItems() != null) items.addAll(Arrays.asList(hatch.getSharedItems()));
                if (inventory.getItemInputs() != null) items.addAll(Arrays.asList(inventory.getItemInputs()));
                CheckRecipeResult result = pfp$tryInputs(color, items, inventory.getFluidInputs(), inventory, false);
                if (result.wasSuccessful()) return result;
                if (result != CheckRecipeResultRegistry.NO_RECIPE) last = result;
            }
        }

        List<ItemStack> plainItems = getStoredInputsForColor(Optional.of((byte) -1));
        pfp$addControllerCircuit(plainItems);
        pfp$plainItems = plainItems.toArray(new ItemStack[0]);
        pfp$plainFluids = getStoredFluidsForColor(Optional.of((byte) -1)).toArray(new FluidStack[0]);
        // Prefer a complete unpainted recipe, then exact-color recipes, then recipes needing shared unpainted inputs.
        // Shared inputs must never let a complete unpainted recipe escape its own power cap via an empty painted bus.
        for (int color = -1; color < 16; color++) {
            if (!pfp$hasInputs(color)) continue;
            for (int pass = 0; pass < (color == -1 ? 1 : 2); pass++) {
                boolean shared = pass == 1;
                List<FluidStack> fluids = pfp$readFluids(color, shared);
                if (isInputSeparationEnabled() && !mInputBusses.isEmpty()) {
                    for (MTEHatchInputBus bus : mInputBusses) {
                        if (!bus.isValid() || bus instanceof MTEHatchCraftingInputME) continue;
                        if (bus.getColor() != color && !(shared && bus.getColor() == -1)) continue;
                        List<ItemStack> items = new ArrayList<>();
                        for (int slot = 0; slot < bus.getSizeInventory(); slot++) {
                            ItemStack item = bus.getStackInSlot(slot);
                            if (item != null) items.add(item);
                        }
                        pfp$addControllerCircuit(items);
                        CheckRecipeResult result = pfp$tryInputs(
                            color,
                            items,
                            fluids.toArray(new FluidStack[0]),
                            null,
                            shared);
                        if (result.wasSuccessful()) return result;
                        if (result != CheckRecipeResultRegistry.NO_RECIPE) last = result;
                    }
                } else {
                    List<ItemStack> items = pfp$readItems(color, shared);
                    pfp$addControllerCircuit(items);
                    CheckRecipeResult result = pfp$tryInputs(
                        color,
                        items,
                        fluids.toArray(new FluidStack[0]),
                        null,
                        shared);
                    if (result.wasSuccessful()) return result;
                    if (result != CheckRecipeResultRegistry.NO_RECIPE) last = result;
                }
            }
        }
        return last;
    }

    @Unique
    private void pfp$addControllerCircuit(List<ItemStack> items) {
        ItemStack controller = getControllerSlot();
        if (canUseControllerSlotForRecipe() && controller != null && !items.contains(controller)) items.add(controller);
    }

    @Unique
    private boolean pfp$hasInputs(int color) {
        for (MTEHatchInputBus bus : mInputBusses) {
            if (bus.isValid() && !(bus instanceof MTEHatchCraftingInputME) && bus.getColor() == color) return true;
        }
        for (MTEHatchInput hatch : mInputHatches) if (hatch.isValid() && hatch.getColor() == color) return true;
        return color == -1 && mInputBusses.isEmpty() && mInputHatches.isEmpty();
    }

    @Unique
    private CheckRecipeResult pfp$tryInputs(int color, List<ItemStack> items, FluidStack[] fluids,
        IDualInputInventory inventory, boolean shared) {
        if (!PowerSettings.validColor(color)) return CheckRecipeResultRegistry.NO_RECIPE;
        PowerLimit limit = pfp$settings.get(color);
        pfp$attemptBudget = limit.isAuto() ? -1 : Math.min(limit.eut(), pfp$ratedPower());
        pfp$requiresColoredIngredient = shared;
        try {
            setupProcessingLogic(processingLogic);
            if (inventory instanceof IDualInputInventoryWithPattern
                && !processingLogic.tryCachePossibleRecipesFromPattern((IDualInputInventoryWithPattern) inventory)) {
                return CheckRecipeResultRegistry.NO_RECIPE;
            }
            processingLogic.setInputItems(items.toArray(new ItemStack[0]));
            processingLogic.setInputFluids(fluids);
            CheckRecipeResult result = processingLogic.process();
            if (result.wasSuccessful()) pfp$activeColor = color;
            return result;
        } finally {
            pfp$attemptBudget = -1;
            pfp$requiresColoredIngredient = false;
        }
    }

    /** Narrow only the synchronous native inventory getter's view; restore it before matching or consuming anything. */
    @Unique
    private List<ItemStack> pfp$readItems(int color, boolean shared) {
        if (shared || color == -1) return getStoredInputsForColor(Optional.of((byte) color));
        ArrayList<MTEHatchInputBus> original = mInputBusses;
        try {
            mInputBusses = new ArrayList<>();
            for (MTEHatchInputBus bus : original) if (bus.getColor() == color) mInputBusses.add(bus);
            return getStoredInputsForColor(Optional.of((byte) color));
        } finally {
            mInputBusses = original;
        }
    }

    @Unique
    private List<FluidStack> pfp$readFluids(int color, boolean shared) {
        if (shared || color == -1) return getStoredFluidsForColor(Optional.of((byte) color));
        ArrayList<MTEHatchInput> original = mInputHatches;
        try {
            mInputHatches = new ArrayList<>();
            for (MTEHatchInput hatch : original) if (hatch.getColor() == color) mInputHatches.add(hatch);
            return getStoredFluidsForColor(Optional.of((byte) color));
        } finally {
            mInputHatches = original;
        }
    }
}

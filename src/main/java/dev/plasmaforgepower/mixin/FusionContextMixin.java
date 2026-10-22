package dev.plasmaforgepower.mixin;

import java.util.ArrayList;
import java.util.Optional;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.plasmaforgepower.api.FusionPowerAccess;
import dev.plasmaforgepower.core.PowerNbt;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.common.tileentities.machines.IDualInputHatch;

/** Observes the original input traversal. It neither sorts groups nor replaces doCheckRecipe. */
@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class FusionContextMixin {

    @Inject(method = "doCheckRecipe", at = @At("HEAD"))
    private void pfp$resetContext(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if ((Object) this instanceof FusionPowerAccess) ((FusionPowerAccess) this).pfp$inputGroup(-1, false);
    }

    @Redirect(
        method = "doCheckRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/common/tileentities/machines/IDualInputHatch;getSharedItems()[Lnet/minecraft/item/ItemStack;"))
    private ItemStack[] pfp$dualContext(IDualInputHatch hatch) {
        if ((Object) this instanceof FusionPowerAccess) ((FusionPowerAccess) this).pfp$inputGroup(
            hatch.getBaseMetaTileEntity()
                .getColorization(),
            true);
        return hatch.getSharedItems();
    }

    @Redirect(
        method = "doCheckRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/metatileentity/implementations/MTEMultiBlockBase;getStoredFluidsForColor(Ljava/util/Optional;)Ljava/util/ArrayList;"))
    private ArrayList<FluidStack> pfp$fluidContext(MTEMultiBlockBase machine, Optional<Byte> color) {
        if (machine instanceof FusionPowerAccess)
            ((FusionPowerAccess) machine).pfp$inputGroup(color.orElse((byte) -1), false);
        return machine.getStoredFluidsForColor(color);
    }

    @Inject(method = "doCheckRecipe", at = @At("RETURN"))
    private void pfp$finishContext(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if ((Object) this instanceof FusionPowerAccess) ((FusionPowerAccess) this).pfp$finishAttempt(
            cir.getReturnValue()
                .wasSuccessful());
    }

    @Inject(method = "saveNBTData", at = @At("TAIL"))
    private void pfp$saveFusion(NBTTagCompound tag, CallbackInfo ci) {
        if ((Object) this instanceof FusionPowerAccess)
            tag.setTag(PowerNbt.KEY, ((FusionPowerAccess) this).pfp$saveFusionState());
    }

    @Inject(method = "loadNBTData", at = @At("TAIL"))
    private void pfp$loadFusion(NBTTagCompound tag, CallbackInfo ci) {
        if ((Object) this instanceof FusionPowerAccess)
            ((FusionPowerAccess) this).pfp$loadFusionState(tag.getCompoundTag(PowerNbt.KEY));
    }
}

package dev.aestoragevault.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.aestoragevault.world.NativeBusOwners;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class NativeBusOwnershipMixin {

    @Inject(method = "checkStructure", at = @At("RETURN"))
    private void vault$nativeOwner(boolean reset, IGregTechTileEntity base, CallbackInfoReturnable<Boolean> cir) {
        if (base != null && base.isServerSide() && cir.getReturnValue())
            NativeBusOwners.checked((MTEMultiBlockBase) (Object) this);
    }
}

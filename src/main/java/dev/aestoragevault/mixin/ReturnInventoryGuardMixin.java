package dev.aestoragevault.mixin;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.aestoragevault.world.ReturnBus;
import gregtech.api.metatileentity.CommonMetaTileEntity;

/** Native saving reads getRealInventory directly, so temporarily hiding slots never hides saved contents. */
@Mixin(value = CommonMetaTileEntity.class, remap = false)
public abstract class ReturnInventoryGuardMixin {

    @Inject(method = { "getStackInSlot", "func_70301_a" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void vault$hold(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if ((Object) this instanceof ReturnBus && !((ReturnBus) this).vaultDeliveries()
            .isEmpty()) cir.setReturnValue(null);
    }
}

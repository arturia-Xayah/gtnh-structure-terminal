package dev.aestoragevault.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import appeng.util.item.AEStack;
import dev.aestoragevault.core.VisibleAmount;

/** Preserve native signed adjustments; cap only positive additions that would overflow. */
@Mixin(value = AEStack.class, remap = false)
public abstract class VisibleAmountMixin {

    @Shadow
    private long stackSize;

    @ModifyVariable(method = "incStackSize(J)V", at = @At("HEAD"), argsOnly = true)
    private long vault$boundedVisibleAddition(long delta) {
        return VisibleAmount.addition(stackSize, delta);
    }
}

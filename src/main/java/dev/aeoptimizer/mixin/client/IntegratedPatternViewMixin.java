package dev.aeoptimizer.mixin.client;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.implementations.GuiInterfaceTerminal;
import dev.structureterminal.pattern.IntegratedPattern;

/** Reuse native search and validation per child; display/edit remains the physical holder. */
@Mixin(value = GuiInterfaceTerminal.class, remap = false)
public abstract class IntegratedPatternViewMixin {

    @Shadow
    private static boolean itemStackMatchesSearchTerm(ItemStack stack, String search, boolean in) {
        throw new AssertionError();
    }

    @Shadow
    private boolean recipeIsBroken(ItemStack stack) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean hasInvalidTypeStack(ItemStack stack, IAEStackType<?>[] supported) {
        throw new AssertionError();
    }

    @Shadow
    private boolean isUseSubstitute(ItemStack stack) {
        throw new AssertionError();
    }

    @Inject(method = "itemStackMatchesSearchTerm", at = @At("HEAD"), cancellable = true)
    private static void modernization$search(ItemStack stack, String search, boolean in,
        CallbackInfoReturnable<Boolean> cir) {
        if (!IntegratedPattern.is(stack)) return;
        for (ItemStack child : IntegratedPattern.read(stack)) if (itemStackMatchesSearchTerm(child, search, in)) {
            cir.setReturnValue(true);
            return;
        }
        cir.setReturnValue(false);
    }

    @Inject(method = "recipeIsBroken", at = @At("HEAD"), cancellable = true)
    private void modernization$broken(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!IntegratedPattern.is(stack)) return;
        for (ItemStack child : IntegratedPattern.read(stack)) if (recipeIsBroken(child)) {
            cir.setReturnValue(true);
            return;
        }
        cir.setReturnValue(false);
    }

    @Inject(method = "hasInvalidTypeStack", at = @At("HEAD"), cancellable = true)
    private static void modernization$type(ItemStack stack, IAEStackType<?>[] supported,
        CallbackInfoReturnable<Boolean> cir) {
        if (!IntegratedPattern.is(stack)) return;
        for (ItemStack child : IntegratedPattern.read(stack)) if (hasInvalidTypeStack(child, supported)) {
            cir.setReturnValue(true);
            return;
        }
        cir.setReturnValue(false);
    }

    @Inject(method = "isUseSubstitute", at = @At("HEAD"), cancellable = true)
    private void modernization$substitute(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!IntegratedPattern.is(stack)) return;
        for (ItemStack child : IntegratedPattern.read(stack)) if (isUseSubstitute(child)) {
            cir.setReturnValue(true);
            return;
        }
        cir.setReturnValue(false);
    }
}

package dev.aeoptimizer.mixin;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.AEApi;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerInterface;
import appeng.container.slot.SlotRestrictedInput;
import dev.structureterminal.pattern.IntegratedPattern;

/** Extends only interface pattern slots; all native enabled/edit/security/inventory guards still execute. */
@Mixin(value = SlotRestrictedInput.class, remap = false)
public abstract class IntegratedPatternSlotMixin {

    @Shadow
    @Final
    private SlotRestrictedInput.PlacableItemType which;

    @Redirect(
        method = "func_75214_a",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;func_77973_b()Lnet/minecraft/item/Item;",
            remap = false))
    private Item modernization$acceptHolder(ItemStack stack) {
        AEBaseContainer container = ((PatternSlotAccessor) this).modernization$container();
        if (IntegratedPattern.is(stack) && container instanceof ContainerInterface
            && which == SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN) {
            return AEApi.instance()
                .definitions()
                .items()
                .encodedPattern()
                .maybeStack(1)
                .get()
                .getItem();
        }
        return stack.getItem();
    }
}

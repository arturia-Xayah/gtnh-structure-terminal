package dev.aeoptimizer.mixin;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.AEApi;
import appeng.container.implementations.ContainerInterfaceTerminal;
import dev.structureterminal.pattern.IntegratedPattern;

/** Only widens the native cursor-item type guard; movement, duplicate checks and sync remain native. */
@Mixin(value = ContainerInterfaceTerminal.class, remap = false)
public abstract class IntegratedPatternTerminalMixin {

    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private java.util.Map<Long, Object> trackedById;

    @Redirect(
        method = "doAction",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;func_77973_b()Lnet/minecraft/item/Item;",
            ordinal = 0,
            remap = false))
    private Item modernization$acceptHolder(ItemStack stack, net.minecraft.entity.player.EntityPlayerMP player,
        appeng.helpers.InventoryAction action, int slot, long id) {
        Object tracker = trackedById.get(id);
        net.minecraft.inventory.IInventory inventory = tracker instanceof PatternTrackerAccessor
            ? ((PatternTrackerAccessor) tracker).modernization$patterns()
            : null;
        Object owner = inventory instanceof PatternInventoryAccessor
            ? ((PatternInventoryAccessor) inventory).modernization$owner()
            : null;
        boolean supported = owner instanceof appeng.helpers.DualityInterface
            && ((appeng.helpers.DualityInterface) owner).getPatterns() == inventory;
        return IntegratedPattern.is(stack) && supported ? AEApi.instance()
            .definitions()
            .items()
            .encodedPattern()
            .maybeStack(1)
            .get()
            .getItem() : stack.getItem();
    }
}

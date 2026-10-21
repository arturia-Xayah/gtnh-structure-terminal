package dev.aeoptimizer.mixin;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.implementations.tiles.ICraftingMachine;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.helpers.DualityInterface;
import dev.aeoptimizer.dispatch.BatchContext;

/** Legacy plan consumers do not have a batch-count contract. Preserve their native one-craft path. */
@Mixin(value = DualityInterface.class, remap = false)
public abstract class DualityMixin {

    @Redirect(
        method = "pushPattern",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/implementations/tiles/ICraftingMachine;pushPattern(Lappeng/api/networking/crafting/ICraftingPatternDetails;Lnet/minecraft/inventory/InventoryCrafting;Lnet/minecraftforge/common/util/ForgeDirection;)Z"))
    private boolean aeo$legacyPlans(ICraftingMachine machine, ICraftingPatternDetails pattern, InventoryCrafting table,
        ForgeDirection side) {
        return !BatchContext.ACTIVE.get() && machine.pushPattern(pattern, table, side);
    }
}

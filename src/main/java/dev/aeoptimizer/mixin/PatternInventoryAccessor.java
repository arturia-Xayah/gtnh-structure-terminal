package dev.aeoptimizer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;

@Mixin(value = AppEngInternalInventory.class, remap = false)
public interface PatternInventoryAccessor {

    @Accessor("te")
    IAEAppEngInventory modernization$owner();
}

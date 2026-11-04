package dev.aeoptimizer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.container.AEBaseContainer;
import appeng.container.slot.AppEngSlot;

@Mixin(value = AppEngSlot.class, remap = false)
public interface PatternSlotAccessor {

    @Accessor("myContainer")
    AEBaseContainer modernization$container();
}

package dev.aeoptimizer.mixin;

import net.minecraft.inventory.IInventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "appeng.container.implementations.ContainerInterfaceTerminal$InvTracker", remap = false)
public interface PatternTrackerAccessor {

    @Accessor("patterns")
    IInventory modernization$patterns();
}

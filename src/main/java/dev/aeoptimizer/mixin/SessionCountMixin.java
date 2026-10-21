package dev.aeoptimizer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.me.cluster.implementations.CraftingCPUCluster.TaskProgress.SessionCraftCount;

@Mixin(value = SessionCraftCount.class, remap = false)
public interface SessionCountMixin {

    @Accessor("remaining")
    void aeo$remaining(long count);
}

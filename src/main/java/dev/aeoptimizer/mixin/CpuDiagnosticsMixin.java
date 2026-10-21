package dev.aeoptimizer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.api.storage.data.IAEStack;
import appeng.me.cluster.implementations.CraftingCpuDiagnostics;
import appeng.me.diagnostics.CraftingDiagnosticSessionId;

@Mixin(value = CraftingCpuDiagnostics.class, remap = false)
public interface CpuDiagnosticsMixin {

    @Invoker("recordExpectedOutput")
    void aeo$expected(IAEStack<?> output, long tick, CraftingDiagnosticSessionId session);
}

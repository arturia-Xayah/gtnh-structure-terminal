package dev.aeoptimizer.api;

import java.util.Map;

import appeng.me.diagnostics.CraftingDiagnosticSessionId;

public interface ProgressAccess {

    long aeo$get();

    void aeo$set(long count);

    Map<CraftingDiagnosticSessionId, Long> aeo$consume(long count);
}

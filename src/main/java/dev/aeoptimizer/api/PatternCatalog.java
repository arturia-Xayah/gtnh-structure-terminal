package dev.aeoptimizer.api;

import java.util.List;

import appeng.api.networking.crafting.ICraftingPatternDetails;

/** Diagnostics-only view before native per-output priority filtering. */
public interface PatternCatalog {

    List<ICraftingPatternDetails> aeo$registeredPatterns();
}

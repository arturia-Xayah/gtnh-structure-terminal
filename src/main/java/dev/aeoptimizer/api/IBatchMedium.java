package dev.aeoptimizer.api;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.util.inv.MEInventoryCrafting;

/** Opt-in for non-native providers. false must leave all input stacks unconsumed. */
public interface IBatchMedium extends ICraftingMedium {

    long maxBatch(ICraftingPatternDetails original, long requested);

    boolean pushBatch(ICraftingPatternDetails original, MEInventoryCrafting scaledInputs, long crafts);
}

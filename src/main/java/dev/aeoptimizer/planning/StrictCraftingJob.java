// Adapted for selected-route validation and opt-in dispatch by AE Order Optimizer, 2026.
package dev.aeoptimizer.planning;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.config.CraftingMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingCalculations;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.CraftingRequest.SubstitutionMode;
import appeng.hooks.TickHandler;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import it.unimi.dsi.fastutil.longs.LongObjectImmutablePair;
import it.unimi.dsi.fastutil.longs.LongObjectPair;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

public final class StrictCraftingJob<StackType extends IAEStack<StackType>> implements ICraftingJob<StackType> {

    private final CraftingContext context;
    private final boolean detailed;
    private final BaseActionSource reportSource;
    private final CraftingRequest originalRequest;
    private final ICraftingCallback callback;

    private final AbstractObject2LongMap<ICraftingPatternDetails> tasks = new Object2LongOpenHashMap<>();
    private final AbstractObject2LongMap<IAEStack<?>> ingredients = new Object2LongOpenHashMap<>();
    private final AbstractObject2LongMap<IAEStack<?>> missingIngredients = new Object2LongOpenHashMap<>();
    private boolean calculated = false;
    private boolean isSimulated = false;
    private long byteCost = 0;
    private String errorMsg = "";

    public StrictCraftingJob(final World world, final IGrid meGrid, final BaseActionSource actionSource,
        final StackType what, final CraftingMode craftingMode, final ICraftingCallback callback, boolean detailed) {
        this.detailed = detailed;
        this.reportSource = actionSource;
        this.context = new CraftingContext(world, meGrid, actionSource);
        this.callback = callback;
        this.originalRequest = new CraftingRequest(what, SubstitutionMode.PRECISE_FRESH, true, craftingMode);
        this.context.itemModel.ignore(what);
    }

    public CraftingContext getContext() {
        return this.context;
    }

    /** Same precise first-match selection as native Lite. Only the selected dependency route is validated. */
    private void calculateImpl() {
        java.util.Map<IAEStack<?>, LongObjectPair<ICraftingPatternDetails>> chosen = new java.util.HashMap<>();
        java.util.Map<IAEStack<?>, java.util.List<ICraftingPatternDetails>> diagnosticCatalog = detailed
            ? Reports.catalog(context)
            : java.util.Collections.emptyMap();
        dev.aeoptimizer.core.StrictGraph<IAEStack<?>> graph = new dev.aeoptimizer.core.StrictGraph<>(
            originalRequest.stack,
            stack -> {
                List<ICraftingPatternDetails> candidates = context.getPrecisePatternsFor(stack);
                LongObjectPair<ICraftingPatternDetails> match = null;
                for (ICraftingPatternDetails pattern : candidates) {
                    for (IAEStack<?> out : pattern.getCondensedAEOutputs()) {
                        if (out.equals(stack)) {
                            match = new LongObjectImmutablePair<>(out.getStackSize(), pattern);
                            break;
                        }
                    }
                    if (match != null) break;
                }
                List<ICraftingPatternDetails> reported = detailed ? diagnosticCatalog.getOrDefault(stack, candidates)
                    : candidates;
                if (detailed && reported.size() > 1) {
                    StringBuilder text = new StringBuilder("AEO_ALTERNATIVES: ").append(stack.getDisplayName());
                    for (int i = 0; i < reported.size(); i++) text.append("\n  ")
                        .append(i + 1)
                        .append(": ")
                        .append(Reports.describe(reported.get(i)))
                        .append(match != null && reported.get(i) == match.right() ? " [SELECTED]" : "");
                    Reports.detail(text.toString(), reportSource);
                }
                if (match == null) return null;
                chosen.put(stack, match);
                ICraftingPatternDetails pattern = match.right();
                java.util.List<IAEStack<?>> inputs = new java.util.ArrayList<>();
                for (IAEStack<?> input : pattern.getCondensedAEInputs()) inputs.add(input);
                return new dev.aeoptimizer.core.StrictGraph.Recipe<>(
                    inputs,
                    pattern.getCondensedAEOutputs().length,
                    () -> Reports.describe(pattern));
            },
            stack -> stack.getDisplayName());
        if (originalRequest.stack.getStackSize() < 0) throw new IllegalArgumentException("Request is negative");
        missingIngredients.put(originalRequest.stack, originalRequest.stack.getStackSize());
        for (IAEStack<?> current : graph.order) exploreItem(current, chosen.get(current));
    }

    // currentPair is the output-pattern pair, where output contains the pattern output count
    private void exploreItem(IAEStack<?> current, @Nullable LongObjectPair<ICraftingPatternDetails> currentPair) {
        long count = moveMissingByExtract(current);
        if (count == 0) return;
        if (currentPair == null) return;
        // Apply the pattern
        ICraftingPatternDetails pattern = currentPair.right();
        long patternMultiplier = dev.aeoptimizer.core.BatchMath.ceilDiv(count, currentPair.leftLong());
        for (IAEStack<?> stack : pattern.getCondensedAEInputs()) {
            if (stack.getStackSize() < 0) {
                throw new IllegalStateException(
                    String.format("Pattern %s has negative inputs", current.getDisplayName()));
            }
            addCountToMap(missingIngredients, stack, Math.multiplyExact(stack.getStackSize(), patternMultiplier));
        }
        addCountToMap(tasks, pattern, patternMultiplier);
        byteCost = Math.addExact(
            byteCost,
            CraftingCalculations
                .adjustByteCost(current, Math.multiplyExact(currentPair.leftLong(), patternMultiplier)));
        addCountToMap(missingIngredients, current, -count);
    }

    // Returns the remainder count, updates the missingIngredient map directly.
    private long moveMissingByExtract(IAEStack<?> stack) {
        long count = missingIngredients.getLong(stack);
        if (count == 0) return 0;
        IAEStack<?> result = context.itemModel.extractItems(
            stack.copy()
                .setStackSize(count),
            Actionable.MODULATE);
        if (result != null) {
            long extracted = result.getStackSize();
            addCountToMap(ingredients, stack, extracted);
            byteCost = Math.addExact(byteCost, CraftingCalculations.adjustByteCost(stack, extracted));
            if (extracted >= count) {
                missingIngredients.removeLong(stack);
                return 0;
            }
            addCountToMap(missingIngredients, stack, -extracted);
            return count - extracted;
        }
        return count;
    }

    private void calculate() {
        if (calculated) return;
        try {
            calculateImpl();
        } catch (Throwable t) {
            errorMsg = t.getMessage() == null ? t.toString() : t.getMessage();
            Reports.error(errorMsg, reportSource);
            isSimulated = true;
        } finally {
            calculated = true;
        }
    }

    private <T> long addCountToMap(AbstractObject2LongMap<T> map, T key, long count) {
        long oldValue = map.getLong(key);
        long newValue = Math.addExact(oldValue, count);
        if (newValue == 0) {
            map.removeLong(key);
            return 0;
        }
        if (newValue < 0) {
            throw new IllegalStateException(String.format("Negative result for %s, this is a bug.", key));
        }
        map.put(key, newValue);
        return newValue;
    }

    @Override
    public boolean isSimulation() {
        return isSimulated
            || (originalRequest.craftingMode != CraftingMode.IGNORE_MISSING && !missingIngredients.isEmpty());
    }

    @Override
    public long getByteTotal() {
        return byteCost;
    }

    @Override
    public StackType getOutput() {
        return (StackType) originalRequest.stack;
    }

    @Override
    public boolean simulateFor(int milli) {
        calculate();
        if (callback != null) {
            callback.calculationComplete(this);
        }
        return false;
    }

    @Override
    public Future<ICraftingJob<StackType>> schedule() {
        calculate();
        TickHandler.INSTANCE.registerCraftingSimulation(this.context.world, this);
        return CompletableFuture.completedFuture(this);
    }

    @Override
    public boolean supportsCPUCluster(ICraftingCPU cluster) {
        return cluster instanceof CraftingCPUCluster;
    }

    @Override
    public CraftingMode getCraftingMode() {
        return this.originalRequest.craftingMode;
    }

    @Override
    public void startCrafting(MECraftingInventory storage, ICraftingCPU craftingCPUCluster, BaseActionSource src) {
        calculate();
        CraftingCPUCluster cluster = (CraftingCPUCluster) craftingCPUCluster;
        for (it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<ICraftingPatternDetails> entry : tasks
            .object2LongEntrySet()) {
            cluster.addCrafting(entry.getKey(), entry.getLongValue());
        }
        for (it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<IAEStack<?>> entry : ingredients
            .object2LongEntrySet()) {
            pullStack(storage, cluster, entry.getKey(), entry.getLongValue());
        }
        for (it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<IAEStack<?>> entry : missingIngredients
            .object2LongEntrySet()) {
            pullStack(storage, cluster, entry.getKey(), entry.getLongValue());
        }
    }

    private void pullStack(MECraftingInventory storage, CraftingCPUCluster cluster, IAEStack<?> stack, long count) {
        IAEStack<?> extracted = storage.extractItems(
            stack.copy()
                .setStackSize(count),
            Actionable.MODULATE);
        if (extracted == null || extracted.getStackSize() != count) {
            if (cluster.isMissingMode()) {
                if (extracted == null) {
                    cluster.addEmitable(
                        stack.copy()
                            .setStackSize(count));
                    return;
                } else if (extracted.getStackSize() != count) {
                    cluster.addEmitable(
                        stack.copy()
                            .setStackSize(count - extracted.getStackSize()));
                }
            } else {
                throw new CraftBranchFailure(stack, count);
            }
        }
        cluster.addStorage(extracted);
    }

    @Override
    public MECraftingInventory getStorageAtBeginning() {
        return context.availableCache;
    }

    @Override
    public void populatePlan(IItemList plan) {
        // This is what gets shown in the crafting plan
        for (it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<ICraftingPatternDetails> entry : tasks
            .object2LongEntrySet()) {
            for (IAEStack<?> output : entry.getKey()
                .getCondensedAEOutputs()) {
                plan.addRequestable(
                    output.copy()
                        .setStackSize(0)
                        .setCountRequestable(Math.multiplyExact(entry.getLongValue(), output.getStackSize()))
                        .setCountRequestableCrafts(entry.getLongValue()));
            }
        }
        for (it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<IAEStack<?>> entry : ingredients
            .object2LongEntrySet()) {
            plan.add(
                entry.getKey()
                    .copy()
                    .setStackSize(entry.getLongValue()));
        }
        for (it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<IAEStack<?>> entry : missingIngredients
            .object2LongEntrySet()) {
            if (originalRequest.craftingMode == CraftingMode.IGNORE_MISSING) {
                plan.addRequestable(
                    entry.getKey()
                        .copy()
                        .setStackSize(0)
                        .setCountRequestable(entry.getLongValue()));
            } else {
                plan.add(
                    entry.getKey()
                        .copy()
                        .setStackSize(entry.getLongValue()));
            }
        }
    }

    @Override
    public boolean supportsOptimization() {
        return true;
    }

    @Override
    public String getErrorMessage() {
        return errorMsg;
    }
}

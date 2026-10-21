package dev.aeoptimizer.packtest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import appeng.api.config.Actionable;
import appeng.api.config.CraftingMode;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AECableType;
import appeng.hooks.TickHandler;
import appeng.me.cache.CraftingGridCache;
import appeng.me.storage.MEInventoryHandler;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEFluidStackType;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;

/** Native ordering only. Adapters supply encoded patterns and explicitly configured raw stock. */
final class LoadedStargateProbe implements ICraftingProvider, IGridHost, ICellProvider {

    final List<Map<String, Object>> rows = new ArrayList<>();
    final Map<String, Object> metadata = new LinkedHashMap<>();
    private final List<ICraftingPatternDetails> patterns = new ArrayList<>();
    private final Map<IAEStackType<?>, IMEInventoryHandler> cells = new LinkedHashMap<>();
    private final World world;
    private final IGrid grid;
    private final CraftingGridCache crafting;
    private final BaseActionSource source;
    private final ItemStack kit = OptimizerPackTest.token("complete_local_stargate_kit");
    private Future<ICraftingJob> future;
    private int ticks, index, startedTick;
    private long started, done, activeNs;
    private boolean lite;
    private final int mode, repeat;
    private final String scenario;
    private final JsonObject input;

    LoadedStargateProbe(World world, IGrid grid, CraftingGridCache crafting, BaseActionSource source, String filename,
        String scenario, int mode, int repeat) throws Exception {
        this.mode = mode;
        this.repeat = repeat;
        this.scenario = scenario;
        this.world = world;
        this.grid = grid;
        this.crafting = crafting;
        this.source = source;
        input = new JsonParser().parse(new String(Files.readAllBytes(Paths.get(filename)), StandardCharsets.UTF_8))
            .getAsJsonObject();
        Class<?> detailClass = Class.forName("com.glodblock.github.util.FluidPatternDetails");
        Item patternItem = null;
        for (Object object : Item.itemRegistry) {
            if (object.getClass()
                .getName()
                .equals("com.glodblock.github.common.item.ItemFluidEncodedPattern")) {
                patternItem = (Item) object;
                break;
            }
        }
        OptimizerPackTest.check(patternItem != null, "AE2FC fluid pattern item missing");
        for (JsonElement element : input.getAsJsonArray("patterns")) {
            JsonObject row = element.getAsJsonObject();
            List<IAEStack<?>> in = new ArrayList<>(), out = new ArrayList<>();
            for (JsonElement value : row.getAsJsonArray("inputs")) in.add(stack(value.getAsJsonObject()));
            for (JsonElement value : row.getAsJsonArray("outputs")) out.add(stack(value.getAsJsonObject()));
            patterns.add(pattern(patternItem, detailClass, in, out));
        }
        List<IAEStack<?>> targets = new ArrayList<>();
        for (Map.Entry<String, JsonElement> target : input.getAsJsonObject("targets")
            .entrySet())
            targets.add(
                stack(
                    target.getKey(),
                    target.getValue()
                        .getAsLong()));
        patterns.add(pattern(patternItem, detailClass, targets, Collections.singletonList(AEItemStack.create(kit))));

        Stock<IAEItemStack> items = new Stock<>(AEItemStackType.ITEM_STACK_TYPE);
        Stock<IAEFluidStack> fluids = new Stock<>(AEFluidStackType.FLUID_STACK_TYPE);
        for (Map.Entry<String, JsonElement> raw : input.getAsJsonObject("raw_supply_upper_bound")
            .entrySet()) {
            IAEStack<?> stock = stack(
                raw.getKey(),
                raw.getValue()
                    .getAsLong());
            if (stock instanceof IAEItemStack) items.content.add((IAEItemStack) stock);
            else fluids.content.add((IAEFluidStack) stock);
        }
        cells.put(AEItemStackType.ITEM_STACK_TYPE, new MEInventoryHandler<>(items, AEItemStackType.ITEM_STACK_TYPE));
        cells.put(
            AEFluidStackType.FLUID_STACK_TYPE,
            new MEInventoryHandler<>(fluids, AEFluidStackType.FLUID_STACK_TYPE));
        ((IStorageGrid) grid.getCache(IStorageGrid.class)).registerCellProvider(this);
        grid.postEvent(new appeng.api.networking.events.MENetworkCellArrayUpdate());
        crafting.addNode(null, this);
        metadata.put("encoded_patterns_including_kit_aggregator", patterns.size());
        metadata.put("pattern_class", detailClass.getName());
        metadata.put(
            "pattern_codec",
            "explicit modern typed AE item/fluid NBT; each encoded stack round-tripped and checked");
        metadata.put("indexed_unique_patterns", new java.util.HashSet<>(patterns).size());
        metadata.put("raw_supply", input.get("raw_supply_upper_bound"));
        metadata.put("route_diagnostics", input.get("diagnostics"));
        metadata.put(
            "stock_adapter",
            "read-only virtual inventory, exactly the listed raw types and quantities; no intermediate stock");
        metadata.put(
            "kit_aggregator",
            "one test-only processing pattern combines the four requested part quantities into a named test token; not a GT recipe");
    }

    private ICraftingPatternDetails pattern(Item item, Class<?> cls, List<IAEStack<?>> in, List<IAEStack<?>> out)
        throws Exception {
        ItemStack encoded = new ItemStack(item);
        Object builder = cls.getConstructor(ItemStack.class)
            .newInstance(encoded);
        cls.getMethod("setInputs", IAEStack[].class)
            .invoke(builder, (Object) in.toArray(new IAEStack<?>[0]));
        cls.getMethod("setOutputs", IAEStack[].class)
            .invoke(builder, (Object) out.toArray(new IAEStack<?>[0]));
        ItemStack saved = (ItemStack) cls.getMethod("writeToStack")
            .invoke(builder);
        // writeToStack in this AE2FC build serializes its legacy item/drop view. Preserve the real stack type
        // explicitly when preparing this modern-API fixture; verify the encoded pattern after re-reading it.
        java.lang.reflect.Method write = cls.getMethod("writeStackArray", IAEStack[].class);
        saved.getTagCompound()
            .setTag("in", (net.minecraft.nbt.NBTTagList) write.invoke(null, (Object) in.toArray(new IAEStack<?>[0])));
        saved.getTagCompound()
            .setTag("out", (net.minecraft.nbt.NBTTagList) write.invoke(null, (Object) out.toArray(new IAEStack<?>[0])));
        ICraftingPatternDetails details = ((ICraftingPatternItem) item).getPatternForItem(saved, world);
        OptimizerPackTest.check(
            details != null && details.getCondensedAEInputs().length > 0 && details.getCondensedAEOutputs().length > 0,
            "invalid native AE2FC pattern");
        checkRoundTrip(in, details.getAEInputs());
        checkRoundTrip(out, details.getAEOutputs());
        return details;
    }

    private void checkRoundTrip(List<IAEStack<?>> expected, IAEStack<?>[] decoded) {
        OptimizerPackTest.check(expected.size() == decoded.length, "pattern slot count changed during encoding");
        for (int i = 0; i < decoded.length; i++) OptimizerPackTest.check(
            expected.get(i)
                .isSameType(decoded[i])
                && expected.get(i)
                    .getStackSize() == decoded[i].getStackSize(),
            "pattern item/fluid type or quantity changed during encoding");
    }

    boolean tick() throws Exception {
        ticks++;
        if (ticks < 8) return false;
        if (index >= 1) return true;
        if (future == null) {
            if (index == 0) {
                int visible = 0;
                IStorageGrid sg = grid.getCache(IStorageGrid.class);
                for (IMEInventoryHandler cell : cells.values()) {
                    IItemList available = cell.getStackType()
                        .createList();
                    cell.getAvailableItems(available);
                    for (Object object : available) {
                        IAEStack stack = (IAEStack) object;
                        IAEStack found = ((IItemList) sg.getMEMonitor(stack.getStackType())
                            .getStorageList()).findPrecise(stack);
                        OptimizerPackTest.check(
                            found != null && found.getStackSize() == stack.getStackSize(),
                            "configured raw stock not visible in native storage: " + key(stack));
                        visible++;
                    }
                }
                metadata.put("raw_types_verified_in_native_monitor", visible);
            }
            lite = mode == 1;
            startedTick = ticks;
            done = activeNs = 0;
            started = System.nanoTime();
            future = crafting.beginCraftingJob(
                world,
                grid,
                source,
                AEItemStack.create(kit),
                CraftingMode.STANDARD,
                lite,
                job -> { if (done == 0) done = System.nanoTime(); });
            activeNs += System.nanoTime() - started;
            if (future.isDone()) done = System.nanoTime();
            if (future instanceof ICraftingJob) {
                java.lang.reflect.Field field = TickHandler.class.getDeclaredField("craftingJobs");
                field.setAccessible(true);
                Multimap<World, ICraftingJob> jobs = (Multimap<World, ICraftingJob>) field.get(TickHandler.INSTANCE);
                ICraftingJob original = (ICraftingJob) future;
                ICraftingJob wrapper = (ICraftingJob) Proxy.newProxyInstance(
                    ICraftingJob.class.getClassLoader(),
                    new Class[] { ICraftingJob.class },
                    (proxy, method, args) -> {
                        long before = System.nanoTime();
                        try {
                            return method.invoke(original, args);
                        } catch (InvocationTargetException error) {
                            throw error.getCause();
                        } finally {
                            if (method.getName()
                                .equals("simulateFor")) activeNs += System.nanoTime() - before;
                        }
                    });
                synchronized (jobs) {
                    OptimizerPackTest.check(jobs.remove(world, original), "scheduled native job missing");
                    jobs.put(world, wrapper);
                }
            }
            return false;
        }
        boolean timeout = !future.isDone()
            && System.nanoTime() - started > Long.getLong("aeorderbench.orderBudgetSeconds", 60L) * 1_000_000_000L;
        if (!future.isDone() && !timeout) return false;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("scenario", scenario);
        row.put("mode", mode == 0 ? "standard" : mode == 1 ? "lite" : "optimizer");
        row.put("repeat", repeat);
        row.put("warmup", repeat == 0);
        row.put("patterns", patterns.size());
        row.put("timed_out", timeout);
        row.put("ticks", ticks - startedTick);
        row.put("calculation_active_ms", activeNs / 1_000_000d);
        row.put("plan_ready_wall_ms", ((done == 0 ? System.nanoTime() : done) - started) / 1_000_000d);
        if (timeout) future.cancel(true);
        if (!timeout) {
            ICraftingJob job = future.get();
            row.put(
                "job_class",
                job.getClass()
                    .getName());
            row.put("simulation_missing", job.isSimulation());
            row.put("error", job.getErrorMessage());
            row.put("crafting_bytes", job.getByteTotal());
            IItemList<IAEStack<?>> plan = appeng.api.AEApi.instance()
                .storage()
                .createAEStackList();
            job.populatePlan(plan);
            List<Map<String, Object>> entries = new ArrayList<>();
            for (IAEStack<?> value : plan) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("key", key(value));
                entry.put("stored_or_missing", value.getStackSize());
                entry.put("to_craft", value.getCountRequestable());
                entries.add(entry);
            }
            row.put("plan", entries);
            if (job instanceof appeng.crafting.v2.CraftingJobV2) row.put(
                "resolved_tasks",
                ((appeng.crafting.v2.CraftingJobV2) job).getContext()
                    .getResolvedTasks()
                    .size());
            if (lite || mode == 2) {
                java.lang.reflect.Field missingField = job.getClass()
                    .getDeclaredField("missingIngredients");
                missingField.setAccessible(true);
                List<Map<String, Object>> missing = new ArrayList<>();
                for (Object object : ((Map<?, ?>) missingField.get(job)).entrySet()) {
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) object;
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("key", key((IAEStack<?>) entry.getKey()));
                    value.put("amount", entry.getValue());
                    missing.add(value);
                }
                row.put("native_missing_ingredients", missing);
            }
        }
        if (timeout && future instanceof appeng.crafting.v2.CraftingJobV2) {
            appeng.crafting.v2.CraftingContext context = ((appeng.crafting.v2.CraftingJobV2) future).getContext();
            row.put(
                "resolved_tasks_at_timeout",
                context.getResolvedTasks()
                    .size());
            row.put(
                "live_requests_at_timeout",
                context.getLiveRequests()
                    .size());
        }
        if (input.has("expected_raw_bill") && !timeout) {
            Map<String, Long> expected = new LinkedHashMap<>(), actual = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : input.getAsJsonObject("expected_raw_bill")
                .entrySet())
                expected.put(
                    key(stack(e.getKey(), 1)),
                    e.getValue()
                        .getAsLong());
            for (Map<String, Object> e : (List<Map<String, Object>>) row.get("plan")) {
                String k = (String) e.get("key");
                if (expected.containsKey(k)) actual.put(k, ((Number) e.get("stored_or_missing")).longValue());
            }
            row.put("raw_bill_matches_independent_oracle", expected.equals(actual));
            row.put("expected_raw_bill", expected);
            row.put("actual_raw_bill", actual);
        }
        row.put("submitted", false);
        rows.add(row);
        System.out.println(
            "AE_ORDER_LOADED_ORDER mode=" + row.get("mode")
                + " repeat="
                + index / 2
                + " active_ms="
                + row.get("calculation_active_ms")
                + " wall_ms="
                + row.get("plan_ready_wall_ms")
                + " timeout="
                + timeout
                + " missing="
                + row.get("simulation_missing")
                + " error="
                + row.get("error"));
        Files.write(
            Paths.get("stargate-last-order.json"),
            new com.google.gson.GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(rows)
                .getBytes(StandardCharsets.UTF_8));
        future = null;
        index++;
        return false;
    }

    static IAEStack<?> stack(JsonObject value) throws Exception {
        return stack(
            value.has("item") ? value.get("item")
                .getAsString()
                : "fluid:" + value.get("fluid")
                    .getAsString(),
            value.get("amount")
                .getAsLong());
    }

    private static IAEStack<?> stack(String key, long amount) throws Exception {
        if (key.startsWith("fluid:")) {
            net.minecraftforge.fluids.Fluid fluid = FluidRegistry.getFluid(key.substring(6));
            OptimizerPackTest.check(fluid != null, "unknown fluid " + key);
            return AEFluidStack.create(new FluidStack(fluid, 1))
                .setStackSize(amount);
        }
        int at = key.indexOf('@');
        int colon = key.indexOf(':', at);
        String name = key.substring(0, at);
        int meta = Integer.parseInt(key.substring(at + 1, colon < 0 ? key.length() : colon));
        Item item = (Item) Item.itemRegistry.getObject(name);
        OptimizerPackTest.check(item != null, "unknown item " + name);
        ItemStack stack = new ItemStack(item, 1, meta);
        if (colon >= 0) stack.setTagCompound((NBTTagCompound) JsonToNBT.func_150315_a(key.substring(colon + 1)));
        return AEItemStack.create(stack)
            .setStackSize(amount);
    }

    private static String key(IAEStack<?> stack) {
        if (stack instanceof IAEItemStack) stack = appeng.util.Platform.convertStack((IAEItemStack) stack);
        if (stack instanceof IAEFluidStack) return "fluid:" + ((IAEFluidStack) stack).getFluid()
            .getName();
        ItemStack item = ((IAEItemStack) stack).getItemStack();
        return String.valueOf(Item.itemRegistry.getNameForObject(item.getItem())) + "@"
            + item.getItemDamage()
            + (item.hasTagCompound() ? ":" + item.getTagCompound() : "");
    }

    @Override
    public void provideCrafting(ICraftingProviderHelper helper) {
        for (ICraftingPatternDetails details : patterns) helper.addCraftingOption(this, details);
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails details, InventoryCrafting table) {
        return false;
    }

    @Override
    public boolean isBusy() {
        return true;
    }

    @Override
    public IGridNode getGridNode(ForgeDirection direction) {
        return null;
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection direction) {
        return AECableType.NONE;
    }

    @Override
    public void securityBreak() {
        throw new UnsupportedOperationException("test provider");
    }

    void detach() {
        crafting.removeNode(null, this);
        ((IStorageGrid) grid.getCache(IStorageGrid.class)).unregisterCellProvider(this);
        grid.postEvent(new appeng.api.networking.events.MENetworkCellArrayUpdate());
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public List<IMEInventoryHandler> getCellArray(IAEStackType<?> type) {
        return cells.containsKey(type) ? Collections.singletonList(cells.get(type)) : Collections.emptyList();
    }

    private static final class Stock<T extends IAEStack<T>> implements IMEInventory<T> {

        final IAEStackType<T> type;
        final IItemList<T> content;

        Stock(IAEStackType<T> type) {
            this.type = type;
            this.content = type.createList();
        }

        @Override
        public T injectItems(T input, Actionable mode, BaseActionSource source) {
            return input;
        }

        @Override
        public T extractItems(T request, Actionable mode, BaseActionSource source) {
            OptimizerPackTest.check(mode == Actionable.SIMULATE, "planning probe attempted to consume virtual stock");
            T found = content.findPrecise(request);
            return found == null ? null
                : request.copy()
                    .setStackSize(Math.min(request.getStackSize(), found.getStackSize()));
        }

        @Override
        public IItemList<T> getAvailableItems(IItemList<T> out) {
            for (T value : content) out.add(value.copy());
            return out;
        }

        @Override
        public IAEStackType<?> getStackType() {
            return type;
        }
    }
}

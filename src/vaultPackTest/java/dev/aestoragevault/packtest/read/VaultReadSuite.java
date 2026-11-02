package dev.aestoragevault.packtest.read;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.definitions.IBlockDefinition;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.util.IterationCounter;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEFluidStackType;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.aestoragevault.VaultMod;
import dev.aestoragevault.ae.NativeResourceCache;
import dev.aestoragevault.ae.ResourceCodec;
import dev.aestoragevault.ae.VaultInventory;
import dev.aestoragevault.core.NativeInventoryView;
import dev.aestoragevault.core.Resource;
import dev.aestoragevault.core.VaultStore;
import dev.aestoragevault.world.TileAccess;
import dev.aestoragevault.world.TileController;
import dev.modernization.packtest.quantity.probe.TickProbe;
import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;

/** Frozen pre-change adapter versus production adapter on real, separately powered formed vaults. */
@Mod(
    modid = "vaultreadtests",
    name = "Vault read comparison",
    version = "1",
    dependencies = "required-after:aestoragevault")
public final class VaultReadSuite {

    private static final int WARM_TICKS = 80, MEASURE_TICKS = 120;
    private MinecraftServer server;
    private WorldServer world;
    private final TileController[] controllers = new TileController[4];
    private final TileAccess[] access = new TileAccess[4];
    private final List<IAEStack<?>> requests = new ArrayList<>();
    private final List<Resource> resources = new ArrayList<>();
    private final List<Map<String, Object>> rows = new ArrayList<>(), checks = new ArrayList<>();
    private final Map<String, List<Long>> samples = new LinkedHashMap<>();
    private IMEInventoryHandler<IAEItemStack> items;
    private IMEInventoryHandler<IAEFluidStack> fluids;
    private int age, phase, step;
    private boolean done, pendingTick;
    private long lastWork;
    private long coldEnum, coldAllocation, coldPoint, heapBefore, retainedDelta;
    private VaultStore store;
    private final List<Map<String, Object>> pairedRows = new ArrayList<>();
    private volatile Resource blackhole;
    private final com.sun.management.ThreadMXBean allocator = (com.sun.management.ThreadMXBean) ManagementFactory
        .getThreadMXBean();
    private final int round = Integer.getInteger("vault.readRound", 0);

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (Boolean.getBoolean("aestoragevault.readTests")) {
            ForgeChunkManager.setForcedChunkLoadingCallback(this, (tickets, w) -> {});
            FMLCommonHandler.instance()
                .bus()
                .register(this);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        try {
            if (++age == 20) setup();
            if (age < 100) return;
            if (Boolean.getBoolean("aestoragevault.lookupOnly")) {
                for (phase = 0; phase < 8; phase += 2) setupPhase();
                phase = 6;
                correctness();
                pairedQueries();
                finish(null);
                return;
            }
            if (pendingTick) {
                check(TickProbe.lastNanos >= lastWork, "whole tick probe excluded workload");
                add("whole_tick_ns", TickProbe.lastNanos);
                pendingTick = false;
            }
            if (step == 0) setupPhase();
            if (step < WARM_TICKS + MEASURE_TICKS) {
                boolean measured = step >= WARM_TICKS;
                long allocationStart = allocated(), start = System.nanoTime();
                timePoints("point_hot_batch_ns", true, measured);
                timePoints("point_stream_batch_ns", false, measured);
                long a = allocated(), t = System.nanoTime();
                enumerate();
                if (measured) {
                    add("stable_enumeration_ns", System.nanoTime() - t);
                    add("stable_enumeration_alloc", allocated() - a);
                }
                t = System.nanoTime();
                for (int i = 0; i < 8; i++) {
                    Resource r = resources.get((step * 8 + i) % resources.size());
                    check(store.insert(r, 64, false) == 64 && store.extract(r, 64, false) == 64, "mutation failed");
                }
                if (measured) add("journal_changes_ns", System.nanoTime() - t);
                a = allocated();
                t = System.nanoTime();
                enumerate();
                if (measured) {
                    add("changed_enumeration_ns", System.nanoTime() - t);
                    add("changed_enumeration_alloc", allocated() - a);
                }
                if (measured) {
                    lastWork = System.nanoTime() - start;
                    add("work_ns", lastWork);
                    add("work_alloc", allocated() - allocationStart);
                    pendingTick = true;
                }
                step++;
                return;
            }
            finishPhase();
            phase++;
            step = 0;
            if (phase == 8) {
                correctness();
                finish(null);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try {
                finish(failure);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private int config() {
        return phase / 2;
    }

    private boolean optimized() {
        return ((phase + round) & 1) == 1;
    }

    private int size() {
        return config() == 0 ? 1000 : 10000;
    }

    private int bits() {
        return config() == 2 ? 255 : 40;
    }

    private String caseName() {
        return new String[] { "1000_simple", "10000_simple", "10000_mixed_huge", "10000_rich_nbt" }[config()];
    }

    private void setup() throws Exception {
        server = MinecraftServer.getServer();
        world = server.worldServerForDimension(0);
        check(
            server.isDedicatedServer() && world.getWorldInfo()
                .getWorldName()
                .equals("vault-read-validation"),
            "wrong server");
        ForgeChunkManager.Ticket ticket = ForgeChunkManager.requestTicket(this, world, ForgeChunkManager.Type.NORMAL);
        ForgeChunkManager.forceChunk(ticket, new net.minecraft.world.ChunkCoordIntPair(0, 0));
        ForgeChunkManager.forceChunk(ticket, new net.minecraft.world.ChunkCoordIntPair(0, 1));
        world.theChunkProviderServer.loadChunk(0, 0);
        world.theChunkProviderServer.loadChunk(0, 1);
        FakePlayer player = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "VaultRead"));
        int bus = -1;
        for (int i = 0; i < GregTechAPI.METATILEENTITIES.length; i++) if (GregTechAPI.METATILEENTITIES[i] != null
            && GregTechAPI.METATILEENTITIES[i].getClass() == MTEHatchInputBus.class) {
                bus = i;
                break;
            }
        for (int n = 0; n < 4; n++) {
            int z = 8 + n * 4;
            for (int x = 7; x <= 9; x++) for (int y = 79; y <= 81; y++)
                for (int d = z; d <= z + 2; d++) world.setBlock(x, y, d, GregTechAPI.sBlockCasings1, 6, 3);
            world.setBlockToAir(8, 80, z + 1);
            world.setBlock(8, 80, z, VaultMod.CONTROLLER, 2, 3);
            world.setBlock(7, 80, z + 1, VaultMod.ACCESS, 0, 3);
            world.setBlockToAir(9, 80, z + 1);
            check(
                GregTechAPI.METATILEENTITIES[bus].getStackForm(1)
                    .tryPlaceItemIntoWorld(player, world, 9, 80, z + 1, 1, .5f, .5f, .5f),
                "bus placement");
            place(
                AEApi.instance()
                    .definitions()
                    .blocks()
                    .controller(),
                6,
                80,
                z + 1);
            place(
                AEApi.instance()
                    .definitions()
                    .blocks()
                    .energyCellCreative(),
                5,
                80,
                z + 1);
            controllers[n] = (TileController) world.getTileEntity(8, 80, z);
            controllers[n].automatic = false;
            controllers[n].checkStructure();
            access[n] = (TileAccess) world.getTileEntity(7, 80, z + 1);
        }
    }

    private void place(IBlockDefinition definition, int x, int y, int z) {
        world.setBlock(
            x,
            y,
            z,
            definition.maybeBlock()
                .get(),
            0,
            3);
    }

    private void setupPhase() throws Exception {
        check(access[config()].available(), "real formed vault / powered AE unavailable");
        store = controllers[config()].store();
        requests.clear();
        resources.clear();
        samples.clear();
        List<String> fluidNames = new ArrayList<>(
            FluidRegistry.getRegisteredFluids()
                .keySet());
        Collections.sort(fluidNames);
        for (int i = 0; i < size(); i++) {
            IAEStack<?> request;
            if (config() == 2 && i >= 9000) {
                Fluid fluid = FluidRegistry.getFluid(fluidNames.get(i - 9000));
                request = AEFluidStack.create(new FluidStack(fluid, 1));
            } else if (i == size() - 1) request = AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1));
            else {
                ItemStack item = new ItemStack(Items.paper);
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("Variant", i);
                if (config() == 3) {
                    NBTTagCompound nested = new NBTTagCompound();
                    NBTTagList list = new NBTTagList();
                    for (int k = 0; k < 12; k++) list.appendTag(
                        new NBTTagString(
                            "configuration-payload-" + k
                                + "-abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"));
                    nested.setTag("Lines", list);
                    nested.setInteger("Index", i);
                    tag.setTag("Payload", nested);
                }
                item.setTagCompound(tag);
                request = AEItemStack.create(item);
            }
            requests.add(request);
            resources.add(ResourceCodec.encode(request));
        }
        if (store.types() == 0) {
            Map<Resource, BigInteger> seed = new HashMap<>();
            for (int i = 0; i < size(); i++) seed.put(
                resources.get(i),
                BigInteger.ONE.shiftLeft(bits() - 1)
                    .add(BigInteger.valueOf(i)));
            store.absorbExact(UUID.randomUUID(), BigInteger.ONE.shiftLeft(bits() + 20), seed);
            check(store.types() == size(), "native identity merged benchmark types");
        }
        // Clear only derived indexes between adapter phases; persistent stock and files remain identical.
        clearReadViews(store);
        items = optimized() ? new VaultInventory<>(access[config()], AEItemStackType.ITEM_STACK_TYPE, "items")
            : new BaselineVaultInventory<>(access[config()], AEItemStackType.ITEM_STACK_TYPE, "items");
        fluids = optimized() ? new VaultInventory<>(access[config()], AEFluidStackType.FLUID_STACK_TYPE, "fluids")
            : new BaselineVaultInventory<>(access[config()], AEFluidStackType.FLUID_STACK_TYPE, "fluids");
        // Warm native global interning with the frozen path, outside timed cold-cache enumeration.
        IMEInventoryHandler<IAEItemStack> savedItems = items;
        IMEInventoryHandler<IAEFluidStack> savedFluids = fluids;
        items = new BaselineVaultInventory<>(access[config()], AEItemStackType.ITEM_STACK_TYPE, "items");
        fluids = new BaselineVaultInventory<>(access[config()], AEFluidStackType.FLUID_STACK_TYPE, "fluids");
        enumerate();
        items = savedItems;
        fluids = savedFluids;
        heapBefore = usedHeapAfterGc();
        long a = allocated(), t = System.nanoTime();
        enumerate();
        coldEnum = System.nanoTime() - t;
        coldAllocation = allocated() - a;
        t = System.nanoTime();
        for (int i = 0; i < 32; i++) point(i);
        coldPoint = (System.nanoTime() - t) / 32;
        // Match eventual query-cache occupancy, including the streaming working set.
        if (optimized()) for (int i = 0; i < Math.min(size(), NativeResourceCache.LIMIT); i++) point(i);
        retainedDelta = usedHeapAfterGc() - heapBefore;
    }

    private static void clearReadViews(VaultStore store) throws Exception {
        Field f = VaultStore.class.getDeclaredField("readViews");
        f.setAccessible(true);
        ((Map<?, ?>) f.get(store)).clear();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void point(int index) {
        IAEStack request = requests.get(index);
        IAEStack actual = request instanceof IAEItemStack ? items.getAvailableItem((IAEItemStack) request, 0)
            : fluids.getAvailableItem((IAEFluidStack) request, 0);
        long expected = bits() > 63 ? Long.MAX_VALUE : (1L << (bits() - 1)) + index;
        check(actual != null && actual.getStackSize() == expected, "point count mismatch");
    }

    private void timePoints(String metric, boolean hot, boolean measured) {
        long t = System.nanoTime();
        for (int i = 0; i < 128; i++) point(hot ? i % Math.min(size(), 256) : (step * 128 + i) % size());
        if (measured) add(metric, System.nanoTime() - t);
    }

    private void enumerate() {
        IItemList<IAEItemStack> itemList = AEItemStackType.ITEM_STACK_TYPE.createPrimitiveList();
        IItemList<IAEFluidStack> fluidList = AEFluidStackType.FLUID_STACK_TYPE.createPrimitiveList();
        items.getAvailableItems(itemList, 0);
        fluids.getAvailableItems(fluidList, 0);
        check(itemList.size() + fluidList.size() == size(), "enumeration count mismatch");
        long expected = bits() > 63 ? Long.MAX_VALUE : 1L << (bits() - 1);
        check(
            itemList.findPrecise((IAEItemStack) requests.get(0))
                .getStackSize() == expected,
            "enumeration quantity mismatch");
    }

    private void finishPhase() throws Exception {
        check(
            samples.get("whole_tick_ns")
                .size() == MEASURE_TICKS,
            "tick sample count");
        for (int i = 0; i < size(); i++) check(
            store.exactAmount(resources.get(i))
                .equals(
                    BigInteger.ONE.shiftLeft(bits() - 1)
                        .add(BigInteger.valueOf(i))),
            "stock conservation");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("round", round);
        row.put("phase", phase);
        row.put("case", caseName());
        row.put("types", size());
        row.put("quantity_bits", bits());
        row.put("variant", optimized() ? "optimized" : "baseline");
        row.put("measured_ticks", MEASURE_TICKS);
        row.put("cold_enumeration_ns", coldEnum);
        row.put("cold_enumeration_alloc", coldAllocation);
        row.put("cold_point_ns_per_query", coldPoint);
        row.put("retained_heap_delta_estimate", retainedDelta);
        Map<String, Object> metrics = new LinkedHashMap<>();
        for (Map.Entry<String, List<Long>> e : samples.entrySet()) metrics.put(e.getKey(), stats(e.getValue()));
        row.put("metrics", metrics);
        row.put("raw", new LinkedHashMap<>(samples));
        rows.add(row);
        System.out.println(
            "VAULT_READ_PHASE " + new GsonBuilder().create()
                .toJson(rowWithoutRaw(row)));
        write(null);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void correctness() throws Exception {
        VaultStore s = controllers[0].store();
        TileAccess a = access[0];
        VaultInventory<IAEItemStack> inv = new VaultInventory<>(a, AEItemStackType.ITEM_STACK_TYPE, "items");
        IAEItemStack iron = AEItemStack.create(new ItemStack(Items.iron_ingot));
        IItemList<IAEItemStack> list = AEItemStackType.ITEM_STACK_TYPE.createPrimitiveList();
        inv.getAvailableItems(list, 0);
        check(
            inv.injectItems(
                iron.copy()
                    .setStackSize(100),
                Actionable.MODULATE,
                new MachineSource(a)) == null,
            "new insertion");
        list = AEItemStackType.ITEM_STACK_TYPE.createPrimitiveList();
        inv.getAvailableItems(list, 0);
        check(
            list.findPrecise(iron)
                .getStackSize() == 100,
            "cache missed addition");
        list.findPrecise(iron)
            .setStackSize(7);
        IItemList<IAEItemStack> fresh = AEItemStackType.ITEM_STACK_TYPE.createPrimitiveList();
        inv.getAvailableItems(fresh, 0);
        check(
            fresh.findPrecise(iron)
                .getStackSize() == 100,
            "returned stack alias");
        NBTTagCompound poison = new NBTTagCompound();
        poison.setString("Unexpected", "consumer mutation");
        fresh.findPrecise(iron)
            .setTagCompound(poison);
        fresh = AEItemStackType.ITEM_STACK_TYPE.createPrimitiveList();
        inv.getAvailableItems(fresh, 0);
        check(
            fresh.findPrecise(iron) != null && fresh.findPrecise(iron)
                .getStackSize() == 100,
            "native shared-definition mutation poisoned prototype");
        check(
            inv.extractItems(
                iron.copy()
                    .setStackSize(100),
                Actionable.SIMULATE,
                new MachineSource(a))
                .getStackSize() == 100,
            "simulate extract");
        check(
            inv.getAvailableItem(iron, 0)
                .getStackSize() == 100,
            "simulation mutated");
        inv.extractItems(
            iron.copy()
                .setStackSize(100),
            Actionable.MODULATE,
            new MachineSource(a));
        fresh = AEItemStackType.ITEM_STACK_TYPE.createPrimitiveList();
        inv.getAvailableItems(fresh, 0);
        check(fresh.findPrecise(iron) == null, "cache retained deleted type");
        rowCheck("native_cache_add_remove_simulate_and_no_alias");
        NativeResourceCache cache = new NativeResourceCache();
        for (int i = 0; i < NativeResourceCache.LIMIT + 20; i++) {
            ItemStack item = new ItemStack(Items.paper);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("CacheTest", i);
            item.setTagCompound(tag);
            IAEItemStack ae = AEItemStack.create(item);
            Resource expected = ResourceCodec.encode(ae);
            check(
                expected.equals(
                    cache.encode(
                        ae.copy()
                            .setStackSize(400))),
                "cache identity collision");
        }
        check(cache.size() == NativeResourceCache.LIMIT, "unbounded encoding cache");
        rowCheck("exact_NBT_identity_and_bounded_key_cache");
        IAEItemStack mutable = AEItemStack.create(new ItemStack(Items.paper));
        Resource old = cache.encode(mutable);
        NBTTagCompound changed = new NBTTagCompound();
        changed.setString("Changed", "new identity");
        mutable.setTagCompound(changed);
        check(
            !old.equals(cache.encode(mutable)) && cache.encode(mutable)
                .equals(ResourceCodec.encode(mutable)),
            "mutable native definition stale");
        rowCheck("changed_native_NBT_identity_invalidates_key");
        for (int n = 0; n < 4; n++) {
            for (appeng.api.storage.data.IAEStackType type : new appeng.api.storage.data.IAEStackType[] {
                AEItemStackType.ITEM_STACK_TYPE, AEFluidStackType.FLUID_STACK_TYPE }) {
                IItemList result = access[n].getProxy()
                    .getStorage()
                    .getMEMonitor(type)
                    .getAvailableItems(type.createPrimitiveList(), IterationCounter.fetchNewId());
                int count = controllers[n].store()
                    .nativeSnapshot(type == AEItemStackType.ITEM_STACK_TYPE ? "items" : "fluids")
                    .size();
                check(result.size() == count, "real AE routing inventory mismatch");
            }
        }
        rowCheck("real_powered_AE_monitors_match_all_four_vaults");
        TileController owner = a.bound;
        a.unbind(owner);
        fresh = AEItemStackType.ITEM_STACK_TYPE.createPrimitiveList();
        inv.getAvailableItems(fresh, 0);
        check(fresh.isEmpty(), "unbound inventory visible");
        a.bind(owner);
        rowCheck("unbind_hides_stock_and_clears_owned_adapters");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void pairedQueries() throws Exception {
        for (int n = 0; n < 4; n++) {
            VaultStore ledger = controllers[n].store();
            NativeInventoryView.Snapshot snapshot = ledger.nativeSnapshot("items");
            IAEItemStack[] hot = new IAEItemStack[128];
            Resource[] keys = new Resource[128];
            for (int i = 0; i < hot.length; i++) {
                keys[i] = snapshot.resource(i);
                hot[i] = (IAEItemStack) ResourceCodec.decode(keys[i]);
            }
            IMEInventoryHandler<IAEItemStack>[] handlers = new IMEInventoryHandler[] {
                new BaselineVaultInventory<>(access[n], AEItemStackType.ITEM_STACK_TYPE, "items"),
                new VaultInventory<>(access[n], AEItemStackType.ITEM_STACK_TYPE, "items") };
            NativeResourceCache codecCache = new NativeResourceCache();
            for (int i = 0; i < 20000; i++) {
                for (IMEInventoryHandler<IAEItemStack> handler : handlers)
                    check(handler.getAvailableItem(hot[i % 128], 0) != null, "paired warmup query");
                codecCache.encode(hot[i % 128]);
            }
            Map<String, List<Long>> paired = new LinkedHashMap<>();
            for (int sample = 0; sample < 120; sample++) {
                for (int position = 0; position < 2; position++) {
                    int variant = (position + sample) & 1;
                    long t = System.nanoTime();
                    for (int i = 0; i < 128; i++) check(
                        handlers[variant].getAvailableItem(hot[i], 0)
                            .getStackSize() > 0,
                        "paired point");
                    paired
                        .computeIfAbsent(
                            variant == 0 ? "baseline_batch_ns" : "optimized_batch_ns",
                            k -> new ArrayList<>())
                        .add(System.nanoTime() - t);
                }
                long t = System.nanoTime();
                for (int i = 0; i < 128; i++) check(access[n].available(), "availability");
                paired.computeIfAbsent("availability_batch_ns", k -> new ArrayList<>())
                    .add(System.nanoTime() - t);
                t = System.nanoTime();
                for (int i = 0; i < 128; i++) blackhole = ResourceCodec.encode(hot[i]);
                paired.computeIfAbsent("baseline_encoding_batch_ns", k -> new ArrayList<>())
                    .add(System.nanoTime() - t);
                t = System.nanoTime();
                for (int i = 0; i < 128; i++) blackhole = codecCache.encode(hot[i]);
                paired.computeIfAbsent("cached_encoding_batch_ns", k -> new ArrayList<>())
                    .add(System.nanoTime() - t);
                t = System.nanoTime();
                for (int i = 0; i < 128; i++) check(ledger.amount(keys[i]) > 0, "ledger query");
                paired.computeIfAbsent("ledger_canonical_key_batch_ns", k -> new ArrayList<>())
                    .add(System.nanoTime() - t);
            }
            Map<String, Object> result = new LinkedHashMap<>(), metrics = new LinkedHashMap<>();
            result.put("case", new String[] { "1000_simple", "10000_simple", "10000_mixed_huge", "10000_rich_nbt" }[n]);
            for (Map.Entry<String, List<Long>> entry : paired.entrySet())
                metrics.put(entry.getKey(), stats(entry.getValue()));
            result.put("metrics", metrics);
            result.put("raw", paired);
            result.put("batch_size", 128);
            result.put("samples", 120);
            pairedRows.add(result);
            System.out.println(
                "VAULT_LOOKUP_PAIRED " + new GsonBuilder().create()
                    .toJson(rowWithoutRaw(result)));
        }
    }

    private void rowCheck(String name) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("name", name);
        r.put("passed", true);
        checks.add(r);
    }

    private void add(String key, long value) {
        samples.computeIfAbsent(key, k -> new ArrayList<>())
            .add(value);
    }

    private long allocated() {
        return allocator.getThreadAllocatedBytes(
            Thread.currentThread()
                .getId());
    }

    private static long usedHeapAfterGc() {
        System.gc();
        return ManagementFactory.getMemoryMXBean()
            .getHeapMemoryUsage()
            .getUsed();
    }

    private static Map<String, Object> stats(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("p50", sorted.get(sorted.size() / 2));
        out.put("p95", sorted.get((int) Math.ceil(sorted.size() * .95) - 1));
        out.put("max", sorted.get(sorted.size() - 1));
        out.put(
            "mean",
            sorted.stream()
                .mapToLong(Long::longValue)
                .average()
                .getAsDouble());
        return out;
    }

    private static Map<String, Object> rowWithoutRaw(Map<String, Object> row) {
        Map<String, Object> copy = new LinkedHashMap<>(row);
        copy.remove("raw");
        return copy;
    }

    private void write(Throwable failure) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(
            "passed",
            failure == null && checks.size() == 5
                && (Boolean.getBoolean("aestoragevault.lookupOnly") ? pairedRows.size() == 4 : rows.size() == 8));
        report.put("paired_queries", pairedRows);
        report.put("client_launched", false);
        report.put("java", System.getProperty("java.version"));
        report.put(
            "scope",
            "actual pre-change and optimized adapters, real formed powered vault availability and native AE lists; direct adapter timing, real AE monitor correctness separately; 128 hot + 128 streaming queries and two full enumerations with 16 journal changes per measured tick");
        report.put("rows", rows);
        report.put("checks", checks);
        if (failure != null) {
            report.put("failure", failure.toString());
            report.put("incomplete_samples", samples);
        }
        Files.write(
            server.getFile("vault-read-tests.json")
                .toPath(),
            new GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(report)
                .getBytes(StandardCharsets.UTF_8));
    }

    private void finish(Throwable failure) throws Exception {
        done = true;
        write(failure);
        server.initiateShutdown();
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}

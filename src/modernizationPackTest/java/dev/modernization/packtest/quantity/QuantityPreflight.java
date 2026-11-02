package dev.modernization.packtest.quantity;

import java.lang.management.ManagementFactory;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.GsonBuilder;

import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.aestoragevault.ae.ResourceCodec;
import dev.aestoragevault.core.Resource;

/** Actual dedicated-server tick benchmark; ledger + native codecs, without a simulated TPS claim. */
@Mod(
    modid = "quantitypreflight",
    name = "Quantity preflight",
    version = "1",
    dependencies = "required-after:aestoragevault")
public final class QuantityPreflight {

    private MinecraftServer server;
    private int age, phase, phaseAge, lastIndex = -1;
    private boolean done;
    private LongVaultStore baseline;
    private BigVaultStore bigint;
    private final List<Resource> resources = new ArrayList<>();
    private final List<Map<String, Object>> rows = new ArrayList<>();
    private final List<Long> work = new ArrayList<>(), ticks = new ArrayList<>(), enumerations = new ArrayList<>();
    private final List<Long> allocations = new ArrayList<>();
    private BigInteger initial;
    private long savedMspt;
    private boolean capture;
    private long seedNanos, checkpointNanos;
    private final com.sun.management.ThreadMXBean alloc = (com.sun.management.ThreadMXBean) ManagementFactory
        .getThreadMXBean();

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (Boolean.getBoolean("aestoragevault.quantityPreflight")) FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (done) return;
        try {
            if (server == null) server = MinecraftServer.getServer();
            if (event.phase == TickEvent.Phase.START) {
                if (capture && lastIndex >= 0)
                    savedMspt = dev.modernization.packtest.quantity.probe.TickProbe.lastNanos;
                return;
            }
            if (++age < 80) return;
            if (capture) {
                ticks.add(savedMspt);
                capture = false;
            }
            if (phaseAge == 0) setup();
            if (phaseAge < 180) {
                long allocated = alloc.getThreadAllocatedBytes(
                    Thread.currentThread()
                        .getId());
                long start = System.nanoTime();
                for (int i = 0; i < 1000; i++) {
                    Resource r = resources.get((phaseAge * 1000 + i) % resources.size());
                    long inserted = baseline != null ? baseline.insert(r, 64, true) : bigint.insert(r, 64, true);
                    long extracted = baseline != null ? baseline.extract(r, 64, true) : bigint.extract(r, 64, true);
                    check(inserted == 64 && extracted == 64, "simulation");
                }
                for (int i = 0; i < 100; i++) {
                    Resource r = resources.get((phaseAge * 100 + i) % resources.size());
                    long inserted = baseline != null ? baseline.insert(r, 64, false) : bigint.insert(r, 64, false);
                    long extracted = baseline != null ? baseline.extract(r, 64, false) : bigint.extract(r, 64, false);
                    check(inserted == 64 && extracted == 64, "transfer");
                }
                if (baseline != null) baseline.flush();
                else bigint.flush();
                if (phaseAge % 20 == 0) {
                    long e = System.nanoTime();
                    enumerate();
                    if (phaseAge >= 60) enumerations.add(System.nanoTime() - e);
                }
                if (phaseAge >= 60) {
                    work.add(System.nanoTime() - start);
                    allocations.add(
                        alloc.getThreadAllocatedBytes(
                            Thread.currentThread()
                                .getId())
                            - allocated);
                    capture = true;
                    lastIndex = server.getTickCounter() % 100;
                }
                phaseAge++;
                return;
            }
            finishPhase();
            phase++;
            phaseAge = 0;
            if (phase == 16) finish(null);
        } catch (Throwable e) {
            e.printStackTrace();
            try {
                finish(e);
            } catch (Exception fatal) {
                throw new RuntimeException(fatal);
            }
        }
    }

    private int slot() {
        int trial = phase / 8, slot = phase % 8;
        return trial == 0 ? slot : 7 - slot;
    }

    private int bits() {
        int n = slot() % 4;
        return n < 2 ? 40 : n == 2 ? 127 : 1023;
    }

    private void setup() throws Exception {
        check(
            server.isDedicatedServer() && server.worldServerForDimension(0)
                .getWorldInfo()
                .getWorldName()
                .equals("quantity-preflight"),
            "wrong world");
        resources.clear();
        work.clear();
        ticks.clear();
        enumerations.clear();
        allocations.clear();
        int size = slot() < 4 ? 1000 : 10000;
        for (int i = 0; i < size; i++) {
            IAEStack<?> prototype;
            if (i < size - 1) {
                ItemStack stack = new ItemStack(Items.paper);
                stack.setTagCompound(new NBTTagCompound());
                stack.getTagCompound()
                    .setInteger("Variant", i);
                prototype = AEItemStack.create(stack);
            } else {
                FluidStack fluid = new FluidStack(FluidRegistry.WATER, 1);
                fluid.tag = new NBTTagCompound();
                fluid.tag.setInteger("Variant", i);
                prototype = AEFluidStack.create(fluid);
            }
            resources.add(ResourceCodec.encode(prototype));
        }
        initial = BigInteger.ONE.shiftLeft(bits() - 1);
        Path folder = server.getFile("quantity-data/" + phase)
            .toPath();
        UUID id = UUID.randomUUID();
        long start = System.nanoTime();
        if (slot() % 4 == 0) {
            baseline = new LongVaultStore(folder, id, true);
            baseline.absorb(UUID.randomUUID(), initial.multiply(BigInteger.valueOf(size)), Collections.emptyMap());
            Map<Resource, Long> map = new HashMap<>();
            for (int i = 0; i < size; i++) map.put(resources.get(i), initial.longValue() + i);
            baseline.absorb(UUID.randomUUID(), BigInteger.ONE, map);
            baseline.checkpoint();
        } else {
            bigint = new BigVaultStore(folder, id, true);
            bigint.absorb(UUID.randomUUID(), initial.multiply(BigInteger.valueOf(size)), Collections.emptyMap());
            for (int i = 0; i < size; i++) bigint.seedExact(resources.get(i), initial.add(BigInteger.valueOf(i)));
            bigint.checkpoint();
        }
        seedNanos = System.nanoTime() - start;
    }

    private void enumerate() throws Exception {
        Map<Resource, ?> entries = baseline != null ? baseline.entries() : bigint.entries();
        appeng.api.storage.data.IItemList items = appeng.util.item.AEItemStackType.ITEM_STACK_TYPE
            .createPrimitiveList();
        appeng.api.storage.data.IItemList fluids = appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE
            .createPrimitiveList();
        for (Map.Entry<Resource, ?> entry : entries.entrySet()) {
            Object number = entry.getValue();
            long count = number instanceof BigInteger ? ((BigInteger) number).min(BigInteger.valueOf(1L << 40))
                .longValue() : Math.min((Long) number, 1L << 40);
            IAEStack stack = ResourceCodec.decode(entry.getKey());
            stack.setStackSize(count);
            if (entry.getKey().channel.equals("items")) items.addStorage(stack);
            else fluids.addStorage(stack);
        }
        check(items.size() + fluids.size() == resources.size(), "enumeration dropped types");
    }

    private void finishPhase() throws Exception {
        check(work.size() == 120 && ticks.size() == 120, "missing tick samples " + work.size() + "/" + ticks.size());
        for (int i = 0; i < ticks.size(); i++)
            check(ticks.get(i) >= work.get(i), "tick clock excludes workload: " + ticks.get(i) + "/" + work.get(i));
        BigInteger count = baseline != null ? BigInteger.valueOf(baseline.amount(resources.get(0)))
            : bigint.exactAmount(resources.get(0));
        check(count.equals(initial), "quantity changed");
        long start = System.nanoTime();
        if (baseline != null) {
            baseline.checkpoint();
            baseline.close();
            baseline = null;
        } else {
            bigint.checkpoint();
            bigint.close();
            bigint = null;
        }
        checkpointNanos = System.nanoTime() - start;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("trial", phase / 8);
        row.put("variant", slot() % 4 == 0 ? "baseline" : "bigint");
        row.put("types", resources.size());
        row.put("quantity_bits", bits());
        row.put("measured_ticks", ticks.size());
        row.put("work_ms", stats(work));
        row.put("whole_server_mspt", stats(ticks));
        row.put("native_enumeration_ms", stats(enumerations));
        row.put(
            "alloc_bytes_per_tick",
            allocations.stream()
                .mapToLong(Long::longValue)
                .average()
                .getAsDouble());
        row.put("seed_ms", seedNanos / 1e6);
        row.put("checkpoint_ms", checkpointNanos / 1e6);
        rows.add(row);
        System.out.println(
            "QUANTITY_PREFLIGHT " + new GsonBuilder().create()
                .toJson(row));
        write(null);
    }

    private static Map<String, Double> stats(List<Long> values) {
        List<Long> copy = new ArrayList<>(values);
        Collections.sort(copy);
        Map<String, Double> m = new LinkedHashMap<>();
        m.put(
            "mean",
            copy.stream()
                .mapToLong(Long::longValue)
                .average()
                .getAsDouble() / 1e6);
        m.put("p50", copy.get(copy.size() / 2) / 1e6);
        m.put("p95", copy.get((int) Math.ceil(copy.size() * .95) - 1) / 1e6);
        m.put("max", copy.get(copy.size() - 1) / 1e6);
        return m;
    }

    private void write(Throwable failure) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("passed", failure == null && rows.size() == 16);
        report.put("client_launched", false);
        report.put(
            "scope",
            "server tick workload: 2000 simulations + 200 journal changes and one flush per tick, native mixed item/fluid enumeration every 20 ticks; no AE network routing or GUI clients");
        report.put("java", System.getProperty("java.version"));
        report.put("rows", rows);
        if (failure != null) report.put("failure", failure.toString());
        Files.write(
            server.getFile("quantity-preflight.json")
                .toPath(),
            new GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(report)
                .getBytes(StandardCharsets.UTF_8));
    }

    private void finish(Throwable failure) throws Exception {
        done = true;
        write(failure);
        if (baseline != null) baseline.close();
        if (bigint != null) bigint.close();
        server.initiateShutdown();
    }

    private static void check(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
    }
}

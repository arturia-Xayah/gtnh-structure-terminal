package dev.aeoptimizer.packtest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;

import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.CraftingMode;
import appeng.api.definitions.IBlockDefinition;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AECableType;
import appeng.helpers.PatternHelper;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.IGridProxyable;
import appeng.me.storage.MEInventoryHandler;
import appeng.tile.misc.TileInterface;
import appeng.tile.storage.TileDrive;
import appeng.util.inv.MEInventoryCrafting;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.aeoptimizer.OrderOptimizer;
import dev.aeoptimizer.api.CpuBatchStats;
import dev.aeoptimizer.api.IBatchMedium;
import dev.aeoptimizer.network.OptimizerNetworks;
import dev.aeoptimizer.network.TileOptimizer;
import dev.aeoptimizer.planning.StrictCraftingJob;
import dev.aeoptimizer.ui.OptimizerContainer;
import io.netty.channel.embedded.EmbeddedChannel;

/** Explicit test build only; dedicated disposable world required. No client startup. */
@Mod(
    modid = "aeoptimizertest",
    name = "AE Optimizer Backend Tests",
    version = "test",
    dependencies = "required-after:aeorderoptimizer")
public final class OptimizerPackTest {

    StargateBenchmark stargate;
    WorldServer world;
    MinecraftServer server;
    FakePlayer player;
    PlayerSource source;
    IGridProxyable controller;
    CraftingGridCache cache;
    IMEMonitor<IAEItemStack> storage;
    TileOptimizer optimizer;
    Provider provider;
    CraftingCPUCluster cpu;
    int ticks, phase, waitUntil, index;
    boolean done;
    Future<ICraftingJob> future;
    ICraftingLink link;
    long expected, returned, startTick;
    String current;
    final List<Map<String, Object>> rows = new ArrayList<>();
    TileInterface realInterface;
    TileEntityChest chest;
    long chestItems, initialBatches;
    FluidStock fluidStock;
    IGrid childGrid;
    CraftingGridCache childCache;
    IMEMonitor<IAEItemStack> childStorage;
    int originRound;
    Provider secondProvider;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (Boolean.getBoolean("aeoptimizer.tests") && FMLCommonHandler.instance()
            .getSide()
            .isServer()) {
            ForgeChunkManager.setForcedChunkLoadingCallback(this, (tickets, w) -> {});
            FMLCommonHandler.instance()
                .bus()
                .register(this);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        if (Boolean.getBoolean("aeoptimizer.stargate")) {
            if (stargate == null) stargate = new StargateBenchmark(this);
            stargate.tick();
            return;
        }
        ticks++;
        try {
            if (phase == 0) {
                setup();
                CraftableInputsPackChecks.run(this);
                phase = 1;
                waitUntil = ticks + 160;
                return;
            }
            if (ticks < waitUntil) return;
            check(ticks < 4000, "test tick budget exceeded");
            if (phase == 1) {
                cache = controller.getProxy()
                    .getGrid()
                    .getCache(appeng.api.networking.crafting.ICraftingGrid.class);
                storage = controller.getProxy()
                    .getGrid()
                    .<IStorageGrid>getCache(IStorageGrid.class)
                    .getItemInventory();
                provider = new Provider();
                setPatterns(pattern(new ItemStack[] { iron() }, token("simple")));
                future = order(token("simple"), 1, false);
                phase = 2;
                return;
            }
            if (phase == 2) {
                if (!future.isDone()) return;
                check(
                    future.get()
                        .getClass()
                        .getName()
                        .contains("CraftingJobV2"),
                    "no-component normal changed");
                row("no_component_standard_untouched", true);
                future = order(token("simple"), 1, true);
                phase = 3;
                return;
            }
            if (phase == 3) {
                if (!future.isDone()) return;
                check(
                    future.get()
                        .getClass()
                        .getName()
                        .contains("CraftingJobFast"),
                    "no-component Lite changed");
                row("no_component_lite_untouched", true);
                stock(10000);
                future = order(token("simple"), 10000, true);
                phase = 4;
                return;
            }
            if (phase == 4) {
                if (!future.isDone()) return;
                submit("native_10000", 10000);
                phase = 5;
                return;
            }
            if (phase == 5) {
                if (!dispatchDone()) return;
                check(provider.batches.size() == 10000, "native should dispatch once per craft");
                check(world.setBlock(8, 81, 8, OrderOptimizer.BLOCK, 0, 3), "optimizer placement");
                optimizer = (TileOptimizer) world.getTileEntity(8, 81, 8);
                optimizer.getProxy()
                    .setOwner(player);
                phase = 6;
                waitUntil = ticks + 100;
                return;
            }
            if (phase == 6) {
                check(OptimizerNetworks.find(grid()) == optimizer, "optimizer not online");
                check(!OrderOptimizer.BLOCK.canPlaceBlockAt(world, 8, 82, 8), "second optimizer placement accepted");
                row("duplicate_placement_rejected", true);
                OptimizerContainer gui = new OptimizerContainer(optimizer);
                player.setPosition(8, 81, 8);
                check(gui.enchantItem(player, 0) && optimizer.detailed, "GUI server toggle failed");
                NBTTagCompound n = new NBTTagCompound();
                optimizer.writeToNBT(n);
                TileOptimizer restored = new TileOptimizer();
                restored.readFromNBT(n);
                check(restored.detailed, "detail NBT lost");
                check(!gui.enchantItem(player, 99), "unexpected GUI action accepted");
                player.setPosition(100, 81, 100);
                check(!gui.enchantItem(player, 0) && optimizer.detailed, "out-of-range GUI write accepted");
                player.setPosition(8, 81, 8);
                row("gui_toggle_and_nbt", true);
                planningChecks();
                index = 0;
                phase = 7;
                return;
            }
            if (phase == 7) {
                long[] amounts = { 1, 4095, 4096, 4097, 10000, 1000000, 17, 19, 10000 };
                if (index == amounts.length) {
                    phase = 10;
                    waitUntil = ticks + 5;
                    return;
                }
                provider.reset();
                cache.setDiagnosticsEnabled(index == 4);
                provider.rejectLarge = index == 6;
                provider.limit = index == 7 ? 7 : Long.MAX_VALUE;
                if (index == 8) {
                    fluidStock = new FluidStock(10000000L);
                    ((IStorageGrid) grid().getCache(IStorageGrid.class)).registerCellProvider(fluidStock);
                    grid().postEvent(new appeng.api.networking.events.MENetworkCellArrayUpdate());
                    setPatterns(fluidPattern());
                } else setPatterns(pattern(new ItemStack[] { iron() }, token("bulk")));
                stock(amounts[index]);
                future = order(token("bulk"), amounts[index], false);
                phase = 8;
                return;
            }
            if (phase == 8) {
                if (!future.isDone()) return;
                check(future.get() instanceof StrictCraftingJob, "component did not route to strict Lite");
                submit("optimized_" + index, new long[] { 1, 4095, 4096, 4097, 10000, 1000000, 17, 19, 10000 }[index]);
                phase = 9;
                return;
            }
            if (phase == 9) {
                if (!dispatchDone()) return;
                if (index == 4) {
                    long observed = 0;
                    for (appeng.me.diagnostics.DiagnosticRowView sample : cache
                        .createDiagnosticRows("", appeng.api.config.DiagnosticSortMode.NAME, true))
                        if (sample.stack.isSameType(AEItemStack.create(token("bulk"))))
                            observed += sample.totalProduced;
                    check(observed == 10000, "native diagnostic output count differs: " + observed);
                    row("native_diagnostics_count_bulk_outputs", true);
                    check(provider.batches.equals(Arrays.asList(4096L, 4096L, 1808L)), "10000 batch shape wrong");
                    check(
                        provider.dispatchTicks.stream()
                            .distinct()
                            .count() == 1,
                        "10000 did not dispatch within one tick");
                }
                if (index == 6) check(provider.batches.size() == 17, "reject-large retry lost work");
                if (index == 7) check(provider.batches.equals(Arrays.asList(7L, 7L, 5L)), "receiver capacity ignored");
                if (index == 8) {
                    check(
                        provider.fluidInputs == 10000000L && fluidStock.remaining == 0,
                        "fluid quantity conservation failed");
                    ((IStorageGrid) grid().getCache(IStorageGrid.class)).unregisterCellProvider(fluidStock);
                    grid().postEvent(new appeng.api.networking.events.MENetworkCellArrayUpdate());
                }
                index++;
                phase = 7;
                return;
            }
            if (phase == 10) {
                cache.removeNode(null, provider);
                realInterface = (TileInterface) place(
                    AEApi.instance()
                        .definitions()
                        .blocks()
                        .iface(),
                    8,
                    80,
                    9);
                realInterface.setSide(ForgeDirection.NORTH);
                world.setBlock(8, 80, 10, net.minecraft.init.Blocks.chest, 0, 3);
                chest = (TileEntityChest) world.getTileEntity(8, 80, 10);
                ICraftingPatternDetails p = pattern(new ItemStack[] { iron() }, token("real_interface"));
                realInterface.getInterfaceDuality()
                    .getPatterns()
                    .setInventorySlotContents(0, p.getPattern());
                stock(10000);
                phase = 12;
                waitUntil = ticks + 60;
                return;
            }
            if (phase == 12) {
                future = order(token("real_interface"), 10000, false);
                phase = 13;
                return;
            }
            if (phase == 13) {
                if (!future.isDone()) return;
                link = cache.submitJob(future.get(), null, null, false, source);
                check(link != null, "native interface order refused");
                cpu = (CraftingCPUCluster) cache.getCpus()
                    .iterator()
                    .next();
                chestItems = 0;
                initialBatches = ((CpuBatchStats) cpu).aeo$getBatches();
                startTick = ticks;
                phase = 14;
                return;
            }
            if (phase == 14) {
                long received = 0;
                for (int i = 0; i < chest.getSizeInventory(); i++) {
                    ItemStack item = chest.getStackInSlot(i);
                    if (item == null) continue;
                    check(item.getItem() == Items.iron_ingot, "unexpected chest item");
                    received += item.stackSize;
                    chest.setInventorySlotContents(i, null);
                }
                if (received > 0) {
                    chestItems += received;
                    IAEStack<?> left = cpu.injectItems(
                        AEItemStack.create(token("real_interface"))
                            .setStackSize(received),
                        Actionable.MODULATE,
                        source);
                    if (left != null) storage.injectItems((IAEItemStack) left, Actionable.MODULATE, source);
                }
                if (chestItems < 10000 || cpu.isBusy()) return;
                check(
                    chestItems == 10000 && stockLeft() == 0,
                    "native interface partial reception lost or duplicated input");
                row("native_interface_chest_partial_reception", true);
                long batchCount = ((CpuBatchStats) cpu).aeo$getBatches() - initialBatches;
                check(batchCount == 3, "native interface did not use three bulk transactions: " + batchCount);
                rows.get(rows.size() - 1)
                    .put("bulk_transactions", batchCount);
                rows.get(rows.size() - 1)
                    .put("received_items", chestItems);
                realInterface.getInterfaceDuality()
                    .getPatterns()
                    .setInventorySlotContents(0, null);
                place(
                    AEApi.instance()
                        .definitions()
                        .blocks()
                        .energyCellCreative(),
                    13,
                    80,
                    8);
                TileDrive childDrive = (TileDrive) place(
                    AEApi.instance()
                        .definitions()
                        .blocks()
                        .drive(),
                    14,
                    80,
                    8);
                childDrive.getInternalInventory()
                    .setInventorySlotContents(
                        0,
                        AEApi.instance()
                            .definitions()
                            .items()
                            .cellUniverse()
                            .maybeStack(1)
                            .get());
                phase = 30;
                waitUntil = ticks + 80;
                return;
            }
            if (phase == 30) {
                childGrid = ((IGridProxyable) world.getTileEntity(13, 80, 8)).getProxy()
                    .getGrid();
                childCache = childGrid.getCache(appeng.api.networking.crafting.ICraftingGrid.class);
                childStorage = ((IStorageGrid) childGrid.getCache(IStorageGrid.class)).getItemInventory();
                check(OptimizerNetworks.find(childGrid) == null, "child unexpectedly optimized");
                provider.reset();
                setPatterns(pattern(new ItemStack[] { iron() }, token("cross_grid")));
                childCache.addNode(null, provider);
                check(
                    childStorage.injectItems(
                        AEItemStack.create(iron())
                            .setStackSize(10000),
                        Actionable.MODULATE,
                        source) == null,
                    "child stock failed");
                future = childCache.beginCraftingJob(
                    world,
                    childGrid,
                    source,
                    AEItemStack.create(token("cross_grid"))
                        .setStackSize(10000),
                    CraftingMode.STANDARD,
                    true,
                    job -> {});
                originRound = 0;
                phase = 31;
                return;
            }
            if (phase == 31) {
                if (!future.isDone()) return;
                cpu = (CraftingCPUCluster) cache.getCpus()
                    .iterator()
                    .next();
                check(
                    cpu.submitJob(childGrid, future.get(), source, null) != null,
                    "cross-grid direct submission failed");
                NBTTagCompound cpuSave = new NBTTagCompound();
                cpu.writeToNBT(cpuSave);
                check(cpuSave.hasKey("AEOOrderingGrid"), "cross-grid origin was not persisted");
                dev.aeoptimizer.dispatch.OrderOrigin reloaded = new dev.aeoptimizer.dispatch.OrderOrigin();
                reloaded.read(cpuSave);
                check(reloaded.enabled(grid()) == (originRound == 1), "origin NBT changed opt-in behavior");
                row("cross_grid_origin_nbt_" + originRound, true);
                returned = 0;
                phase = 32;
                return;
            }
            if (phase == 32) {
                if (provider.pending > 0) {
                    long n = provider.pending;
                    provider.pending = 0;
                    IAEStack<?> left = cpu.injectItems(
                        AEItemStack.create(token("cross_grid"))
                            .setStackSize(n),
                        Actionable.MODULATE,
                        source);
                    if (left != null) childStorage.injectItems((IAEItemStack) left, Actionable.MODULATE, source);
                    returned += n;
                }
                if (returned < 10000 || cpu.isBusy()) return;
                if (originRound == 0) {
                    check(provider.batches.size() == 10000, "CPU owner enabled batching for non-opt-in origin");
                    row("cpu_dispatch_respects_non_opt_in_ordering_grid", true);
                    world.setBlockToAir(8, 81, 8);
                    world.setBlock(13, 81, 8, OrderOptimizer.BLOCK, 0, 3);
                    ((TileOptimizer) world.getTileEntity(13, 81, 8)).getProxy()
                        .setOwner(player);
                    originRound = 1;
                    phase = 33;
                    waitUntil = ticks + 80;
                    return;
                }
                check(
                    provider.batches.equals(Arrays.asList(4096L, 4096L, 1808L)),
                    "non-opt-in CPU blocked opt-in origin batching");
                row("cpu_dispatch_respects_opt_in_ordering_grid", true);
                world.setBlockToAir(13, 81, 8);
                world.setBlock(8, 81, 8, OrderOptimizer.BLOCK, 0, 3);
                optimizer = (TileOptimizer) world.getTileEntity(8, 81, 8);
                optimizer.getProxy()
                    .setOwner(player);
                childCache.removeNode(null, provider);
                phase = 40;
                waitUntil = ticks + 80;
                return;
            }
            if (phase == 33) {
                check(
                    OptimizerNetworks.find(grid()) == null && OptimizerNetworks.find(childGrid) != null,
                    "cross-grid component placement failed");
                provider.reset();
                check(
                    childStorage.injectItems(
                        AEItemStack.create(iron())
                            .setStackSize(10000),
                        Actionable.MODULATE,
                        source) == null,
                    "child restock");
                future = childCache.beginCraftingJob(
                    world,
                    childGrid,
                    source,
                    AEItemStack.create(token("cross_grid"))
                        .setStackSize(10000),
                    CraftingMode.STANDARD,
                    false,
                    job -> {});
                phase = 31;
                return;
            }
            if (phase == 40) {
                provider.reset();
                setPatterns(pattern(new ItemStack[] { iron() }, token("round_robin")));
                secondProvider = new Provider();
                secondProvider.patterns = provider.patterns;
                secondProvider.product = provider.product;
                cache.addNode(null, secondProvider);
                stock(10000);
                future = order(token("round_robin"), 10000, false);
                phase = 41;
                return;
            }
            if (phase == 41) {
                if (!future.isDone()) return;
                cpu = (CraftingCPUCluster) cache.getCpus()
                    .iterator()
                    .next();
                check(
                    cache.submitJob(future.get(), null, null, false, source) != null,
                    "multi-medium submission failed");
                returned = 0;
                phase = 42;
                return;
            }
            if (phase == 42) {
                long n = provider.pending + secondProvider.pending;
                provider.pending = 0;
                secondProvider.pending = 0;
                if (n > 0) {
                    IAEStack<?> left = cpu.injectItems(
                        AEItemStack.create(token("round_robin"))
                            .setStackSize(n),
                        Actionable.MODULATE,
                        source);
                    if (left != null) storage.injectItems((IAEItemStack) left, Actionable.MODULATE, source);
                    returned += n;
                }
                if (returned < 10000 || cpu.isBusy()) return;
                check(provider.inputs > 0 && secondProvider.inputs > 0, "bulk dispatch starved a receiver");
                check(
                    provider.inputs + secondProvider.inputs == 10000
                        && provider.batches.size() + secondProvider.batches.size() == 3,
                    "multi-medium counts differ");
                row("multiple_receivers_round_robin", true);
                rows.get(rows.size() - 1)
                    .put("receiver_inputs", Arrays.asList(provider.inputs, secondProvider.inputs));
                cache.removeNode(null, secondProvider);
                phase = 15;
                waitUntil = ticks + 20;
                return;
            }
            if (phase == 15) {
                // Merge an already-existing second optimizer through a newly placed cable.
                world.setBlock(10, 81, 8, OrderOptimizer.BLOCK, 0, 3);
                TileOptimizer duplicate = (TileOptimizer) world.getTileEntity(10, 81, 8);
                duplicate.getProxy()
                    .setOwner(player);
                phase = 16;
                waitUntil = ticks + 40;
                return;
            }
            if (phase == 16) {
                place(
                    AEApi.instance()
                        .definitions()
                        .blocks()
                        .energyCellCreative(),
                    9,
                    81,
                    8);
                phase = 17;
                waitUntil = ticks + 60;
                return;
            }
            if (phase == 17) {
                TileOptimizer duplicate = (TileOptimizer) world.getTileEntity(10, 81, 8);
                check(
                    duplicate.conflict && OptimizerNetworks.find(grid()) == optimizer,
                    "merged duplicate optimizer remained active");
                row("network_merge_disconnects_duplicate", true);
                IGrid isolated = duplicate.getProxy()
                    .getNode()
                    .getGrid();
                Future<ICraftingJob> borrowed = cache.beginCraftingJob(
                    world,
                    isolated,
                    source,
                    AEItemStack.create(token("borrowed")),
                    CraftingMode.STANDARD,
                    true,
                    job -> {});
                check(
                    borrowed.get()
                        .getClass()
                        .getName()
                        .contains("CraftingJobFast"),
                    "cache owner incorrectly enabled child grid");
                row("ordering_grid_without_component_ignores_cache_owner", true);
                world.setBlockToAir(10, 81, 8);
                phase = 18;
                waitUntil = ticks + 80;
                return;
            }
            if (phase == 18) {
                IGrid child = ((IGridProxyable) world.getTileEntity(13, 80, 8)).getProxy()
                    .getGrid();
                CraftingGridCache childCache = child.getCache(appeng.api.networking.crafting.ICraftingGrid.class);
                check(OptimizerNetworks.find(child) == null, "separate child unexpectedly has an optimizer");
                check(
                    childCache
                        .beginCraftingJob(
                            world,
                            grid(),
                            source,
                            AEItemStack.create(token("borrowed")),
                            CraftingMode.STANDARD,
                            true,
                            job -> {})
                        .get() instanceof StrictCraftingJob,
                    "ordering-grid component was not used");
                row("ordering_grid_with_component_overrides_cache_owner", true);
                world.setBlockToAir(13, 81, 8);
                world.setBlockToAir(13, 80, 8);
                world.setBlockToAir(8, 81, 8);
                setPatterns(pattern(new ItemStack[] { iron() }, token("bulk")));
                future = order(token("bulk"), 1, true);
                phase = 11;
                return;
            }
            if (phase == 11) {
                if (!future.isDone()) return;
                check(
                    future.get()
                        .getClass()
                        .getName()
                        .contains("CraftingJobFast"),
                    "removed component still active");
                row("component_removal_restores_native", true);
                finish(null);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    void planningChecks() throws Exception {
        optimizer.detailed = false;
        ItemStack a = token("A"), b = token("B"), c = token("C"), d = token("D");
        setPatterns(pattern(new ItemStack[] { a, b }, a));
        check(
            order(a, 1, false).get()
                .getErrorMessage()
                .contains("AEO_CYCLE"),
            "self loop accepted");
        row("reject_self_cycle", true);
        setPatterns(pattern(new ItemStack[] { a, b }, c), pattern(new ItemStack[] { c, d }, a));
        check(
            order(a, 1, false).get()
                .getErrorMessage()
                .contains("AEO_CYCLE"),
            "two-recipe loop accepted");
        row("reject_multi_node_cycle", true);
        setPatterns(pattern(new ItemStack[] { iron() }, a, b));
        check(
            order(a, 1, false).get()
                .getErrorMessage()
                .contains("AEO_MULTI_OUTPUT"),
            "multi-output accepted");
        row("reject_selected_multi_output", true);
        setPatterns(
            pattern(new ItemStack[] { iron() }, a),
            pattern(new ItemStack[] { b }, b),
            pattern(new ItemStack[] { d }, c, d));
        stock(10);
        ICraftingJob clean = order(a, 5, false).get();
        check(
            clean.getErrorMessage()
                .isEmpty() && !clean.isSimulation(),
            "unrelated invalid route blocked valid order");
        row("unrelated_invalid_patterns_not_checked", true);
        ICraftingPatternDetails preferred = pattern(new ItemStack[] { iron() }, a);
        preferred.setPriority(100);
        setPatterns(preferred, pattern(new ItemStack[] { iron() }, a, b), pattern(new ItemStack[] { a }, a));
        check(
            order(a, 1, false).get()
                .getErrorMessage()
                .isEmpty(),
            "unselected alternative was validated");
        row("unselected_invalid_alternatives_not_checked", true);
        setPatterns(pattern(new ItemStack[] { iron() }, a), pattern(new ItemStack[] { token("missing") }, a));
        List<ICraftingPatternDetails> nativeOrder = cache.getCraftingMultiPatterns()
            .get(AEItemStack.create(a));
        long reportsBefore = dev.aeoptimizer.planning.Reports.DETAILED_REPORTS.get();
        optimizer.detailed = true;
        ICraftingJob detail = order(a, 1, false).get();
        check(
            detail.getErrorMessage()
                .isEmpty(),
            "alternatives interrupted planning");
        ICraftingJob nativeLite = new appeng.crafting.fast.CraftingJobFast<IAEItemStack>(
            world,
            grid(),
            source,
            AEItemStack.create(a),
            CraftingMode.STANDARD,
            null).schedule()
                .get();
        check(detail.isSimulation() == nativeLite.isSimulation(), "selection differs from native Lite");
        check(
            dev.aeoptimizer.planning.Reports.DETAILED_REPORTS.get() == reportsBefore + 1,
            "alternative report not emitted");
        check(
            dev.aeoptimizer.planning.Reports.catalog(((StrictCraftingJob) detail).getContext())
                .get(AEItemStack.create(a))
                .size() == 2,
            "registered alternative lost");
        row("alternatives_report_preserves_native_selection", true);
        optimizer.detailed = false;
        setPatterns(
            pattern(new ItemStack[] { iron() }, new ItemStack(Items.gold_ingot, 4)),
            pattern(new ItemStack[] { new ItemStack(Items.gold_ingot) }, b),
            pattern(new ItemStack[] { new ItemStack(Items.gold_ingot, 2) }, c),
            pattern(new ItemStack[] { b, c }, d));
        stock(1);
        ICraftingJob shared = order(d, 1, false).get();
        check(!shared.isSimulation(), "shared demand was rounded independently");
        appeng.api.storage.data.IItemList<IAEStack<?>> sharedPlan = AEApi.instance()
            .storage()
            .createAEStackList();
        shared.populatePlan(sharedPlan);
        check(
            sharedPlan.findPrecise(AEItemStack.create(iron()))
                .getStackSize() == 1,
            "shared raw bill differs");
        row("shared_dependency_exact_bill", true);
        setPatterns(pattern(new ItemStack[] { iron() }, a));
        check(
            order(a, 100, false).get()
                .isSimulation(),
            "real raw shortage not reported");
        row("real_missing_retained", true);
    }

    void submit(String name, long quantity) throws Exception {
        ICraftingJob job = future.get();
        check(
            !job.isSimulation() && job.getErrorMessage()
                .isEmpty(),
            "dispatch plan invalid " + job.getErrorMessage());
        expected = quantity;
        returned = 0;
        current = name;
        startTick = ticks;
        link = cache.submitJob(job, null, null, false, source);
        check(link != null, "submit failed");
        cpu = (CraftingCPUCluster) cache.getCpus()
            .iterator()
            .next();
    }

    boolean dispatchDone() {
        if (provider.pending > 0) {
            long n = provider.pending;
            provider.pending = 0;
            IAEStack<?> left = cpu.injectItems(
                AEItemStack.create(provider.product)
                    .setStackSize(n),
                Actionable.MODULATE,
                source);
            if (left != null) storage.injectItems((IAEItemStack) left, Actionable.MODULATE, source);
            returned += n;
        }
        if (returned < expected || cpu.isBusy()) return false;
        check(returned == expected && provider.inputs == expected, "input/output accounting differs");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("case", current);
        row.put("quantity", expected);
        row.put("push_calls", provider.batches.size());
        row.put("batches", new ArrayList<>(provider.batches));
        row.put("input_items", provider.inputs);
        row.put("returned_items", returned);
        row.put("fluid_input_mB", provider.fluidInputs);
        row.put("elapsed_ticks", ticks - startTick);
        row.put("dispatch_world_ticks", new ArrayList<>(provider.dispatchTicks));
        row.put("passed", true);
        rows.add(row);
        check(stockLeft() == 0, "stock not fully consumed");
        return true;
    }

    IGrid grid() {
        try {
            return controller.getProxy()
                .getGrid();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    Future<ICraftingJob> order(ItemStack output, long n, boolean lite) {
        return cache.beginCraftingJob(
            world,
            grid(),
            source,
            AEItemStack.create(output)
                .setStackSize(n),
            CraftingMode.STANDARD,
            lite,
            job -> {});
    }

    void stock(long n) {
        long left = stockLeft();
        if (left > 0) storage.extractItems(
            AEItemStack.create(iron())
                .setStackSize(left),
            Actionable.MODULATE,
            source);
        check(
            storage.injectItems(
                AEItemStack.create(iron())
                    .setStackSize(n),
                Actionable.MODULATE,
                source) == null,
            "stock injection failed");
    }

    long stockLeft() {
        IAEItemStack s = storage.getStorageList()
            .findPrecise(AEItemStack.create(iron()));
        return s == null ? 0 : s.getStackSize();
    }

    void setPatterns(ICraftingPatternDetails... patterns) {
        if (provider == null) provider = new Provider();
        else cache.removeNode(null, provider);
        provider.patterns = Arrays.asList(patterns);
        provider.product = patterns[0].getCondensedOutputs()[0].getItemStack();
        cache.addNode(null, provider);
    }

    void row(String name, boolean passed) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("case", name);
        r.put("passed", passed);
        rows.add(r);
    }

    static void check(boolean ok, String text) {
        if (!ok) throw new AssertionError(text);
    }

    static ItemStack iron() {
        return new ItemStack(Items.iron_ingot);
    }

    static ItemStack token(String name) {
        ItemStack s = new ItemStack(Items.paper);
        NBTTagCompound n = new NBTTagCompound();
        n.setString("optimizer_test", name);
        s.setTagCompound(n);
        s.setStackDisplayName(name);
        return s;
    }

    ICraftingPatternDetails pattern(ItemStack[] inputs, ItemStack... outputs) {
        ItemStack p = AEApi.instance()
            .definitions()
            .items()
            .encodedPattern()
            .maybeStack(1)
            .get();
        NBTTagCompound n = new NBTTagCompound();
        n.setBoolean("crafting", false);
        n.setBoolean("substitute", false);
        n.setBoolean("beSubstitute", false);
        NBTTagList in = new NBTTagList(), out = new NBTTagList();
        for (ItemStack s : inputs) in.appendTag(s.writeToNBT(new NBTTagCompound()));
        for (ItemStack s : outputs) out.appendTag(s.writeToNBT(new NBTTagCompound()));
        n.setTag("in", in);
        n.setTag("out", out);
        p.setTagCompound(n);
        return new PatternHelper(p, world);
    }

    ICraftingPatternDetails fluidPattern() throws Exception {
        net.minecraft.item.Item item = null;
        for (Object candidate : net.minecraft.item.Item.itemRegistry) if (candidate.getClass()
            .getName()
            .equals("com.glodblock.github.common.item.ItemFluidEncodedPattern"))
            item = (net.minecraft.item.Item) candidate;
        check(item != null, "modern AE2FC pattern item missing");
        Class<?> cls = Class.forName("com.glodblock.github.util.FluidPatternDetails");
        Object builder = cls.getConstructor(ItemStack.class)
            .newInstance(new ItemStack(item));
        IAEStack<?>[] in = { AEItemStack.create(iron()),
            AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1000)) };
        IAEStack<?>[] out = { AEItemStack.create(token("bulk")) };
        cls.getMethod("setInputs", IAEStack[].class)
            .invoke(builder, (Object) in);
        cls.getMethod("setOutputs", IAEStack[].class)
            .invoke(builder, (Object) out);
        ItemStack encoded = (ItemStack) cls.getMethod("writeToStack")
            .invoke(builder);
        encoded.getTagCompound()
            .setTag(
                "in",
                (NBTTagList) cls.getMethod("writeStackArray", IAEStack[].class)
                    .invoke(null, (Object) in));
        encoded.getTagCompound()
            .setTag(
                "out",
                (NBTTagList) cls.getMethod("writeStackArray", IAEStack[].class)
                    .invoke(null, (Object) out));
        ICraftingPatternDetails result = ((appeng.api.implementations.ICraftingPatternItem) item)
            .getPatternForItem(encoded, world);
        check(
            result.getCondensedAEInputs().length == 2 && result.getAEInputs()[1] instanceof IAEFluidStack,
            "fluid encoded as an item");
        return result;
    }

    static final class FluidStock implements ICellProvider, IMEInventory<IAEFluidStack> {

        long remaining;
        final IAEFluidStack water = AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1));
        final IAEStackType<IAEFluidStack> type = appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;
        final IMEInventoryHandler<IAEFluidStack> handler;

        FluidStock(long amount) {
            remaining = amount;
            handler = new MEInventoryHandler<>(this, type);
        }

        public int getPriority() {
            return 0;
        }

        public List<IMEInventoryHandler> getCellArray(IAEStackType<?> stackType) {
            return stackType == type ? Collections.singletonList(handler) : Collections.emptyList();
        }

        public IAEStackType<?> getStackType() {
            return type;
        }

        public IAEFluidStack injectItems(IAEFluidStack stack, Actionable mode,
            appeng.api.networking.security.BaseActionSource source) {
            if (!water.isSameType(stack)) return stack;
            if (mode == Actionable.MODULATE) remaining = Math.addExact(remaining, stack.getStackSize());
            return null;
        }

        public IAEFluidStack extractItems(IAEFluidStack stack, Actionable mode,
            appeng.api.networking.security.BaseActionSource source) {
            if (!water.isSameType(stack) || remaining == 0) return null;
            long n = Math.min(remaining, stack.getStackSize());
            if (mode == Actionable.MODULATE) remaining -= n;
            return water.copy()
                .setStackSize(n);
        }

        public IItemList<IAEFluidStack> getAvailableItems(IItemList<IAEFluidStack> list) {
            if (remaining > 0) list.add(
                water.copy()
                    .setStackSize(remaining));
            return list;
        }
    }

    void setup() throws Exception {
        server = MinecraftServer.getServer();
        world = server.worldServerForDimension(0);
        check(
            server.isDedicatedServer() && world.getWorldInfo()
                .getWorldName()
                .equals("ae-order-validation"),
            "refusing non-test world");
        for (int x = -1; x <= 2; x++) for (int z = -1; z <= 2; z++) world.theChunkProviderServer.loadChunk(x, z);
        ForgeChunkManager.Ticket ticket = ForgeChunkManager.requestTicket(this, world, ForgeChunkManager.Type.NORMAL);
        check(ticket != null, "chunk ticket");
        ForgeChunkManager.forceChunk(ticket, new net.minecraft.world.ChunkCoordIntPair(0, 0));
        player = new FakePlayer(
            world,
            new GameProfile(UUID.fromString("ae000000-0000-4000-8000-000000000045"), "AEOptimizerTest"));
        NetworkManager wire = new NetworkManager(false);
        new EmbeddedChannel(wire);
        new NetHandlerPlayServer(server, wire, player) {

            @Override
            public void sendPacket(Packet packet) {}
        };
        player.setPosition(8, 80, 8);
        source = new PlayerSource(player, null);
        controller = (IGridProxyable) place(
            AEApi.instance()
                .definitions()
                .blocks()
                .controller(),
            8,
            80,
            8);
        place(
            AEApi.instance()
                .definitions()
                .blocks()
                .energyCellCreative(),
            7,
            80,
            8);
        TileDrive drive = (TileDrive) place(
            AEApi.instance()
                .definitions()
                .blocks()
                .drive(),
            9,
            80,
            8);
        place(
            AEApi.instance()
                .definitions()
                .blocks()
                .craftingStorageSingularity(),
            8,
            80,
            7);
        place(
            AEApi.instance()
                .definitions()
                .blocks()
                .craftingAccelerator4096x(),
            8,
            81,
            7);
        drive.getInternalInventory()
            .setInventorySlotContents(
                0,
                AEApi.instance()
                    .definitions()
                    .items()
                    .cellUniverse()
                    .maybeStack(1)
                    .get());
    }

    net.minecraft.tileentity.TileEntity place(IBlockDefinition def, int x, int y, int z) {
        ItemStack s = def.maybeStack(1)
            .get();
        Block b = Block.getBlockFromItem(s.getItem());
        check(world.setBlock(x, y, z, b, s.getItemDamage(), 3), "placement");
        b.onBlockPlacedBy(world, x, y, z, player, s);
        IGridProxyable tile = (IGridProxyable) world.getTileEntity(x, y, z);
        tile.getProxy()
            .setOwner(player);
        return (net.minecraft.tileentity.TileEntity) tile;
    }

    void finish(Throwable failure) {
        if (done) return;
        done = true;
        try {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("completed", failure == null);
            r.put("cases", rows);
            r.put("client_launched", false);
            r.put("java", System.getProperty("java.version"));
            if (failure != null) r.put("fatal_error", failure.toString());
            Files.write(
                Paths.get("ae-order-report.json"),
                new GsonBuilder().setPrettyPrinting()
                    .create()
                    .toJson(r)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (server != null) server.initiateShutdown();
    }

    final class Provider implements ICraftingProvider, IGridHost, IBatchMedium {

        List<ICraftingPatternDetails> patterns = Collections.emptyList();
        ItemStack product;
        long pending, inputs, fluidInputs, limit = Long.MAX_VALUE;
        boolean rejectLarge;
        final List<Long> batches = new ArrayList<>(), dispatchTicks = new ArrayList<>();

        void reset() {
            pending = 0;
            inputs = 0;
            fluidInputs = 0;
            limit = Long.MAX_VALUE;
            rejectLarge = false;
            batches.clear();
            dispatchTicks.clear();
        }

        public long maxBatch(ICraftingPatternDetails p, long desired) {
            return Math.min(limit, desired);
        }

        public boolean pushBatch(ICraftingPatternDetails p, MEInventoryCrafting table, long crafts) {
            if (rejectLarge && crafts > 1) return false;
            long actual = 0, fluids = 0;
            Map<IAEStack<?>, Long> vector = new LinkedHashMap<>();
            for (int i = 0; i < table.getSizeInventory(); i++) {
                IAEStack<?> stack = table.getAEStackInSlot(i);
                if (stack != null) {
                    vector.merge(stack, stack.getStackSize(), Long::sum);
                    if (stack instanceof IAEFluidStack) fluids += stack.getStackSize();
                    else actual += stack.getStackSize();
                }
            }
            for (IAEStack<?> input : p.getCondensedAEInputs()) check(
                vector.getOrDefault(input, 0L) == Math.multiplyExact(input.getStackSize(), crafts),
                "incorrect scaled input vector");
            inputs += actual;
            fluidInputs += fluids;
            pending += crafts;
            batches.add(crafts);
            dispatchTicks.add(world.getTotalWorldTime());
            return true;
        }

        public boolean pushPattern(ICraftingPatternDetails p, InventoryCrafting table) {
            return pushBatch(p, (MEInventoryCrafting) table, 1);
        }

        public boolean isBusy() {
            return false;
        }

        public void provideCrafting(ICraftingProviderHelper helper) {
            for (ICraftingPatternDetails p : patterns) helper.addCraftingOption(this, p);
        }

        public IGridNode getGridNode(ForgeDirection dir) {
            return null;
        }

        public AECableType getCableConnectionType(ForgeDirection dir) {
            return AECableType.NONE;
        }

        public void securityBreak() {
            throw new UnsupportedOperationException();
        }
    }
}

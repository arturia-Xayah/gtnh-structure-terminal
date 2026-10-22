package dev.aestoragevault.packtest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.ThreadedFileIOBase;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.Upgrades;
import appeng.api.definitions.IBlockDefinition;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.security.ReshuffleActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerPriority;
import appeng.helpers.ReshuffleReport;
import appeng.helpers.ReshuffleTask;
import appeng.me.helpers.IGridProxyable;
import appeng.tile.storage.TileDrive;
import appeng.util.AEStackTypeFilter;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEFluidStackType;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;
import appeng.util.item.IAEStackList;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.aestoragevault.VaultMod;
import dev.aestoragevault.ae.ResourceCodec;
import dev.aestoragevault.core.Resource;
import dev.aestoragevault.ui.OutputContainer;
import dev.aestoragevault.world.TileAccess;
import dev.aestoragevault.world.TileController;
import dev.aestoragevault.world.TileOutput;
import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/** Native powered AE network + file-backed warehouse; no client entry point. */
@Mod(
    modid = "vaultiotests",
    name = "Vault IO backend validation",
    version = "1",
    dependencies = "required-after:aestoragevault;required-after:ae2fc")
public final class VaultIOSuite {

    private MinecraftServer server;
    private WorldServer world;
    private FakePlayer player;
    private TileController controller;
    private TileAccess access;
    private TileOutput output;
    private TileDrive drive;
    private int age;
    private boolean done, restart;
    private long blockedStock, blockedCalls;
    private long measuredCalls;
    private final List<Map<String, Object>> tests = new ArrayList<>();
    private final List<Long> timings = new ArrayList<>();
    private java.nio.file.Path marker;

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        if (!Boolean.getBoolean("aestoragevault.ioTests")) return;
        ForgeChunkManager.setForcedChunkLoadingCallback(this, (tickets, w) -> {});
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        try {
            age++;
            if (age == 1) setup();
            if (restart) {
                if (age == 80) {
                    restartTests();
                    finish();
                }
                return;
            }
            if (output != null && output.exportCalls != measuredCalls) {
                measuredCalls = output.exportCalls;
                timings.add(output.lastNanos);
                check(output.lastScanned <= TileOutput.TYPES_PER_TICK, "per-tick type budget exceeded");
            }
            if (age == 60) {
                priorityAndFilterTests();
                access.setPriority(1000);
            }
            if (age == 63) run("native_high_priority_insertion", () -> {
                networkInsert(
                    AEItemStack.create(new ItemStack(Items.gold_ingot))
                        .setStackSize(64));
                check(
                    controller.store()
                        .amount(item(Items.gold_ingot)) == 64,
                    "high priority ignored");
                access.setPriority(-1000);
            });
            if (age == 66) run("native_low_priority_insertion", () -> {
                networkInsert(
                    AEItemStack.create(new ItemStack(Items.diamond))
                        .setStackSize(64));
                check(
                    controller.store()
                        .amount(item(Items.diamond)) == 0,
                    "low priority ignored");
                check(
                    networkAmount(AEItemStack.create(new ItemStack(Items.diamond))) == 64,
                    "native drive rejected diamond");
                access.setPriority(1000);
            });
            if (age == 70) run("native_Reshuffle_moves_items_and_fluids_by_priority_without_loss", () -> {
                Map<Resource, Long> contents = new HashMap<>();
                contents.put(item(Items.iron_ingot), 1000000L);
                contents.put(water(), 1000000L);
                for (Map.Entry<Resource, Long> entry : contents.entrySet()) networkInsert(
                    ResourceCodec.decode(entry.getKey())
                        .setStackSize(entry.getValue()));
                ReshuffleTask task = new ReshuffleTask(
                    new AEStackTypeFilter(),
                    access.getProxy()
                        .getStorage(),
                    new IAEStackList(),
                    new ReshuffleActionSource(access),
                    true,
                    false);
                task.initialize();
                int calls = 0;
                while (task.isRunning() && calls++ < 10000) task.processNextBatch();
                ReshuffleReport report = task.getReport();
                check(
                    !task.isRunning() && report.cantInject.isEmpty() && report.cantExtract.isEmpty(),
                    "reshuffle unfinished");
                check(report.lostItems.isEmpty() && report.gainedItems.isEmpty(), "reshuffle changed quantities");
                check(
                    controller.store()
                        .amount(item(Items.diamond)) == 64,
                    "reshuffle did not migrate lower priority drive");
                check(
                    controller.store()
                        .amount(item(Items.iron_ingot)) == 1000000,
                    "iron changed");
                check(
                    controller.store()
                        .amount(water()) == 1000000,
                    "water changed");
            });
            if (age == 80) setupOutput();
            if (age == 120)
                run("native_output_batches_one_million_items_and_fluid_without_exposing_private_stock", () -> {
                    check(output.available(), "output inactive " + controller.status);
                    check(
                        controller.store()
                            .amount(item(Items.iron_ingot)) == 0,
                        "iron not exported");
                    check(
                        controller.store()
                            .amount(water()) == 0,
                        "fluid not exported");
                    check(
                        networkAmount(AEItemStack.create(new ItemStack(Items.iron_ingot))) == 1000000,
                        "wrong network iron");
                    check(
                        networkAmount(AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1))) == 1000000,
                        "wrong network water");
                    check(
                        controller.store()
                            .amount(item(Items.gold_ingot)) == 77,
                        "filter leaked gold");
                    check(output.totalBatches < 64, "per-item dispatch " + output.totalBatches);
                    check(
                        output.getCellArray(AEItemStackType.ITEM_STACK_TYPE)
                            .isEmpty()
                            && output.getCellArray(AEFluidStackType.FLUID_STACK_TYPE)
                                .isEmpty(),
                        "self mounted");
                    check(
                        networkAmount(AEItemStack.create(new ItemStack(Items.gold_ingot))) == 0,
                        "private stock leaked into ME monitor");
                    for (int i = 0; i < 36; i++) output.filters.putAEStackInSlot(i, null);
                    output.filters.putAEStackInSlot(35, AEItemStack.create(new ItemStack(Items.gold_ingot)));
                });
            if (age == 200) run("precise_whitelist_addresses_one_type_among_1000_others", () -> {
                check(
                    controller.store()
                        .amount(item(Items.gold_ingot)) == 0,
                    "starved matching type");
                check(networkAmount(AEItemStack.create(new ItemStack(Items.gold_ingot))) == 77, "wrong exported gold");
                check(
                    controller.store()
                        .types() == 1000,
                    "unmatched type changed");
                check(output.lastScanned <= 1, "precise whitelist scanned other stored types");
                for (int i = 0; i < drive.getInternalInventory()
                    .getSizeInventory(); i++)
                    drive.getInternalInventory()
                        .setInventorySlotContents(i, null);
                for (int i = 0; i < 36; i++) output.filters.putAEStackInSlot(i, null);
                blockedStock = stockCount();
                blockedCalls = output.exportCalls;
            });
            if (age == 240) {
                run("full_network_preserves_all_stock_and_backs_off", () -> {
                    check(stockCount() == blockedStock, "full network lost stock");
                    check(output.exportCalls - blockedCalls < 15, "no idle backoff");
                });
                world.setBlockToAir(4, 80, 9);
                world.setBlockToAir(5, 80, 9);
                resetCells();
                blockedStock = stockCount();
            }
            if (age == 280) run("unpowered_network_preserves_stock", () -> {
                check(
                    !output.getProxy()
                        .isActive(),
                    "network remained powered");
                check(stockCount() == blockedStock, "exported without power");
                place(
                    AEApi.instance()
                        .definitions()
                        .blocks()
                        .controller(),
                    5,
                    80,
                    9);
                place(
                    AEApi.instance()
                        .definitions()
                        .blocks()
                        .energyCellCreative(),
                    4,
                    80,
                    9);
            });
            if (age == 320) {
                outputFinalTests();
                saveRestart();
                finish();
            }
        } catch (Throwable error) {
            error.printStackTrace();
            record("harness", false, error.toString());
            try {
                finish();
            } catch (Exception nested) {
                server.initiateShutdown();
            }
        }
    }

    private void setup() throws Exception {
        server = MinecraftServer.getServer();
        world = server.worldServerForDimension(0);
        check(
            server.isDedicatedServer() && world.getWorldInfo()
                .getWorldName()
                .equals("vault-io-validation"),
            "not disposable dedicated world");
        ForgeChunkManager.Ticket ticket = ForgeChunkManager.requestTicket(this, world, ForgeChunkManager.Type.NORMAL);
        ForgeChunkManager.forceChunk(ticket, new net.minecraft.world.ChunkCoordIntPair(0, 0));
        world.theChunkProviderServer.loadChunk(0, 0);
        marker = server.getFile("vault-io-restart.nbt")
            .toPath();
        restart = Files.exists(marker);
        player = new FakePlayer(
            world,
            new GameProfile(UUID.fromString("ae000000-0000-4000-8000-000000000062"), "VaultIOTest"));
        NetworkManager wire = new NetworkManager(false);
        new EmbeddedChannel(wire);
        new NetHandlerPlayServer(server, wire, player) {

            @Override
            public void sendPacket(Packet p) {}
        };
        player.setPosition(7.5, 80, 7.5);
        server.getConfigurationManager().playerEntityList.add(player);
        world.playerEntities.add(player);
        if (!restart) {
            for (int x = 7; x <= 9; x++) for (int y = 79; y <= 81; y++)
                for (int z = 8; z <= 10; z++) world.setBlock(x, y, z, GregTechAPI.sBlockCasings1, 6, 3);
            world.setBlockToAir(8, 80, 9);
            world.setBlock(8, 80, 8, VaultMod.CONTROLLER, 2, 3);
            world.setBlock(7, 80, 9, VaultMod.ACCESS, 0, 3);
            int bus = -1;
            for (int i = 0; i < GregTechAPI.METATILEENTITIES.length; i++) if (GregTechAPI.METATILEENTITIES[i] != null
                && GregTechAPI.METATILEENTITIES[i].getClass() == MTEHatchInputBus.class) {
                    bus = i;
                    break;
                }
            world.setBlockToAir(9, 80, 9);
            check(
                GregTechAPI.METATILEENTITIES[bus].getStackForm(1)
                    .tryPlaceItemIntoWorld(player, world, 9, 80, 9, 1, .5f, .5f, .5f),
                "bus placement");
            place(
                AEApi.instance()
                    .definitions()
                    .blocks()
                    .controller(),
                6,
                80,
                9);
            place(
                AEApi.instance()
                    .definitions()
                    .blocks()
                    .controller(),
                5,
                80,
                9);
            place(
                AEApi.instance()
                    .definitions()
                    .blocks()
                    .energyCellCreative(),
                4,
                80,
                9);
            drive = (TileDrive) place(
                AEApi.instance()
                    .definitions()
                    .blocks()
                    .drive(),
                6,
                81,
                9);
            resetCells();
        }
        controller = (TileController) world.getTileEntity(8, 80, 8);
        access = (TileAccess) world.getTileEntity(7, 80, 9);
        drive = (TileDrive) world.getTileEntity(6, 81, 9);
        if (restart) output = (TileOutput) access;
    }

    private void resetCells() throws Exception {
        drive.getInternalInventory()
            .setInventorySlotContents(
                0,
                AEApi.instance()
                    .definitions()
                    .items()
                    .cell16384k()
                    .maybeStack(1)
                    .get());
        Object fluid = Class.forName("com.glodblock.github.loader.ItemAndBlockHolder")
            .getField("CELL1K")
            .get(null);
        drive.getInternalInventory()
            .setInventorySlotContents(1, new ItemStack((Item) fluid));
    }

    private void priorityAndFilterTests() throws Exception {
        run("priority_uses_native_container_and_persists_signed_int", () -> {
            check(access.available(), "storage access not active");
            controller.store()
                .absorb(UUID.randomUUID(), 8388608, Collections.emptyMap());
            ContainerPriority screen = new ContainerPriority(player.inventory, access);
            screen.setPriority(Integer.MIN_VALUE, player);
            check(access.getPriority() == Integer.MIN_VALUE, "negative priority");
            NBTTagCompound tag = new NBTTagCompound();
            access.writeToNBT(tag);
            TileAccess copy = new TileAccess();
            copy.readFromNBT(tag);
            check(copy.getPriority() == Integer.MIN_VALUE, "priority NBT lost");
            screen.setPriority(Integer.MAX_VALUE, player);
            check(
                ((IMEInventoryHandler) access.getCellArray(AEItemStackType.ITEM_STACK_TYPE)
                    .get(0)).getPriority() == Integer.MAX_VALUE,
                "handler priority mismatch");
        });
        run("36_mixed_filters_native_fuzzy_inverter_and_ore_cards", () -> {
            TileOutput tile = new TileOutput();
            IAEStack<?> iron = AEItemStack.create(new ItemStack(Items.iron_ingot)),
                water = AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1));
            check(tile.filters.getSizeInventory() == 36 && tile.matches(iron) && tile.matches(water), "empty filter");
            tile.filters.putAEStackInSlot(35, water);
            check(tile.matches(water) && !tile.matches(iron), "fluid filter");
            tile.upgrades.setInventorySlotContents(
                0,
                AEApi.instance()
                    .definitions()
                    .materials()
                    .cardInverter()
                    .maybeStack(1)
                    .get());
            check(!tile.matches(water) && tile.matches(iron), "inverter");
            tile.filters.putAEStackInSlot(35, null);
            check(tile.matches(water) && tile.matches(iron), "empty inverter must export all");
            tile.upgrades.setInventorySlotContents(0, null);
            tile.filters.putAEStackInSlot(0, AEItemStack.create(new ItemStack(Items.iron_pickaxe, 1, 0)));
            IAEStack<?> worn = AEItemStack.create(new ItemStack(Items.iron_pickaxe, 1, 100));
            check(!tile.matches(worn), "precise damage ignored");
            tile.upgrades.setInventorySlotContents(
                0,
                AEApi.instance()
                    .definitions()
                    .materials()
                    .cardFuzzy()
                    .maybeStack(1)
                    .get());
            check(tile.matches(worn), "native fuzzy failed");
            tile.upgrades.setInventorySlotContents(
                1,
                AEApi.instance()
                    .definitions()
                    .materials()
                    .cardOreFilter()
                    .maybeStack(1)
                    .get());
            net.minecraftforge.oredict.OreDictionary.registerOre("ingotVaultIOTest", new ItemStack(Items.iron_ingot));
            tile.setFilter("ingotVaultIOTest");
            check(tile.matches(iron) && !tile.matches(water), "ore filter");
            NBTTagCompound saved = new NBTTagCompound();
            tile.writeToNBT(saved);
            TileOutput copy = new TileOutput();
            copy.readFromNBT(saved);
            check(
                copy.filters.getAEStackInSlot(0) != null && copy.getInstalledUpgrades(Upgrades.FUZZY) == 1
                    && copy.getFilter()
                        .equals("ingotVaultIOTest"),
                "filters/cards/settings NBT");
        });
    }

    private void setupOutput() throws Exception {
        for (Map.Entry<Resource, Long> e : controller.store()
            .entries()
            .entrySet())
            controller.store()
                .extract(e.getKey(), e.getValue(), false);
        resetCells();
        world.setBlock(7, 80, 9, VaultMod.OUTPUT, 0, 3);
        output = (TileOutput) world.getTileEntity(7, 80, 9);
        output.getProxy()
            .setOwner(player);
        access = output;
        controller.checkStructure();
        controller.store()
            .insert(item(Items.iron_ingot), 1000000, false);
        controller.store()
            .insert(water(), 1000000, false);
        controller.store()
            .insert(item(Items.gold_ingot), 77, false);
        for (int i = 0; i < 1000; i++) {
            ItemStack paper = new ItemStack(Items.paper);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("variant", i);
            paper.setTagCompound(tag);
            controller.store()
                .insert(ResourceCodec.encode(AEItemStack.create(paper)), 1, false);
        }
        output.filters.putAEStackInSlot(0, AEItemStack.create(new ItemStack(Items.iron_ingot)));
        output.filters.putAEStackInSlot(1, AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)));
        run("link_and_output_are_exclusive_and_output_has_native_channel", () -> {
            check(controller.formed, "output cannot form");
            world.setBlock(9, 81, 10, VaultMod.ACCESS, 0, 3);
            controller.checkStructure();
            check(!controller.formed, "link+output formed");
            world.setBlock(9, 81, 10, GregTechAPI.sBlockCasings1, 6, 3);
            controller.checkStructure();
            check(controller.formed, "repair after duplicate port");
        });
    }

    private void outputFinalTests() throws Exception {
        run("output_GUI_native_phantom_packets_validate_indices_and_distance", () -> {
            OutputContainer gui = new OutputContainer(player, output);
            player.openContainer = gui;
            check(gui.inventorySlots.size() == 39, "physical slot layout");
            Int2ObjectOpenHashMap<IAEStack<?>> slots = new Int2ObjectOpenHashMap<>();
            slots.put(35, AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 12345)));
            slots.put(36, AEItemStack.create(new ItemStack(Items.diamond)));
            slots.put(-1, null);
            gui.receiveSlotStacks(StorageName.CONFIG, slots);
            check(
                output.filters.getAEStackInSlot(35)
                    .getStackSize() == 1,
                "fluid packet did not normalize");
            slots.clear();
            slots.put(0, AEItemStack.create(new ItemStack(Items.diamond)));
            player.setPosition(100, 80, 100);
            gui.receiveSlotStacks(StorageName.CONFIG, slots);
            check(output.filters.getAEStackInSlot(0) == null, "remote filter write accepted");
            player.setPosition(7.5, 80, 7.5);
            player.openContainer = player.inventoryContainer;
        });
        run("output_break_structure_blocks_export_and_retains_inventory", () -> {
            long before = stockCount();
            world.setBlockToAir(9, 81, 10);
            check(!output.available(), "broken output available");
            output.exportTick();
            check(stockCount() == before, "broken output changed stock");
            world.setBlock(9, 81, 10, GregTechAPI.sBlockCasings1, 6, 3);
            controller.checkStructure();
        });
        output.upgrades.setInventorySlotContents(
            0,
            AEApi.instance()
                .definitions()
                .materials()
                .cardFuzzy()
                .maybeStack(1)
                .get());
        output.upgrades.setInventorySlotContents(
            1,
            AEApi.instance()
                .definitions()
                .materials()
                .cardInverter()
                .maybeStack(1)
                .get());
        output.upgrades.setInventorySlotContents(
            2,
            AEApi.instance()
                .definitions()
                .materials()
                .cardOreFilter()
                .maybeStack(1)
                .get());
        output.setFilter("");
        output.filters.putAEStackInSlot(0, AEItemStack.create(new ItemStack(Items.paper)));
        for (Resource resource : controller.store()
            .entries()
            .keySet()) check(!output.matches(ResourceCodec.decode(resource)), "restart fixture must be quiescent");
        run("output_drop_preserves_real_cards_and_phantom_filters", () -> {
            ItemStack drop = VaultMod.OUTPUT.getDrops(world, 7, 80, 9, 0, 0)
                .get(0);
            TileOutput copy = new TileOutput();
            copy.readOutput(drop.getTagCompound());
            check(
                copy.getInstalledUpgrades(Upgrades.INVERTER) == 1 && copy.filters.getAEStackInSlot(35) != null,
                "drop config lost");
        });
        controller.automatic = false;
        controller.markDirty();
    }

    private void saveRestart() throws Exception {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Id", controller.id.toString());
        tag.setLong(
            "Capacity",
            controller.store()
                .capacity());
        tag.setLong("Stock", stockCount());
        tag.setInteger(
            "Types",
            controller.store()
                .types());
        CompressedStreamTools.writeCompressed(tag, Files.newOutputStream(marker));
        world.saveAllChunks(true, null);
        ThreadedFileIOBase.threadedIOInstance.waitForFinish();
    }

    private void restartTests() throws Exception {
        NBTTagCompound tag = CompressedStreamTools.readCompressed(Files.newInputStream(marker));
        run("fresh_process_restores_output_controller_stock_and_capacity", () -> {
            check(
                controller.id.toString()
                    .equals(tag.getString("Id")),
                "identity");
            check(
                controller.store()
                    .capacity() == tag.getLong("Capacity") && stockCount() == tag.getLong("Stock")
                    && controller.store()
                        .types() == tag.getInteger("Types"),
                "ledger changed");
        });
        run("fresh_process_restores_mixed_filters_cards_and_AE_connection", () -> {
            check(output.filters.getAEStackInSlot(35) instanceof IAEFluidStack, "fluid filter lost");
            check(
                output.getInstalledUpgrades(Upgrades.FUZZY) == 1 && output.getInstalledUpgrades(Upgrades.INVERTER) == 1
                    && output.getInstalledUpgrades(Upgrades.ORE_FILTER) == 1,
                "cards lost");
            check(output.available(), "network not restored");
            check(
                output.getCellArray(AEItemStackType.ITEM_STACK_TYPE)
                    .isEmpty(),
                "stock mounted after restart");
        });
    }

    private long stockCount() {
        long count = 0;
        for (long n : controller.store()
            .entries()
            .values()) count += n;
        return count;
    }

    private Resource item(Item item) throws Exception {
        return ResourceCodec.encode(AEItemStack.create(new ItemStack(item)));
    }

    private Resource water() throws Exception {
        return ResourceCodec.encode(AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void networkInsert(IAEStack<?> request) throws Exception {
        IMEInventory inventory = access.getProxy()
            .getStorage()
            .getMEMonitor(request.getStackType());
        check(
            inventory.injectItems(request, Actionable.MODULATE, new MachineSource(access)) == null,
            "network rejected injection");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private long networkAmount(IAEStack<?> request) throws Exception {
        IMEInventory inventory = access.getProxy()
            .getStorage()
            .getMEMonitor(request.getStackType());
        IAEStack<?> result = (IAEStack<?>) inventory.extractItems(
            request.copy()
                .setStackSize(Long.MAX_VALUE),
            Actionable.SIMULATE,
            new MachineSource(access));
        return result == null ? 0 : result.getStackSize();
    }

    private TileEntity place(IBlockDefinition definition, int x, int y, int z) {
        ItemStack stack = definition.maybeStack(1)
            .get();
        Block block = Block.getBlockFromItem(stack.getItem());
        world.setBlock(x, y, z, block, stack.getItemDamage(), 3);
        block.onBlockPlacedBy(world, x, y, z, player, stack);
        TileEntity tile = world.getTileEntity(x, y, z);
        ((IGridProxyable) tile).getProxy()
            .setOwner(player);
        return tile;
    }

    private interface Test {

        void run() throws Exception;
    }

    private void run(String name, Test test) {
        try {
            test.run();
            record(name, true, "");
        } catch (Throwable failure) {
            failure.printStackTrace();
            record(name, false, failure.toString());
        }
    }

    private void record(String name, boolean passed, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("passed", passed);
        row.put("detail", detail);
        tests.add(row);
        System.out.println("VAULT_IO_TEST " + name + " " + passed + " " + detail);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private void finish() throws Exception {
        done = true;
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(
            "passed",
            !tests.isEmpty() && tests.stream()
                .allMatch(row -> Boolean.TRUE.equals(row.get("passed"))));
        report.put("tests", tests);
        report.put("client_launched", false);
        report.put("java", System.getProperty("java.version"));
        Collections.sort(timings);
        if (!timings.isEmpty()) {
            report.put("export_call_p95_us", timings.get(timings.size() * 95 / 100) / 1000.0);
            report.put("export_call_max_us", timings.get(timings.size() - 1) / 1000.0);
        }
        if (output != null) {
            report.put("export_batches", output.totalBatches);
            report.put("exported_units", output.totalMoved);
        }
        Files.write(
            server.getFile(restart ? "vault-io-tests-restart.json" : "vault-io-tests.json")
                .toPath(),
            new GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(report)
                .getBytes(StandardCharsets.UTF_8));
        server.initiateShutdown();
    }
}

package dev.structureterminal.servertest;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.world.BlockEvent;

import com.google.gson.GsonBuilder;
import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.enumerable.Flip;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import dev.structureterminal.StructureTerminal;
import dev.structureterminal.TerminalSettings;
import dev.structureterminal.bridge.NativeBridge;
import dev.structureterminal.bridge.StructureScanner;
import dev.structureterminal.bridge.StructureScanner.Cell;
import dev.structureterminal.bridge.StructureScanner.Scan;
import dev.structureterminal.server.BuildService;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;

@Mod(
    modid = "structureterminalservertest",
    name = "Headless integration fixtures",
    version = "1",
    dependencies = "required-after:structureterminal")
public final class HeadlessSuite {

    private static Block controller;
    private MinecraftServer server;
    private WorldServer world;
    private FakePlayer player;
    private FixtureTile tile;
    private final List<Scenario> scenarios = new ArrayList<Scenario>();
    private final List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
    private int index, age, totalTicks;
    private Scenario current;
    private Scan scan;
    private ForgeChunkManager.Ticket ticket;
    private boolean initialized, finished, cancelBreak, cancelPlace;
    private Cell protectedCell;
    private static final int X = 16, Y = 70, Z = 16;

    private interface Action {

        void run() throws Exception;
    }

    private static final class Scenario {

        String name;
        Action setup, check;

        Scenario(String name, Action setup, Action check) {
            this.name = name;
            this.setup = setup;
            this.check = check;
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (!Boolean.getBoolean("structureterminal.serverTests") || !FMLCommonHandler.instance()
            .getSide()
            .isServer()) throw new IllegalStateException("Headless fixtures require a dedicated test server");
        controller = new BlockContainer(net.minecraft.block.material.Material.iron) {

            @Override
            public TileEntity createNewTileEntity(World world, int meta) {
                return new FixtureTile();
            }
        }.setBlockName("terminal_test_controller")
            .setHardness(2);
        GameRegistry.registerBlock(controller, "controller");
        GameRegistry.registerTileEntity(FixtureTile.class, "TerminalHeadlessFixture");
        StructureLibAPI.registerChannelDescription("test_coil", "structureterminalservertest", "Test coil");
        StructureLibAPI
            .registerChannelItem("test_coil", "structureterminalservertest", 1, new ItemStack(Blocks.wool, 1, 14));
        StructureLibAPI
            .registerChannelItem("test_coil", "structureterminalservertest", 2, new ItemStack(Blocks.wool, 1, 5));
        StructureLibAPI.registerChannelDescription("test_glass", "structureterminalservertest", "Test glass");
        StructureLibAPI
            .registerChannelItem("test_glass", "structureterminalservertest", 1, new ItemStack(Blocks.glass));
        StructureLibAPI.registerChannelItem(
            "test_glass",
            "structureterminalservertest",
            2,
            new ItemStack(Blocks.stained_glass, 1, 5));
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        MinecraftForge.EVENT_BUS.register(this);
        ForgeChunkManager.setForcedChunkLoadingCallback(
            this,
            (tickets, loadedWorld) -> {
                for (ForgeChunkManager.Ticket old : tickets) ForgeChunkManager.releaseTicket(old);
            });
    }

    @SubscribeEvent
    public void preventBreak(BlockEvent.BreakEvent event) {
        if (cancelBreak && protectedCell != null
            && event.x == protectedCell.x
            && event.y == protectedCell.y
            && event.z == protectedCell.z) event.setCanceled(true);
    }

    @SubscribeEvent
    public void preventPlace(BlockEvent.PlaceEvent event) {
        if (cancelPlace && protectedCell != null
            && event.x == protectedCell.x
            && event.y == protectedCell.y
            && event.z == protectedCell.z) event.setCanceled(true);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        try {
            if (!initialized) {
                initialize();
                initialized = true;
            }
            if (++totalTicks > 4000) throw new IllegalStateException("Suite tick timeout");
            if (current == null) {
                if (index == scenarios.size()) {
                    finish();
                    return;
                }
                current = scenarios.get(index++);
                age = 0;
                reset();
                try {
                    current.setup.run();
                } catch (Throwable failure) {
                    record(false, failure);
                    current = null;
                }
            } else if (++age >= 48) {
                try {
                    current.check.run();
                    record(true, null);
                } catch (Throwable failure) {
                    record(false, failure);
                }
                current = null;
            }
        } catch (Throwable failure) {
            Map<String, Object> error = new LinkedHashMap<String, Object>();
            error.put("name", "suite_error");
            error.put("passed", false);
            error.put("error", failure.toString());
            results.add(error);
            failure.printStackTrace();
            finish();
        }
    }

    @SuppressWarnings("unchecked")
    private void initialize() {
        server = MinecraftServer.getServer();
        world = server.worldServerForDimension(0);
        require(server.isDedicatedServer(), "Not a dedicated server");
        require(
            world.getWorldInfo()
                .getWorldName()
                .equals("terminal-test-world"),
            "Wrong test world");
        ticket = ForgeChunkManager.requestTicket(this, world, ForgeChunkManager.Type.NORMAL);
        require(ticket != null, "Unable to keep test chunks loaded");
        for (int cx = 0; cx <= 1; cx++)
            for (int cz = 0; cz <= 1; cz++) ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(cx, cz));
        world.getGameRules()
            .setOrCreateGameRule("doMobSpawning", "false");
        player = new FakePlayer(
            world,
            new GameProfile(UUID.fromString("ae000000-0000-4000-8000-000000000001"), "TerminalTest"));
        final EmbeddedChannel wire = new EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter());
        NetworkManager manager = new NetworkManager(false) {

            @Override
            public Channel channel() {
                return wire;
            }
        };
        new NetHandlerPlayServer(server, manager, player) {

            @Override
            public void sendPacket(Packet packet) {}
        };
        player.setPosition(X + 2.5, Y, Z + 3.5);
        player.capabilities.allowEdit = true;
        player.capabilities.isCreativeMode = false;
        server.getConfigurationManager().playerEntityList.add(player);
        // A dedicated world pauses entity/tile ticks after 1200 ticks with no world players or forced chunks.
        // Register the test actor in both player lists, just as an actual connected player would be registered.
        world.playerEntities.add(player);
        addScenarios();
    }

    private void add(String name, Action setup, Action check) {
        scenarios.add(new Scenario(name, setup, check));
    }

    private void addScenarios() {
        add("empty_survival_build", () -> request(1, new TerminalSettings(), false), () -> {
            complete();
            counts(55, 56, 56);
        });
        add("partial_correct_coils_preserved", () -> {
            fill(Blocks.wool, 14, 4);
            request(1, new TerminalSettings(), false);
        }, () -> {
            complete();
            counts(55, 56, 60);
        });
        add("already_complete_consumes_nothing", () -> {
            fillAll();
            request(1, new TerminalSettings(), false);
        }, () -> {
            complete();
            counts(64, 64, 64);
        });
        add("replace_partial_matching_coils_no_double_charge", () -> {
            fill(Blocks.wool, 14, 4);
            request(4, new TerminalSettings(), true);
        }, () -> {
            complete();
            counts(55, 56, 60);
        });
        add("replace_all_coils_to_new_tier", () -> {
            fillAll();
            player.inventory.setInventorySlotContents(4, new ItemStack(Blocks.wool, 64, 5));
            TerminalSettings settings = new TerminalSettings();
            settings.channels.put("test_coil", 2);
            request(4, settings, true);
        }, () -> {
            require(placed(Blocks.wool, 5) == 8 && placed(Blocks.wool, 14) == 0, "Coil upgrade incomplete");
            require(carried(Blocks.wool, 5) == 56 && carried(Blocks.wool, 14) == 72, "Upgrade accounting wrong");
            conserved(Blocks.wool, 14, 72);
            conserved(Blocks.wool, 5, 64);
            require(!busy(), "Upgrade did not finish");
        });
        add("replace_mixed_existing_coil_tiers", () -> {
            fill(Blocks.wool, 14, 6);
            int changed = 0;
            for (Cell c : scan.cells) if (changed < 4 && world.getBlock(c.x, c.y, c.z) == Blocks.wool) {
                world.setBlock(c.x, c.y, c.z, Blocks.wool, 5, 3);
                changed++;
            }
            request(4, new TerminalSettings(), true);
        }, () -> {
            complete();
            counts(55, 56, 58);
            require(carried(Blocks.wool, 5) == 4, "Old coils not recovered");
        });
        add("replacement_missing_new_material_preserves_old", () -> {
            fillAll();
            TerminalSettings settings = new TerminalSettings();
            settings.channels.put("test_coil", 2);
            request(4, settings, true);
        }, () -> {
            complete();
            counts(64, 64, 64);
            require(placed(Blocks.wool, 5) == 0, "Unpaid new coils");
        });
        add("replacement_requires_confirmation", () -> {
            fillAll();
            TerminalSettings settings = new TerminalSettings();
            settings.channels.put("test_coil", 2);
            player.inventory.setInventorySlotContents(4, new ItemStack(Blocks.wool, 64, 5));
            request(4, settings, false);
        }, () -> {
            complete();
            counts(64, 64, 64);
            require(carried(Blocks.wool, 5) == 64, "Unconfirmed request consumed material");
        });
        add("replacement_place_cancel_rolls_back", () -> {
            fillAll();
            protectedCell = first(Blocks.wool);
            cancelPlace = true;
            TerminalSettings settings = new TerminalSettings();
            settings.channels.put("test_coil", 2);
            player.inventory.setInventorySlotContents(4, new ItemStack(Blocks.wool, 64, 5));
            request(4, settings, true);
        }, () -> {
            require(
                world.getBlock(protectedCell.x, protectedCell.y, protectedCell.z) == Blocks.wool
                    && world.getBlockMetadata(protectedCell.x, protectedCell.y, protectedCell.z) == 14,
                "Old block not restored");
            conserved(Blocks.wool, 14, 72);
            conserved(Blocks.wool, 5, 64);
            require(!busy(), "Canceled replacement still active");
        });
        add("replacement_keeps_filled_container", () -> {
            fillAll();
            Cell c = first(Blocks.iron_block);
            world.setBlock(c.x, c.y, c.z, Blocks.chest, 0, 3);
            ((TileEntityChest) world.getTileEntity(c.x, c.y, c.z))
                .setInventorySlotContents(0, new ItemStack(Blocks.diamond_block, 7));
            request(4, new TerminalSettings(), true);
        }, () -> {
            Cell c = first(Blocks.iron_block);
            require(world.getBlock(c.x, c.y, c.z) == Blocks.chest, "Replacement removed container");
            require(
                ((TileEntityChest) world.getTileEntity(c.x, c.y, c.z)).getStackInSlot(0).stackSize == 7,
                "Container contents changed");
        });
        add("replace_glass_across_block_ids", () -> {
            fillAll();
            TerminalSettings settings = new TerminalSettings();
            settings.channels.put("test_glass", 2);
            player.inventory.setInventorySlotContents(4, new ItemStack(Blocks.stained_glass, 64, 5));
            request(4, settings, true);
        }, () -> {
            require(
                placed(Blocks.stained_glass, 5) == 8 && placed(Blocks.glass, 0) == 0,
                "Glass replacement incomplete");
            require(carried(Blocks.glass, 0) == 72 && carried(Blocks.stained_glass, 5) == 56, "Glass accounting wrong");
            conserved(Blocks.glass, 0, 72);
            conserved(Blocks.stained_glass, 5, 64);
        });
        add("build_with_independent_glass_channel", () -> {
            TerminalSettings settings = new TerminalSettings();
            settings.channels.put("test_glass", 2);
            player.inventory.setInventorySlotContents(4, new ItemStack(Blocks.stained_glass, 64, 5));
            request(1, settings, false);
        }, () -> {
            require(placed(Blocks.stained_glass, 5) == 8 && placed(Blocks.wool, 14) == 8, "Independent channel wrong");
            require(carried(Blocks.glass, 0) == 64 && carried(Blocks.stained_glass, 5) == 56, "Wrong glass consumed");
        });
        add("replacement_break_event_cancellation_respected", () -> {
            fillAll();
            protectedCell = first(Blocks.wool);
            cancelBreak = true;
            TerminalSettings settings = new TerminalSettings();
            settings.channels.put("test_coil", 2);
            player.inventory.setInventorySlotContents(4, new ItemStack(Blocks.wool, 64, 5));
            request(4, settings, true);
        }, () -> {
            require(
                world.getBlockMetadata(protectedCell.x, protectedCell.y, protectedCell.z) == 14,
                "Break-canceled replacement changed block");
            conserved(Blocks.wool, 14, 72);
            conserved(Blocks.wool, 5, 64);
        });
        add("replacement_foreign_obstruction_is_preserved", () -> {
            Cell c = first(Blocks.iron_block);
            world.setBlock(c.x, c.y, c.z, Blocks.bedrock, 0, 3);
            request(4, new TerminalSettings(), true);
        }, () -> {
            Cell c = first(Blocks.iron_block);
            require(world.getBlock(c.x, c.y, c.z) == Blocks.bedrock, "Replacement removed bedrock");
            require(!busy(), "Obstructed replacement did not stop");
        });
        add("wrong_coil_is_not_silently_destroyed", () -> {
            Cell c = first(Blocks.wool);
            world.setBlock(c.x, c.y, c.z, Blocks.wool, 5, 3);
            request(1, new TerminalSettings(), false);
        }, () -> {
            Cell c = first(Blocks.wool);
            require(
                world.getBlock(c.x, c.y, c.z) == Blocks.wool && world.getBlockMetadata(c.x, c.y, c.z) == 5,
                "Wrong coil was destroyed");
            require(!busy(), "Blocked build did not stop");
            conserved(Blocks.iron_block, 0, 64);
            conserved(Blocks.glass, 0, 64);
            conserved(Blocks.wool, 14, 64);
        });
        add("missing_materials_stop_without_free_blocks", () -> {
            player.inventory.setInventorySlotContents(1, new ItemStack(Blocks.iron_block, 4));
            request(1, new TerminalSettings(), false);
        }, () -> {
            require(!busy(), "Missing-material job kept running");
            conserved(Blocks.iron_block, 0, 4);
            require(placed(Blocks.iron_block, 0) <= 4, "Free blocks");
        });
        add("resume_after_materials_restocked", () -> {
            fill(Blocks.iron_block, 0, 4);
            player.inventory.setInventorySlotContents(1, new ItemStack(Blocks.iron_block, 60));
            request(1, new TerminalSettings(), false);
        }, () -> {
            complete();
            counts(55, 56, 56);
        });
        add("foreign_obstruction_preserved", () -> {
            Cell c = first(Blocks.iron_block);
            world.setBlock(c.x, c.y, c.z, Blocks.bedrock, 0, 3);
            request(1, new TerminalSettings(), false);
        }, () -> {
            Cell c = first(Blocks.iron_block);
            require(world.getBlock(c.x, c.y, c.z) == Blocks.bedrock, "Obstruction was replaced");
            require(!busy(), "Obstructed job did not stop");
        });
        add("demolition_returns_all_ordinary_materials", () -> {
            fillAll();
            request(3, new TerminalSettings(), true);
        }, () -> {
            require(nonAir() == 0, "Demolition left ordinary blocks");
            counts(73, 72, 72);
            require(
                world.getTileEntity(X, Y, Z) == tile,
                "Controller identity changed: block=" + world
                    .getBlock(X, Y, Z) + ", tile=" + world.getTileEntity(X, Y, Z) + ", expected=" + tile);
        });
        add("demolition_requires_confirmation", () -> {
            fillAll();
            request(3, new TerminalSettings(), false);
        }, () -> {
            require(nonAir() == 25, "Unconfirmed demolition modified world");
            counts(64, 64, 64);
        });
        add("filled_chest_is_never_demolished", () -> {
            fillAll();
            Cell c = first(Blocks.iron_block);
            world.setBlock(c.x, c.y, c.z, Blocks.chest, 0, 3);
            ((TileEntityChest) world.getTileEntity(c.x, c.y, c.z))
                .setInventorySlotContents(0, new ItemStack(Blocks.diamond_block, 7));
            request(3, new TerminalSettings(), true);
        }, () -> {
            Cell c = first(Blocks.iron_block);
            require(world.getBlock(c.x, c.y, c.z) == Blocks.chest, "Chest removed");
            require(
                ((TileEntityChest) world.getTileEntity(c.x, c.y, c.z)).getStackInSlot(0).stackSize == 7,
                "Chest contents changed");
            require(nonAir() == 1, "Unrelated demolition outcome");
        });
        add("empty_tile_entity_also_skipped_by_default", () -> {
            fillAll();
            Cell c = first(Blocks.iron_block);
            world.setBlock(c.x, c.y, c.z, Blocks.chest, 0, 3);
            request(3, new TerminalSettings(), true);
        }, () -> {
            Cell c = first(Blocks.iron_block);
            require(world.getTileEntity(c.x, c.y, c.z) instanceof TileEntityChest, "Empty tile entity removed");
            require(nonAir() == 1, "Ordinary blocks not removed");
        });
        add("break_event_cancellation_respected", () -> {
            fillAll();
            protectedCell = first(Blocks.wool);
            cancelBreak = true;
            request(3, new TerminalSettings(), true);
        }, () -> {
            require(nonAir() == 1, "Protected block removed");
            require(
                world.getBlock(protectedCell.x, protectedCell.y, protectedCell.z) == Blocks.wool,
                "Protected block missing");
        });
        add("full_inventory_demolition_drops_are_conserved", () -> {
            fillAll();
            for (int i = 1; i < 36; i++) player.inventory.setInventorySlotContents(i, new ItemStack(Blocks.stone, 64));
            request(3, new TerminalSettings(), true);
        }, () -> {
            require(nonAir() == 0, "Demolition incomplete");
            conserved(Blocks.iron_block, 0, 9);
            conserved(Blocks.glass, 0, 8);
            conserved(Blocks.wool, 14, 8);
        });
        add("invalid_selected_material_rejected", () -> {
            TerminalSettings settings = new TerminalSettings();
            settings.choices.put(
                scan.groups.keySet()
                    .iterator()
                    .next(),
                "minecraft:diamond_block@0");
            request(1, settings, false);
        }, () -> {
            require(nonAir() == 0 && !busy(), "Invalid selection allowed");
            counts(64, 64, 64);
        });
        add("readonly_scan_keeps_running_state", () -> {
            tile.running = true;
            for (int i = 0; i < 10; i++) StructureScanner.scan(bridge(), player, new TerminalSettings());
        }, () -> {
            require(
                tile.running && tile.progress > 0,
                "Read-only scan state: running=" + tile.running + ", progress=" + tile.progress);
            require(tile.setterCalls == 0 && tile.checkCalls == 0, "Scan called state-changing methods");
            counts(64, 64, 64);
        });
        add("mirrored_preview_does_not_call_orientation_setter", () -> {
            tile.setExtendedFacing(
                tile.getExtendedFacing()
                    .with(Flip.HORIZONTAL));
            tile.setterCalls = 0;
            tile.running = true;
            for (int i = 0; i < 10; i++) {
                TerminalSettings settings = new TerminalSettings();
                settings.mirror = i % 2 == 0;
                StructureScanner.scan(bridge(), player, settings);
            }
        }, () -> {
            require(
                tile.running && tile.setterCalls == 0 && tile.checkCalls == 0,
                "Mirror preview changed running machine");
            require(
                tile.getExtendedFacing()
                    .getFlip() == Flip.HORIZONTAL,
                "Orientation changed");
        });
        add("shift_right_click_has_no_native_force_check", () -> {
            tile.running = true;
            player.setSneaking(true);
            StructureTerminal.item.onItemUseFirst(player.getHeldItem(), player, world, X, Y, Z, 2, 0.5f, 0.5f, 0.5f);
            player.setSneaking(false);
        }, () -> {
            require(
                tile.running && tile.setterCalls == 0 && tile.checkCalls == 0,
                "Shift-right-click mutated controller");
            counts(64, 64, 64);
        });
        add("logout_cancels_job", () -> {
            request(1, new TerminalSettings(), false);
            server.getConfigurationManager().playerEntityList.remove(player);
        }, () -> require(!busy(), "Offline job kept running"));
        add("changing_held_tool_stops_job", () -> {
            request(1, new TerminalSettings(), false);
            player.inventory.currentItem = 1;
        }, () -> {
            require(nonAir() == 0 && !busy(), "Job continued without terminal");
            counts(64, 64, 64);
        });
    }

    @SuppressWarnings("unchecked")
    private void reset() throws Exception {
        Field jobs = BuildService.class.getDeclaredField("jobs");
        jobs.setAccessible(true);
        ((Map<?, ?>) jobs.get(BuildService.INSTANCE)).clear();
        if (!server.getConfigurationManager().playerEntityList.contains(player))
            server.getConfigurationManager().playerEntityList.add(player);
        for (Object value : new ArrayList<Object>(world.loadedEntityList))
            if (value instanceof EntityItem) ((EntityItem) value).setDead();
        for (int dx = -5; dx <= 5; dx++) for (int dy = -5; dy <= 5; dy++)
            for (int dz = -5; dz <= 5; dz++) world.setBlockToAir(X + dx, Y + dy, Z + dz);
        world.setBlock(X, Y, Z, controller, 0, 3);
        tile = (FixtureTile) world.getTileEntity(X, Y, Z);
        for (int i = 0; i < 36; i++) player.inventory.setInventorySlotContents(i, null);
        player.inventory.currentItem = 0;
        player.inventory.setInventorySlotContents(0, new ItemStack(StructureTerminal.item));
        player.inventory.setInventorySlotContents(1, new ItemStack(Blocks.iron_block, 64));
        player.inventory.setInventorySlotContents(2, new ItemStack(Blocks.glass, 64));
        player.inventory.setInventorySlotContents(3, new ItemStack(Blocks.wool, 64, 14));
        scan = StructureScanner.scan(bridge(), player, new TerminalSettings());
        require(scan.unknown == 0, "Fixture candidates missing");
        require(scan.cells.size() == 26, "Unexpected fixture size " + scan.cells.size());
        tile.setterCalls = tile.checkCalls = 0;
        cancelBreak = false;
        cancelPlace = false;
        protectedCell = null;
    }

    private NativeBridge bridge() {
        return NativeBridge.resolve(world, X, Y, Z, 2);
    }

    private void request(int action, TerminalSettings settings, boolean confirmed) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("action", action);
        tag.setInteger("x", X);
        tag.setInteger("y", Y);
        tag.setInteger("z", Z);
        tag.setInteger("side", 2);
        tag.setBoolean("confirmed", confirmed);
        tag.setTag("settings", settings.write());
        BuildService.INSTANCE.enqueue(player, tag);
    }

    private Cell first(Block block) {
        for (Cell c : scan.cells) if (!c.air && !c.candidates.isEmpty()
            && Block.getBlockFromItem(
                c.candidates.get(0)
                    .getItem())
                == block)
            return c;
        throw new AssertionError("No fixture cell for " + block);
    }

    private void fill(Block block, int meta, int count) {
        for (Cell c : scan.cells) if (count > 0 && !c.air
            && !c.candidates.isEmpty()
            && Block.getBlockFromItem(
                c.candidates.get(0)
                    .getItem())
                == block) {
                    world.setBlock(c.x, c.y, c.z, block, meta, 3);
                    count--;
                }
    }

    private void fillAll() {
        for (Cell c : scan.cells) if (!c.air && !c.candidates.isEmpty()) {
            ItemStack item = c.candidates.get(0);
            world.setBlock(c.x, c.y, c.z, Block.getBlockFromItem(item.getItem()), item.getItemDamage(), 3);
        }
    }

    private int carried(Block block, int meta) {
        int count = 0;
        for (ItemStack item : player.inventory.mainInventory)
            if (item != null && item.getItem() == Item.getItemFromBlock(block) && item.getItemDamage() == meta)
                count += item.stackSize;
        return count;
    }

    private int placed(Block block, int meta) {
        int count = 0;
        for (Cell c : scan.cells)
            if (world.getBlock(c.x, c.y, c.z) == block && world.getBlockMetadata(c.x, c.y, c.z) == meta) count++;
        return count;
    }

    private int nonAir() {
        int count = 0;
        for (Cell c : scan.cells) if (!c.air && !world.isAirBlock(c.x, c.y, c.z)) count++;
        return count;
    }

    private void conserved(Block block, int meta, int expected) {
        int actual = carried(block, meta) + placed(block, meta);
        for (Object e : world.loadedEntityList) if (e instanceof EntityItem && !((EntityItem) e).isDead) {
            ItemStack stack = ((EntityItem) e).getEntityItem();
            if (stack.getItem() == Item.getItemFromBlock(block) && stack.getItemDamage() == meta)
                actual += stack.stackSize;
        }
        require(
            actual == expected,
            "Conservation failed: " + block + "@" + meta + " expected " + expected + " got " + actual);
    }

    private void counts(int iron, int glass, int coils) {
        require(carried(Blocks.iron_block, 0) == iron, "Iron count " + carried(Blocks.iron_block, 0));
        require(carried(Blocks.glass, 0) == glass, "Glass count " + carried(Blocks.glass, 0));
        require(carried(Blocks.wool, 14) == coils, "Coil count " + carried(Blocks.wool, 14));
    }

    private void complete() throws Exception {
        for (Cell c : scan.cells) if (!c.air) {
            ItemStack item = c.candidates.get(0);
            require(
                world.getBlock(c.x, c.y, c.z) == Block.getBlockFromItem(item.getItem())
                    && world.getBlockMetadata(c.x, c.y, c.z) == item.getItemDamage(),
                "Incomplete cell " + c.position());
        }
        require(!busy(), "Completed job still active");
    }

    private boolean busy() throws Exception {
        Field jobs = BuildService.class.getDeclaredField("jobs");
        jobs.setAccessible(true);
        return ((Map<?, ?>) jobs.get(BuildService.INSTANCE)).containsKey(player.getUniqueID());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private void record(boolean passed, Throwable error) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("name", current.name);
        result.put("passed", passed);
        result.put("ticks", age);
        result.put("world_non_air", nonAir());
        result.put("iron_carried", carried(Blocks.iron_block, 0));
        result.put("glass_carried", carried(Blocks.glass, 0));
        result.put("coils_carried", carried(Blocks.wool, 14));
        result.put("running", tile.running);
        result.put("progress_ticks", tile.progress);
        result.put("orientation_setter_calls", tile.setterCalls);
        result.put("structure_check_calls", tile.checkCalls);
        if (error != null) {
            result.put("error", error.toString());
            error.printStackTrace();
        }
        results.add(result);
        System.out.println("TERMINAL_SERVER_TEST " + current.name + " " + (passed ? "PASS" : "FAIL"));
    }

    private void finish() {
        finished = true;
        try {
            Map<String, Object> report = new LinkedHashMap<String, Object>();
            boolean passed = !results.isEmpty();
            for (Map<String, Object> r : results) passed &= Boolean.TRUE.equals(r.get("passed"));
            report.put("passed", passed);
            report.put("dedicated_server", true);
            report.put("gui_started", false);
            report.put("fixture_only", true);
            report.put("gregtech_full_pack_tested", false);
            report.put("tests", results);
            Files.write(
                server.getFile("terminal-server-tests.json")
                    .toPath(),
                new GsonBuilder().setPrettyPrinting()
                    .create()
                    .toJson(report)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            error.printStackTrace();
        }
        if (server != null) {
            server.getConfigurationManager().playerEntityList.remove(player);
            world.playerEntities.remove(player);
            if (ticket != null) ForgeChunkManager.releaseTicket(ticket);
            server.initiateShutdown();
        }
    }
}

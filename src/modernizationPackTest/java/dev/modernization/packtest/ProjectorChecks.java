package dev.modernization.packtest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import com.google.gson.Gson;

import cpw.mods.fml.common.registry.GameRegistry;
import dev.structureterminal.projection.Projection;
import dev.structureterminal.projection.ProjectorMenu;
import dev.structureterminal.projection.ProjectorTile;
import io.netty.buffer.Unpooled;

final class ProjectorChecks {

    private static void expect(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static NBTTagCompound sync(ProjectorTile tile) throws Exception {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            tile.getDescriptionPacket()
                .writePacketData(packet);
            S35PacketUpdateTileEntity received = new S35PacketUpdateTileEntity();
            received.readPacketData(packet);
            // The vanilla NBT accessor is stripped on a dedicated server.
            NBTTagCompound payload = null;
            for (java.lang.reflect.Field field : S35PacketUpdateTileEntity.class.getDeclaredFields()) {
                if (field.getType() == NBTTagCompound.class) {
                    field.setAccessible(true);
                    payload = (NBTTagCompound) field.get(received);
                    break;
                }
            }
            expect(payload != null && !packet.isReadable(), "native sync packet");
            return payload;
        } finally {
            packet.release();
        }
    }

    // Same loaded coordinates and player motion. Both arms stop at contact; no dimension change or terrain I/O.
    private static void compareContact(Map<String, Object> report, WorldServer world, FakePlayer p,
        ProjectorTile gate) {
        int x = gate.xCoord, y = gate.yCoord + 2, z = gate.zCoord;
        expect(world.isAirBlock(x, y, z), "benchmark space");
        int samples = 3000, rounds = 9;
        double[][] times = new double[2][rounds];
        dev.structureterminal.projection.GateTravel travel = new dev.structureterminal.projection.GateTravel();
        for (int pass = 0; pass < rounds + 4; pass++) for (int index = 0; index < 2; index++) {
            int arm = (pass + index) % 2;
            if (arm == 0) world.setBlock(x, y, z, net.minecraft.init.Blocks.portal, 1, 2);
            else world.setBlockToAir(x, y, z);
            p.setPosition(x + .5, y + .05, z + .5);
            p.timeUntilPortal = 1;
            long start = System.nanoTime();
            for (int j = 0; j < samples; j++) {
                p.moveEntity(0, 0, (j & 1) == 0 ? .035 : -.035);
                if (arm == 1) travel.sample(p);
            }
            long elapsed = System.nanoTime() - start;
            if (pass >= 4) times[arm][pass - 4] = elapsed / (1000.0 * samples);
            expect(arm == 0 ? p.timeUntilPortal > 1 : p.timeUntilPortal == 1, "vanilla portal contact isolated");
            expect(
                Math.abs(p.posX - x - .5) < .001 && Math.abs(p.posZ - z - .5) < .001,
                "same movement without transfer");
        }
        p.timeUntilPortal = 0;
        world.setBlockToAir(x, y, z);
        Arrays.sort(times[0]);
        Arrays.sort(times[1]);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("samples_per_round", samples);
        metrics.put("rounds", rounds);
        metrics.put("vanilla_movement_contact_median_us", times[0][rounds / 2]);
        metrics.put("projected_disc_movement_contact_median_us", times[1][rounds / 2]);
        report.put("loaded_contact_benchmark", metrics);
    }

    private static void exerciseTravel(List<String> tests, MinecraftServer server, WorldServer w, FakePlayer p,
        ProjectorTile a, Block block, int x, int y, int z, Map<String, Object> report) throws Exception {
        Projection gate = Projection.DEFAULT;
        String channel = "projector-test";
        expect(
            a.configure(gate, channel, 0, true)
                .isEmpty(),
            "source open");
        int bx = x + 18;
        w.setBlock(bx, y, z, block, 2, 3);
        ProjectorTile b = (ProjectorTile) w.getTileEntity(bx, y, z);
        w.setBlockToAir(bx, y + 1, z);
        w.setBlockToAir(bx, y + 2, z);
        expect(
            b.configure(gate, channel, 4, true)
                .isEmpty(),
            "target open");
        expect(
            b.configure(gate, channel, 4, true, "月球基地", true)
                .isEmpty(),
            "name target");
        expect(
            "月球基地".equals(sync(a).getString("destinationName")) && "主基地".equals(sync(b).getString("destinationName")),
            "paired names");
        b.configure(gate, channel, 4, true, "月面车站", false);
        expect(
            "月面车站".equals(sync(a).getString("destinationName")) && !sync(b).getBoolean("showDestination"),
            "rename and independent label flags");
        b.configure(gate, "elsewhere", 4, true);
        expect(
            sync(a).getString("destinationName")
                .isEmpty(),
            "old partner after retune");
        b.configure(gate, channel, 4, true);
        expect(
            b.configure(gate, channel, 4, true, "bad§kname", true)
                .equals("invalidStation") && "月面车站".equals(sync(a).getString("destinationName")),
            "invalid edit atomic");
        b.configure(gate, channel, 4, false);
        expect(
            sync(a).getString("destinationName")
                .isEmpty(),
            "closed partner");
        b.configure(gate, channel, 4, true);
        tests.add("station_names_pair_rename_retune_close_reopen");
        compareContact(report, w, p, a);
        w.setBlock(bx + 1, y, z, block);
        ProjectorTile c = (ProjectorTile) w.getTileEntity(bx + 1, y, z);
        expect(
            c.configure(gate, "other", 0, true)
                .equals("overlap") && !c.open,
            "overlap");
        expect(
            a.configure(gate, "other", 0, false)
                .isEmpty(),
            "close");
        expect(
            a.configure(gate, channel, 0, true)
                .isEmpty(),
            "reopen");
        w.setBlockToAir(bx + 1, y, z);
        w.setBlock(x + 40, y, z, block);
        c = (ProjectorTile) w.getTileEntity(x + 40, y, z);
        expect(
            c.configure(gate, channel, 0, true)
                .equals("full") && !c.open,
            "third endpoint");
        dev.structureterminal.projection.GateDirectory directory = (dev.structureterminal.projection.GateDirectory) w.perWorldStorage
            .loadData(dev.structureterminal.projection.GateDirectory.class, "structureterminal_projected_gates");
        NBTTagCompound catalog = new NBTTagCompound(), restored = new NBTTagCompound();
        directory.writeToNBT(catalog);
        dev.structureterminal.projection.GateDirectory copy = new dev.structureterminal.projection.GateDirectory(
            "test");
        copy.readFromNBT(catalog);
        copy.writeToNBT(restored);
        expect(
            catalog.equals(restored) && catalog.getTagList("gates", 10)
                .tagCount() == 2,
            "catalog reload");
        w.setBlock(bx + 3, y, z, block);
        ProjectorTile neighbor = (ProjectorTile) w.getTileEntity(bx + 3, y, z);
        expect(
            neighbor.configure(gate.resized(Projection.Shape.DISC, 2, 2, 1, gate.profile()), "", 0, true)
                .isEmpty(),
            "nearby visual gate");
        expect(
            b.configure(gate.resized(Projection.Shape.DISC, 8, 8, 1, gate.profile()), "changed", 0, true)
                .equals("overlap") && b.gate.width == 4
                && b.frequency.equals(channel)
                && b.exitFacing == 4,
            "atomic resize rejection");
        w.setBlockToAir(bx + 3, y, z);
        tests.add("pair_catalog_reload_and_rejected_resize_keeps_original_gate");
        tests.add("paired_frequency_limit_and_overlap_rejection");

        dev.structureterminal.projection.GateTravel travel = new dev.structureterminal.projection.GateTravel();
        p.setPosition(x + .5, y + 1, z + .5);
        travel.sample(p);
        expect(p.posX == x + .5, "animated source");
        a.started -= Projection.OPEN_TICKS;
        b.started -= Projection.OPEN_TICKS;
        w.setBlock(bx, y + 2, z, net.minecraft.init.Blocks.stone);
        travel.sample(p);
        expect(p.posX == x + .5, "obstructed exit");
        w.setBlockToAir(bx, y + 2, z);
        travel = new dev.structureterminal.projection.GateTravel();
        p.setPosition(x - 2, y + 1, z + .5);
        travel.sample(p);
        p.setPosition(x + 3, y + 1, z + .5);
        travel.sample(p);
        expect(p.posX == bx + .5 && p.posY == y + 1 && p.rotationYaw == 270, "swept transfer and exit facing");
        travel.sample(p);
        expect(p.posX == bx + .5, "no bounce");
        p.setPosition(bx + .5, y + 1, z + 2);
        travel.sample(p);
        p.setPosition(bx + .5, y + 1, z + .5);
        travel.sample(p);
        expect(p.posX == x + .5 && p.rotationYaw == 90, "return using default facing");
        tests.add("same_dimension_swept_travel_blocked_exit_facing_and_return");
        w.setBlockToAir(bx, y, z);
        expect(
            sync(a).getString("destinationName")
                .isEmpty(),
            "broken destination label");
        expect(
            c.configure(gate, channel, 0, true)
                .isEmpty(),
            "broken gate released channel");
        w.setBlockToAir(x + 40, y, z);

        // A FakePlayer skips login. Initialise WR-CBE's normal per-player state before firing Forge's dimension event.
        Class<?> ether = Class.forName("codechicken.wirelessredstone.addons.RedstoneEtherAddons");
        Object wireless = ether.getMethod("server")
            .invoke(null);
        wireless.getClass()
            .getMethod("onLogin", net.minecraft.entity.player.EntityPlayer.class)
            .invoke(wireless, p);
        // Leave the arrival aperture before sampling the new pair (also exercises the persisted arrival latch).
        travel = new dev.structureterminal.projection.GateTravel();
        travel.sample(p);
        expect(p.posX == x + .5, "arrival latch across player tracking restart");
        p.setPosition(x + 2, y + 1, z + .5);
        travel.sample(p);
        WorldServer nether = server.worldServerForDimension(-1);
        int ny = 136, nx = 0, nz = 0;
        nether.setBlock(nx, ny, nz, block, 3, 3);
        nether.setBlockToAir(nx, ny + 1, nz);
        nether.setBlockToAir(nx, ny + 2, nz);
        ProjectorTile d = (ProjectorTile) nether.getTileEntity(nx, ny, nz);
        expect(
            d.configure(gate, channel, 0, true)
                .isEmpty(),
            "cross-dimension pair");
        d.configure(gate, channel, 0, true, "下界", true);
        expect(
            "下界".equals(sync(a).getString("destinationName")) && "主基地".equals(sync(d).getString("destinationName")),
            "cross-dimensional names");
        d.started -= Projection.OPEN_TICKS;
        SceneChecks.run(p, a, d, tests);
        p.setPosition(x + .5, y + 1, z + .5);
        travel.sample(p);
        expect(
            p.dimension == -1 && p.worldObj == nether && p.posX == nx + .5 && p.rotationYaw == 270,
            "nether arrival");
        p.setPosition(nx + 2, ny + 1, nz + .5);
        travel.sample(p);
        p.setPosition(nx + .5, ny + 1, nz + .5);
        travel.sample(p);
        expect(p.dimension == 0 && p.worldObj == w && p.posX == x + .5, "overworld return");
        nether.setBlockToAir(nx, ny, nz);
        tests.add("overworld_nether_roof_round_trip");
        WorldServer end = server.worldServerForDimension(1);
        int ey = 110;
        end.setBlock(0, ey, 0, block);
        end.setBlockToAir(0, ey + 1, 0);
        end.setBlockToAir(0, ey + 2, 0);
        ProjectorTile endGate = (ProjectorTile) end.getTileEntity(0, ey, 0);
        expect(
            endGate.configure(gate, channel, 0, true)
                .isEmpty(),
            "End pair");
        endGate.started -= Projection.OPEN_TICKS;
        p.setPosition(x + 2, y + 1, z + .5);
        travel.sample(p);
        p.setPosition(x + .5, y + 1, z + .5);
        travel.sample(p);
        expect(p.dimension == 1 && p.worldObj == end && p.posY == ey + 1, "End arrival");
        p.setPosition(.5, ey + 1, 2);
        travel.sample(p);
        p.setPosition(.5, ey + 1, .5);
        travel.sample(p);
        expect(
            p.dimension == 0 && p.worldObj == w
                && w.playerEntities.contains(p)
                && !end.playerEntities.contains(p)
                && p.posX == x + .5
                && p.posY == y + 1,
            "End exit ownership and position");
        end.setBlockToAir(0, ey, 0);
        tests.add("end_departure_entity_membership_and_position");
    }

    static void run() throws Exception {
        MinecraftServer server = MinecraftServer.getServer();
        Map<String, Object> report = new LinkedHashMap<>();
        List<String> tests = new ArrayList<>();
        try {
            WorldServer w = server.worldServerForDimension(0);
            int x = w.getSpawnPoint().posX, z = w.getSpawnPoint().posZ, y = 104;
            List<String> chat = new ArrayList<>();
            FakePlayer p = new FakePlayer(
                w,
                FakePlayerFactory.getMinecraft(w)
                    .getGameProfile()) {

                @Override
                public void addChatMessage(net.minecraft.util.IChatComponent message) {
                    chat.add(net.minecraft.util.IChatComponent.Serializer.func_150696_a(message));
                }
            };
            net.minecraft.network.NetworkManager wire = new net.minecraft.network.NetworkManager(false);
            new io.netty.channel.embedded.EmbeddedChannel(wire);
            new net.minecraft.network.NetHandlerPlayServer(server, wire, p) {

                @Override
                public void sendPacket(net.minecraft.network.Packet packet) {}
            };
            p.setPosition(x + .5, y, z + 2);
            Block block = GameRegistry.findBlock("structureterminal", "portal_projector");
            expect(block != null && w.setBlock(x, y, z, block), "missing block");
            p.rotationYaw = 90;
            block.onBlockPlacedBy(w, x, y, z, p, new ItemStack(block));
            ProjectorTile tile = (ProjectorTile) w.getTileEntity(x, y, z);
            expect(tile != null && !tile.canUpdate() && tile.getBlockMetadata() == 1, "orientation");
            tests.add("dedicated_registration_and_placement");
            ProjectorMenu menu = ProjectorMenu.at(w, x, y, z);
            expect(menu != null && menu.canInteractWith(p), "editor");
            p.openContainer = menu;
            NBTTagCompound command = new NBTTagCompound();
            Projection custom = new Projection(
                Projection.Shape.CUSTOM,
                7,
                4,
                7,
                new int[] { 2, 3, 4, 8, 10, 9, 1 },
                0x000000,
                0xfd13ee);
            ProjectorTile.encode(custom, command);
            command.setString("frequency", "  ");
            command.setString("stationName", "主基地");
            command.setBoolean("showDestination", false);
            command.setInteger("window", menu.windowId + 1);
            ProjectorMenu.apply(p, command);
            expect(tile.gate.shape == Projection.Shape.DISC, "stale editor");
            command.setInteger("window", menu.windowId);
            ProjectorMenu.apply(p, command);
            expect(
                tile.gate.shape == custom.shape && Arrays.equals(tile.gate.profile(), custom.profile()),
                "edit not applied");
            expect(
                chat.stream()
                    .filter(line -> line.contains("projector.emptyFrequency"))
                    .count() == 1 && tile.open
                    && tile.frequency.isEmpty(),
                "single successful empty-frequency notice: " + chat);
            tests.add("blank_frequency_menu_feedback");
            tests.add("apply_uses_live_editor");
            NBTTagCompound saved = new NBTTagCompound();
            tile.writeToNBT(saved);
            ProjectorTile loaded = (ProjectorTile) TileEntity.createAndLoadEntity(saved);
            expect(
                loaded != null && loaded.gate.width == 7
                    && loaded.gate.height == 4
                    && loaded.gate.primary == 0
                    && loaded.gate.secondary == 0xfd13ee
                    && loaded.gate.innerQuarters == 7
                    && loaded.started == tile.started
                    && loaded.stationName.equals("主基地")
                    && !loaded.showDestination,
                "save/load");
            NBTTagCompound payload = sync(tile);
            Projection synced = ProjectorTile.decode(payload);
            expect(
                Arrays.equals(synced.profile(), custom.profile()) && synced.primary == custom.primary
                    && synced.secondary == custom.secondary
                    && synced.innerQuarters == custom.innerQuarters
                    && payload.getLong("started") == tile.started
                    && !payload.getBoolean("showDestination")
                    && "主基地".equals(payload.getString("stationName")),
                "sync payload");
            NBTTagCompound old = new NBTTagCompound();
            old.setInteger("width", 4);
            old.setInteger("height", 4);
            old.setInteger("shape", 2);
            old.setInteger("bore", 72);
            old.setIntArray("profile", custom.profile());
            Projection legacy = ProjectorTile.decode(old);
            expect(
                legacy.innerRadius() == 1.5 && legacy.primary == Projection.ICE_BLUE
                    && legacy.secondary == Projection.FROST,
                "legacy settings");
            tests.add("pre_palette_save_migration");
            tests.add("saved_and_synced_shape_and_animation_time");
            for (int dy = 1; dy <= 4; dy++)
                for (int dx = -4; dx <= 4; dx++) expect(w.isAirBlock(x + dx, y + dy, z), "projection occupied world");
            exerciseTravel(tests, server, w, p, tile, block, x, y, z, report);
            w.setBlockToAir(x, y, z);
            expect(w.getTileEntity(x, y, z) == null && !menu.canInteractWith(p), "removed projector");
            tests.add("projection_leaves_world_empty");
            report.put("passed", true);
        } catch (Throwable error) {
            error.printStackTrace();
            report.put("passed", false);
            report.put("error", error.toString());
        }
        report.put("tests", tests);
        Files.write(
            server.getFile("projector-tests.json")
                .toPath(),
            new Gson().toJson(report)
                .getBytes(StandardCharsets.UTF_8));
        server.initiateShutdown();
    }
}

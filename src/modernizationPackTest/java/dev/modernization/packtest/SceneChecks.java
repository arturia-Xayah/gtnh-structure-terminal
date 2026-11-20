package dev.modernization.packtest;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import dev.structureterminal.net.TerminalNetwork;
import dev.structureterminal.projection.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class SceneChecks {

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    private static void manyGates(FakePlayer player, ProjectorTile existing, WorldServer world) {
        int count = 12, x = existing.xCoord, y = 205, z = existing.zCoord, exitX = x + 32;
        int landmarkY = (int) Math.floor(SceneView.centerY(y, Projection.DEFAULT.height));
        long start = world.getTotalWorldTime();
        NBTTagCompound[] requests = new NBTTagCompound[count];
        try {
            player.setPosition(x + .5, landmarkY, z + count / 2.0);
            for (int i = 0; i < count; i++) {
                String frequency = "multi-view-" + i;
                for (int gateX : new int[] { x, exitX }) {
                    world.setBlock(gateX, y, z + i, existing.getBlockType(), 0, 3);
                    ProjectorTile gate = (ProjectorTile) world.getTileEntity(gateX, y, z + i);
                    check(
                        gate.configure(Projection.DEFAULT, frequency, 0, true, "", false, gateX == x, i % 2 == 1)
                            .isEmpty(),
                        "open multi-view gate " + i);
                    gate.started -= Projection.OPEN_TICKS;
                }
                world.setBlock(exitX + 2, landmarkY, z + i + 1, Blocks.wool, i, 2);
                NBTTagCompound q = requests[i] = new NBTTagCompound();
                q.setInteger("dim", player.dimension);
                q.setInteger("x", x);
                q.setInteger("y", y);
                q.setInteger("z", z + i);
                q.setLong("token", i + 1);
            }
            for (int elapsed : new int[] { 0, SceneView.LIVE_TICKS, SceneView.SNAPSHOT_TICKS }) {
                world.getWorldInfo()
                    .incrementTotalWorldTime(start + elapsed);
                for (int i = count - 1; i >= 0; i--) {
                    NBTTagCompound q = requests[i], reply = SceneService.read(player, q);
                    check(
                        reply != null && reply.hasKey("view") && reply.getLong("token") == i + 1,
                        "independent reply " + i);
                    boolean still = i % 2 == 1;
                    if (still && elapsed == SceneView.LIVE_TICKS) {
                        check(
                            !reply.hasKey("terrain") && reply.getInteger("refreshIn") == 195,
                            "still frame retained across twelve destinations");
                    } else if (elapsed == 0 || elapsed == SceneView.LIVE_TICKS || still) {
                        int cell = reply.getIntArray("terrain")[SceneView.index(2, 0, 1)];
                        check(
                            cell >>> 8 == Block.getIdFromBlock(Blocks.wool)
                                && (cell >> 4 & 15) == (elapsed == 0 ? i : 15),
                            "correct landmark " + i);
                    }
                    q.setLong("revision", reply.getLong("revision"));
                    check(
                        SceneService.read(player, q)
                            .getBoolean("busy"),
                        "throttle only the same source");
                }
                if (elapsed == 0)
                    for (int i = 0; i < count; i++) world.setBlock(exitX + 2, landmarkY, z + i + 1, Blocks.wool, 15, 2);
            }
        } finally {
            for (int i = 0; i < count; i++) {
                world.setBlockToAir(x, y, z + i);
                world.setBlockToAir(exitX, y, z + i);
                world.setBlockToAir(exitX + 2, landmarkY, z + i + 1);
            }
        }
    }

    static void run(FakePlayer p, ProjectorTile a, ProjectorTile b, List<String> cases) {
        WorldServer w = (WorldServer) a.getWorldObj(), dest = (WorldServer) b.getWorldObj();
        long time = w.getTotalWorldTime();
        String channel = a.frequency;
        int y = (int) Math.floor(SceneView.centerY(b.yCoord, b.gate.height));
        double px = p.posX, py = p.posY, pz = p.posZ;
        NBTTagCompound q = new NBTTagCompound();
        q.setInteger("dim", p.dimension);
        q.setInteger("x", a.xCoord);
        q.setInteger("y", a.yCoord);
        q.setInteger("z", a.zCoord);
        q.setLong("token", 1234);
        try {
            p.setPosition(a.xCoord + .5, a.yCoord + 2, a.zCoord + .5);
            check(SceneService.read(p, q) == null, "disabled gates reject remote views");
            a.configure(a.gate, channel, a.exitFacing, true, a.stationName, true, true, true);
            a.started -= Projection.OPEN_TICKS;
            NBTTagCompound save = new NBTTagCompound();
            a.writeToNBT(save);
            ProjectorTile loaded = new ProjectorTile();
            loaded.readFromNBT(save);
            check(loaded.sceneEnabled && loaded.snapshot, "persist view settings");
            // This test's nether gate faces east, and its target landmark is a block above the roof.
            dest.setBlock(b.xCoord + 3, y, b.zCoord, Blocks.gold_block);
            dest.setBlock(b.xCoord - 3, y, b.zCoord, Blocks.diamond_block);
            dest.setBlock(b.xCoord + 16, y, b.zCoord + 4, Blocks.diamond_block);
            int chunks = dest.theChunkProviderServer.getLoadedChunkCount();
            p.setPosition(
                a.xCoord + .5 + 16.001,
                SceneView.centerY(a.yCoord, a.gate.height) - p.getEyeHeight(),
                a.zCoord + .5);
            check(SceneService.read(p, q) == null, "16 block request radius");
            p.setPosition(
                a.xCoord + .5 + 16,
                SceneView.centerY(a.yCoord, a.gate.height) - p.getEyeHeight(),
                a.zCoord + .5);
            NBTTagCompound first = SceneService.read(p, q);
            check(
                first != null && first.hasKey("terrain") && first.getInteger("refreshIn") == 200,
                "first snapshot at boundary");
            int[] terrain = first.getIntArray("terrain");
            check(
                terrain[SceneView.index(3, 0, 0)] >>> 8 == Block.getIdFromBlock(Blocks.gold_block),
                "actual destination landmark");
            check(
                (terrain[SceneView.index(-3, 0, 0)] >>> 8) == 0 && (terrain[SceneView.index(16, 0, 4)] >>> 8) == 0,
                "exit half-space and 16 radius");
            check(dest.theChunkProviderServer.getLoadedChunkCount() == chunks, "preview loaded new chunks");
            ByteBuf bytes = Unpooled.buffer();
            try {
                new TerminalNetwork.SceneReply(first).toBytes(bytes);
                TerminalNetwork.SceneReply wire = new TerminalNetwork.SceneReply();
                wire.fromBytes(bytes);
                check(wire.tag.equals(first) && !bytes.isReadable(), "scene wire round trip");
            } finally {
                bytes.release();
            }
            dest.setBlock(b.xCoord + 3, y, b.zCoord, Blocks.iron_block);
            // Query at 195 to allow the per-player five-tick request limit to expire exactly at 200.
            q.setLong("revision", first.getLong("revision"));
            w.getWorldInfo()
                .incrementTotalWorldTime(time + 195);
            NBTTagCompound cached = SceneService.read(p, q);
            check(!cached.hasKey("terrain") && cached.getInteger("refreshIn") == 5, "snapshot stayed fixed");
            w.getWorldInfo()
                .incrementTotalWorldTime(time + 200);
            NBTTagCompound refreshed = SceneService.read(p, q);
            check(
                refreshed.getIntArray("terrain")[SceneView.index(3, 0, 0)] >>> 8
                    == Block.getIdFromBlock(Blocks.iron_block),
                "snapshot refreshed at tick 200");
            a.configure(a.gate, channel, a.exitFacing, true, a.stationName, true, true, false);
            a.started -= Projection.OPEN_TICKS;
            w.getWorldInfo()
                .incrementTotalWorldTime(time + 205);
            NBTTagCompound live = SceneService.read(p, q);
            check(live.getInteger("refreshIn") == SceneView.LIVE_TICKS, "live sampler cadence");
            dest.setBlock(b.xCoord + 3, y, b.zCoord, Blocks.emerald_block);
            w.getWorldInfo()
                .incrementTotalWorldTime(time + 210);
            check(
                SceneService.read(p, q)
                    .getIntArray("terrain")[SceneView.index(3, 0, 0)] >>> 8
                    == Block.getIdFromBlock(Blocks.emerald_block),
                "live block update");
            b.configure(b.gate, channel, b.exitFacing, false);
            w.getWorldInfo()
                .incrementTotalWorldTime(time + 215);
            check(
                !SceneService.read(p, q)
                    .hasKey("view"),
                "disconnected exit releases image");
            manyGates(p, a, w);
            cases.add("twelve_nearby_gates_receive_distinct_scenes_and_independent_updates");
            cases.add("cross_dimension_scene_distance_halfspace_no_chunk_tickets_and_wire");
            cases.add("camera_settings_saved_live_update_snapshot_200_ticks_disconnect");
        } finally {
            SceneService.clear();
            w.getWorldInfo()
                .incrementTotalWorldTime(time);
            a.configure(a.gate, channel, a.exitFacing, true, a.stationName, a.showDestination, false, false);
            a.started -= Projection.OPEN_TICKS;
            b.configure(b.gate, channel, b.exitFacing, true);
            b.started -= Projection.OPEN_TICKS;
            p.setPosition(px, py, pz);
        }
    }
}

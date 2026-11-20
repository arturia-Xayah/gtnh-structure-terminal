package dev.structureterminal.projection;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;

import dev.structureterminal.net.TerminalNetwork;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Server thread only. Read loaded chunks directly; never acquire a ticket or send a machine's saved inventory. */
public final class SceneService {

    private static final Map<EntityPlayerMP, Map<ProjectorTile, Long>> requested = new WeakHashMap<>();
    private static final Map<ProjectorTile, Destination> cache = new WeakHashMap<>();
    private static long revision, lastPrune = Long.MIN_VALUE;

    private static final class Destination {

        final Sample[] modes = new Sample[2];
        long lastUse;
    }

    private static final class Sample {

        long time, revision, opened;
        int facing;
        int[] terrain;
        NBTTagCompound content;
    }

    public static void clear() {
        cache.clear();
        requested.clear();
        lastPrune = Long.MIN_VALUE;
    }

    public static void expire(long clock) {
        if (lastPrune == Long.MIN_VALUE || clock < lastPrune || clock - lastPrune >= SceneView.SNAPSHOT_TICKS) {
            cache.values()
                .removeIf(entry -> clock < entry.lastUse || clock - entry.lastUse > 2 * SceneView.SNAPSHOT_TICKS);
            for (Map<ProjectorTile, Long> recent : requested.values()) recent.values()
                .removeIf(t -> clock < t || clock - t >= SceneView.LIVE_TICKS);
            lastPrune = clock;
        }
    }

    public static void request(EntityPlayerMP p, NBTTagCompound request) {
        NBTTagCompound reply = read(p, request);
        if (reply != null) TerminalNetwork.channel.sendTo(new TerminalNetwork.SceneReply(reply), p);
    }

    public static NBTTagCompound read(EntityPlayerMP p, NBTTagCompound request) {
        if (p.isDead || p.dimension != request.getInteger("dim")) return null;
        int x = request.getInteger("x"), y = request.getInteger("y"), z = request.getInteger("z");
        if (!p.worldObj.blockExists(x, y, z)) return null;
        TileEntity te = p.worldObj.getTileEntity(x, y, z);
        if (!(te instanceof ProjectorTile)) return null;
        ProjectorTile a = (ProjectorTile) te;
        long now = p.worldObj.getTotalWorldTime();
        if (!a.open || !a.sceneEnabled
            || now - a.started < Projection.OPEN_TICKS
            || !SceneView.inRange(
                p.posX - x - .5,
                p.posY + p.getEyeHeight() - SceneView.centerY(y, a.gate.height),
                p.posZ - z - .5))
            return null;
        long clock = net.minecraft.server.MinecraftServer.getServer()
            .worldServerForDimension(0)
            .getTotalWorldTime();
        NBTTagCompound out = new NBTTagCompound();
        out.setBoolean("sceneReply", true);
        out.setLong("token", request.getLong("token"));
        Map<ProjectorTile, Long> gates = requested.computeIfAbsent(p, ignored -> new WeakHashMap<>());
        Long last = gates.get(a);
        if (last != null && clock >= last && clock - last < SceneView.LIVE_TICKS) {
            out.setBoolean("busy", true);
            out.setInteger("refreshIn", (int) (last + SceneView.LIVE_TICKS - clock));
            return out;
        }
        gates.put(a, clock);
        GateDirectory.Entry peer = GateDirectory.get()
            .peer(a);
        if (peer == null) return out;
        WorldServer w = DimensionManager.getWorld(peer.dim);
        if (w == null || !w.blockExists(peer.x, peer.y, peer.z)) return out;
        TileEntity target = w.getTileEntity(peer.x, peer.y, peer.z);
        if (!(target instanceof ProjectorTile)) return out;
        ProjectorTile b = (ProjectorTile) target;
        if (!b.open || !b.frequency.equals(a.frequency) || w.getTotalWorldTime() - b.started < Projection.OPEN_TICKS)
            return out;
        Destination destination = cache.computeIfAbsent(b, ignored -> new Destination());
        destination.lastUse = clock;
        Sample[] modes = destination.modes;
        int slot = a.snapshot ? 1 : 0, interval = a.snapshot ? SceneView.SNAPSHOT_TICKS : SceneView.LIVE_TICKS;
        int facing = b.exitFacing == 0 ? b.getBlockMetadata() & 3 : b.exitFacing - 1;
        Sample sample = modes[slot];
        if (sample == null || clock < sample.time
            || clock - sample.time >= interval
            || sample.opened != b.started
            || sample.facing != facing) {
            Sample next = capture(w, b, facing);
            next.time = clock;
            next.opened = b.started;
            next.facing = facing;
            next.revision = sample != null && Arrays.equals(sample.terrain, next.terrain) ? sample.revision
                : ++revision;
            sample = modes[slot] = next;
        }
        out.setInteger("refreshIn", (int) (interval - (clock - sample.time)));
        out.setTag("view", sample.content);
        out.setLong("revision", sample.revision);
        if (request.getLong("revision") != sample.revision) out.setIntArray("terrain", sample.terrain);
        return out;
    }

    private static Sample capture(WorldServer w, ProjectorTile b, int facing) {
        int ox = b.xCoord, oz = b.zCoord;
        double cy = SceneView.centerY(b.yCoord, b.gate.height);
        int oy = (int) Math.floor(cy);
        Sample sample = new Sample();
        sample.terrain = new int[SceneView.VOLUME];
        NBTTagCompound data = sample.content = new NBTTagCompound();
        data.setInteger("facing", facing);
        data.setDouble("cy", cy - oy);
        data.setInteger("biome", w.getBiomeGenForCoords(ox, oz).biomeID);
        data.setBoolean("noSky", w.provider.hasNoSky);
        data.setInteger("skyLoss", w.skylightSubtracted);
        NBTTagList machines = new NBTTagList();
        for (int cx = (ox - 16) >> 4; cx <= (ox + 16) >> 4; cx++)
            for (int cz = (oz - 16) >> 4; cz <= (oz + 16) >> 4; cz++) {
                Chunk c = (Chunk) w.theChunkProviderServer.loadedChunkHashMap
                    .getValueByKey(ChunkCoordIntPair.chunkXZ2Int(cx, cz));
                if (c == null) continue;
                for (int wx = Math.max(cx * 16, ox - 16); wx <= Math.min(cx * 16 + 15, ox + 16); wx++)
                    for (int wz = Math.max(cz * 16, oz - 16); wz <= Math.min(cz * 16 + 15, oz + 16); wz++) {
                        int dx = wx - ox, dz = wz - oz;
                        if (dx * SceneView.fx(facing) + dz * SceneView.fz(facing) < 0 || dx * dx + dz * dz > 256)
                            continue;
                        for (int wy = Math.max(0, oy - 16); wy <= Math.min(w.getHeight() - 1, oy + 16); wy++) {
                            if (!SceneView.inRange(dx, wy + .5 - cy, dz)) continue;
                            int lx = wx & 15, lz = wz & 15;
                            Block block = c.getBlock(lx, wy, lz);
                            int id = Block.getIdFromBlock(block);

                            int meta = c.getBlockMetadata(lx, wy, lz);
                            int light = c.getBlockLightValue(lx, wy, lz, w.skylightSubtracted);
                            int index = SceneView.index(dx, wy - oy, dz);
                            sample.terrain[index] = id << 8 | meta << 4 | light;
                            if (id > 0 && block.hasTileEntity(meta) && machines.tagCount() < 256) {
                                TileEntity tile = (TileEntity) c.chunkTileEntityMap
                                    .get(new net.minecraft.world.ChunkPosition(lx, wy, lz));
                                if (tile instanceof gregtech.api.metatileentity.BaseMetaTileEntity) {
                                    IGregTechTileEntity gt = (IGregTechTileEntity) tile;
                                    NBTTagCompound info = new NBTTagCompound();
                                    info.setInteger("i", index);
                                    info.setInteger("mID", gt.getMetaTileID());
                                    info.setByte("mColor", (byte) (gt.getColorization() + 1));
                                    info.setShort(
                                        "mFacing",
                                        (short) gt.getFrontFacing()
                                            .ordinal());
                                    info.setBoolean("mActive", gt.isActive());
                                    machines.appendTag(info);
                                }
                            }
                        }
                    }
            }
        data.setTag("machines", machines);
        NBTTagList actors = new NBTTagList();
        List<?> entities = w.getEntitiesWithinAABB(
            EntityLivingBase.class,
            AxisAlignedBB.getBoundingBox(ox - 16, cy - 16, oz - 16, ox + 17, cy + 16, oz + 17));
        for (Object obj : entities) {
            EntityLivingBase e = (EntityLivingBase) obj;
            double dx = e.posX - ox - .5, dy = e.boundingBox.minY - cy, dz = e.posZ - oz - .5;
            if (e.isDead || e.isInvisible()
                || !SceneView.inRange(dx, dy, dz)
                || dx * SceneView.fx(facing) + dz * SceneView.fz(facing) < 0) continue;
            String kind = e instanceof EntityPlayer ? "@player" : EntityList.getEntityString(e);
            if (kind == null) continue;
            NBTTagCompound actor = new NBTTagCompound();
            actor.setString("kind", kind);
            if (e instanceof EntityPlayer) {
                actor.setString("name", e.getCommandSenderName());
                actor.setString(
                    "uuid",
                    e.getUniqueID()
                        .toString());
            }
            actor.setInteger("id", e.getEntityId());
            actor.setFloat("x", (float) dx);
            actor.setFloat("y", (float) dy);
            actor.setFloat("z", (float) dz);
            actor.setFloat("yaw", e.rotationYaw);
            actor.setFloat("head", e.rotationYawHead);
            actor.setFloat("swing", e.limbSwing);
            actor.setFloat("amount", e.limbSwingAmount);
            actors.appendTag(actor);
            if (actors.tagCount() == 32) break;
        }
        data.setTag("actors", actors);
        return sample;
    }
}

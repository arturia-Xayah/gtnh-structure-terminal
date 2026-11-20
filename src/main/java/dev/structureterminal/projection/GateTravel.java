package dev.structureterminal.projection;

import java.util.WeakHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** One movement sample per online player at server tick end. Projector tiles never tick. */
public final class GateTravel {

    private static final String ARRIVED_TAG = "structureterminal.projectorArrival";

    private static final class Motion {

        double x, y, z;
        int dimension;
        boolean sampled, arrived;
        GateDirectory.Entry failed;
        long retry;

        void record(EntityPlayerMP p) {
            x = p.posX;
            y = p.boundingBox.minY;
            z = p.posZ;
            dimension = p.dimension;
            sampled = true;
        }
    }

    private final WeakHashMap<EntityPlayerMP, Motion> players = new WeakHashMap<>();

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Object player : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) sample((EntityPlayerMP) player);
    }

    public void sample(EntityPlayerMP p) {
        Motion m = players.computeIfAbsent(p, k -> new Motion());
        double x = p.posX, y = p.boundingBox.minY, z = p.posZ;
        // Command teleports and dimension changes must not sweep every intervening chunk.
        if (!m.sampled || m.dimension != p.dimension
            || Math.max(Math.max(Math.abs(x - m.x), Math.abs(y - m.y)), Math.abs(z - m.z)) > 32) {
            m.arrived = !m.sampled && p.getEntityData()
                .getBoolean(ARRIVED_TAG);
            if (m.sampled && p.getEntityData()
                .getBoolean(ARRIVED_TAG))
                p.getEntityData()
                    .removeTag(ARRIVED_TAG);
            m.record(p);
            m.retry = 0;
        }
        double ox = m.x, oy = m.y, oz = m.z;
        m.record(p);
        if (p.isDead || p.ridingEntity != null || p.riddenByEntity != null || p.isPlayerSleeping()) return;
        long now = MinecraftServer.getServer()
            .worldServerForDimension(0)
            .getTotalWorldTime();

        GateDirectory data = GateDirectory.get();
        double r = (p.boundingBox.maxX - p.boundingBox.minX) * .5, h = p.boundingBox.maxY - y;
        boolean stillInside = false;
        for (GateDirectory.Entry e : data
            .nearby(p.dimension, Math.min(ox, x) - r, Math.min(oz, z) - r, Math.max(ox, x) + r, Math.max(oz, z) + r)) {
            if (m.arrived) {
                if (e.surface.touches(x, y, z, x, y, z, r, h)) stillInside = true;
                continue;
            }
            GateDirectory.Entry dest = data.partner(e);
            if (dest == null || (m.failed == e && m.retry > now) || !e.surface.touches(ox, oy, oz, x, y, z, r, h))
                continue;
            ProjectorTile source = live((WorldServer) p.worldObj, e, false);
            if (!ready(source)) continue;
            String failure = travel(p, dest);
            if (failure == null) {
                m.record(p);
                m.arrived = true;
                p.getEntityData()
                    .setBoolean(ARRIVED_TAG, true);
                m.failed = null;
            } else {
                m.failed = e;
                m.retry = now + 40;
                p.addChatMessage(new ChatComponentTranslation("projector.error." + failure));
            }
            return;
        }
        if (m.arrived && !stillInside) p.getEntityData()
            .removeTag(ARRIVED_TAG);
        m.arrived = stillInside;
    }

    private static boolean ready(ProjectorTile t) {
        return t != null && t.getWorldObj()
            .getTotalWorldTime() - t.started >= Projection.OPEN_TICKS;
    }

    private static ProjectorTile live(WorldServer w, GateDirectory.Entry e, boolean load) {
        if (load) w.getChunkFromChunkCoords(e.x >> 4, e.z >> 4);
        else if (!w.blockExists(e.x, e.y, e.z)) return null;
        TileEntity t = w.getTileEntity(e.x, e.y, e.z);
        if (!(t instanceof ProjectorTile)) return null;
        ProjectorTile gate = (ProjectorTile) t;
        return gate.open && gate.frequency.equals(e.channel) && (gate.getBlockMetadata() & 3) == e.turn ? gate : null;
    }

    private static String travel(EntityPlayerMP p, GateDirectory.Entry dest) {
        if (!DimensionManager.isDimensionRegistered(dest.dim)) return "unavailable";
        MinecraftServer server = MinecraftServer.getServer();
        WorldServer w = server.worldServerForDimension(dest.dim);
        if (w == null || w.provider.dimensionId != dest.dim) return "unavailable";
        ProjectorTile target = live(w, dest, true);
        if (!ready(target)) return "unavailable";
        final double x = dest.x + .5, y = dest.y + 1, z = dest.z + .5;
        AxisAlignedBB box = p.boundingBox.getOffsetBoundingBox(x - p.posX, y - p.boundingBox.minY, z - p.posZ);
        if (box.maxY > w.getHeight() || !w.getCollidingBoundingBoxes(p, box)
            .isEmpty()
            || w.isAnyLiquid(box)
            || w.getBlock(dest.x, dest.y + 1, dest.z) == Blocks.fire
            || w.getBlock(dest.x, dest.y + 2, dest.z) == Blocks.fire) return "blocked";
        final float yaw = 90f * (target.exitFacing == 0 ? dest.turn : target.exitFacing - 1);
        if (dest.dim != p.dimension) {
            server.getConfigurationManager()
                .transferPlayerToDimension(p, dest.dim, new Teleporter(w) {

                    @Override
                    public void placeInPortal(Entity e, double oldX, double oldY, double oldZ, float oldYaw) {
                        e.setLocationAndAngles(x, y, z, yaw, 0);
                    }
                });
            // 1.7.10 skips placement and spawning in transferEntityToWorld when departing dimension 1.
            if (!w.playerEntities.contains(p)) {
                p.setLocationAndAngles(x, y, z, yaw, 0);
                w.spawnEntityInWorld(p);
            }
        }
        p.playerNetServerHandler.setPlayerLocation(x, y, z, yaw, 0);
        p.motionX = p.motionY = p.motionZ = 0;
        p.fallDistance = 0;
        p.velocityChanged = true;
        return null;
    }
}

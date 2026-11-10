package dev.aestoragevault.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import dev.aestoragevault.VaultMod;
import dev.aestoragevault.core.VaultGeometry;
import dev.aestoragevault.world.TileController;
import dev.aestoragevault.world.VaultSkin;
import gregtech.api.render.RenderOverlay;

/** Use GT's own world-scoped casing overlay path, also supported by Angelica WorldSlice. */
public final class VaultCasingSkin {

    private static final java.util.Map<World, java.util.concurrent.ConcurrentMap<net.minecraft.util.ChunkCoordinates, TileController>> OWNERS = java.util.Collections
        .synchronizedMap(new java.util.WeakHashMap<>());
    private final List<net.minecraft.util.ChunkCoordinates> positions = new ArrayList<>();

    /** Chunk render snapshots may omit neighboring tile entities. Use the synced client footprint. */
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public static TileController owner(int x, int y, int z) {
        World world = net.minecraft.client.Minecraft.getMinecraft().theWorld;
        java.util.Map<net.minecraft.util.ChunkCoordinates, TileController> map = OWNERS.get(world);
        TileController owner = map == null ? null : map.get(new net.minecraft.util.ChunkCoordinates(x, y, z));
        return owner != null && owner.formed && !owner.isInvalid() ? owner : null;
    }

    private final List<RenderOverlay.OverlayTicket> tickets = new ArrayList<>();
    private long shape = Long.MIN_VALUE;

    public void update(TileController owner, boolean remove) {
        World world = owner.getWorldObj();
        if (world == null || !world.isRemote) return;
        int front = world.getBlockMetadata(owner.xCoord, owner.yCoord, owner.zCoord);
        int cx = owner.xCoord - VaultGeometry.normal(front, 0);
        int cy = owner.yCoord - VaultGeometry.normal(front, 1);
        int cz = owner.zCoord - VaultGeometry.normal(front, 2);
        long next = 0;
        if (!remove && owner.formed && !owner.isInvalid()) {
            int bit = 0;
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++, bit++) {
                if (VaultGeometry.shell(x, y, z) && world.blockExists(cx + x, cy + y, cz + z)
                    && VaultMod.isLuVCasing(world, cx + x, cy + y, cz + z)) next |= 1L << bit;
            }
            next |= (long) (front + 1) << 27;
        }
        java.util.concurrent.ConcurrentMap<net.minecraft.util.ChunkCoordinates, TileController> map = OWNERS
            .computeIfAbsent(world, w -> new java.util.concurrent.ConcurrentHashMap<>());
        for (net.minecraft.util.ChunkCoordinates pos : positions) map.remove(pos, owner);
        positions.clear();
        if (!remove && owner.formed && !owner.isInvalid())
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                if (!VaultGeometry.shell(x, y, z)) continue;
                net.minecraft.util.ChunkCoordinates pos = new net.minecraft.util.ChunkCoordinates(
                    cx + x,
                    cy + y,
                    cz + z);
                positions.add(pos);
                map.put(pos, owner);
            }
        if (next == shape) return;
        for (RenderOverlay.OverlayTicket ticket : tickets) ticket.remove();
        tickets.clear();
        shape = next;
        if (next != 0) {
            RenderOverlay overlays = RenderOverlay.getOrCreate(world);
            int bit = 0;
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++, bit++) {
                if ((next & 1L << bit) == 0) continue;
                for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                    if (x * side.offsetX + y * side.offsetY + z * side.offsetZ != 1) continue;
                    tickets.add(
                        overlays.set(
                            owner.xCoord,
                            owner.yCoord,
                            owner.zCoord,
                            cx + x,
                            cy + y,
                            cz + z,
                            side,
                            VaultSkin.overlayTexture(VaultGeometry.tile(x, y, z, side.ordinal())),
                            0));
                }
            }
        }
        world.markBlockRangeForRenderUpdate(
            owner.xCoord - 2,
            owner.yCoord - 2,
            owner.zCoord - 2,
            owner.xCoord + 2,
            owner.yCoord + 2,
            owner.zCoord + 2);
    }
}

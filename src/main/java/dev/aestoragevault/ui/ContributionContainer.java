package dev.aestoragevault.ui;

import java.math.BigInteger;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;

import dev.aestoragevault.VaultMod;
import dev.aestoragevault.core.CapacityValue;
import dev.aestoragevault.core.CellContribution;
import dev.aestoragevault.world.TileController;

/**
 * Slot-free detail window with explicit return requests: six definitions per page and at most three per packet, only
 * sent to its viewers.
 */
public final class ContributionContainer extends Container {

    public static final int PAGE_SIZE = 6;
    public final TileController tile;
    private int page, sentPage = -1, ticks;
    private long sentRevision = Long.MIN_VALUE;
    private BigInteger sentCapacity;
    private boolean sentBus;
    private long lastReturnTick = Long.MIN_VALUE;

    public void returnCells(EntityPlayerMP player, String key, String quantity) {
        if (!canInteractWith(player) || player.openContainer != this) return;
        long now = tile.getWorldObj()
            .getTotalWorldTime();
        if (lastReturnTick != Long.MIN_VALUE && now - lastReturnTick < 10) {
            reply(player, 0, "busy");
            return;
        }
        lastReturnTick = now;
        try {
            CellContribution c = tile.store() == null ? null
                : tile.store()
                    .contribution(key);
            if (c == null) {
                reply(player, 0, "invalid");
                return;
            }
            BigInteger requested = dev.aestoragevault.core.ReturnNumbers.clamp(quantity, c.count);
            int actual = dev.aestoragevault.world.CellReturns.eject(tile, key, requested);
            reply(
                player,
                actual,
                actual == 0 ? "noSpace"
                    : BigInteger.valueOf(actual)
                        .equals(requested) ? "ok" : "partial");
        } catch (Exception failure) {
            if (tile.store() != null && (!tile.store()
                .isHealthy()
                || !tile.store()
                    .pendingReturns()
                    .isEmpty()))
                tile.ioFailure(failure);
            reply(player, 0, "failed");
        }
        sentPage = -1;
        detectAndSendChanges();
    }

    private void reply(EntityPlayerMP player, int count, String message) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("ReturnWindow", windowId);
        tag.setInteger("Returned", count);
        tag.setString("ReturnMessage", message);
        player.playerNetServerHandler
            .sendPacket(new S35PacketUpdateTileEntity(tile.xCoord, tile.yCoord, tile.zCoord, 0, tag));
    }

    public ContributionContainer(TileController tile) {
        this.tile = tile;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return !tile.isInvalid() && player.worldObj == tile.getWorldObj()
            && player.worldObj.getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord) == tile
            && player.getDistanceSq(tile.xCoord + 0.5, tile.yCoord + 0.5, tile.zCoord + 0.5) <= 64;
    }

    @Override
    public boolean enchantItem(EntityPlayer player, int action) {
        if (player.worldObj.isRemote || !canInteractWith(player)) return false;
        if (action == 0) player.openGui(VaultMod.INSTANCE, 0, player.worldObj, tile.xCoord, tile.yCoord, tile.zCoord);
        else if (action == 1 || action == 2) {
            int count = tile.contributions()
                .size();
            int last = Math.max(0, (count - 1) / PAGE_SIZE);
            page = Math.min(last, Math.max(0, page + (action == 1 ? -1 : 1)));
            sentPage = -1;
            detectAndSendChanges();
        } else return false;
        return true;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (tile.getWorldObj().isRemote || tile.store() == null) return;
        if (sentPage >= 0 && ++ticks % 20 != 0) return;
        long revision = tile.store()
            .contributionRevision();
        BigInteger capacity = tile.store()
            .capacity();
        dev.aestoragevault.world.VaultStructure.Shape shape = dev.aestoragevault.world.VaultStructure
            .inspectExclusive(tile);
        boolean hasBus = shape != null && shape.output != null;
        if (sentRevision == revision && sentPage == page && capacity.equals(sentCapacity) && hasBus == sentBus) return;
        List<CellContribution> all = tile.contributions();
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pages - 1);
        // Vanilla tile packets have a signed-short compressed-NBT length. Keep even worst-case
        // 4096-byte BigInteger fields below it: send six UI rows as two bounded three-row parts.
        for (NBTTagCompound data : pageData(windowId, page, all, capacity)) {
            data.setBoolean("ReturnBus", hasBus);
            for (Object viewer : crafters) {
                if (viewer instanceof EntityPlayerMP && canInteractWith((EntityPlayerMP) viewer))
                    ((EntityPlayerMP) viewer).playerNetServerHandler
                        .sendPacket(new S35PacketUpdateTileEntity(tile.xCoord, tile.yCoord, tile.zCoord, 0, data));
            }
        }
        sentPage = page;
        sentRevision = revision;
        sentCapacity = capacity;
        sentBus = hasBus;
    }

    public static List<NBTTagCompound> pageData(int window, int page, List<CellContribution> all, BigInteger capacity) {
        List<NBTTagCompound> packets = new java.util.ArrayList<>();
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * PAGE_SIZE, end = Math.min(start + PAGE_SIZE, all.size());
        int parts = Math.max(1, (end - start + 2) / 3);
        for (int part = 0; part < parts; part++) {
            NBTTagCompound data = new NBTTagCompound();
            data.setInteger("HistoryWindow", window);
            data.setInteger("Page", page);
            data.setInteger("Pages", pages);
            data.setInteger("CellTypes", all.size());
            data.setInteger("Part", part);
            data.setInteger("Parts", parts);
            data.setByteArray("Capacity", CapacityValue.encode(capacity));
            int from = start + part * 3;
            data.setTag("Cells", TileController.historyNbt(all.subList(from, Math.min(from + 3, end))));
            packets.add(data);
        }
        return packets;
    }

    @Override
    public void addCraftingToCrafters(ICrafting viewer) {
        sentPage = -1;
        super.addCraftingToCrafters(viewer);
    }

    @Override
    public net.minecraft.item.ItemStack transferStackInSlot(EntityPlayer player, int slot) {
        return null;
    }
}

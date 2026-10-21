package dev.aeoptimizer.ui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.item.ItemStack;

import appeng.api.config.SecurityPermissions;
import appeng.api.networking.security.ISecurityGrid;
import dev.aeoptimizer.network.TileOptimizer;

public class OptimizerContainer extends Container {

    public final TileOptimizer tile;
    public boolean detailed, conflict;

    public OptimizerContainer(TileOptimizer tile) {
        this.tile = tile;
        this.detailed = tile.detailed;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (tile.isInvalid() || tile.getWorldObj()
            .getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord) != tile
            || player.getDistanceSq(tile.xCoord + .5, tile.yCoord + .5, tile.zCoord + .5) > 64) return false;
        if (tile.getWorldObj().isRemote) return true;
        if (tile.getProxy()
            .getNode() == null) return false;
        ISecurityGrid security = tile.getProxy()
            .getNode()
            .getGrid()
            .getCache(ISecurityGrid.class);
        return security.hasPermission(player, SecurityPermissions.BUILD);
    }

    @Override
    public boolean enchantItem(EntityPlayer player, int action) {
        if (action != 0 || !canInteractWith(player) || tile.getWorldObj().isRemote) return false;
        tile.detailed = !tile.detailed;
        tile.markDirty();
        detectAndSendChanges();
        return true;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        detailed = tile.detailed;
        conflict = tile.conflict;
        for (Object viewer : crafters) {
            ((ICrafting) viewer).sendProgressBarUpdate(this, 0, detailed ? 1 : 0);
            ((ICrafting) viewer).sendProgressBarUpdate(this, 1, conflict ? 1 : 0);
        }
    }

    @Override
    public void updateProgressBar(int id, int value) {
        if (id == 0) detailed = value != 0;
        if (id == 1) conflict = value != 0;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slot) {
        return null;
    }
}

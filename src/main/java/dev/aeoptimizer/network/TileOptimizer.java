package dev.aeoptimizer.network;

import java.util.EnumSet;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.me.helpers.AENetworkProxy;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import dev.aeoptimizer.OrderOptimizer;

public class TileOptimizer extends AENetworkTile {

    public boolean detailed;
    public boolean conflict;

    @Override
    protected AENetworkProxy createProxy() {
        AENetworkProxy proxy = new AENetworkProxy(this, "proxy", new ItemStack(OrderOptimizer.BLOCK), true);
        proxy.setIdlePowerUsage(1);
        proxy.setValidSides(EnumSet.allOf(ForgeDirection.class));
        return proxy;
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readSettings(NBTTagCompound nbt) {
        detailed = nbt.getBoolean("Detailed");
        conflict = nbt.getBoolean("DuplicateDisconnected");
        if (conflict) getProxy().setValidSides(EnumSet.noneOf(ForgeDirection.class));
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeSettings(NBTTagCompound nbt) {
        nbt.setBoolean("Detailed", detailed);
        nbt.setBoolean("DuplicateDisconnected", conflict);
    }

    @TileEvent(TileEventType.TICK)
    public void enforceUnique() {
        if (worldObj == null || worldObj.isRemote) return;
        if (conflict) {
            if (worldObj.getTotalWorldTime() % 20 == 0
                && !OptimizerNetworks.hasNeighborOptimizer(worldObj, xCoord, yCoord, zCoord, this)) {
                conflict = false;
                getProxy().setValidSides(EnumSet.allOf(ForgeDirection.class));
                markDirty();
            }
            return;
        }
        if (getProxy().getNode() == null) return;
        TileOptimizer winner = OptimizerNetworks.winner(
            getProxy().getNode()
                .getGrid());
        if (winner != null && winner != this) {
            conflict = true;
            getProxy().setValidSides(EnumSet.noneOf(ForgeDirection.class));
            markDirty();
            OrderOptimizer.LOG.warn("Duplicate optimizer disconnected at {},{},{}", xCoord, yCoord, zCoord);
        }
    }
}

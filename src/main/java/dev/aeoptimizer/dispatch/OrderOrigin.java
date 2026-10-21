package dev.aeoptimizer.dispatch;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.util.DimensionalCoord;
import appeng.crafting.fast.CraftingJobFast;
import appeng.crafting.v2.CraftingJobV2;
import dev.aeoptimizer.network.OptimizerNetworks;
import dev.aeoptimizer.planning.StrictCraftingJob;

/** Persist the submitting grid's location when it differs from the CPU grid. Never use pattern-provider ownership. */
public final class OrderOrigin {

    private IGridNode node;
    private NBTTagCompound location;
    private boolean mixed;

    public static IGrid of(ICraftingJob job, IGrid fallback) {
        if (job instanceof StrictCraftingJob) return ((StrictCraftingJob) job).getContext().meGrid;
        if (job instanceof CraftingJobFast) return ((CraftingJobFast) job).getContext().meGrid;
        if (job instanceof CraftingJobV2) return ((CraftingJobV2) job).getContext().meGrid;
        return fallback;
    }

    public void accept(IGrid origin, IGrid cpu, boolean merged) {
        if (merged && grid(cpu) != origin) {
            mixed = true;
            return;
        }
        if (merged) return;
        mixed = false;
        node = null;
        location = null;
        if (origin == null || origin == cpu) return;
        node = origin.getPivot();
        outer: for (Class<? extends IGridHost> type : origin.getMachinesClasses()) {
            if (!type.getName()
                .equals("appeng.tile.networking.TileController")) continue;
            for (IGridNode controller : origin.getMachines(type)) {
                node = controller;
                break outer;
            }
        }
        if (node == null) {
            mixed = true;
            return;
        }
        // A full optimizer block is an unambiguous anchor even in controllerless grids.
        dev.aeoptimizer.network.TileOptimizer anchor = OptimizerNetworks.winner(origin);
        if (anchor != null) node = anchor.getProxy()
            .getNode();
        DimensionalCoord coord = node.getGridBlock()
            .getLocation();
        if (coord == null) {
            mixed = true;
            node = null;
            return;
        }
        location = new NBTTagCompound();
        location.setInteger("dim", coord.getDimension());
        location.setInteger("x", coord.x);
        location.setInteger("y", coord.y);
        location.setInteger("z", coord.z);
        World world = DimensionManager.getWorld(coord.getDimension());
        TileEntity tile = world == null ? null : world.getTileEntity(coord.x, coord.y, coord.z);
        boolean found = false;
        if (tile instanceof IGridHost) for (ForgeDirection side : ForgeDirection.values()) {
            if (((IGridHost) tile).getGridNode(side) == node) {
                location.setInteger("side", side.ordinal());
                found = true;
                break;
            }
        }
        // Do not guess a face: a cable bus can contain parts belonging to several independent grids.
        if (!found) {
            mixed = true;
            node = null;
            location = null;
        }
    }

    private IGrid grid(IGrid cpu) {
        if (location == null) return cpu;
        if (node != null) return node.getGrid();
        World world = DimensionManager.getWorld(location.getInteger("dim"));
        if (world == null
            || !world.blockExists(location.getInteger("x"), location.getInteger("y"), location.getInteger("z")))
            return null;
        TileEntity tile = world
            .getTileEntity(location.getInteger("x"), location.getInteger("y"), location.getInteger("z"));
        if (!(tile instanceof IGridHost)) return null;
        ForgeDirection side = ForgeDirection.getOrientation(location.hasKey("side") ? location.getInteger("side") : 6);
        node = ((IGridHost) tile).getGridNode(side);
        return node == null ? null : node.getGrid();
    }

    public boolean enabled(IGrid cpu) {
        return !mixed && OptimizerNetworks.find(grid(cpu)) != null;
    }

    public void write(NBTTagCompound nbt) {
        nbt.removeTag("AEOOrderingGrid");
        nbt.removeTag("AEOMixedOrderingGrids");
        if (location != null) nbt.setTag("AEOOrderingGrid", location.copy());
        if (mixed) nbt.setBoolean("AEOMixedOrderingGrids", true);
    }

    public void read(NBTTagCompound nbt) {
        node = null;
        location = nbt.hasKey("AEOOrderingGrid") ? nbt.getCompoundTag("AEOOrderingGrid") : null;
        mixed = nbt.getBoolean("AEOMixedOrderingGrids");
    }
}

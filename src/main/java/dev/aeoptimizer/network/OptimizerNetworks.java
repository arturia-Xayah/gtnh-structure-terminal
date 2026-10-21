package dev.aeoptimizer.network;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;

public final class OptimizerNetworks {

    private OptimizerNetworks() {}

    public static TileOptimizer winner(IGrid grid) {
        TileOptimizer best = null;
        if (grid == null) return null;
        for (IGridNode node : grid.getMachines(TileOptimizer.class)) {
            TileOptimizer tile = (TileOptimizer) node.getMachine();
            if (tile.isInvalid() || tile.conflict) continue;
            if (best == null || compare(tile, best) < 0) best = tile;
        }
        return best;
    }

    public static TileOptimizer find(IGrid grid) {
        TileOptimizer tile = winner(grid);
        return tile != null && tile.getProxy()
            .isActive() ? tile : null;
    }

    private static int compare(TileOptimizer a, TileOptimizer b) {
        int c = Integer.compare(a.getWorldObj().provider.dimensionId, b.getWorldObj().provider.dimensionId);
        if (c == 0) c = Integer.compare(a.xCoord, b.xCoord);
        if (c == 0) c = Integer.compare(a.yCoord, b.yCoord);
        if (c == 0) c = Integer.compare(a.zCoord, b.zCoord);
        return c;
    }

    public static boolean hasNeighborOptimizer(World world, int x, int y, int z, TileOptimizer self) {
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            TileEntity tile = world.getTileEntity(x + side.offsetX, y + side.offsetY, z + side.offsetZ);
            if (!(tile instanceof IGridHost)) continue;
            IGridNode node = ((IGridHost) tile).getGridNode(side.getOpposite());
            if (node == null) continue;
            TileOptimizer winner = winner(node.getGrid());
            if (winner != null && winner != self) return true;
        }
        return false;
    }
}

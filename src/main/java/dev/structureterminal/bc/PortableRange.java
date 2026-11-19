package dev.structureterminal.bc;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import buildcraft.core.DefaultProps;
import buildcraft.core.lib.block.BlockBuildCraft;
import buildcraft.core.lib.block.TileBuildCraft;

public final class PortableRange {

    public static final String KEY = "modernization.bcDimensions";

    private PortableRange() {}

    public static int front(TileEntity tile) {
        return ((BlockBuildCraft) tile.getBlockType()).getFrontSide(tile.getBlockMetadata());
    }

    public static int[] dimensions(TileEntity tile) {
        return ((RangeMachine) tile).rangeData()
            .getIntArray(KEY);
    }

    public static int[] bounds(int x, int y, int z, int facing, int[] size, int height) {
        if (size.length != 3) throw new IllegalArgumentException("bc.range.dimensions");
        return WorkRange.from(x, y, z, facing, size[0], size[1], size[2], DefaultProps.MARKER_RANGE, height)
            .coords();
    }

    public static boolean apply(TileEntity tile, int[] size) {
        int[] box = bounds(
            tile.xCoord,
            tile.yCoord,
            tile.zCoord,
            front(tile),
            size,
            tile.getWorldObj()
                .getHeight());
        NBTTagCompound data = ((RangeMachine) tile).rangeData();
        boolean changed = !java.util.Arrays.equals(size, data.getIntArray(KEY));
        boolean sameBox = java.util.Arrays.equals(box, RangeJobs.bounds(tile));
        data.setIntArray(KEY, size.clone());
        boolean queued = RangeJobs.submit(tile, box);
        // Native BC only sends a new box when it actually starts using it.
        if (changed && (queued || sameBox)) ((TileBuildCraft) tile).sendNetworkUpdate();
        return queued;
    }
}

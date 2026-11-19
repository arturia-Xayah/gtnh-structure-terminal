package dev.structureterminal.bc;

import java.util.Arrays;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import buildcraft.core.Box;
import buildcraft.core.internal.IBoxProvider;

public final class RangeJobs {

    public static final String PENDING = "structureterminal.pendingBcRange";
    public static final String MANUAL = "structureterminal.manualBcRange";

    private RangeJobs() {}

    public static int[] bounds(TileEntity t) {
        Box b = ((IBoxProvider) t).getBox();
        return b.isInitialized() ? new int[] { b.xMin, b.yMin, b.zMin, b.xMax, b.yMax, b.zMax } : new int[0];
    }

    public static boolean submit(TileEntity t, int[] corners) {
        NBTTagCompound data = ((RangeMachine) t).rangeData();
        boolean defer = false;
        if (Arrays.equals(bounds(t), corners)) {
            data.removeTag(PENDING);
            data.setBoolean(MANUAL, true);
        } else if (((RangeMachine) t).rangeBusy()) {
            data.setIntArray(PENDING, corners.clone());
            defer = true;
        } else {
            data.removeTag(PENDING);
            ((RangeMachine) t).applyBounds(corners);
            data.setBoolean(MANUAL, true);
        }
        t.markDirty();
        return defer;
    }

    public static void finish(TileEntity t) {
        if (t.getWorldObj().isRemote || !((RangeMachine) t).rangeData()
            .hasKey(PENDING) || ((RangeMachine) t).rangeBusy()) return;
        int[] c = ((RangeMachine) t).rangeData()
            .getIntArray(PENDING);
        if (c.length == 6) submit(t, c);
    }
}

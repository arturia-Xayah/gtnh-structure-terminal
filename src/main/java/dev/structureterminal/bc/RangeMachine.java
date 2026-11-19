package dev.structureterminal.bc;

/** Implemented only by the two supported BuildCraft tile entities. */
public interface RangeMachine {

    net.minecraft.nbt.NBTTagCompound rangeData();

    boolean rangeBusy();

    void applyBounds(int[] corners);
}

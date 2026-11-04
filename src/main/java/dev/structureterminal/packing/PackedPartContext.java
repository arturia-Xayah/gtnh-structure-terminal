package dev.structureterminal.packing;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;

/** Restores before the part joins its grid; no global placement or item-NBT interception. */
public final class PackedPartContext implements AutoCloseable {

    private static final ThreadLocal<PackedPartContext> CURRENT = new ThreadLocal<>();
    private final NBTTagCompound contents;
    private final IPartHost host;
    private final ForgeDirection side;
    private boolean restored;

    public PackedPartContext(NBTTagCompound contents, IPartHost host, ForgeDirection side) {
        if (CURRENT.get() != null) throw new IllegalStateException("Nested packed-part placement");
        this.contents = (NBTTagCompound) contents.copy();
        this.host = host;
        this.side = side;
        CURRENT.set(this);
    }

    public static void addToWorld(IPart part) {
        PackedPartContext context = CURRENT.get();
        if (context != null && !context.restored && context.host.getPart(context.side) == part) {
            context.restored = true;
            part.readFromNBT(context.contents);
        }
        part.addToWorld();
    }

    public boolean restored() {
        return restored;
    }

    @Override
    public void close() {
        CURRENT.remove();
    }
}

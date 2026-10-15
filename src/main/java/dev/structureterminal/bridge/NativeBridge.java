package dev.structureterminal.bridge;

import java.lang.reflect.Method;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.IAlignment;
import com.gtnewhorizon.structurelib.alignment.IAlignmentProvider;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructableProvider;
import com.gtnewhorizon.structurelib.alignment.constructable.IMultiblockInfoContainer;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.alignment.enumerable.Flip;

/** Uses the installed pack's definitions, never a copied table of GTO machine blocks. */
public final class NativeBridge {

    public final TileEntity tile;
    public final Object context;
    public final IConstructable constructable;
    public final IAlignment alignment;

    private NativeBridge(TileEntity tile, Object context, IConstructable constructable, IAlignment alignment) {
        this.tile = tile;
        this.context = context;
        this.constructable = constructable;
        this.alignment = alignment;
    }

    public static NativeBridge resolve(World world, int x, int y, int z, int side) {
        if (!world.blockExists(x, y, z)) return null;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile == null) return null;
        Object context = tile;
        try {
            Method getter = tile.getClass()
                .getMethod("getMetaTileEntity");
            Object meta = getter.invoke(tile);
            if (meta != null) context = meta;
        } catch (ReflectiveOperationException ignored) {
            // A non-GregTech controller is also supported when it implements StructureLib's public API.
        }
        IAlignment alignment = alignment(context);
        if (alignment == null) alignment = alignment(tile);
        IConstructable c = constructable(context);
        if (c == null) c = constructable(tile);
        if (c == null && IMultiblockInfoContainer.contains(tile.getClass())) {
            ExtendedFacing facing = alignment == null ? ExtendedFacing.of(ForgeDirection.getOrientation(side))
                : alignment.getExtendedFacing();
            c = IMultiblockInfoContainer.<TileEntity>get(tile.getClass())
                .toConstructable(tile, facing);
        }
        return c == null ? null : new NativeBridge(tile, context, c, alignment);
    }

    private static IConstructable constructable(Object object) {
        if (object instanceof IConstructableProvider) return ((IConstructableProvider) object).getConstructable();
        return object instanceof IConstructable ? (IConstructable) object : null;
    }

    private static IAlignment alignment(Object object) {
        return object instanceof IAlignmentProvider ? ((IAlignmentProvider) object).getAlignment() : null;
    }

    public boolean canMirror(boolean mirrored) {
        if (alignment == null) return !mirrored;
        ExtendedFacing next = alignment.getExtendedFacing()
            .with(flip(mirrored));
        return alignment.isNewExtendedFacingValid(next);
    }

    /** A marker alone is insufficient: the two default survival overloads can delegate back to each other. */
    public boolean supportsSurvivalConstruction() {
        Class<?> api = com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable.class;
        if (!api.isInstance(constructable)) return false;
        try {
            return constructable.getClass()
                .getMethod(
                    "survivalConstruct",
                    net.minecraft.item.ItemStack.class,
                    int.class,
                    com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment.class)
                .getDeclaringClass() != api
                || constructable.getClass()
                    .getMethod(
                        "survivalConstruct",
                        net.minecraft.item.ItemStack.class,
                        int.class,
                        com.gtnewhorizon.structurelib.structure.IItemSource.class,
                        net.minecraft.entity.player.EntityPlayerMP.class)
                    .getDeclaringClass() != api;
        } catch (ReflectiveOperationException error) {
            return false;
        }
    }

    public ExtendedFacing applyMirror(boolean mirrored) {
        if (!canMirror(mirrored)) throw new IllegalArgumentException("This machine does not support that mirror mode");
        if (alignment == null) return null;
        ExtendedFacing old = alignment.getExtendedFacing();
        ExtendedFacing next = old.with(flip(mirrored));
        if (next != old) alignment.setExtendedFacing(next);
        return old;
    }

    /** Compute preview coordinates without calling a controller setter (GT setters can stop running machines). */
    public int[] previewPosition(int x, int y, int z, boolean mirrored) {
        if (!canMirror(mirrored)) throw new IllegalArgumentException("Unsupported mirror mode");
        if (alignment == null) return new int[] { x, y, z };
        ExtendedFacing current = alignment.getExtendedFacing();
        ExtendedFacing target = current.with(flip(mirrored));
        int[] local = new int[3], offset = new int[3];
        current.getOffsetABC(new int[] { x - tile.xCoord, y - tile.yCoord, z - tile.zCoord }, local);
        target.getWorldOffset(local, offset);
        return new int[] { tile.xCoord + offset[0], tile.yCoord + offset[1], tile.zCoord + offset[2] };
    }

    public void restore(ExtendedFacing facing) {
        if (alignment != null && facing != null && alignment.getExtendedFacing() != facing)
            alignment.setExtendedFacing(facing);
    }

    private Flip flip(boolean mirrored) {
        boolean vertical = alignment.getFlip()
            .isVerticallyFliped();
        return vertical ? (mirrored ? Flip.BOTH : Flip.VERTICAL) : (mirrored ? Flip.HORIZONTAL : Flip.NONE);
    }
}

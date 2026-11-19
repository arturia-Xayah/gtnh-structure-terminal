package dev.structureterminal.bc;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.recursive_pineapple.matter_manipulator.common.building.BlockAnalyzer.IBlockApplyContext;
import com.recursive_pineapple.matter_manipulator.common.building.ITileAnalysisIntegration;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Transform;

public final class MachineRangeCopy implements ITileAnalysisIntegration {

    private ForgeDirection face;
    private int width, height;
    private final int depth;

    public MachineRangeCopy(int face, int[] size) {
        this.face = ForgeDirection.getOrientation(face);
        width = size[0];
        height = size[1];
        depth = size[2];
    }

    public int[] bounds(World world, int x, int y, int z) {
        if (face.offsetY != 0) throw new IllegalArgumentException("bc.range.vertical");
        return PortableRange.bounds(x, y, z, face.ordinal(), new int[] { width, height, depth }, world.getHeight());
    }

    public void transform(Transform transform) {
        ForgeDirection oldRight = right(face);
        face = transform.apply(face);
        if (face.offsetY != 0) return;
        ForgeDirection newRight = right(face), widthDir = transform.apply(oldRight),
            heightDir = transform.apply(ForgeDirection.UP);
        int oldWidth = width;
        // Project both transformed axes onto the destination machine's right and up axes.
        width = oldWidth * (widthDir.offsetX * newRight.offsetX + widthDir.offsetZ * newRight.offsetZ)
            + height * (heightDir.offsetX * newRight.offsetX + heightDir.offsetZ * newRight.offsetZ);
        height = oldWidth * widthDir.offsetY + height * heightDir.offsetY;
    }

    private static ForgeDirection right(ForgeDirection front) {
        switch (front) {
            case NORTH:
                return ForgeDirection.EAST;
            case SOUTH:
                return ForgeDirection.WEST;
            case EAST:
                return ForgeDirection.SOUTH;
            default:
                return ForgeDirection.NORTH;
        }
    }

    public boolean apply(IBlockApplyContext ctx) {
        TileEntity tile = ctx.getTileEntity();
        if (!(tile instanceof RangeMachine)) return false;
        World world = ctx.getWorld();
        try {
            bounds(world, tile.xCoord, tile.yCoord, tile.zCoord);
            if (PortableRange.front(tile) != face.ordinal())
                world.setBlockMetadataWithNotify(tile.xCoord, tile.yCoord, tile.zCoord, face.ordinal(), 3);
            PortableRange.apply(tile, new int[] { width, height, depth });
            return true;
        } catch (IllegalArgumentException invalid) {
            ctx.warn(new ChatComponentTranslation(invalid.getMessage()));
            return false;
        }
    }

    public MachineRangeCopy clone() {
        return new MachineRangeCopy(face.ordinal(), new int[] { width, height, depth });
    }

    public boolean getRequiredItemsForExistingBlock(IBlockApplyContext ctx) {
        return true;
    }

    public boolean getRequiredItemsForNewBlock(IBlockApplyContext ctx) {
        return true;
    }

    public void getItemTag(ItemStack stack) {}

    public void getItemDetailsChat(List<IChatComponent> details) {}

    public void migrate() {}
}

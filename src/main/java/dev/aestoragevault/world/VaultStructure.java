package dev.aestoragevault.world;

import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import dev.aestoragevault.VaultMod;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;

/** Read-only shape inspection shared by storage access and the formed appearance. Never loads chunks. */
public final class VaultStructure {

    private VaultStructure() {}

    public static final class Shape {

        public IInventory bus;
        public IGregTechTileEntity busBase;
        public TileAccess access;
    }

    public static Shape inspect(TileController controller) {
        World world = controller.getWorldObj();
        if (world == null || controller.isInvalid()) return null;
        ForgeDirection front = ForgeDirection
            .getOrientation(world.getBlockMetadata(controller.xCoord, controller.yCoord, controller.zCoord));
        if (front == ForgeDirection.UNKNOWN || front.offsetY != 0) front = ForgeDirection.NORTH;
        int rightX = -front.offsetZ, rightZ = front.offsetX, buses = 0, interfaces = 0;
        Shape result = new Shape();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int depth = 0; depth <= 2; depth++) {
            int wx = controller.xCoord + rightX * x - front.offsetX * depth;
            int wy = controller.yCoord + y;
            int wz = controller.zCoord + rightZ * x - front.offsetZ * depth;
            if (!world.blockExists(wx, wy, wz)) return null;
            if (x == 0 && y == 0 && depth == 0) {
                if (world.getTileEntity(wx, wy, wz) != controller) return null;
                continue;
            }
            if (x == 0 && y == 0 && depth == 1) {
                if (!world.isAirBlock(wx, wy, wz)) return null;
                continue;
            }
            if (VaultMod.isLuVCasing(world, wx, wy, wz)) continue;
            TileEntity te = world.getTileEntity(wx, wy, wz);
            if (te instanceof TileAccess) {
                result.access = (TileAccess) te;
                interfaces++;
            } else if (te instanceof IGregTechTileEntity) {
                IGregTechTileEntity base = (IGregTechTileEntity) te;
                if (base.getMetaTileEntity() == null || base.getMetaTileEntity()
                    .getClass() != MTEHatchInputBus.class) return null;
                result.bus = (IInventory) base.getMetaTileEntity();
                result.busBase = base;
                buses++;
            } else return null;
        }
        return buses == 1 && interfaces == 1 ? result : null;
    }

    public static boolean isFormedBus(IGregTechTileEntity bus) {
        World world = bus.getWorld();
        if (world == null) return false;
        int x = bus.getXCoord(), y = bus.getYCoord(), z = bus.getZCoord();
        for (int dx = -2; dx <= 2; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -2; dz <= 2; dz++) {
            if (!world.blockExists(x + dx, y + dy, z + dz)) continue;
            TileEntity tile = world.getTileEntity(x + dx, y + dy, z + dz);
            if (!(tile instanceof TileController) || !((TileController) tile).formed) continue;
            Shape shape = inspect((TileController) tile);
            if (shape != null && shape.busBase == bus) return true;
        }
        return false;
    }
}

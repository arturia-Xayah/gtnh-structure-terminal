package dev.aestoragevault.world;

import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import dev.aestoragevault.VaultMod;
import dev.aestoragevault.core.VaultGeometry;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;

/** Read-only shape and exclusive-component checks. Never loads chunks. */
public final class VaultStructure {

    private VaultStructure() {}

    public static final class Shape {

        public IInventory bus;
        public IGregTechTileEntity busBase;
        public TileAccess access;
        public int centerX, centerY, centerZ;
    }

    public static Shape inspect(TileController controller) {
        World world = controller.getWorldObj();
        if (world == null || controller.isInvalid()) return null;
        int side = world.getBlockMetadata(controller.xCoord, controller.yCoord, controller.zCoord);
        Shape result = new Shape();
        result.centerX = controller.xCoord - VaultGeometry.normal(side, 0);
        result.centerY = controller.yCoord - VaultGeometry.normal(side, 1);
        result.centerZ = controller.zCoord - VaultGeometry.normal(side, 2);
        int buses = 0, interfaces = 0;
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            int wx = result.centerX + x, wy = result.centerY + y, wz = result.centerZ + z;
            if (!world.blockExists(wx, wy, wz)) return null;
            if (wx == controller.xCoord && wy == controller.yCoord && wz == controller.zCoord) {
                if (world.getTileEntity(wx, wy, wz) != controller) return null;
                continue;
            }
            if (x == 0 && y == 0 && z == 0) {
                if (!world.isAirBlock(wx, wy, wz)) return null;
                continue;
            }
            if (VaultMod.isLuVCasing(world, wx, wy, wz)) continue;
            TileEntity tile = world.getTileEntity(wx, wy, wz);
            if (tile instanceof TileAccess) {
                result.access = (TileAccess) tile;
                interfaces++;
            } else if (tile instanceof IGregTechTileEntity) {
                IGregTechTileEntity base = (IGregTechTileEntity) tile;
                if (base.getMetaTileEntity() == null || base.getMetaTileEntity()
                    .getClass() != MTEHatchInputBus.class) return null;
                result.bus = (IInventory) base.getMetaTileEntity();
                result.busBase = base;
                buses++;
            } else return null;
        }
        return buses == 1 && interfaces == 1 ? result : null;
    }

    public static Shape inspectExclusive(TileController controller) {
        Shape shape = inspect(controller);
        if (shape == null || NativeBusOwners.inUse((MTEHatchInputBus) shape.bus)) return null;
        if (shared(controller, shape, shape.busBase.getXCoord(), shape.busBase.getYCoord(), shape.busBase.getZCoord())
            || shared(controller, shape, shape.access.xCoord, shape.access.yCoord, shape.access.zCoord)) return null;
        return shape;
    }

    private static boolean shared(TileController owner, Shape shape, int x, int y, int z) {
        World world = owner.getWorldObj();
        for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
            if (!world.blockExists(x + dx, y + dy, z + dz)) continue;
            TileEntity tile = world.getTileEntity(x + dx, y + dy, z + dz);
            if (!(tile instanceof TileController) || tile == owner) continue;
            Shape other = inspect((TileController) tile);
            if (other != null && (other.busBase == shape.busBase || other.access == shape.access)) return true;
        }
        return false;
    }

    public static TileController formedOwner(IBlockAccess world, int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
            if (world instanceof World && !((World) world).blockExists(x + dx, y + dy, z + dz)) continue;
            TileEntity tile = world.getTileEntity(x + dx, y + dy, z + dz);
            if (!(tile instanceof TileController) || !((TileController) tile).formed) continue;
            TileController controller = (TileController) tile;
            Shape shape = inspectExclusive(controller);
            if (shape != null && VaultGeometry.shell(x - shape.centerX, y - shape.centerY, z - shape.centerZ))
                return controller;
        }
        return null;
    }

    public static int skinTile(IBlockAccess world, int x, int y, int z, int side) {
        TileController owner = formedOwner(world, x, y, z);
        if (owner == null) return -1;
        int front = world.getBlockMetadata(owner.xCoord, owner.yCoord, owner.zCoord);
        return VaultGeometry.tile(
            x - owner.xCoord + VaultGeometry.normal(front, 0),
            y - owner.yCoord + VaultGeometry.normal(front, 1),
            z - owner.zCoord + VaultGeometry.normal(front, 2),
            side);
    }

    public static boolean isFormedBus(IGregTechTileEntity bus) {
        return bus.getWorld() != null
            && skinTile(bus.getWorld(), bus.getXCoord(), bus.getYCoord(), bus.getZCoord(), 1) >= 0;
    }
}

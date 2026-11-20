package dev.structureterminal.projection;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public final class ProjectorMenu extends Container {

    public static final int ID = 38;
    public final ProjectorTile tile;

    private ProjectorMenu(ProjectorTile tile) {
        this.tile = tile;
    }

    public static ProjectorMenu at(World world, int x, int y, int z) {
        if (!world.blockExists(x, y, z)) return null;
        TileEntity t = world.getTileEntity(x, y, z);
        return t instanceof ProjectorTile ? new ProjectorMenu((ProjectorTile) t) : null;
    }

    @Override
    public boolean canInteractWith(EntityPlayer p) {
        return !p.isDead && !tile.isInvalid()
            && p.worldObj == tile.getWorldObj()
            && p.getDistanceSq(tile.xCoord + .5, tile.yCoord + .5, tile.zCoord + .5) <= 64
            && p.worldObj.getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord) == tile;
    }

    public static void apply(EntityPlayerMP p, NBTTagCompound n) {
        if (!(p.openContainer instanceof ProjectorMenu)) return;
        ProjectorMenu menu = (ProjectorMenu) p.openContainer;
        ProjectorTile t = menu.tile;
        if (!menu.canInteractWith(p) || p.openContainer.windowId != n.getInteger("window")
            || !p.capabilities.allowEdit
            || !p.worldObj.canMineBlock(p, t.xCoord, t.yCoord, t.zCoord)
            || !p.canPlayerEdit(t.xCoord, t.yCoord, t.zCoord, 1, p.getHeldItem())) return;
        String problem = t.configure(
            ProjectorTile.decode(n),
            n.getString("frequency"),
            n.getInteger("exitFacing"),
            !n.getBoolean("close"),
            n.hasKey("stationName") ? n.getString("stationName") : t.stationName,
            n.hasKey("showDestination") ? n.getBoolean("showDestination") : t.showDestination,
            n.hasKey("sceneEnabled") ? n.getBoolean("sceneEnabled") : t.sceneEnabled,
            n.hasKey("snapshot") ? n.getBoolean("snapshot") : t.snapshot);
        if (!problem.isEmpty()) {
            p.addChatMessage(new net.minecraft.util.ChatComponentTranslation("projector.error." + problem));
            return;
        }
        p.closeScreen();
        if (t.open && t.frequency.isEmpty())
            p.addChatMessage(new net.minecraft.util.ChatComponentTranslation("projector.emptyFrequency"));
    }
}

package dev.aestoragevault.ui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import dev.aestoragevault.ui.dashboard.VaultLayout;
import dev.aestoragevault.world.TileController;

public class VaultContainer extends Container {

    public final TileController tile;

    public VaultContainer(EntityPlayer player, TileController tile) {
        this.tile = tile;
        VaultLayout layout = VaultLayout.full();
        for (int i = 0; i < 36; i++)
            addSlotToContainer(new Slot(player.inventory, i < 27 ? i + 9 : i - 27, layout.slotX(i), layout.slotY(i)));
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return !tile.isInvalid() && player.worldObj == tile.getWorldObj()
            && player.worldObj.getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord) == tile
            && player.getDistanceSq(tile.xCoord + 0.5, tile.yCoord + 0.5, tile.zCoord + 0.5) <= 64;
    }

    @Override
    public boolean enchantItem(EntityPlayer player, int action) {
        if (!canInteractWith(player) || player.worldObj.isRemote) return false;
        if (action == 0) tile.automatic = !tile.automatic;
        else if (action == 1) tile.checkStructure();
        else return false;
        tile.markDirty();
        tile.getWorldObj()
            .markBlockForUpdate(tile.xCoord, tile.yCoord, tile.zCoord);
        return true;
    }

    @Override
    public net.minecraft.item.ItemStack transferStackInSlot(EntityPlayer player, int slot) {
        return null;
    }
}

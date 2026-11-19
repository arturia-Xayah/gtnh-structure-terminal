package dev.structureterminal.bc;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import buildcraft.builders.TileArchitect;
import buildcraft.builders.gui.ContainerArchitect;
import buildcraft.builders.gui.ContainerFiller;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dev.structureterminal.StructureTerminal;

public final class RangeAccess {

    private static void open(EntityPlayer p, TileEntity tile) {
        if (RangeContainer.at(p, p.worldObj, tile.xCoord, tile.yCoord, tile.zCoord) != null) p
            .openGui(StructureTerminal.instance, RangeContainer.GUI, p.worldObj, tile.xCoord, tile.yCoord, tile.zCoord);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void unconfiguredTable(PlayerInteractEvent e) {
        if (e.world.isRemote || e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
            || e.useBlock == Event.Result.DENY
            || e.entityPlayer.getHeldItem() != null) return;
        TileEntity t = e.world.getTileEntity(e.x, e.y, e.z);
        if (t instanceof TileArchitect && ((TileArchitect) t).mode == TileArchitect.Mode.NONE) {
            e.setCanceled(true);
            open(e.entityPlayer, t);
        }
    }

    @SubscribeEvent
    public void drops(net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent event) {
        TileEntity tile = event.world.getTileEntity(event.x, event.y, event.z);
        if (!(tile instanceof RangeMachine) || tile.getBlockType() != event.block) return;
        int[] size = PortableRange.dimensions(tile);
        if (size.length != 3) return;
        for (net.minecraft.item.ItemStack stack : event.drops) {
            if (stack.getItem() != net.minecraft.item.Item.getItemFromBlock(event.block)) continue;
            if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
            stack.getTagCompound()
                .setIntArray(PortableRange.KEY, size.clone());
        }
    }

    public static void request(EntityPlayerMP p, NBTTagCompound tag) {
        Container c = p.openContainer;
        if (c == null || c.windowId != tag.getInteger("window")) return;
        if (tag.getBoolean("open")) {
            if ((c instanceof ContainerArchitect || c instanceof ContainerFiller) && c.canInteractWith(p)
                && c.getSlot(0).inventory instanceof TileEntity) open(p, (TileEntity) c.getSlot(0).inventory);
        } else if (c instanceof RangeContainer) ((RangeContainer) c).handle(p, tag);
    }
}

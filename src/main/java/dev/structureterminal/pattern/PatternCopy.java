package dev.structureterminal.pattern;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.AEApi;
import appeng.helpers.IInterfaceHost;

/** Memory-card copies pay one empty holder plus one real blank per child, atomically per holder. */
public final class PatternCopy {

    private PatternCopy() {}

    public static void save(NBTTagCompound data, IInterfaceHost host) {
        NBTTagList list = data.getTagList("interfacePatterns", 10);
        IInventory patterns = host.getPatterns();
        for (int i = 0; i < patterns.getSizeInventory(); i++) {
            ItemStack stack = patterns.getStackInSlot(i);
            if (!IntegratedPattern.is(stack)) continue;
            NBTTagCompound tag = stack.writeToNBT(new NBTTagCompound());
            tag.setInteger("Slot", i);
            list.appendTag(tag);
        }
        if (list.tagCount() > 0) data.setTag("interfacePatterns", list);
    }

    public static NBTTagCompound insert(NBTTagCompound source, EntityPlayer player, IInterfaceHost host) {
        NBTTagList list = source.getTagList("interfacePatterns", 10), ordinary = new NBTTagList();
        IInventory patterns = host.getPatterns();
        int enabled = Math.min(host.rows() * host.rowSize(), patterns.getSizeInventory());
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            ItemStack holder = ItemStack.loadItemStackFromNBT(tag);
            if (!IntegratedPattern.is(holder)) {
                ordinary.appendTag(tag.copy());
                continue;
            }
            int preferred = tag.getInteger("Slot"), free = -1;
            if (preferred >= 0 && preferred < enabled && patterns.getStackInSlot(preferred) == null) free = preferred;
            else for (int slot = 0; slot < enabled; slot++) if (patterns.getStackInSlot(slot) == null) {
                free = slot;
                break;
            }
            if (free >= 0 && consume(player, IntegratedPattern.count(holder))) {
                holder.stackSize = 1;
                patterns.setInventorySlotContents(free, holder);
            }
        }
        NBTTagCompound filtered = (NBTTagCompound) source.copy();
        filtered.setTag("interfacePatterns", ordinary);
        return filtered;
    }

    private static boolean consume(EntityPlayer player, int count) {
        if (player.capabilities.isCreativeMode) return true;
        int empty = -1, blanks = 0;
        int[] costs = new int[player.inventory.mainInventory.length];
        for (int slot = 0; slot < costs.length; slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (IntegratedPattern.is(stack) && IntegratedPattern.count(stack) == 0 && empty < 0) empty = slot;
            if (AEApi.instance()
                .definitions()
                .materials()
                .blankPattern()
                .isSameAs(stack)) {
                costs[slot] = Math.min(count - blanks, stack.stackSize);
                blanks += costs[slot];
            }
        }
        if (empty < 0 || blanks < count) return false;
        player.inventory.decrStackSize(empty, 1);
        for (int i = 0; i < costs.length; i++) if (costs[i] > 0) player.inventory.decrStackSize(i, costs[i]);
        player.inventory.markDirty();
        return true;
    }
}

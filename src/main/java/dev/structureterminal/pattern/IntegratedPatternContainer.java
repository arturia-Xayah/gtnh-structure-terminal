package dev.structureterminal.pattern;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** The held stack stays in its original slot. Writes are immediate, never deferred until close/death. */
public final class IntegratedPatternContainer extends Container {

    public final EntityPlayer owner;
    public final int heldSlot;
    public final ItemStack holder;
    public final InventoryBasic patterns;
    private boolean loading = true;

    public static boolean canOpen(EntityPlayer player, int slot) {
        return slot >= 0 && slot < 9
            && slot == player.inventory.currentItem
            && IntegratedPattern.is(player.inventory.getStackInSlot(slot));
    }

    public IntegratedPatternContainer(EntityPlayer player, int slot) {
        owner = player;
        heldSlot = slot;
        holder = player.inventory.getStackInSlot(slot);
        patterns = new InventoryBasic("integrated_pattern", false, IntegratedPattern.SIZE) {

            @Override
            public int getInventoryStackLimit() {
                return 1;
            }

            @Override
            public boolean isItemValidForSlot(int index, ItemStack stack) {
                return IntegratedPattern.details(stack, owner.worldObj) != null;
            }

            @Override
            public void markDirty() {
                super.markDirty();
                if (!loading && !owner.worldObj.isRemote && canInteractWith(owner)) {
                    ItemStack[] all = new ItemStack[IntegratedPattern.SIZE];
                    for (int i = 0; i < all.length; i++) all[i] = getStackInSlot(i);
                    IntegratedPattern.write(holder, all);
                    owner.inventory.markDirty();
                }
            }
        };
        ItemStack[] stored = IntegratedPattern.read(holder);
        for (int i = 0; i < stored.length; i++) patterns.setInventorySlotContents(i, stored[i]);
        for (int row = 0; row < 4; row++) for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(patterns, row * 9 + col, 8 + col * 18, 18 + row * 18) {

                @Override
                public boolean isItemValid(ItemStack stack) {
                    return patterns.isItemValidForSlot(getSlotIndex(), stack);
                }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlotToContainer(new Slot(player.inventory, 9 + row * 9 + col, 8 + col * 18, 103 + row * 18));
        for (int col = 0; col < 9; col++) {
            final boolean locked = col == heldSlot;
            addSlotToContainer(new Slot(player.inventory, col, 8 + col * 18, 161) {

                @Override
                public boolean isItemValid(ItemStack stack) {
                    return !locked;
                }

                @Override
                public boolean canTakeStack(EntityPlayer who) {
                    return !locked;
                }
            });
        }
        loading = false;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return player == owner && !player.isDead
            && player.inventory.currentItem == heldSlot
            && (player.worldObj.isRemote || player.inventory.getStackInSlot(heldSlot) == holder)
            && IntegratedPattern.is(player.inventory.getStackInSlot(heldSlot));
    }

    @Override
    public ItemStack slotClick(int slot, int button, int mode, EntityPlayer player) {
        if (!canInteractWith(player) || slot == 63 + heldSlot || mode == 2 && button == heldSlot) return null;
        return super.slotClick(slot, button, mode, player);
    }

    @Override
    public boolean canDragIntoSlot(Slot slot) {
        return !(slot.inventory == owner.inventory && slot.getSlotIndex() == heldSlot);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (!canInteractWith(player) || index < 0 || index >= inventorySlots.size() || index == 63 + heldSlot)
            return null;
        Slot source = (Slot) inventorySlots.get(index);
        if (!source.getHasStack()) return null;
        ItemStack stack = source.getStack(), original = stack.copy();
        boolean moved;
        if (index < 36) moved = mergeItemStack(stack, 36, 72, true);
        else {
            if (!patterns.isItemValidForSlot(0, stack)) return null;
            // Vanilla mergeItemStack's empty-slot pass ignores per-slot limits. Move exactly one per slot.
            moved = false;
            for (int i = 0; i < 36 && stack.stackSize > 0; i++) if (patterns.getStackInSlot(i) == null) {
                ItemStack one = stack.splitStack(1);
                patterns.setInventorySlotContents(i, one);
                moved = true;
            }
        }
        if (!moved) return null;
        if (stack.stackSize == 0) source.putStack(null);
        else source.onSlotChanged();
        source.onPickupFromSlot(player, stack);
        return original;
    }
}

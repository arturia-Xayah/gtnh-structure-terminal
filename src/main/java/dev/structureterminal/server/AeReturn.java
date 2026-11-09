package dev.structureterminal.server;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.util.item.AEItemStack;

/** Insertion-only return route. Rejected items stay on the tool, never in the backpack or on the ground. */
public final class AeReturn {

    private static final String PENDING = "PendingAeDismantleReturn";

    interface Sink {

        boolean powered(long amount);

        boolean charge(long amount);

        IAEItemStack insert(IAEItemStack stack, Actionable mode);
    }

    private final EntityPlayerMP player;
    private final ItemStack tool;
    private final Sink sink;

    AeReturn(EntityPlayerMP player, ItemStack tool, Sink sink) {
        this.player = player;
        this.tool = tool;
        this.sink = sink;
    }

    public static boolean pending(ItemStack tool) {
        return tool != null && tool.hasTagCompound()
            && tool.getTagCompound()
                .getTagList(PENDING, 10)
                .tagCount() > 0;
    }

    public static AeReturn open(EntityPlayerMP player) {
        for (int slot = 0; slot < player.inventory.mainInventory.length; slot++) {
            ItemStack terminal = player.inventory.mainInventory[slot];
            if (terminal == null) continue;
            IWirelessTermHandler handler = AEApi.instance()
                .registries()
                .wireless()
                .getWirelessTerminalHandler(terminal);
            if (handler == null || !handler.hasInfinityPower(terminal) && !handler.hasPower(player, 1, terminal))
                continue;
            WirelessTerminalGuiObject access = new WirelessTerminalGuiObject(
                handler,
                terminal,
                player,
                player.worldObj,
                slot,
                0,
                0);
            if (!access.rangeCheck()) continue;
            IGridNode node = access.getActionableNode();
            if (node == null || !node.isActive()) continue;
            ISecurityGrid security = node.getGrid()
                .getCache(ISecurityGrid.class);
            if (security != null && !security.hasPermission(player, SecurityPermissions.INJECT)) continue;
            IMEMonitor<IAEItemStack> inventory = access.getItemInventory();
            if (inventory == null) continue;
            return new AeReturn(player, player.getHeldItem(), new Sink() {

                private boolean allowed() {
                    return node.isActive() && access.rangeCheck()
                        && (security == null || security.hasPermission(player, SecurityPermissions.INJECT));
                }

                public boolean powered(long n) {
                    return allowed() && (handler.hasInfinityPower(terminal) || handler.hasPower(player, n, terminal));
                }

                public boolean charge(long n) {
                    return powered(n) && (handler.hasInfinityPower(terminal) || handler.usePower(player, n, terminal));
                }

                public IAEItemStack insert(IAEItemStack stack, Actionable mode) {
                    return allowed() ? inventory.injectItems(stack, mode, new PlayerSource(player, access)) : stack;
                }
            });
        }
        return null;
    }

    public boolean canAccept(List<ItemStack> drops) {
        if (pending(tool)) return false;
        long count = 0;
        for (ItemStack drop : drops) {
            count += drop.stackSize;
            IAEItemStack remaining = sink.insert(AEItemStack.create(drop), Actionable.SIMULATE);
            if (remaining != null && remaining.getStackSize() > 0) return false;
        }
        return sink.powered(count);
    }

    public void stage(List<ItemStack> drops) {
        if (pending(tool)) throw new IllegalStateException("AE return pending");
        write(drops);
    }

    public void cancel() {
        write(new ArrayList<ItemStack>());
    }

    /** Recheck on every retry; updating the remainder prevents duplicates after partial insertion. */
    public boolean flush() {
        List<ItemStack> remaining = new ArrayList<ItemStack>();
        if (tool.hasTagCompound()) {
            NBTTagList list = tool.getTagCompound()
                .getTagList(PENDING, 10);
            for (int i = 0; i < list.tagCount(); i++) {
                ItemStack item = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i));
                if (item != null && item.stackSize > 0) remaining.add(item);
            }
        }
        while (!remaining.isEmpty()) {
            ItemStack item = remaining.get(0);
            IAEItemStack probe = sink.insert(AEItemStack.create(item), Actionable.SIMULATE);
            long accepted = item.stackSize - (probe == null ? 0 : probe.getStackSize());
            if (accepted <= 0 || !sink.charge(accepted)) return false;
            ItemStack request = item.copy();
            request.stackSize = (int) accepted;
            IAEItemStack rest = sink.insert(AEItemStack.create(request), Actionable.MODULATE);
            long inserted = accepted - (rest == null ? 0 : rest.getStackSize());
            if (inserted <= 0) return false;
            item.stackSize -= (int) inserted;
            if (item.stackSize == 0) remaining.remove(0);
            write(remaining);
        }
        return true;
    }

    private void write(List<ItemStack> drops) {
        if (!tool.hasTagCompound()) tool.setTagCompound(new NBTTagCompound());
        NBTTagList list = new NBTTagList();
        for (ItemStack drop : drops) list.appendTag(drop.writeToNBT(new NBTTagCompound()));
        if (list.tagCount() == 0) tool.getTagCompound()
            .removeTag(PENDING);
        else tool.getTagCompound()
            .setTag(PENDING, list);
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
    }
}

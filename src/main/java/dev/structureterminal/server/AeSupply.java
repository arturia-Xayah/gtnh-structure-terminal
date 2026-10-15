package dev.structureterminal.server;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

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

/** Optional integration, instantiated only after Loader reports AE2 present. */
public final class AeSupply {

    private AeSupply() {}

    public static Map<ItemStack, Integer> take(EntityPlayerMP player, Predicate<ItemStack> predicate, boolean simulate,
        int count) {
        return take(player, predicate, simulate, count, null);
    }

    public static boolean takeOne(EntityPlayerMP player, ItemStack item, boolean simulate) {
        return !take(player, s -> dev.structureterminal.bridge.ItemKeys.same(s, item), simulate, 1, item).isEmpty();
    }

    private static Map<ItemStack, Integer> take(EntityPlayerMP player, Predicate<ItemStack> predicate, boolean simulate,
        int count, ItemStack exact) {
        Map<ItemStack, Integer> result = new LinkedHashMap<ItemStack, Integer>();
        for (int slot = 0; slot < player.inventory.mainInventory.length; slot++) {
            ItemStack terminal = player.inventory.mainInventory[slot];
            if (terminal == null) continue;
            IWirelessTermHandler handler = AEApi.instance()
                .registries()
                .wireless()
                .getWirelessTerminalHandler(terminal);
            if (handler == null || (!handler.hasInfinityPower(terminal) && !handler.hasPower(player, 1, terminal)))
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
            if (security != null && !security.hasPermission(player, SecurityPermissions.EXTRACT)) continue;
            IMEMonitor<IAEItemStack> inventory = access.getItemInventory();
            if (inventory == null) continue;
            int left = count;
            Iterable<IAEItemStack> candidates = inventory.getStorageList();
            if (exact != null) {
                IAEItemStack found = inventory.getStorageList()
                    .findPrecise(AEItemStack.create(exact));
                candidates = found == null ? Collections.emptyList() : Collections.singletonList(found);
            }
            for (IAEItemStack stored : candidates) {
                ItemStack item = stored.getItemStack();
                if (!predicate.test(item)) continue;
                IAEItemStack request = AEItemStack.create(item);
                request.setStackSize(Math.min((long) left, stored.getStackSize()));
                if (request.getStackSize() < 1) continue;
                IAEItemStack possible = inventory
                    .extractItems(request, Actionable.SIMULATE, new PlayerSource(player, access));
                if (possible == null || possible.getStackSize() < 1) continue;
                double energy = possible.getStackSize();
                if (!handler.hasInfinityPower(terminal) && !handler.hasPower(player, energy, terminal)) continue;
                if (!simulate && !handler.hasInfinityPower(terminal) && !handler.usePower(player, energy, terminal))
                    continue;
                IAEItemStack extracted = simulate ? possible
                    : inventory.extractItems(possible, Actionable.MODULATE, new PlayerSource(player, access));
                if (extracted == null) continue;
                int amount = (int) Math.min(left, extracted.getStackSize());
                if (amount < 1) continue;
                ItemStack copy = extracted.getItemStack();
                copy.stackSize = amount;
                result.put(copy, amount);
                left -= amount;
                if (left == 0) return result;
            }
            // Use one accessible grid per operation; duplicate terminals must not double-count simulated stock.
            return result;
        }
        return result;
    }
}

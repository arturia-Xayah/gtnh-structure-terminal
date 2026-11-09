package dev.structureterminal.pattern;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.structureterminal.StructureTerminal;

/** C08 copies the held stack after item use. Bind the container only after that copy is complete. */
public final class IntegratedPatternOpening {

    public static final IntegratedPatternOpening INSTANCE = new IntegratedPatternOpening();
    private final Map<EntityPlayer, Request> pending = new WeakHashMap<>();

    private IntegratedPatternOpening() {}

    public void request(EntityPlayer player) {
        if (player.worldObj.isRemote || !IntegratedPatternContainer.canOpen(player, player.inventory.currentItem))
            return;
        pending.put(player, new Request(player));
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Map.Entry<EntityPlayer, Request> entry : pending.entrySet()) {
            EntityPlayer player = entry.getKey();
            Request request = entry.getValue();
            if (!player.isDead && player.openContainer == request.previous
                && IntegratedPatternContainer.canOpen(player, request.slot)
                && ItemStack.areItemStacksEqual(request.stack, player.getHeldItem())) {
                player.openGui(StructureTerminal.instance, IntegratedPattern.GUI, player.worldObj, request.slot, 0, 0);
            }
        }
        pending.clear();
    }

    private static final class Request {

        final int slot;
        final ItemStack stack;
        final Container previous;

        Request(EntityPlayer player) {
            slot = player.inventory.currentItem;
            stack = player.getHeldItem()
                .copy();
            previous = player.openContainer;
        }
    }
}

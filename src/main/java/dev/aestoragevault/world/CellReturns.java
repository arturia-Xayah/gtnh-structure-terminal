package dev.aestoragevault.world;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.UUID;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.ThreadedFileIOBase;

import dev.aestoragevault.ae.CellReader;
import dev.aestoragevault.core.CellContribution;
import dev.aestoragevault.core.PendingReturn;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;

/** Capacity debit + recoverable native-bus delivery, all on the server thread. No player/world drops. */
public final class CellReturns {

    private CellReturns() {}

    public static BigInteger possible(TileController tile, String key) {
        try {
            VaultStructure.Shape shape = VaultStructure.inspectExclusive(tile);
            CellContribution c = tile.store() == null ? null
                : tile.store()
                    .contribution(key);
            if (shape == null || shape.output == null || c == null || !tile.available()) return BigInteger.ZERO;
            return tile.store()
                .maxReturn(key)
                .min(BigInteger.valueOf(room(shape.output, prototype(c))));
        } catch (IOException | RuntimeException e) {
            return BigInteger.ZERO;
        }
    }

    public static int eject(TileController tile, String key, BigInteger requested) throws Exception {
        if (requested.signum() <= 0 || !tile.available()) return 0;
        VaultStructure.Shape shape = VaultStructure.inspectExclusive(tile);
        CellContribution c = tile.store()
            .contribution(key);
        if (shape == null || shape.output == null || c == null) return 0;
        ItemStack item = prototype(c);
        int amount = possible(tile, key).min(requested)
            .intValueExact();
        if (amount == 0) return 0;
        byte[] target = destination(tile, shape);
        // Save destination identity before making a durable debit, just like source identity before imports.
        shape.output.markDirty();
        save(tile);
        amount = Math.min(amount, room(shape.output, item));
        PendingReturn pending = tile.store()
            .beginReturn(UUID.randomUUID(), key, amount, target);
        if (pending == null) return 0;
        tile.returnChanged();
        deliver(tile, pending);
        return pending.source.count.intValueExact();
    }

    public static byte[] destination(TileController tile, VaultStructure.Shape shape) throws IOException {
        ReturnBus bus = (ReturnBus) shape.output;
        NBTTagCompound destination = new NBTTagCompound();
        destination.setString(
            "Bus",
            bus.vaultBusId()
                .toString());
        destination.setInteger("Dimension", tile.getWorldObj().provider.dimensionId);
        destination.setInteger("X", shape.outputBase.getXCoord());
        destination.setInteger("Y", shape.outputBase.getYCoord());
        destination.setInteger("Z", shape.outputBase.getZCoord());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(destination, bytes);
        return bytes.toByteArray();
    }

    /** Reconcile saved receipts before allowing AE access or another absorption/ejection. */
    public static void recover(TileController tile) throws Exception {
        if (tile.store() == null || !tile.store()
            .isHealthy()) return;
        VaultStructure.Shape shape = VaultStructure.inspectExclusive(tile);
        if (shape == null || shape.output == null) return;
        ReturnBus bus = (ReturnBus) shape.output;
        for (UUID token : new ArrayList<>(
            bus.vaultDeliveries()
                .keySet())) {
            if (tile.id.equals(
                bus.vaultDeliveries()
                    .get(token))
                && tile.store()
                    .hasReceipt(token)
                && tile.store()
                    .pendingReturns()
                    .stream()
                    .noneMatch(p -> p.id.equals(token))) {
                bus.vaultDeliveries()
                    .remove(token);
                shape.output.markDirty();
            }
        }
        for (PendingReturn pending : tile.store()
            .pendingReturns()) deliver(tile, pending);
    }

    private static void deliver(TileController tile, PendingReturn pending) throws Exception {
        NBTTagCompound dest = CompressedStreamTools.readCompressed(new ByteArrayInputStream(pending.destination()));
        VaultStructure.Shape shape = VaultStructure.inspectExclusive(tile);
        if (shape == null || shape.output == null
            || shape.outputBase.getXCoord() != dest.getInteger("X")
            || shape.outputBase.getYCoord() != dest.getInteger("Y")
            || shape.outputBase.getZCoord() != dest.getInteger("Z")
            || tile.getWorldObj().provider.dimensionId != dest.getInteger("Dimension"))
            throw new IOException("Return awaits its original output bus");
        ReturnBus bus = (ReturnBus) shape.output;
        if (!bus.vaultBusId()
            .toString()
            .equals(dest.getString("Bus")))
            throw new IOException("Return output bus identity changed; pending delivery retained");
        if (bus.vaultDeliveries()
            .containsKey(pending.id)) {
            if (!tile.id.equals(
                bus.vaultDeliveries()
                    .get(pending.id)))
                throw new IOException("Return owner mismatch");
        } else {
            ItemStack item = prototype(pending.source);
            int amount = pending.source.count.intValueExact();
            if (room(shape.output, item) < amount) throw new IOException("Return awaits output space");
            bus.vaultDeliveries()
                .put(pending.id, tile.id);
            insert(shape.output, item, amount);
            shape.output.markDirty();
        }
        // Receipt and physical items share native tile NBT. Holding guards prevent normal extraction until fsync.
        save(tile);
        tile.store()
            .completeReturn(pending.id);
        bus.vaultDeliveries()
            .remove(pending.id);
        shape.output.markDirty();
        tile.status = "Returned " + pending.source.count + " cells";
        tile.returnChanged();
    }

    public static ItemStack prototype(CellContribution c) throws IOException {
        Object raw = Item.itemRegistry.getObject(c.item);
        if (!(raw instanceof Item)) throw new IOException("Return source item is unavailable");
        ItemStack item = new ItemStack((Item) raw, 1, c.damage);
        CellReader.read(item); // Only a safely reconstructable empty source may be returned.
        return item;
    }

    public static int room(MTEHatchOutputBus bus, ItemStack item) {
        if (!((ReturnBus) bus).vaultDeliveries()
            .isEmpty()) return 0;
        if (bus.getLockedItem() != null && !bus.getLockedItem()
            .isItemEqual(item)) return 0;
        long room = 0;
        for (int i = 0; i < bus.mInventory.length; i++) {
            ItemStack slot = bus.mInventory[i];
            int limit = Math.min(item.getMaxStackSize(), bus.getStackSizeLimit(i, item));
            if (slot == null) room += limit;
            else if (slot.isItemEqual(item) && ItemStack.areItemStackTagsEqual(slot, item))
                room += Math.max(0, limit - slot.stackSize);
        }
        return (int) Math.min(Integer.MAX_VALUE, room);
    }

    private static void insert(MTEHatchOutputBus bus, ItemStack item, int amount) {
        for (int i = 0; i < bus.mInventory.length && amount > 0; i++) {
            ItemStack slot = bus.mInventory[i];
            if (slot != null && !(slot.isItemEqual(item) && ItemStack.areItemStackTagsEqual(slot, item))) continue;
            int present = slot == null ? 0 : slot.stackSize;
            int added = Math
                .min(amount, Math.max(0, Math.min(item.getMaxStackSize(), bus.getStackSizeLimit(i, item)) - present));
            if (added == 0) continue;
            if (slot == null) {
                slot = item.copy();
                slot.stackSize = 0;
                bus.mInventory[i] = slot;
            }
            slot.stackSize += added;
            amount -= added;
        }
        if (amount != 0) throw new IllegalStateException("Output plan changed during commit");
    }

    private static void save(TileController tile) throws Exception {
        tile.markDirty();
        ((WorldServer) tile.getWorldObj()).saveAllChunks(true, null);
        ThreadedFileIOBase.threadedIOInstance.waitForFinish();
    }
}

package dev.aestoragevault.world;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.ThreadedFileIOBase;

import dev.aestoragevault.VaultMod;
import dev.aestoragevault.ae.CellReader;
import dev.aestoragevault.core.VaultStore;
import dev.aestoragevault.storage.Repositories;

public class TileController extends TileEntity {

    public static final String TOKEN = "aestoragevault_import";
    public UUID id;
    public long capacity;
    public long used;
    public int types;
    public boolean formed;
    public boolean automatic = true;
    public String status = "Waiting for structure check";
    private boolean invalidIdentity;
    private VaultStore store;
    private IInventory bus;
    private TileAccess access;
    private boolean loaded;
    private boolean checking;

    public VaultStore store() {
        return store;
    }

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) return;
        if (!loaded) {
            loaded = true;
            if (invalidIdentity) {
                status = "LOCKED: Invalid inventory identity";
                return;
            }
            boolean create = id == null;
            if (create) {
                id = UUID.randomUUID();
                markDirty();
            }
            try {
                store = Repositories.INSTANCE.open(this, id, create);
                capacity = store.capacity();
            } catch (IOException | RuntimeException e) {
                status = "LOCKED: " + e.getMessage();
                VaultMod.LOG.error(status, e);
            }
        }
        if (worldObj.getTotalWorldTime() % 20 == 0) {
            checkStructure();
            if (formed && automatic && available()) absorb();
            updateSummary();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    public boolean available() {
        return store != null && store.isHealthy()
            && !status.startsWith("LOCKED:")
            && !isInvalid()
            && checkShape()
            && formed;
    }

    /** Every AE access validates the 27 cells: broken/unloaded structures cannot use stale mounted storage. */
    private boolean checkShape() {
        if (worldObj == null || checking) return false;
        VaultStructure.Shape shape = VaultStructure.inspectExclusive(this);
        if (shape == null) return false;
        IInventory foundBus = shape.bus;
        TileAccess foundAccess = shape.access;
        // Exclusive inspection rejects every other complete shape, so any remaining binding is stale.
        if (foundAccess.bound != null && foundAccess.bound != this) foundAccess.unbind(foundAccess.bound);
        bus = foundBus;
        if (access != foundAccess && !checking) {
            if (access != null) access.unbind(this);
            access = foundAccess;
            access.bind(this);
        }
        return true;
    }

    public void checkStructure() {
        boolean valid = checkShape();
        boolean changed = valid != formed;
        formed = valid;
        if (!valid) {
            if (access != null) {
                checking = true;
                access.unbind(this);
                access = null;
                checking = false;
            }
            bus = null;
        }
        if (store != null && !status.startsWith("LOCKED:"))
            status = formed ? "Online" : "Need face-center controller, 23 LuV casings, exclusive bus/access";
        if (changed && access != null) access.refresh();
    }

    private void absorb() {
        if (bus == null) return;
        List<Import> plans = new ArrayList<>();
        for (int slot = 0; slot < bus.getSizeInventory(); slot++) {
            ItemStack input = bus.getStackInSlot(slot);
            if (input == null) continue;
            UUID token = readToken(input);
            if (token != null && store.hasReceipt(token)) {
                bus.setInventorySlotContents(slot, null);
                bus.markDirty();
                continue; // durable transaction already credited this cell before a world rollback
            }
            try {
                CellReader plan = CellReader.read(input);
                if (token == null) {
                    token = UUID.randomUUID();
                    if (!input.hasTagCompound()) input.setTagCompound(new NBTTagCompound());
                    input.getTagCompound()
                        .setString(TOKEN, token.toString());
                }
                plans.add(new Import(slot, token, input.copy(), plan));
            } catch (IOException | RuntimeException e) {
                status = "Slot " + (slot + 1) + ": " + e.getMessage();
            }
        }
        if (plans.isEmpty()) return;
        try {
            // Persist source identity BEFORE external credit. Replay can then consume restored tokens without
            // re-credit.
            bus.markDirty();
            markDirty();
            ((WorldServer) worldObj).saveAllChunks(true, null);
            ThreadedFileIOBase.threadedIOInstance.waitForFinish();
            for (Import plan : plans) {
                ItemStack now = bus.getStackInSlot(plan.slot);
                if (now == null || !ItemStack.areItemStacksEqual(now, plan.source)) {
                    status = "Input changed during save; source retained";
                    continue;
                }
                store.absorb(plan.token, plan.cell.bytes, plan.cell.contents);
                capacity = store.capacity();
                markDirty();
                bus.setInventorySlotContents(plan.slot, null);
                bus.markDirty();
                if (access != null) access.notifyImport(plan.cell.contents);
            }
            status = "Cells absorbed; contents migrated";
        } catch (IOException | net.minecraft.world.MinecraftException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) Thread.currentThread()
                .interrupt();
            status = "Import paused: " + e.getMessage();
            VaultMod.LOG.error(status, e);
        }
    }

    private static UUID readToken(ItemStack stack) {
        if (!stack.hasTagCompound() || !stack.getTagCompound()
            .hasKey(TOKEN)) return null;
        try {
            return UUID.fromString(
                stack.getTagCompound()
                    .getString(TOKEN));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void updateSummary() {
        if (store != null) {
            capacity = store.capacity();
            used = store.usedBytesCeil();
            types = store.types();
        }
        markDirty();
    }

    public void ioFailure(Exception error) {
        status = "LOCKED: " + error.getMessage();
        automatic = false;
        if (access != null) access.refresh();
        VaultMod.LOG.error(status, error);
    }

    @Override
    public void invalidate() {
        detach();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        detach();
        super.onChunkUnload();
    }

    private void detach() {
        formed = false;
        if (access != null) access.unbind(this);
        access = null;
        Repositories.INSTANCE.release(this);
        store = null;
        loaded = false;
    }

    public NBTTagCompound itemData() {
        NBTTagCompound tag = new NBTTagCompound();
        if (id != null) tag.setString("VaultId", id.toString());
        tag.setLong("MaximumBytes", store == null ? capacity : store.capacity());
        tag.setBoolean("Automatic", automatic);
        return tag;
    }

    public void restoreItem(NBTTagCompound tag) {
        try {
            if (tag.hasKey("VaultId")) id = UUID.fromString(tag.getString("VaultId"));
        } catch (IllegalArgumentException e) {
            invalidIdentity = true;
        }
        capacity = Math.max(0, tag.getLong("MaximumBytes"));
        automatic = !tag.hasKey("Automatic") || tag.getBoolean("Automatic");
        if (capacity > 0 && id == null) invalidIdentity = true;
        markDirty();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagCompound data = itemData();
        for (Object key : data.func_150296_c()) tag.setTag((String) key, data.getTag((String) key));
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        restoreItem(tag);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = itemData();
        tag.setLong("Used", used);
        tag.setInteger("Types", types);
        tag.setBoolean("Formed", formed);
        tag.setString("Status", status);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager network, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();
        restoreItem(tag);
        used = tag.getLong("Used");
        types = tag.getInteger("Types");
        formed = tag.getBoolean("Formed");
        status = tag.getString("Status");
        worldObj.markBlockRangeForRenderUpdate(xCoord - 2, yCoord - 2, zCoord - 2, xCoord + 2, yCoord + 2, zCoord + 2);
    }

    private static final class Import {

        final int slot;
        final UUID token;
        final ItemStack source;
        final CellReader cell;

        Import(int slot, UUID token, ItemStack source, CellReader cell) {
            this.slot = slot;
            this.token = token;
            this.source = source;
            this.cell = cell;
        }
    }
}

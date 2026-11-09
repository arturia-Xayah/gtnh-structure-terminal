package dev.aestoragevault.world;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.ThreadedFileIOBase;

import dev.aestoragevault.VaultMod;
import dev.aestoragevault.ae.CellReader;
import dev.aestoragevault.core.CapacityValue;
import dev.aestoragevault.core.CellContribution;
import dev.aestoragevault.core.VaultStore;
import dev.aestoragevault.storage.Repositories;

public class TileController extends TileEntity {

    public static final String TOKEN = "aestoragevault_import";
    public UUID id;
    public long ledgerSequence = -1;
    public BigInteger capacity = BigInteger.ZERO;
    public BigInteger used = BigInteger.ZERO;
    public int types;
    public volatile boolean formed;
    /** Client-owned render state; never serialized or referenced by the dedicated server. */
    public Object clientSkin;
    public boolean automatic = true;
    public String status = "Waiting for structure check";
    private boolean invalidIdentity;
    private VaultStore store;
    private IInventory bus;
    private ReturnBus returnBus;
    private TileAccess access;
    private boolean loaded;
    private boolean checking;
    private List<CellContribution> savedContributions = new ArrayList<>();
    public List<CellContribution> historyPage = new ArrayList<>();
    private int pendingWindow = -1, pendingPage, pendingPart;
    private List<CellContribution> pendingHistory = new ArrayList<>();
    public boolean historyReturnBus;
    public int returnReplyVersion, returnReplyWindow = -1, returnedCount;
    public String returnMessage = "";
    public int historyWindow = -1, historyIndex, historyPages = 1, historyTypes;
    public BigInteger historyCapacity = BigInteger.ZERO;

    public List<CellContribution> contributions() {
        return store == null ? new ArrayList<>(savedContributions) : store.contributions();
    }

    public static NBTTagList historyNbt(List<CellContribution> entries) {
        NBTTagList list = new NBTTagList();
        for (CellContribution c : entries) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("Item", c.item);
            tag.setInteger("Damage", c.damage);
            tag.setByteArray("Count", CapacityValue.encode(c.count));
            tag.setByteArray("Bytes", CapacityValue.encode(c.bytes));
            list.appendTag(tag);
        }
        return list;
    }

    private static List<CellContribution> readHistory(NBTTagList list, int max) {
        if (list.tagCount() > max) throw new IllegalArgumentException("Too many cell definitions");
        List<CellContribution> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            CellContribution c = new CellContribution(
                t.getString("Item"),
                t.getInteger("Damage"),
                CapacityValue.decode(t.getByteArray("Count")),
                CapacityValue.decode(t.getByteArray("Bytes")));
            if (!seen.add(c.key())) throw new IllegalArgumentException("Duplicate cell definition");
            result.add(c);
        }
        return result;
    }

    public VaultStore store() {
        return store;
    }

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) {
            if (worldObj.getTotalWorldTime() % 20 == 0) VaultMod.proxy.updateSkin(this, false);
            return;
        }
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
            try {
                if (formed && store != null
                    && (store.hasPendingReturn() || returnBus != null && !returnBus.vaultDeliveries()
                        .isEmpty()))
                    CellReturns.recover(this);
            } catch (Exception e) {
                ioFailure(e);
            }
            if (formed && automatic && available()) absorb();
            updateSummary();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    public boolean available() {
        return store != null && store.isHealthy()
            && !store.hasPendingReturn()
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
        returnBus = shape.output == null ? null : (ReturnBus) shape.output;
        // An access hatch may detach itself while a pending return holds IO; bind it again on recovery.
        if (access != foundAccess || foundAccess.bound != this) {
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
            returnBus = null;
        }
        if (store != null && !status.startsWith("LOCKED:"))
            status = formed ? "Online" : "Need face-center controller, LuV casings, exclusive buses/access";
        if (changed && access != null) access.refresh();
        if (changed && worldObj != null && !worldObj.isRemote) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
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
                store.absorbCell(
                    plan.token,
                    plan.cell.bytes,
                    plan.cell.contents,
                    String.valueOf(net.minecraft.item.Item.itemRegistry.getNameForObject(plan.source.getItem())),
                    plan.source.getItemDamage(),
                    plan.source.stackSize);
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

    public void returnChanged() {
        updateSummary();
        if (store != null) ledgerSequence = store.sequence();
        if (access != null) access.refresh();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
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
        if (worldObj != null && worldObj.isRemote) VaultMod.proxy.updateSkin(this, true);
        if (store != null) {
            savedContributions = store.contributions();
            ledgerSequence = store.sequence();
        }
        formed = false;
        if (access != null) access.unbind(this);
        access = null;
        returnBus = null;
        Repositories.INSTANCE.release(this);
        store = null;
        loaded = false;
    }

    public NBTTagCompound itemData() {
        return descriptionData(true);
    }

    private NBTTagCompound descriptionData(boolean includeHistory) {
        NBTTagCompound tag = new NBTTagCompound();
        if (id != null) tag.setString("VaultId", id.toString());
        tag.setByteArray("MaximumBytesBig", CapacityValue.encode(store == null ? capacity : store.capacity()));
        tag.setBoolean("Automatic", automatic);
        tag.setLong("LedgerSequence", store == null ? ledgerSequence : store.sequence());
        if (includeHistory) tag.setTag("AbsorbedCells", historyNbt(contributions()));
        return tag;
    }

    public void restoreItem(NBTTagCompound tag) {
        ledgerSequence = tag.hasKey("LedgerSequence") ? tag.getLong("LedgerSequence") : -1;
        try {
            if (tag.hasKey("VaultId")) id = UUID.fromString(tag.getString("VaultId"));
        } catch (IllegalArgumentException e) {
            invalidIdentity = true;
        }
        try {
            capacity = readBytes(tag, "MaximumBytesBig", "MaximumBytes");
            if (tag.hasKey("AbsorbedCells"))
                savedContributions = readHistory(tag.getTagList("AbsorbedCells", 10), 1024);
        } catch (IllegalArgumentException e) {
            invalidIdentity = true;
            capacity = BigInteger.ZERO;
        }
        automatic = !tag.hasKey("Automatic") || tag.getBoolean("Automatic");
        if (capacity.signum() > 0 && id == null) invalidIdentity = true;
        markDirty();
    }

    private static BigInteger readBytes(NBTTagCompound tag, String key, String legacyKey) {
        return tag.hasKey(key) ? CapacityValue.decode(tag.getByteArray(key))
            : BigInteger.valueOf(Math.max(0, tag.getLong(legacyKey)));
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
        NBTTagCompound tag = descriptionData(false); // Only detail viewers receive contribution records.
        tag.setByteArray("UsedBig", CapacityValue.encode(used));
        tag.setInteger("Types", types);
        tag.setBoolean("Formed", formed);
        tag.setString("Status", status);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager network, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();
        if (tag.hasKey("ReturnWindow")) {
            returnReplyWindow = tag.getInteger("ReturnWindow");
            returnedCount = tag.getInteger("Returned");
            returnMessage = tag.getString("ReturnMessage");
            returnReplyVersion++;
            return;
        }
        if (tag.hasKey("HistoryWindow")) {
            try {
                int window = tag.getInteger("HistoryWindow"), page = tag.getInteger("Page");
                int part = tag.getInteger("Part"), parts = tag.getInteger("Parts");
                if (parts < 1 || parts > 2 || part < 0 || part >= parts)
                    throw new IllegalArgumentException("Invalid history packet parts");
                if (part == 0) {
                    pendingHistory = new ArrayList<>();
                    pendingWindow = window;
                    pendingPage = page;
                    pendingPart = 0;
                    historyWindow = -1;
                }
                if (pendingWindow != window || pendingPage != page || pendingPart != part)
                    throw new IllegalArgumentException("Out-of-order history page");
                pendingHistory.addAll(readHistory(tag.getTagList("Cells", 10), 3));
                pendingPart++;
                if (part + 1 < parts) return;
                historyPage = pendingHistory;
                historyWindow = window;
                historyIndex = tag.getInteger("Page");
                historyPages = tag.getInteger("Pages");
                historyTypes = tag.getInteger("CellTypes");
                historyReturnBus = tag.getBoolean("ReturnBus");
                historyCapacity = CapacityValue.decode(tag.getByteArray("Capacity"));
            } catch (IllegalArgumentException e) {
                historyWindow = -1;
                historyPage.clear();
            }
            return;
        }
        restoreItem(tag);
        used = readBytes(tag, "UsedBig", "Used");
        types = tag.getInteger("Types");
        formed = tag.getBoolean("Formed");
        status = tag.getString("Status");
        VaultMod.proxy.updateSkin(this, false);
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

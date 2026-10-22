package dev.aestoragevault.world;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.networking.GridFlags;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.IConfigManager;
import appeng.helpers.IOreFilterable;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import appeng.util.item.IAEStackList;
import appeng.util.prioitylist.OreFilteredList;
import dev.aestoragevault.VaultMod;
import dev.aestoragevault.ae.ResourceCodec;
import dev.aestoragevault.core.Resource;
import dev.aestoragevault.core.VaultStore;

/** An exclusive alternative to a storage link: injects stock, never mounts it in the destination grid. */
public class TileOutput extends TileAccess
    implements IUpgradeableHost, IAEAppEngInventory, IIAEStackInventory, IConfigManagerHost, IOreFilterable {

    public static final int FILTER_SLOTS = 36;
    public static final int TYPES_PER_TICK = 32;
    public static final long BATCH_AMOUNT = 1L << 30;
    public static final long SOFT_BUDGET_NANOS = 500_000;
    public final IAEStackInventory filters = new IAEStackInventory(this, FILTER_SLOTS, StorageName.CONFIG);
    public final UpgradeInventory upgrades = new UpgradeInventory(this, 3) {

        @Override
        public int getMaxInstalled(Upgrades type) {
            return type == Upgrades.FUZZY || type == Upgrades.INVERTER || type == Upgrades.ORE_FILTER ? 1 : 0;
        }
    };
    private final ConfigManager config = new ConfigManager(this);
    private final Map<Resource, IAEStack<?>> decoded = new LinkedHashMap<Resource, IAEStack<?>>(256, .75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<Resource, IAEStack<?>> e) {
            return size() > 256;
        }
    };
    private IAEStackList partition;
    private OreFilteredList orePartition;
    private String oreFilter = "";
    private int idleDelay = 1;
    private long scannedWithoutProgress;
    private long nextTick;
    private boolean preciseCompiled;
    private List<Resource> precise;
    private int preciseCursor;
    public int lastScanned;
    public long lastMoved;
    public long lastNanos;
    public long exportCalls;
    public long totalBatches;
    public long totalMoved;

    public TileOutput() {
        config.registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
    }

    @Override
    protected AENetworkProxy createProxy() {
        AENetworkProxy proxy = new AENetworkProxy(this, "proxy", new ItemStack(VaultMod.OUTPUT), true);
        proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
        proxy.setIdlePowerUsage(2);
        proxy.setValidSides(EnumSet.allOf(ForgeDirection.class));
        return proxy;
    }

    @Override
    public List<IMEInventoryHandler> getCellArray(IAEStackType<?> type) {
        return Collections.emptyList();
    }

    @Override
    public void notifyImport(Map<Resource, Long> imported) {
        // Imported stock is private until the export is actually accepted by the destination network.
        nextTick = 0;
    }

    @Override
    public void bind(TileController owner) {
        if (bound == owner) return;
        super.bind(owner);
        nextTick = 0;
    }

    @TileEvent(TileEventType.TICK)
    public void exportTick() {
        if (worldObj == null || worldObj.isRemote || worldObj.getTotalWorldTime() < nextTick) return;
        long tick = worldObj.getTotalWorldTime();
        exportCalls++;
        lastScanned = 0;
        lastMoved = 0;
        long start = System.nanoTime();
        try {
            if (!available()) {
                nextTick = tick + 20;
                return;
            }
            VaultStore store = bound.store();
            List<Resource> exact = preciseCandidates();
            int candidates = exact == null ? store.types() : exact.size();
            int limit = Math.min(TYPES_PER_TICK, candidates);
            int eligible = 0;
            for (int index = 0; index < limit; index++) {
                if (lastScanned > 0 && System.nanoTime() - start >= SOFT_BUDGET_NANOS) break;
                Resource resource;
                if (exact != null) {
                    resource = exact.get(preciseCursor++ % exact.size());
                    if (preciseCursor >= exact.size()) preciseCursor = 0;
                } else {
                    List<Resource> batch = store.nextOutputBatch(1);
                    if (batch.isEmpty()) break;
                    resource = batch.get(0);
                }
                lastScanned++;
                if (store.amount(resource) <= 0) continue;
                IAEStack<?> stack = decoded.get(resource);
                if (stack == null) {
                    stack = ResourceCodec.decode(resource);
                    decoded.put(resource, stack);
                }
                if (!matches(stack)) continue;
                eligible++;
                lastMoved += exportOne(resource, stack, store);
            }
            if (lastMoved > 0) {
                idleDelay = 1;
                scannedWithoutProgress = 0;
            } else {
                scannedWithoutProgress += lastScanned;
                if (eligible > 0 || scannedWithoutProgress >= candidates) {
                    scannedWithoutProgress = 0;
                    idleDelay = Math.min(20, idleDelay * 2);
                } else {
                    nextTick = tick + 1;
                    return;
                }
            }
            nextTick = tick + idleDelay;
        } catch (GridAccessException ignored) {
            nextTick = tick + 20;
        } catch (IOException | RuntimeException error) {
            if (bound != null) bound.ioFailure(error);
            nextTick = tick + 20;
        } finally {
            lastNanos = System.nanoTime() - start;
        }
    }

    /** Precise whitelists address at most 36 ledger keys, irrespective of total stored types. */
    private List<Resource> preciseCandidates() throws IOException {
        if (preciseCompiled) return precise;
        preciseCompiled = true;
        precise = null;
        if (getInstalledUpgrades(Upgrades.FUZZY) > 0 || getInstalledUpgrades(Upgrades.INVERTER) > 0
            || getInstalledUpgrades(Upgrades.ORE_FILTER) > 0 && !oreFilter.trim()
                .isEmpty())
            return null;
        List<Resource> result = new ArrayList<>();
        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            IAEStack<?> filter = filters.getAEStackInSlot(slot);
            if (filter == null) continue;
            Resource resource = ResourceCodec.encode(filter);
            if (!result.contains(resource)) result.add(resource);
            decoded.put(resource, ResourceCodec.decode(resource));
        }
        precise = result.isEmpty() ? null : result;
        return precise;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private long exportOne(Resource resource, IAEStack<?> template, VaultStore store)
        throws IOException, GridAccessException {
        IMEInventory target = getProxy().getStorage()
            .getMEMonitor(template.getStackType());
        MachineSource source = new MachineSource(this);
        long requested = Math.min(store.amount(resource), BATCH_AMOUNT);
        if (requested <= 0) return 0;
        IAEStack request = template.copy()
            .setStackSize(requested);
        IAEStack simulation = Platform
            .poweredInsert(getProxy().getEnergy(), target, request, source, Actionable.SIMULATE);
        long accepted = requested - (simulation == null ? 0 : simulation.getStackSize());
        if (accepted <= 0 || accepted > requested) return 0;
        // Reserve before invoking the destination; an unavailable destination leaves the ledger unchanged.
        long reserved = store.extract(resource, accepted, false);
        IAEStack remainder = Platform.poweredInsert(
            getProxy().getEnergy(),
            target,
            template.copy()
                .setStackSize(reserved),
            source);
        long refund = remainder == null ? 0 : remainder.getStackSize();
        if (refund < 0 || refund > reserved) throw new IOException("Invalid AE export remainder");
        if (refund > 0 && store.insert(resource, refund, false) != refund)
            throw new IOException("Cannot return AE export remainder");
        long moved = reserved - refund;
        if (moved > 0) {
            totalBatches++;
            totalMoved += Math.min(moved, Long.MAX_VALUE - totalMoved);
        }
        return moved;
    }

    public boolean matches(IAEStack<?> stack) {
        if (partition == null) {
            partition = new IAEStackList();
            for (int slot = 0; slot < FILTER_SLOTS; slot++) {
                IAEStack<?> filter = filters.getAEStackInSlot(slot);
                if (filter != null) partition.add(
                    filter.copy()
                        .setStackSize(1));
            }
            orePartition = new OreFilteredList(oreFilter);
        }
        boolean ore = getInstalledUpgrades(Upgrades.ORE_FILTER) > 0;
        boolean any = !partition.isEmpty() || ore && !oreFilter.trim()
            .isEmpty();
        if (!any) return true;
        boolean listed;
        if (ore && !oreFilter.trim()
            .isEmpty()) listed = stack instanceof IAEItemStack && orePartition.isListed((IAEItemStack) stack);
        else if (getInstalledUpgrades(Upgrades.FUZZY) > 0) listed = !partition.findFuzzy(stack, fuzzyMode())
            .isEmpty();
        else listed = partition.findPrecise(stack) != null;
        return getInstalledUpgrades(Upgrades.INVERTER) > 0 ? !listed : listed;
    }

    public FuzzyMode fuzzyMode() {
        return (FuzzyMode) config.getSetting(Settings.FUZZY_MODE);
    }

    @Override
    public IConfigManager getConfigManager() {
        return config;
    }

    @Override
    public IInventory getInventoryByName(String name) {
        return "upgrades".equals(name) ? upgrades : null;
    }

    @Override
    public int getInstalledUpgrades(Upgrades type) {
        return upgrades.getInstalledUpgrades(type);
    }

    @Override
    public TileOutput getTile() {
        return this;
    }

    @Override
    public String getFilter() {
        return oreFilter;
    }

    @Override
    public void setFilter(String value) {
        oreFilter = value == null ? "" : value.substring(0, Math.min(256, value.length()));
        changed();
    }

    @Override
    public void updateSetting(IConfigManager manager, Enum setting, Enum value) {
        changed();
    }

    @Override
    public void saveChanges() {
        changed();
    }

    @Override
    public void saveAEStackInv() {
        changed();
    }

    @Override
    public IAEStackInventory getAEInventoryByName(StorageName name) {
        return name == StorageName.CONFIG ? filters : null;
    }

    @Override
    public void onChangeInventory(IInventory inv, int slot, InvOperation operation, ItemStack removed,
        ItemStack added) {
        changed();
    }

    private void changed() {
        partition = null;
        preciseCompiled = false;
        preciseCursor = 0;
        nextTick = 0;
        idleDelay = 1;
        scannedWithoutProgress = 0;
        markDirty();
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readOutput(NBTTagCompound tag) {
        filters.readFromNBT(tag, "OutputFilters");
        upgrades.readFromNBT(tag, "OutputUpgrades");
        config.readFromNBT(tag.getCompoundTag("OutputSettings"));
        oreFilter = tag.getString("OutputOreFilter");
        changed();
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeOutput(NBTTagCompound tag) {
        filters.writeToNBT(tag, "OutputFilters");
        upgrades.writeToNBT(tag, "OutputUpgrades");
        NBTTagCompound settings = new NBTTagCompound();
        config.writeToNBT(settings);
        tag.setTag("OutputSettings", settings);
        tag.setString("OutputOreFilter", oreFilter);
    }
}

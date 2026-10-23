package dev.aestoragevault.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.aestoragevault.VaultMod;
import dev.aestoragevault.core.VaultStore;
import dev.aestoragevault.world.TileController;

public final class Repositories {

    public static final Repositories INSTANCE = new Repositories();
    private final Map<Path, VaultStore> stores = new HashMap<>();
    private final Map<Path, TileController> owners = new HashMap<>();
    private long ticks;

    private Repositories() {}

    private Path folder(World world) {
        World root = DimensionManager.getWorld(0);
        if (root == null) root = world;
        return root.getSaveHandler()
            .getWorldDirectory()
            .toPath()
            .resolve("data/aestoragevault");
    }

    public synchronized VaultStore open(TileController owner, UUID id, boolean create) throws IOException {
        Path path = folder(owner.getWorldObj()).resolve(id.toString());
        TileController prior = owners.get(path);
        if (prior != null && prior != owner
            && !prior.isInvalid()
            && prior.getWorldObj()
                .blockExists(prior.xCoord, prior.yCoord, prior.zCoord)
            && prior.getWorldObj()
                .getTileEntity(prior.xCoord, prior.yCoord, prior.zCoord) == prior)
            throw new IOException("This inventory UUID is already mounted by another controller");
        VaultStore store = stores.get(path);
        if (store == null) {
            store = new VaultStore(path.getParent(), id, create);
            // Missing registry entries must lock the whole inventory, never silently disappear from AE.
            try {
                for (dev.aestoragevault.core.Resource r : store.entries()
                    .keySet()) dev.aestoragevault.ae.ResourceCodec.decode(r);
                if (store.capacity()
                    .compareTo(owner.capacity) < 0)
                    throw new IOException("External capacity is older than controller data");
            } catch (IOException | RuntimeException e) {
                store.close();
                throw e;
            }
            stores.put(path, store);
        }
        owners.put(path, owner);
        return store;
    }

    public synchronized void release(TileController owner) {
        java.util.Iterator<Map.Entry<Path, TileController>> iterator = owners.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<Path, TileController> entry = iterator.next();
            if (entry.getValue() != owner) continue;
            VaultStore store = stores.remove(entry.getKey());
            iterator.remove();
            if (store != null) try {
                try {
                    store.checkpoint();
                } finally {
                    store.close();
                }
            } catch (IOException e) {
                VaultMod.LOG.error("Could not close unloaded inventory", e);
            }
        }
    }

    @SubscribeEvent
    public synchronized void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        for (VaultStore store : stores.values()) try {
            if (!store.isHealthy()) continue;
            store.flush();
            if (ticks % 1200 == 0 && store.journalBytesSinceCheckpoint() > 8 * 1024 * 1024) store.checkpoint();
        } catch (IOException e) {
            for (TileController owner : owners.values()) if (owner.store() == store) owner.ioFailure(e);
            VaultMod.LOG.error("External inventory write failure", e);
        }
    }

    @SubscribeEvent
    public synchronized void save(WorldEvent.Save event) {
        if (!event.world.isRemote) for (VaultStore store : stores.values()) try {
            store.flush();
        } catch (IOException e) {
            VaultMod.LOG.error("External inventory flush failure", e);
        }
    }

    public synchronized void closeAll() {
        for (VaultStore store : stores.values()) try {
            try {
                store.checkpoint();
            } finally {
                store.close();
            }
        } catch (IOException e) {
            VaultMod.LOG.error("External inventory close failure", e);
        }
        stores.clear();
        owners.clear();
        ticks = 0;
    }
}

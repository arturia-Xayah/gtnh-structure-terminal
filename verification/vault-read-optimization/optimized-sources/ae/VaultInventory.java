package dev.aestoragevault.ae;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import dev.aestoragevault.core.NativeInventoryView;
import dev.aestoragevault.core.Resource;
import dev.aestoragevault.core.VaultStore;
import dev.aestoragevault.world.TileAccess;

public final class VaultInventory<T extends IAEStack<T>> implements IMEInventoryHandler<T> {

    private final NativeResourceCache resourceCache = new NativeResourceCache();
    private VaultStore cachedStore;
    private NativeInventoryView.Snapshot prototypeTypes;
    private Map<Resource, CachedPrototype<T>> prototypes = new HashMap<>();
    private Object[] orderedPrototypes = new Object[0];
    private final TileAccess access;
    private final IAEStackType<T> type;
    private final String channel;

    public VaultInventory(TileAccess access, IAEStackType<T> type, String channel) {
        this.access = access;
        this.type = type;
        this.channel = channel;
    }

    @Override
    public T injectItems(T input, Actionable mode, BaseActionSource source) {
        if (input == null) return null;
        if (!access.available()) return input;
        try {
            long accepted = access.bound.store()
                .insert(resourceCache.encode(input), input.getStackSize(), mode == Actionable.SIMULATE);
            if (accepted == input.getStackSize()) return null;
            return input.copy()
                .setStackSize(input.getStackSize() - accepted);
        } catch (IOException | RuntimeException e) {
            access.bound.ioFailure(e);
            return input;
        }
    }

    @Override
    public T extractItems(T request, Actionable mode, BaseActionSource source) {
        if (request == null || !access.available()) return null;
        try {
            long taken = access.bound.store()
                .extract(resourceCache.encode(request), request.getStackSize(), mode == Actionable.SIMULATE);
            return taken == 0 ? null
                : request.copy()
                    .setStackSize(taken);
        } catch (IOException | RuntimeException e) {
            access.bound.ioFailure(e);
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized IItemList<T> getAvailableItems(IItemList<T> out, int iteration) {
        if (!access.available()) return out;
        try {
            NativeInventoryView.Snapshot snapshot = store().nativeSnapshot(channel);
            if (!snapshot.sameTypes(prototypeTypes)) {
                Map<Resource, CachedPrototype<T>> current = new HashMap<>();
                Object[] ordered = new Object[snapshot.size()];
                for (int i = 0; i < snapshot.size(); i++) {
                    Resource resource = snapshot.resource(i);
                    CachedPrototype<T> prototype = prototypes.get(resource);
                    if (prototype == null) prototype = new CachedPrototype<>(resource);
                    current.put(resource, prototype);
                    ordered[i] = prototype;
                }
                prototypes = current;
                orderedPrototypes = ordered;
            }
            prototypeTypes = snapshot;
            for (int i = 0; i < snapshot.size(); i++) {
                CachedPrototype<T> prototype = (CachedPrototype<T>) orderedPrototypes[i];
                out.addStorage(
                    prototype.copy()
                        .setStackSize(snapshot.amount(i)));
            }
        } catch (IOException | RuntimeException e) {
            access.bound.ioFailure(e);
        }
        return out;
    }

    private static final class CachedPrototype<S extends IAEStack<S>> {

        private final Resource resource;
        private S stack;
        private Object sharedTag;

        CachedPrototype(Resource resource) throws IOException {
            this.resource = resource;
            restore();
        }

        @SuppressWarnings("unchecked")
        private void restore() throws IOException {
            stack = (S) ResourceCodec.decode(resource);
            sharedTag = tag();
        }

        private Object tag() {
            return stack instanceof IAEItemStack ? ((IAEItemStack) stack).getTagCompound()
                : ((IAEFluidStack) stack).getTagCompound();
        }

        S copy() throws IOException {
            // AEItemStack.copy() shares its definition. Repair if a consumer used setTagCompound on a copy.
            if (tag() != sharedTag) restore();
            return stack.copy();
        }
    }

    /** Drop derived caches on unbind; they are never part of persistent stock or capacity. */
    public synchronized void clearCaches() {
        resourceCache.clear();
        cachedStore = null;
        prototypeTypes = null;
        prototypes.clear();
        orderedPrototypes = new Object[0];
    }

    private synchronized VaultStore store() {
        VaultStore current = access.bound.store();
        if (cachedStore != current) {
            clearCaches();
            cachedStore = current;
        }
        return current;
    }

    @Override
    public T getAvailableItem(T request, int iteration) {
        if (request == null || !access.available()) return null;
        try {
            long count = access.bound.store()
                .amount(resourceCache.encode(request));
            return count == 0 ? null
                : request.copy()
                    .setStackSize(count);
        } catch (IOException | RuntimeException e) {
            access.bound.ioFailure(e);
            return null;
        }
    }

    @Override
    public IAEStackType<T> getStackType() {
        return type;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(T input) {
        return false;
    }

    @Override
    public boolean canAccept(T input) {
        if (input == null || input.getStackSize() <= 0 || !access.available()) return false;
        try {
            return access.bound.store()
                .insert(resourceCache.encode(input), 1, true) > 0;
        } catch (IOException | RuntimeException e) {
            access.bound.ioFailure(e);
            return false;
        }
    }

    @Override
    public int getPriority() {
        return access.getPriority();
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int pass) {
        return pass == 1 || pass == 2;
    }
}

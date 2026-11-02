package dev.aestoragevault.ae;

import java.io.IOException;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import dev.aestoragevault.config.VaultCacheConfig;
import dev.aestoragevault.core.CachePool;
import dev.aestoragevault.core.NativeInventoryView;
import dev.aestoragevault.core.Resource;
import dev.aestoragevault.core.VaultStore;
import dev.aestoragevault.world.TileAccess;
import dev.aestoragevault.world.TileController;

public final class VaultInventory<T extends IAEStack<T>> implements IMEInventoryHandler<T> {

    private final NativeResourceCache resourceCache = new NativeResourceCache();
    private VaultStore cachedStore;
    private NativeInventoryView.Snapshot prototypeTypes;
    private final CachePool.Cache<Resource, CachedPrototype<T>> prototypes = VaultCacheConfig.prototypeCache();
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
        TileController owner = access.bound;
        if (owner == null || !access.available() || access.bound != owner) return input;
        try {
            long accepted = owner.store()
                .insert(resourceCache.encode(input), input.getStackSize(), mode == Actionable.SIMULATE);
            if (accepted == input.getStackSize()) return null;
            return input.copy()
                .setStackSize(input.getStackSize() - accepted);
        } catch (IOException | RuntimeException e) {
            owner.ioFailure(e);
            return input;
        }
    }

    @Override
    public T extractItems(T request, Actionable mode, BaseActionSource source) {
        TileController owner = access.bound;
        if (request == null || owner == null || !access.available() || access.bound != owner) return null;
        try {
            long taken = owner.store()
                .extract(resourceCache.encode(request), request.getStackSize(), mode == Actionable.SIMULATE);
            return taken == 0 ? null
                : request.copy()
                    .setStackSize(taken);
        } catch (IOException | RuntimeException e) {
            owner.ioFailure(e);
            return null;
        }
    }

    @Override
    public IItemList<T> getAvailableItems(IItemList<T> out, int iteration) {
        TileController owner = access.bound;
        if (owner == null || !access.available() || access.bound != owner) return out;
        VaultStore current = owner.store();
        if (current == null) return out;
        try {
            NativeInventoryView.Snapshot snapshot = current.nativeSnapshot(channel);
            preparePrototypes(current, snapshot);
            for (int i = 0; i < snapshot.size(); i++) {
                // Only a single local result is held outside the bounded cache; no all-types prototype array.
                T stack = readStack(snapshot.resource(i));
                out.addStorage(stack.setStackSize(snapshot.amount(i)));
            }
        } catch (IOException | RuntimeException e) {
            owner.ioFailure(e);
        }
        return out;
    }

    private synchronized void preparePrototypes(VaultStore current, NativeInventoryView.Snapshot snapshot) {
        if (cachedStore != current) {
            prototypes.clear();
            prototypeTypes = null;
            cachedStore = current;
        }
        if (!snapshot.sameTypes(prototypeTypes)) {
            // Work is bounded by the configured cache size, not by every stored resource type.
            for (Resource resource : prototypes.keys()) if (current.exactAmount(resource)
                .signum() == 0) prototypes.remove(resource);
        }
        prototypeTypes = snapshot;
    }

    @SuppressWarnings("unchecked")
    private T readStack(Resource resource) throws IOException {
        CachedPrototype<T> prototype = prototypes.get(resource);
        if (prototype != null) return prototype.copy();
        T decoded = (T) ResourceCodec.decode(resource);
        prototype = new CachedPrototype<>(resource, decoded);
        // Never evict the whole warm set during a scan larger than the configured budget.
        return prototypes.putIfRoom(resource, prototype, resource.encodedBytes() * 2 + 512) ? prototype.copy()
            : decoded;
    }

    private static final class CachedPrototype<S extends IAEStack<S>> {

        private final Resource resource;
        private S stack;
        private Object sharedTag;

        CachedPrototype(Resource resource, S decoded) {
            this.resource = resource;
            stack = decoded;
            sharedTag = tag();
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
    }

    @Override
    public T getAvailableItem(T request, int iteration) {
        TileController owner = access.bound;
        if (request == null || owner == null || !access.available() || access.bound != owner) return null;
        try {
            long count = owner.store()
                .amount(resourceCache.encode(request));
            return count == 0 ? null
                : request.copy()
                    .setStackSize(count);
        } catch (IOException | RuntimeException e) {
            owner.ioFailure(e);
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
        TileController owner = access.bound;
        if (input == null || input.getStackSize() <= 0 || owner == null || !access.available() || access.bound != owner)
            return false;
        try {
            return owner.store()
                .insert(resourceCache.encode(input), 1, true) > 0;
        } catch (IOException | RuntimeException e) {
            owner.ioFailure(e);
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

package dev.aestoragevault.ae;

import java.io.IOException;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import dev.aestoragevault.config.VaultCacheConfig;
import dev.aestoragevault.core.CachePool;
import dev.aestoragevault.core.Resource;

/** Per-adapter bounded cache. Native shared NBT identity is immutable; mutable stack counts are never keys. */
public final class NativeResourceCache {

    /** Default retained for test/benchmark working-set descriptions; runtime limits come from configuration. */
    public static final int LIMIT = VaultCacheConfig.DEFAULT_KEY_ENTRIES;
    private final CachePool.Cache<Key, Resource> encoded = VaultCacheConfig.keyCache();

    private final Key probe = new Key();

    public synchronized Resource encode(IAEStack<?> stack) throws IOException {
        // Only these pinned native implementations guarantee the shared-NBT identity used below.
        if (!(stack instanceof AEItemStack) && !(stack instanceof AEFluidStack)) return ResourceCodec.encode(stack);
        probe.bind(stack);
        try {
            Resource cached = encoded.get(probe);
            if (cached != null) return cached;
            Resource resource = ResourceCodec.encode(stack);
            encoded.put(new Key(stack), resource, resource.encodedBytes() * 2 + 128);
            return resource;
        } finally {
            // Even disabled/rejected caches must not retain a huge NBT through the reusable lookup probe.
            probe.kind = null;
            probe.tag = null;
        }
    }

    public synchronized void clear() {
        encoded.clear();
        probe.kind = null;
        probe.tag = null;
    }

    public synchronized long accountedBytes() {
        return encoded.bytes();
    }

    public synchronized int size() {
        return encoded.size();
    }

    private static final class Key {

        private Object kind, tag;
        private int damage;
        private boolean fluid;
        private int hash;

        Key() {}

        Key(IAEStack<?> stack) {
            bind(stack);
        }

        // Only the lookup probe is rebound; stored keys are private snapshots and never change.
        private void bind(IAEStack<?> stack) {
            fluid = stack instanceof AEFluidStack;
            if (fluid) {
                IAEFluidStack value = (IAEFluidStack) stack;
                kind = value.getFluid();
                tag = value.getTagCompound();
                damage = 0;
            } else {
                IAEItemStack value = (IAEItemStack) stack;
                kind = value.getItem();
                tag = value.getTagCompound();
                damage = value.getItemDamage();
            }
            hash = 31 * (31 * System.identityHashCode(kind) + damage) + System.identityHashCode(tag);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return fluid == key.fluid && kind == key.kind && damage == key.damage && tag == key.tag;
        }
    }
}

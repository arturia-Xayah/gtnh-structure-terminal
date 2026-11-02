package dev.aestoragevault.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Derived, non-persistent index. All mutation is guarded by the owning VaultStore monitor. */
public final class NativeInventoryView {

    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private final Map<Resource, Entry> entries = new HashMap<>();
    private final List<Entry> order = new ArrayList<>();
    private Resource[] types;
    private Snapshot cached;

    void update(Resource resource, BigInteger exact) {
        long visible = exact.min(MAX_LONG)
            .longValue();
        Entry entry = entries.get(resource);
        if (entry == null) {
            if (visible == 0) return;
            entry = new Entry(resource, visible, order.size());
            entries.put(resource, entry);
            order.add(entry);
            types = null;
        } else if (visible == 0) {
            entries.remove(resource);
            Entry last = order.remove(order.size() - 1);
            if (last != entry) {
                order.set(entry.slot, last);
                last.slot = entry.slot;
            }
            types = null;
        } else {
            if (entry.amount == visible) return;
            entry.amount = visible;
        }
        cached = null;
    }

    Snapshot snapshot() {
        if (cached != null) return cached;
        if (types == null) {
            types = new Resource[order.size()];
            for (int i = 0; i < types.length; i++) types[i] = order.get(i).resource;
        }
        long[] amounts = new long[order.size()];
        for (int i = 0; i < amounts.length; i++) amounts[i] = order.get(i).amount;
        return cached = new Snapshot(types, amounts);
    }

    private static final class Entry {

        final Resource resource;
        long amount;
        int slot;

        Entry(Resource resource, long amount, int slot) {
            this.resource = resource;
            this.amount = amount;
            this.slot = slot;
        }
    }

    /** Immutable snapshot. No mutable arrays, map entries or cached AE stacks escape to callers. */
    public static final class Snapshot {

        private final Resource[] types;
        private final long[] amounts;

        private Snapshot(Resource[] types, long[] amounts) {
            this.types = types;
            this.amounts = amounts;
        }

        public int size() {
            return types.length;
        }

        public Resource resource(int index) {
            return types[index];
        }

        public long amount(int index) {
            return amounts[index];
        }

        public boolean sameTypes(Snapshot other) {
            return other != null && types == other.types;
        }
    }
}

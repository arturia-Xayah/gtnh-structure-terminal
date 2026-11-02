package dev.aestoragevault.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

/** Weighted, shared LRU bookkeeping. No decoding, world access or caller callbacks under this monitor. */
public final class CachePool {

    private final long maxBytes;
    private final LinkedHashMap<Entry<?, ?>, Boolean> global = new LinkedHashMap<>(16, .75f, true);
    private long bytes;

    public CachePool(long maxBytes) {
        if (maxBytes < 0) throw new IllegalArgumentException("Negative cache budget");
        this.maxBytes = maxBytes;
    }

    public <K, V> Cache<K, V> create(int maxEntries, long localBytes, long maxEntryBytes) {
        if (maxEntries < 0 || localBytes < 0 || maxEntryBytes < 0)
            throw new IllegalArgumentException("Negative cache limit");
        return new Cache<>(this, maxEntries, localBytes, maxEntryBytes);
    }

    public synchronized long bytes() {
        return bytes;
    }

    public synchronized int size() {
        return global.size();
    }

    public synchronized void clear() {
        while (!global.isEmpty()) evict(
            global.keySet()
                .iterator()
                .next());
    }

    private void evict(Entry<?, ?> entry) {
        entry.owner.entries.remove(entry.key);
        entry.owner.bytes -= entry.weight;
        global.remove(entry);
        bytes -= entry.weight;
    }

    private static final class Entry<K, V> {

        final Cache<K, V> owner;
        final K key;
        final V value;
        final long weight;

        Entry(Cache<K, V> owner, K key, V value, long weight) {
            this.owner = owner;
            this.key = key;
            this.value = value;
            this.weight = weight;
        }
    }

    public static final class Cache<K, V> {

        private final CachePool pool;
        private final int maxEntries;
        private final long maxBytes, maxEntryBytes;
        private final LinkedHashMap<K, Entry<K, V>> entries = new LinkedHashMap<>(16, .75f, true);
        private long bytes;

        private Cache(CachePool pool, int maxEntries, long maxBytes, long maxEntryBytes) {
            this.pool = pool;
            this.maxEntries = maxEntries;
            this.maxBytes = maxBytes;
            this.maxEntryBytes = maxEntryBytes;
        }

        public V get(K key) {
            synchronized (pool) {
                Entry<K, V> entry = entries.get(key);
                if (entry == null) return null;
                pool.global.get(entry);
                return entry.value;
            }
        }

        /** Key lookups use normal LRU eviction; full scans use putIfRoom to avoid evicting the whole hot set. */
        public boolean put(K key, V value, long weight) {
            return put(key, value, weight, true);
        }

        public boolean putIfRoom(K key, V value, long weight) {
            return put(key, value, weight, false);
        }

        private boolean put(K key, V value, long weight, boolean evict) {
            if (key == null || value == null || weight <= 0) throw new IllegalArgumentException("Invalid cache entry");
            synchronized (pool) {
                if (maxEntries == 0 || weight > maxEntryBytes || weight > maxBytes || weight > pool.maxBytes)
                    return false;
                Entry<K, V> old = entries.get(key);
                if (!evict) {
                    long oldWeight = old == null ? 0 : old.weight;
                    if ((old == null && entries.size() >= maxEntries) || weight > maxBytes - (bytes - oldWeight)
                        || weight > pool.maxBytes - (pool.bytes - oldWeight)) return false;
                }
                if (old != null) pool.evict(old);
                while (entries.size() >= maxEntries || weight > maxBytes - bytes) pool.evict(
                    entries.values()
                        .iterator()
                        .next());
                while (weight > pool.maxBytes - pool.bytes) pool.evict(
                    pool.global.keySet()
                        .iterator()
                        .next());
                Entry<K, V> entry = new Entry<>(this, key, value, weight);
                entries.put(key, entry);
                pool.global.put(entry, Boolean.TRUE);
                bytes += weight;
                pool.bytes += weight;
                return true;
            }
        }

        public void remove(K key) {
            synchronized (pool) {
                Entry<K, V> entry = entries.get(key);
                if (entry != null) pool.evict(entry);
            }
        }

        public List<K> keys() {
            synchronized (pool) {
                return new ArrayList<>(entries.keySet());
            }
        }

        public void clear() {
            synchronized (pool) {
                Iterator<Entry<K, V>> iterator = entries.values()
                    .iterator();
                while (iterator.hasNext()) {
                    Entry<K, V> entry = iterator.next();
                    pool.global.remove(entry);
                    pool.bytes -= entry.weight;
                    iterator.remove();
                }
                bytes = 0;
            }
        }

        public long bytes() {
            synchronized (pool) {
                return bytes;
            }
        }

        public int size() {
            synchronized (pool) {
                return entries.size();
            }
        }
    }
}

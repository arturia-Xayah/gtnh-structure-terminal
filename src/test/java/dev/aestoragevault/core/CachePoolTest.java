package dev.aestoragevault.core;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public final class CachePoolTest {

    @Test
    public void countLimitUsesAccessOrderAndReplacementCreditsOldWeight() {
        CachePool pool = new CachePool(100);
        CachePool.Cache<String, String> cache = pool.create(2, 100, 100);
        assertTrue(cache.put("a", "A", 10));
        cache.put("b", "B", 20);
        assertEquals("A", cache.get("a"));
        cache.put("c", "C", 30);
        assertNull(cache.get("b"));
        assertEquals(40, pool.bytes());
        cache.put("a", "new", 50);
        assertEquals("new", cache.get("a"));
        assertEquals(80, cache.bytes());
        assertEquals(2, cache.size());
    }

    @Test
    public void localByteBudgetEvictsBeforeOverflowAndOversizeDoesNotFlushHotEntries() {
        CachePool pool = new CachePool(1000);
        CachePool.Cache<Integer, String> cache = pool.create(10, 60, 40);
        cache.put(1, "one", 35);
        cache.put(2, "two", 35);
        assertNull(cache.get(1));
        assertEquals(35, cache.bytes());
        assertFalse(cache.put(3, "oversize", 41));
        assertEquals("two", cache.get(2));
        assertEquals(35, pool.bytes());
    }

    @Test
    public void globalBudgetEvictsAcrossCacheTypesAndHonorsGlobalAccessOrder() {
        CachePool pool = new CachePool(100);
        CachePool.Cache<Integer, String> keys = pool.create(10, 100, 100);
        CachePool.Cache<Integer, String> prototypes = pool.create(10, 100, 100);
        keys.put(1, "key", 40);
        prototypes.put(1, "prototype", 40);
        keys.get(1);
        keys.put(2, "new", 40);
        assertNull(prototypes.get(1));
        assertEquals("key", keys.get(1));
        assertEquals(80, pool.bytes());
        assertEquals(0, prototypes.bytes());
    }

    @Test
    public void fullScanAdmissionNeverChurnsTheWarmSet() {
        CachePool pool = new CachePool(20);
        CachePool.Cache<Integer, Integer> cache = pool.create(2, 20, 20);
        cache.putIfRoom(0, 0, 10);
        cache.putIfRoom(1, 1, 10);
        for (int i = 2; i < 10000; i++) assertFalse(cache.putIfRoom(i, i, 10));
        assertEquals(Integer.valueOf(0), cache.get(0));
        assertEquals(Integer.valueOf(1), cache.get(1));
        assertTrue(cache.putIfRoom(0, 5, 10));
        assertEquals(Integer.valueOf(5), cache.get(0));
        assertEquals(20, pool.bytes());
    }

    @Test
    public void eachZeroSettingDisablesRetentionAndRejectsNegativeLimits() {
        CachePool active = new CachePool(100);
        assertFalse(
            active.create(0, 100, 100)
                .put("a", "b", 1));
        assertFalse(
            active.create(100, 0, 100)
                .put("a", "b", 1));
        assertFalse(
            active.create(100, 100, 0)
                .put("a", "b", 1));
        assertFalse(
            new CachePool(0).create(100, 100, 100)
                .put("a", "b", 1));
        assertEquals(0, active.bytes());
        try {
            new CachePool(-1);
            fail();
        } catch (IllegalArgumentException expected) {}
        try {
            active.create(-1, 1, 1);
            fail();
        } catch (IllegalArgumentException expected) {}
    }

    @Test
    public void longBudgetArithmeticCannotWrap() {
        CachePool pool = new CachePool(Long.MAX_VALUE);
        CachePool.Cache<Integer, Integer> cache = pool.create(4, Long.MAX_VALUE, Long.MAX_VALUE);
        cache.put(1, 1, Long.MAX_VALUE - 1);
        cache.put(2, 2, 2);
        assertNull(cache.get(1));
        assertEquals(2, pool.bytes());
        cache.put(3, 3, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, pool.bytes());
        assertNull(cache.get(2));
        cache.clear();
        assertEquals(0, pool.bytes());
    }

    @Test
    public void randomMixedCachesKeepExactAccountingThroughRemoveAndClear() {
        CachePool pool = new CachePool(1200);
        List<CachePool.Cache<Integer, Integer>> caches = new ArrayList<>();
        for (int i = 0; i < 5; i++) caches.add(pool.create(12, 400, 300));
        Random random = new Random(713);
        for (int i = 0; i < 15000; i++) {
            CachePool.Cache<Integer, Integer> cache = caches.get(random.nextInt(caches.size()));
            int key = random.nextInt(40);
            switch (random.nextInt(6)) {
                case 0:
                    cache.remove(key);
                    break;
                case 1:
                    cache.get(key);
                    break;
                case 2:
                    cache.clear();
                    break;
                case 3:
                    pool.clear();
                    break;
                case 4:
                    cache.putIfRoom(key, key, 1 + random.nextInt(500));
                    break;
                default:
                    cache.put(key, key, 1 + random.nextInt(500));
            }
            long total = 0;
            for (CachePool.Cache<Integer, Integer> member : caches) {
                assertTrue(member.size() <= 12 && member.bytes() <= 400 && member.bytes() >= 0);
                total += member.bytes();
            }
            assertEquals(total, pool.bytes());
            assertTrue(total <= 1200);
        }
    }

    @Test(timeout = 15000)
    public void concurrentBookkeepingHasNoCrossCacheLockInversion() throws Exception {
        CachePool pool = new CachePool(4096);
        List<Thread> threads = new ArrayList<>();
        List<CachePool.Cache<Integer, Integer>> caches = new ArrayList<>();
        for (int i = 0; i < 4; i++) caches.add(pool.create(64, 2048, 256));
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        for (int n = 0; n < 4; n++) {
            final int index = n;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    Random random = new Random(index);
                    for (int i = 0; i < 6000; i++) {
                        CachePool.Cache<Integer, Integer> cache = caches.get(random.nextInt(4));
                        int key = random.nextInt(90);
                        cache.put(key, key, 1 + random.nextInt(256));
                        cache.get(key);
                        if (i % 37 == 0) cache.clear();
                    }
                } catch (Throwable failure) {
                    error.set(failure);
                }
            });
            threads.add(thread);
            thread.start();
        }
        start.countDown();
        for (Thread thread : threads) thread.join();
        assertNull(error.get());
        long total = 0;
        for (CachePool.Cache<Integer, Integer> cache : caches) total += cache.bytes();
        assertEquals(total, pool.bytes());
        assertTrue(total <= 4096);
        pool.clear();
        assertEquals(0, pool.bytes());
        for (CachePool.Cache<Integer, Integer> cache : caches) assertEquals(0, cache.size());
    }

    @Test
    public void resourceByteWeightDoesNotExposeOrCopyMutableArrays() {
        Resource resource = new Resource("items", 8, new byte[15], new byte[25]);
        assertEquals(40, resource.encodedBytes());
        resource.payload()[0] = 9;
        assertEquals(0, resource.payload()[0]);
    }
}

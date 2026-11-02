package dev.aestoragevault.core;

import static org.junit.Assert.*;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.junit.Test;

public final class NativeInventoryViewTest {

    private Resource resource(String channel, int id) {
        return new Resource(
            channel,
            channel.equals("items") ? 8 : 2048,
            new byte[] { (byte) id, (byte) (id >> 8) },
            new byte[] { 1 });
    }

    @Test
    public void snapshotsAreStableAndChannelsInvalidateIndependently() throws Exception {
        try (VaultStore store = new VaultStore(Files.createTempDirectory("vault-view"), UUID.randomUUID(), true)) {
            store.absorb(UUID.randomUUID(), 100000, Collections.emptyMap());
            Resource item = resource("items", 1), fluid = resource("fluids", 2);
            NativeInventoryView.Snapshot empty = store.nativeSnapshot("items");
            store.insert(item, 100, false);
            NativeInventoryView.Snapshot first = store.nativeSnapshot("items");
            assertEquals(0, empty.size());
            assertEquals(100, first.amount(0));
            assertSame(first, store.nativeSnapshot("items"));
            store.insert(fluid, 30, false);
            assertSame(first, store.nativeSnapshot("items"));
            store.extract(item, 5, true);
            store.insert(item, 5, true);
            assertSame(first, store.nativeSnapshot("items"));
            store.extract(item, 25, false);
            NativeInventoryView.Snapshot changed = store.nativeSnapshot("items");
            assertTrue(changed.sameTypes(first));
            assertEquals(75, changed.amount(0));
            assertEquals(100, first.amount(0));
            store.extract(item, 1000, false);
            assertEquals(
                0,
                store.nativeSnapshot("items")
                    .size());
            assertEquals(75, changed.amount(0));
            store.insert(item, 7, false);
            assertEquals(
                7,
                store.nativeSnapshot("items")
                    .amount(0));
            assertEquals(
                30,
                store.nativeSnapshot("fluids")
                    .amount(0));
        }
    }

    @Test
    public void saturatedNativeWindowRemainsCachedUntilVisibleQuantityChanges() throws Exception {
        try (VaultStore store = new VaultStore(Files.createTempDirectory("vault-view-huge"), UUID.randomUUID(), true)) {
            Resource item = resource("items", 1);
            BigInteger huge = BigInteger.valueOf(Long.MAX_VALUE)
                .add(BigInteger.valueOf(10));
            store.absorbExact(UUID.randomUUID(), huge, Collections.singletonMap(item, huge));
            NativeInventoryView.Snapshot first = store.nativeSnapshot("items");
            store.extract(item, 5, false);
            assertSame(first, store.nativeSnapshot("items"));
            store.extract(item, 6, false);
            assertEquals(
                Long.MAX_VALUE - 1,
                store.nativeSnapshot("items")
                    .amount(0));
            assertEquals(Long.MAX_VALUE, first.amount(0));
        }
    }

    @Test
    public void randomChurnMatchesLedgerAndRestoresBothSnapshotAndJournal() throws Exception {
        Path folder = Files.createTempDirectory("vault-view-churn");
        UUID id = UUID.randomUUID();
        Map<Resource, BigInteger> expected;
        try (VaultStore store = new VaultStore(folder, id, true)) {
            store.absorb(UUID.randomUUID(), 1000000, Collections.emptyMap());
            Random random = new Random(193);
            for (int i = 0; i < 1200; i++) {
                Resource r = resource(i % 3 == 0 ? "fluids" : "items", random.nextInt(90));
                if (random.nextBoolean()) store.insert(r, 1 + random.nextInt(16), false);
                else store.extract(r, 1 + random.nextInt(64), false);
                assertView(store);
            }
            store.checkpoint();
            store.insert(resource("items", 140), 500, false);
            expected = store.entries();
        }
        try (VaultStore store = new VaultStore(folder, id, false)) {
            assertEquals(expected, store.entries());
            assertView(store);
        }
    }

    private void assertView(VaultStore store) {
        Map<Resource, BigInteger> actual = new HashMap<>();
        for (String channel : new String[] { "items", "fluids" }) {
            NativeInventoryView.Snapshot snapshot = store.nativeSnapshot(channel);
            for (int i = 0; i < snapshot.size(); i++) {
                assertEquals(channel, snapshot.resource(i).channel);
                assertNull(actual.put(snapshot.resource(i), BigInteger.valueOf(snapshot.amount(i))));
            }
        }
        assertEquals(store.entries(), actual);
    }
}

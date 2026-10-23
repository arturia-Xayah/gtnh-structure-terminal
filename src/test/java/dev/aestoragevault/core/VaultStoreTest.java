package dev.aestoragevault.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.zip.CRC32;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class VaultStoreTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private final UUID id = UUID.randomUUID();

    private Path folder() {
        return temp.getRoot()
            .toPath();
    }

    private Resource item(int n) {
        return resource("items", 8, n);
    }

    private Resource fluid(int n) {
        return resource("fluids", 2048, n);
    }

    private Resource resource(String channel, int rate, int n) {
        byte[] bytes = new byte[] { (byte) (n >>> 24), (byte) (n >>> 16), (byte) (n >>> 8), (byte) n };
        return new Resource(channel, rate, bytes, bytes);
    }

    private VaultStore create(long bytes) throws Exception {
        VaultStore s = new VaultStore(folder(), id, true);
        if (bytes > 0) s.absorb(UUID.randomUUID(), bytes, Collections.emptyMap());
        return s;
    }

    private void expectFailure(Throwing run) throws Exception {
        try {
            run.run();
            fail("Expected failure");
        } catch (IOException | IllegalArgumentException | ArithmeticException expected) {}
    }

    private interface Throwing {

        void run() throws Exception;
    }

    @Test
    public void moreThan63TypesAndNoTypeTax() throws Exception {
        try (VaultStore s = create(1250)) {
            for (int n = 0; n < 10000; n++) assertEquals(1, s.insert(item(n), 1, false));
            assertEquals(10000, s.types());
            assertEquals(BigInteger.valueOf(1250), s.usedBytesCeil());
            assertEquals(0, s.insert(item(10001), 1, false));
            s.checkpoint();
        }
        try (VaultStore s = new VaultStore(folder(), id, false)) {
            assertEquals(10000, s.types());
        }
    }

    @Test
    public void outputTraversalIsBoundedFairAndTracksRemovalsAcrossRestart() throws Exception {
        try (VaultStore s = create(1000)) {
            for (int n = 0; n < 100; n++) s.insert(item(n), 1, false);
            java.util.Set<Resource> visited = new java.util.HashSet<>();
            for (int n = 0; n < 100; n++) visited.addAll(s.nextOutputBatch(1));
            assertEquals(100, visited.size());
            assertEquals(100, s.types());
            for (int n = 0; n < 50; n++) s.extract(item(n), 1, false);
            visited.clear();
            for (int n = 0; n < 50; n++) visited.addAll(s.nextOutputBatch(1));
            assertEquals(50, visited.size());
            for (int n = 0; n < 50; n++) assertFalse(visited.contains(item(n)));
            s.checkpoint();
        }
        try (VaultStore s = new VaultStore(folder(), id, false)) {
            assertEquals(
                32,
                s.nextOutputBatch(32)
                    .size());
            assertEquals(50, s.types());
        }
    }

    @Test
    public void itemFluidShareExactFractionalBudget() throws Exception {
        try (VaultStore s = create(1)) {
            assertEquals(1, s.insert(item(1), 1, false));
            assertEquals(1792, s.insert(fluid(1), 9000, false));
            assertEquals(0, s.insert(fluid(2), 1, false));
            assertEquals(1, s.extract(item(1), 1, false));
            assertEquals(256, s.insert(fluid(2), 1000, false));
            assertEquals(BigInteger.valueOf(2048), s.usedUnits());
        }
    }

    @Test
    public void simulationNeverMutatesOrWrites() throws Exception {
        try (VaultStore s = create(1)) {
            long log = s.journalBytesSinceCheckpoint();
            assertEquals(8, s.insert(item(1), 100, true));
            assertEquals(0, s.extract(item(1), 100, true));
            assertEquals(0, s.types());
            assertEquals(log, s.journalBytesSinceCheckpoint());
            s.insert(item(1), 5, false);
            log = s.journalBytesSinceCheckpoint();
            assertEquals(5, s.extract(item(1), 100, true));
            assertEquals(5, s.amount(item(1)));
            assertEquals(log, s.journalBytesSinceCheckpoint());
        }
    }

    @Test
    public void filledCellCreditsCapacityAndItemsTogether() throws Exception {
        UUID receipt = UUID.randomUUID();
        Map<Resource, Long> data = new HashMap<>();
        data.put(item(1), 3000L);
        data.put(fluid(1), 1000000L);
        try (VaultStore s = create(0)) {
            s.absorb(receipt, 1024, data);
            assertEquals(BigInteger.valueOf(1024), s.capacity());
            assertEquals(2, s.types());
        }
        try (VaultStore s = new VaultStore(folder(), id, false)) {
            s.absorb(receipt, 1024, data);
            assertEquals(BigInteger.valueOf(1024), s.capacity());
            assertEquals(3000, s.amount(item(1)));
            assertEquals(1000000, s.amount(fluid(1)));
            assertTrue(s.hasReceipt(receipt));
        }
    }

    @Test
    public void invalidImportCannotPartiallyCredit() throws Exception {
        try (VaultStore s = create(1)) {
            UUID token = UUID.randomUUID();
            expectFailure(() -> s.absorb(token, 1, Collections.singletonMap(item(1), 17L)));
            assertEquals(BigInteger.valueOf(1), s.capacity());
            assertEquals(0, s.types());
            assertFalse(s.hasReceipt(token));
            expectFailure(() -> s.absorb(token, 1, Collections.singletonMap(item(1), -1L)));
            assertEquals(BigInteger.valueOf(1), s.capacity());
        }
    }

    @Test
    public void bigCapacityCrossesLongBoundaryWhileNativePerTypeCountsStayBounded() throws Exception {
        try (VaultStore s = create(Long.MAX_VALUE)) {
            assertEquals(Long.MAX_VALUE, s.insert(item(1), Long.MAX_VALUE, false));
            assertEquals(0, s.insert(item(1), 1, false));
            assertEquals(Long.MAX_VALUE, s.insert(item(2), Long.MAX_VALUE, false));
            BigInteger before = s.capacity();
            s.absorb(UUID.randomUUID(), 1, Collections.emptyMap());
            assertEquals(before.add(BigInteger.ONE), s.capacity());
            expectFailure(() -> s.absorb(UUID.randomUUID(), 1, Collections.singletonMap(item(1), 1L)));
            assertEquals(Long.MAX_VALUE, s.amount(item(1)));
            s.checkpoint();
        }
        try (VaultStore s = new VaultStore(folder(), id, false)) {
            assertEquals(
                BigInteger.valueOf(Long.MAX_VALUE)
                    .add(BigInteger.ONE),
                s.capacity());
            assertEquals(Long.MAX_VALUE, s.amount(item(2)));
        }
    }

    @Test
    public void extractionRemovesTypeAndPartialInsertionIsBounded() throws Exception {
        try (VaultStore s = create(10)) {
            assertEquals(80, s.insert(item(1), 100, false));
            assertEquals(80, s.extract(item(1), 100, false));
            assertEquals(0, s.types());
            assertEquals(BigInteger.valueOf(0), s.usedBytesCeil());
            assertEquals(0, s.extract(item(1), -4, false));
            assertEquals(0, s.insert(item(1), -4, false));
        }
    }

    @Test
    public void aggregateUsedBytesBeyondLongStillEnforcesExactItemFluidBudget() throws Exception {
        BigInteger bytes = BigInteger.valueOf(Long.MAX_VALUE)
            .add(BigInteger.valueOf(17));
        try (VaultStore s = create(0)) {
            s.absorb(UUID.randomUUID(), bytes, Collections.emptyMap());
            for (int n = 0; n < 8; n++) assertEquals(Long.MAX_VALUE, s.insert(item(n), Long.MAX_VALUE, false));
            assertEquals(BigInteger.valueOf(Long.MAX_VALUE), s.usedBytesCeil());
            assertEquals(136, s.insert(item(8), 1000, false));
            assertEquals(bytes, s.usedBytesCeil());
            assertEquals(0, s.insert(fluid(1), 1, false));
            assertEquals(1, s.extract(item(8), 1, false));
            assertEquals(256, s.insert(fluid(1), 257, false));
            assertEquals(bytes.multiply(BigInteger.valueOf(2048)), s.usedUnits());
        }
        try (VaultStore s = new VaultStore(folder(), id, false)) {
            assertEquals(bytes, s.capacity());
            assertEquals(bytes, s.usedBytesCeil());
            s.checkpoint();
        }
        try (VaultStore s = new VaultStore(folder(), id, false)) {
            assertEquals(bytes, s.usedBytesCeil());
            assertEquals(256, s.amount(fluid(1)));
        }
    }

    @Test
    public void hugeCapacityAndReceiptsSurviveJournalAndSnapshot() throws Exception {
        BigInteger bytes = BigInteger.ONE.shiftLeft(200);
        UUID receipt = UUID.randomUUID();
        try (VaultStore s = create(0)) {
            s.absorb(receipt, bytes, Collections.singletonMap(item(1), 7L));
            s.absorb(receipt, bytes, Collections.singletonMap(item(1), 7L));
            assertEquals(bytes, s.capacity());
        }
        try (VaultStore s = new VaultStore(folder(), id, false)) {
            assertEquals(bytes, s.capacity());
            assertEquals(7, s.amount(item(1)));
            s.checkpoint();
        }
        try (VaultStore s = new VaultStore(folder(), id, false)) {
            assertEquals(bytes, s.capacity());
            assertTrue(s.hasReceipt(receipt));
            long journal = s.journalBytesSinceCheckpoint();
            UUID invalid = UUID.randomUUID();
            expectFailure(() -> s.absorb(invalid, BigInteger.ONE.shiftLeft(32768), Collections.emptyMap()));
            assertEquals(bytes, s.capacity());
            assertFalse(s.hasReceipt(invalid));
            assertEquals(journal, s.journalBytesSinceCheckpoint());
        }
    }

    @Test
    public void legacyV2SnapshotAndWalMigrateWithoutLosingContentsOrReceipts() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream snapshot = new DataOutputStream(bytes);
        snapshot.writeInt(0x41565332);
        snapshot.writeLong(id.getMostSignificantBits());
        snapshot.writeLong(id.getLeastSignificantBits());
        snapshot.writeLong(0);
        snapshot.writeLong(64);
        snapshot.writeInt(0);
        snapshot.writeInt(1);
        legacyResource(snapshot, item(1));
        snapshot.writeLong(8);
        snapshot.writeLong(0x454e445641554c54L);
        CRC32 crc = new CRC32();
        crc.update(bytes.toByteArray());
        snapshot.writeInt((int) crc.getValue());
        Files.write(folder().resolve(id + ".snapshot"), bytes.toByteArray());
        UUID receipt = UUID.randomUUID();
        bytes.reset();
        DataOutputStream body = new DataOutputStream(bytes);
        body.writeByte(2);
        body.writeLong(receipt.getMostSignificantBits());
        body.writeLong(receipt.getLeastSignificantBits());
        body.writeLong(16);
        body.writeInt(1);
        legacyResource(body, fluid(1));
        body.writeLong(2048);
        byte[] data = bytes.toByteArray();
        bytes.reset();
        DataOutputStream wal = new DataOutputStream(bytes);
        wal.writeInt(data.length);
        wal.writeLong(1);
        wal.write(data);
        crc.reset();
        crc.update(new byte[] { 0, 0, 0, 0, 0, 0, 0, 1 });
        crc.update(data);
        wal.writeInt((int) crc.getValue());
        Files.write(folder().resolve(id + ".wal"), bytes.toByteArray());
        BigInteger upgraded = BigInteger.ONE.shiftLeft(90)
            .add(BigInteger.valueOf(80));
        try (VaultStore s = new VaultStore(folder(), id, false)) {
            assertEquals(BigInteger.valueOf(80), s.capacity());
            assertTrue(s.hasReceipt(receipt));
            s.absorb(UUID.randomUUID(), BigInteger.ONE.shiftLeft(90), Collections.emptyMap());
        }
        try (VaultStore s = new VaultStore(folder(), id, false)) {
            assertEquals(upgraded, s.capacity());
            assertEquals(8, s.amount(item(1)));
            assertEquals(2048, s.amount(fluid(1)));
            assertTrue(s.hasReceipt(receipt));
            s.checkpoint();
        }
        try (VaultStore s = new VaultStore(folder(), id, false)) {
            assertEquals(upgraded, s.capacity());
            assertEquals(2048, s.amount(fluid(1)));
        }
    }

    private void legacyResource(DataOutputStream out, Resource r) throws IOException {
        out.writeUTF(r.channel);
        out.writeInt(r.perByte);
        out.writeInt(r.key().length);
        out.write(r.key());
        out.writeInt(r.payload().length);
        out.write(r.payload());
    }

    @Test
    public void sourceDataIsImmutable() throws Exception {
        byte[] key = { 1 }, payload = { 2 };
        Resource r = new Resource("items", 8, key, payload);
        key[0] = 9;
        payload[0] = 9;
        r.key()[0] = 7;
        r.payload()[0] = 7;
        assertArrayEquals(new byte[] { 1 }, r.key());
        assertArrayEquals(new byte[] { 2 }, r.payload());
        try (VaultStore s = create(1)) {
            s.insert(r, 1, false);
            s.entries()
                .clear();
            assertEquals(1, s.types());
        }
    }

    @Test
    public void missingInventoryIsNeverRecreated() throws Exception {
        expectFailure(() -> new VaultStore(folder(), id, false));
        try (VaultStore s = create(1)) {
            s.insert(item(1), 3, false);
        }
        Files.delete(folder().resolve(id + ".snapshot"));
        expectFailure(() -> new VaultStore(folder(), id, false));
    }

    @Test
    public void missingJournalIsNeverTreatedAsEmpty() throws Exception {
        try (VaultStore s = create(1)) {
            s.checkpoint();
        }
        Files.delete(folder().resolve(id + ".wal"));
        expectFailure(() -> new VaultStore(folder(), id, false));
    }

    @Test
    public void unfinishedLastRecordIsDiscarded() throws Exception {
        try (VaultStore s = create(1)) {
            s.insert(item(1), 3, false);
        }
        Path wal = folder().resolve(id + ".wal");
        long correct = Files.size(wal);
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(wal.toFile(), true))) {
            out.writeInt(100);
            out.writeLong(3);
            out.writeByte(1);
        }
        try (VaultStore s = new VaultStore(folder(), id, false)) {
            assertEquals(3, s.amount(item(1)));
            assertEquals(correct, Files.size(wal));
            s.insert(item(1), 1, false);
        }
        try (VaultStore s = new VaultStore(folder(), id, false)) {
            assertEquals(4, s.amount(item(1)));
        }
    }

    @Test
    public void corruptCompleteRecordLocksRatherThanDropsIt() throws Exception {
        try (VaultStore s = create(1)) {
            s.insert(item(1), 3, false);
        }
        Path wal = folder().resolve(id + ".wal");
        byte[] data = Files.readAllBytes(wal);
        data[data.length - 1] ^= 1;
        Files.write(wal, data);
        expectFailure(() -> new VaultStore(folder(), id, false));
    }

    @Test
    public void corruptSnapshotLocksEvenWhenValuesStillLookValid() throws Exception {
        try (VaultStore s = create(1)) {
            s.insert(item(1), 3, false);
            s.checkpoint();
        }
        Path snap = folder().resolve(id + ".snapshot");
        byte[] data = Files.readAllBytes(snap);
        data[35] ^= 1;
        Files.write(snap, data);
        expectFailure(() -> new VaultStore(folder(), id, false));
    }

    @Test
    public void checkpointCrashWindowDoesNotReplayTwice() throws Exception {
        byte[] old;
        try (VaultStore s = create(1)) {
            s.insert(item(1), 3, false);
            s.flush();
            old = Files.readAllBytes(folder().resolve(id + ".wal"));
            s.checkpoint();
        }
        Files.write(folder().resolve(id + ".wal"), old);
        try (VaultStore s = new VaultStore(folder(), id, false)) {
            assertEquals(BigInteger.valueOf(1), s.capacity());
            assertEquals(3, s.amount(item(1)));
            s.insert(item(1), 2, false);
            s.checkpoint();
        }
        try (VaultStore s = new VaultStore(folder(), id, false)) {
            assertEquals(5, s.amount(item(1)));
        }
    }

    @Test
    public void truncatedSnapshotLocks() throws Exception {
        try (VaultStore s = create(1)) {
            s.checkpoint();
        }
        Path snap = folder().resolve(id + ".snapshot");
        byte[] data = Files.readAllBytes(snap);
        Files.write(snap, Arrays.copyOf(data, data.length - 1));
        expectFailure(() -> new VaultStore(folder(), id, false));
    }

    @Test
    public void failedWritePermanentlyStopsFurtherMutation() throws Exception {
        VaultStore s = create(1);
        s.close();
        expectFailure(() -> s.insert(item(1), 1, false));
        assertFalse(s.isHealthy());
        assertEquals(0, s.types());
        expectFailure(() -> s.insert(item(2), 1, false));
    }

    @Test
    public void concurrentOperationsCannotOversubscribeBudget() throws Exception {
        try (VaultStore s = create(100)) {
            List<Thread> workers = new ArrayList<>();
            List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
            for (int n = 0; n < 8; n++) {
                final int index = n;
                Thread t = new Thread(() -> {
                    try {
                        for (int i = 0; i < 200; i++) s.insert(item(index), 1, false);
                    } catch (Throwable e) {
                        failures.add(e);
                    }
                });
                workers.add(t);
                t.start();
            }
            for (Thread t : workers) t.join();
            assertTrue(failures.toString(), failures.isEmpty());
            assertEquals(BigInteger.valueOf(100), s.usedBytesCeil());
            assertEquals(
                800,
                s.entries()
                    .values()
                    .stream()
                    .mapToLong(Long::longValue)
                    .sum());
        }
    }

    @Test
    public void randomizedLedgerMatchesIndependentReferenceAcrossReopens() throws Exception {
        Random rng = new Random(881244);
        Map<Resource, Long> reference = new HashMap<>();
        BigInteger used = BigInteger.ZERO;
        VaultStore s = create(40);
        try {
            for (int i = 0; i < 5000; i++) {
                Resource r = rng.nextBoolean() ? item(rng.nextInt(30)) : fluid(rng.nextInt(30));
                long count = rng.nextInt(4000);
                long have = reference.getOrDefault(r, 0L);
                boolean insert = rng.nextBoolean(), simulate = rng.nextInt(4) == 0;
                long expect = insert ? Math.min(
                    count,
                    BigInteger.valueOf(40 * 2048)
                        .subtract(used)
                        .divide(BigInteger.valueOf(r.units()))
                        .longValue())
                    : Math.min(count, have);
                assertEquals(expect, insert ? s.insert(r, count, simulate) : s.extract(r, count, simulate));
                if (!simulate) {
                    long delta = insert ? expect : -expect;
                    long next = have + delta;
                    if (next == 0) reference.remove(r);
                    else reference.put(r, next);
                    used = used.add(BigInteger.valueOf(delta * r.units()));
                }
                assertEquals(reference, s.entries());
                assertEquals(used, s.usedUnits());
                if (i % 200 == 0) {
                    if (i % 400 == 0) s.checkpoint();
                    s.close();
                    s = new VaultStore(folder(), id, false);
                }
            }
        } finally {
            s.close();
        }
    }

    @Test
    public void importedPerKeyCountOverflowCannotCreditCapacity() throws Exception {
        try (VaultStore s = create(Long.MAX_VALUE - 1)) {
            s.insert(item(1), Long.MAX_VALUE, false);
            UUID receipt = UUID.randomUUID();
            expectFailure(() -> s.absorb(receipt, 1, Collections.singletonMap(item(1), 1L)));
            assertEquals(BigInteger.valueOf(Long.MAX_VALUE - 1), s.capacity());
            assertEquals(Long.MAX_VALUE, s.amount(item(1)));
            assertFalse(s.hasReceipt(receipt));
        }
    }

    @Test
    public void secondFileHandleCannotMountSameInventory() throws Exception {
        try (VaultStore s = create(1)) {
            try {
                new VaultStore(folder(), id, false);
                fail("second handle mounted");
            } catch (java.nio.channels.OverlappingFileLockException expected) {}
            assertTrue(s.isHealthy());
            s.insert(item(1), 1, false);
        }
    }

    @Test
    public void invalidRatesAndEmptyKeysAreRejected() throws Exception {
        expectFailure(() -> new Resource("items", 3, new byte[] { 1 }, new byte[] { 1 }));
        expectFailure(() -> new Resource("items", 8, new byte[0], new byte[] { 1 }));
    }
}

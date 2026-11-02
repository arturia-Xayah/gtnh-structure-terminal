package dev.aestoragevault.core;

import static org.junit.Assert.*;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CellReturnTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private final Resource item = new Resource("items", 8, new byte[] { 1 }, new byte[] { 1 });

    @Test
    public void previousDevelopmentSnapshotUpgradesWithoutChangingCapacityOrSources() throws Exception {
        Path folder = temp.getRoot()
            .toPath();
        UUID id = UUID.randomUUID();
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
        out.writeInt(0x41565335); // AVS5, before return metadata existed.
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
        out.writeLong(0);
        CapacityValue.write(out, BigInteger.valueOf(1024));
        out.writeInt(0); // Receipts.
        out.writeInt(0); // Stock.
        out.writeInt(1);
        new CellContribution("test:cell", 0, BigInteger.ONE, BigInteger.valueOf(1024)).write(out);
        out.writeLong(0x454e445641554c54L);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(bytes.toByteArray());
        out.writeInt((int) crc.getValue());
        Path snapshot = folder.resolve(id + ".snapshot");
        java.nio.file.Files.write(snapshot, bytes.toByteArray());
        java.nio.file.Files.write(folder.resolve(id + ".wal"), new byte[0]);
        try (VaultStore s = new VaultStore(folder, id, false)) {
            assertEquals(BigInteger.valueOf(1024), s.capacity());
            assertEquals(BigInteger.ONE, s.contribution("test:cell@0").count);
            assertEquals(BigInteger.ZERO, s.returnedBytes());
            assertFalse(s.hasPendingReturn());
            s.checkpoint();
        }
        try (java.io.DataInputStream in = new java.io.DataInputStream(java.nio.file.Files.newInputStream(snapshot))) {
            assertEquals(0x41565336, in.readInt());
        }
        try (VaultStore s = new VaultStore(folder, id, false)) {
            assertEquals(BigInteger.valueOf(1024), s.capacity());
            assertEquals(BigInteger.ONE, s.maxReturn("test:cell@0"));
        }
    }

    @Test
    public void partialCapacityAndPendingDeliverySurviveWalAndCheckpoint() throws Exception {
        Path folder = temp.getRoot()
            .toPath();
        UUID vault = UUID.randomUUID(), token = UUID.randomUUID();
        String key = "test:cell@0";
        try (VaultStore s = new VaultStore(folder, vault, true)) {
            s.absorbCell(UUID.randomUUID(), 1000, Collections.emptyMap(), "test:cell", 0, 10);
            s.insert(item, 5600, false); // 700 bytes used, only three 100-byte cells may leave.
            assertEquals(BigInteger.valueOf(3), s.maxReturn(key));
            PendingReturn r = s.beginReturn(token, key, 100, new byte[] { 9 });
            assertEquals(BigInteger.valueOf(3), r.source.count);
            assertEquals(BigInteger.valueOf(700), s.capacity());
            assertEquals(BigInteger.valueOf(7), s.contribution(key).count);
            assertEquals(0, s.insert(item, 1, false));
            assertEquals(0, s.extract(item, 1, false));
            assertEquals(BigInteger.ZERO, s.maxReturn(key));
            try {
                s.absorbCell(UUID.randomUUID(), 100, Collections.emptyMap(), "test:cell", 0, 1);
                fail("Imports must await pending return delivery");
            } catch (java.io.IOException expected) {}
        }
        try (VaultStore s = new VaultStore(folder, vault, false)) {
            assertEquals(
                1,
                s.pendingReturns()
                    .size());
            assertEquals(BigInteger.valueOf(700), s.capacity());
            assertEquals(BigInteger.valueOf(300), s.returnedBytes());
            assertEquals(BigInteger.valueOf(3), s.beginReturn(token, key, 100, new byte[0]).source.count);
            s.checkpoint();
        }
        try (VaultStore s = new VaultStore(folder, vault, false)) {
            assertEquals(
                1,
                s.pendingReturns()
                    .size());
            s.completeReturn(token);
            s.completeReturn(token);
            assertFalse(s.hasPendingReturn());
            assertNull(s.beginReturn(token, key, 1, new byte[0]));
            assertEquals(5600, s.amount(item));
        }
        try (VaultStore s = new VaultStore(folder, vault, false)) {
            assertFalse(s.hasPendingReturn());
            assertEquals(BigInteger.valueOf(700), s.capacity());
            assertEquals(BigInteger.valueOf(7), s.contribution(key).count);
        }
    }

    @Test
    public void fractionalUsedByteAndHistoricalMixedCostsRoundConservatively() throws Exception {
        try (VaultStore s = new VaultStore(
            temp.getRoot()
                .toPath(),
            UUID.randomUUID(),
            true)) {
            s.absorbCell(UUID.randomUUID(), 11, Collections.emptyMap(), "test:cell", 0, 3);
            Resource fluid = new Resource("fluids", 2048, new byte[] { 2 }, new byte[] { 2 });
            s.insert(fluid, 1, false);
            assertEquals(BigInteger.valueOf(2), s.maxReturn("test:cell@0"));
            UUID token = UUID.randomUUID();
            PendingReturn r = s.beginReturn(token, "test:cell@0", 2, new byte[0]);
            assertEquals(BigInteger.valueOf(8), r.source.bytes); // ceil(22/3), leaving 3 bytes.
            assertEquals(BigInteger.valueOf(3), s.capacity());
            s.completeReturn(token);
            assertEquals(BigInteger.ZERO, s.maxReturn("test:cell@0"));
            s.extract(fluid, 1, false);
            token = UUID.randomUUID();
            r = s.beginReturn(token, "test:cell@0", 99, new byte[0]);
            assertEquals(BigInteger.valueOf(3), r.source.bytes);
            s.completeReturn(token);
            assertTrue(
                s.contributions()
                    .isEmpty());
            assertEquals(BigInteger.ZERO, s.capacity());
        }
    }

    @Test
    public void fullOrMissingSourcesMakeNoDebit() throws Exception {
        try (VaultStore s = new VaultStore(
            temp.getRoot()
                .toPath(),
            UUID.randomUUID(),
            true)) {
            s.absorbCell(UUID.randomUUID(), 10, Collections.emptyMap(), "test:cell", 0, 1);
            s.insert(item, 80, false);
            assertNull(s.beginReturn(UUID.randomUUID(), "test:cell@0", 1, new byte[0]));
            assertNull(s.beginReturn(UUID.randomUUID(), "test:missing@0", 1, new byte[0]));
            assertEquals(BigInteger.TEN, s.capacity());
            assertFalse(s.hasPendingReturn());
        }
    }

    @Test
    public void largeCountsAndIntegerPhysicalOutputLimitStayExact() throws Exception {
        try (VaultStore s = new VaultStore(
            temp.getRoot()
                .toPath(),
            UUID.randomUUID(),
            true)) {
            // Repeated long source imports still accumulate full precision in the shared ledger.
            for (int i = 0; i < 3; i++)
                s.absorbCell(UUID.randomUUID(), Long.MAX_VALUE, Collections.emptyMap(), "test:cell", 0, Long.MAX_VALUE);
            BigInteger before = BigInteger.valueOf(Long.MAX_VALUE)
                .multiply(BigInteger.valueOf(3));
            assertEquals(before, s.maxReturn("test:cell@0"));
            UUID token = UUID.randomUUID();
            PendingReturn r = s.beginReturn(token, "test:cell@0", 1024, new byte[0]);
            assertEquals(BigInteger.valueOf(1024), r.source.count);
            assertEquals(before.subtract(BigInteger.valueOf(1024)), s.capacity());
            s.completeReturn(token);
        }
    }

    @Test
    public void quantityInputClampsAndRejectsMalformedValues() {
        BigInteger max = BigInteger.ONE.shiftLeft(250);
        assertEquals(
            max,
            ReturnNumbers.clamp(
                max.add(BigInteger.ONE)
                    .toString(),
                max));
        assertEquals(BigInteger.valueOf(2), ReturnNumbers.clamp("0002", max));
        assertEquals(BigInteger.ZERO, ReturnNumbers.clamp("0", max));
        for (String invalid : new String[] { "", "-1", "+1", "1.5", "1e3", "１２", " 1" }) {
            try {
                ReturnNumbers.clamp(invalid, max);
                fail(invalid);
            } catch (IllegalArgumentException expected) {}
        }
    }
}

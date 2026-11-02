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

public final class BigQuantityTest {

    private Resource item() {
        return new Resource("items", 8, new byte[] { 1 }, new byte[] { 1 });
    }

    private Resource fluid() {
        return new Resource("fluids", 2048, new byte[] { 2 }, new byte[] { 2 });
    }

    @Test
    public void repeatedNativeTransactionsCrossLongAndRestoreFromJournalAndSnapshot() throws Exception {
        Path folder = Files.createTempDirectory("vault-big-quantity");
        UUID id = UUID.randomUUID();
        BigInteger total = BigInteger.valueOf(Long.MAX_VALUE)
            .multiply(BigInteger.valueOf(3));
        try (VaultStore store = new VaultStore(folder, id, true)) {
            store.absorb(UUID.randomUUID(), BigInteger.ONE.shiftLeft(130), Collections.emptyMap());
            for (int i = 0; i < 3; i++) {
                assertEquals(Long.MAX_VALUE, store.insert(item(), Long.MAX_VALUE, false));
                assertEquals(Long.MAX_VALUE, store.insert(fluid(), Long.MAX_VALUE, false));
            }
            assertEquals(total, store.exactAmount(item()));
            assertEquals(total, store.exactAmount(fluid()));
            assertEquals(total.multiply(BigInteger.valueOf(257)), store.usedUnits());
            assertEquals(Long.MAX_VALUE, store.amount(item()));
            assertEquals(64, store.extract(item(), 64, true));
            assertEquals(total, store.exactAmount(item()));
        }
        try (VaultStore store = new VaultStore(folder, id, false)) {
            assertEquals(total, store.exactAmount(item()));
            assertEquals(64, store.extract(item(), 64, false));
            assertEquals(total.subtract(BigInteger.valueOf(64)), store.exactAmount(item()));
            store.checkpoint();
        }
        try (VaultStore store = new VaultStore(folder, id, false)) {
            assertEquals(total.subtract(BigInteger.valueOf(64)), store.exactAmount(item()));
            assertEquals(total, store.exactAmount(fluid()));
        }
    }

    @Test
    public void fullPrecisionImportsPreserveCountsAndReceipts() throws Exception {
        Path folder = Files.createTempDirectory("vault-big-import");
        UUID id = UUID.randomUUID(), receipt = UUID.randomUUID();
        BigInteger count = BigInteger.ONE.shiftLeft(1023)
            .add(BigInteger.valueOf(31));
        Map<Resource, BigInteger> stock = new HashMap<>();
        stock.put(item(), count);
        stock.put(fluid(), count);
        try (VaultStore store = new VaultStore(folder, id, true)) {
            store.absorbExact(receipt, count, stock);
            store.absorbExact(receipt, count, stock);
            assertEquals(count, store.capacity());
            assertEquals(count, store.exactAmount(item()));
            store.checkpoint();
        }
        try (VaultStore store = new VaultStore(folder, id, false)) {
            assertEquals(count, store.exactAmount(fluid()));
            assertTrue(store.hasReceipt(receipt));
        }
    }

    @Test
    public void rejectedHugeImportCannotCreditCapacityOrReceipt() throws Exception {
        Path folder = Files.createTempDirectory("vault-big-limit");
        UUID id = UUID.randomUUID(), receipt = UUID.randomUUID();
        try (VaultStore store = new VaultStore(folder, id, true)) {
            try {
                store.absorbExact(
                    receipt,
                    BigInteger.ONE,
                    Collections.singletonMap(item(), BigInteger.ONE.shiftLeft(200)));
                fail();
            } catch (IllegalArgumentException expected) {}
            assertEquals(BigInteger.ZERO, store.capacity());
            assertEquals(0, store.types());
            assertFalse(store.hasReceipt(receipt));
        }
    }

    @Test
    public void maximumEncodedQuantityStopsCleanlyBeforeUnserializableStock() throws Exception {
        Path folder = Files.createTempDirectory("vault-big-bound");
        UUID id = UUID.randomUUID();
        BigInteger maximum = BigInteger.ONE.shiftLeft(32767)
            .subtract(BigInteger.ONE);
        try (VaultStore store = new VaultStore(folder, id, true)) {
            store.absorbExact(
                UUID.randomUUID(),
                maximum,
                Collections.singletonMap(item(), maximum.subtract(BigInteger.ONE)));
            assertEquals(1, store.insert(item(), 64, false));
            assertEquals(0, store.insert(item(), 64, false));
            assertEquals(maximum, store.exactAmount(item()));
            store.checkpoint();
        }
        try (VaultStore store = new VaultStore(folder, id, false)) {
            assertEquals(maximum, store.exactAmount(item()));
        }
    }

    @Test
    public void visibleAdditionSaturatesPositiveOverflowOnly() {
        Random random = new Random(61);
        for (int i = 0; i < 100000; i++) {
            long current = random.nextLong(), delta = random.nextLong();
            long result = current + VisibleAmount.addition(current, delta);
            if (current >= 0 && delta > 0) {
                BigInteger expected = BigInteger.valueOf(current)
                    .add(BigInteger.valueOf(delta))
                    .min(BigInteger.valueOf(Long.MAX_VALUE));
                assertEquals(expected.longValue(), result);
            } else assertEquals(current + delta, result);
        }
    }
}

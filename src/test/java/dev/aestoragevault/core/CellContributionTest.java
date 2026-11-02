package dev.aestoragevault.core;

import static org.junit.Assert.*;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CellContributionTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void correctedHighEndFormulaAndOverflowBoundary() {
        String singularity = "appliedenergistics2:item.ItemExtremeStorageCell.Singularity";
        assertEquals(1L << 44, CellPolicy.contribution(singularity, Long.MAX_VALUE / 16));
        assertEquals(
            Long.MAX_VALUE / 16,
            CellPolicy.contribution("appliedenergistics2:item.ItemExtremeStorageCell.Universe", Long.MAX_VALUE / 16));
        assertEquals(1L << 36, CellPolicy.contribution("ae2fc:fluid_storage.singularity", Long.MAX_VALUE / 4096));
        assertEquals(
            Long.MAX_VALUE / 2048,
            CellPolicy.contribution("ae2fc:fluid_storage.Universe", Long.MAX_VALUE / 2048));
        assertEquals(1L << 48, CellPolicy.contribution(singularity, Long.MAX_VALUE));
        for (String item : new String[] { singularity, "ae2fc:fluid_storage.singularity",
            "appliedenergistics2:item.ItemExtremeStorageCell.Universe", "ae2fc:fluid_storage.Universe" })
            assertTrue(CellPolicy.requiresEmpty(item));
        assertFalse(CellPolicy.requiresEmpty("other:singularity"));
        assertFalse(CellPolicy.discounted("ae2fc:fluid_storage.Universe"));
        assertEquals(2147483646L, CellPolicy.CHEST_BYTES);
        assertEquals(16777216L, CellPolicy.contribution("other:singularity", 16777216));
    }

    @Test
    public void receiptsAtomicallyDeduplicateCountsAndBytesAcrossWalAndCheckpoint() throws Exception {
        Path folder = temp.getRoot()
            .toPath();
        UUID id = UUID.randomUUID(), first = UUID.randomUUID();
        try (VaultStore s = new VaultStore(folder, id, true)) {
            s.absorbCell(first, 2048, Collections.emptyMap(), "ae:part", 1, 2);
            s.absorbCell(first, 2048, Collections.emptyMap(), "ae:part", 1, 2);
            assertEquals(
                BigInteger.valueOf(2),
                s.contributions()
                    .get(0).count);
            assertEquals(BigInteger.valueOf(2048), s.capacity());
        }
        try (VaultStore s = new VaultStore(folder, id, false)) {
            assertEquals(
                BigInteger.valueOf(2),
                s.contributions()
                    .get(0).count);
            s.absorbCell(UUID.randomUUID(), 1024, Collections.emptyMap(), "ae:part", 1, 1);
            s.absorbCell(UUID.randomUUID(), 4096, Collections.emptyMap(), "ae:part", 2, 1);
            assertEquals(
                2,
                s.contributions()
                    .size());
            assertEquals(
                BigInteger.valueOf(3),
                s.contributions()
                    .get(0).count);
            assertEquals(
                BigInteger.valueOf(3072),
                s.contributions()
                    .get(0).bytes);
            s.checkpoint();
        }
        try (VaultStore s = new VaultStore(folder, id, false)) {
            assertEquals(
                2,
                s.contributions()
                    .size());
            assertEquals(BigInteger.valueOf(7168), s.capacity());
            s.absorbCell(first, 2048, Collections.emptyMap(), "ae:part", 1, 2);
            assertEquals(
                BigInteger.valueOf(3),
                s.contributions()
                    .get(0).count);
        }
    }

    @Test
    public void rejectedImportLeavesHistoryAndCapacityUntouched() throws Exception {
        try (VaultStore s = new VaultStore(
            temp.getRoot()
                .toPath(),
            UUID.randomUUID(),
            true)) {
            Resource r = new Resource("items", 8, new byte[] { 1 }, new byte[] { 1 });
            try {
                s.absorbCell(UUID.randomUUID(), 1, Collections.singletonMap(r, 9L), "ae:cell", 0, 1);
                fail("over-capacity import accepted");
            } catch (IllegalArgumentException expected) {}
            assertTrue(
                s.contributions()
                    .isEmpty());
            assertEquals(BigInteger.ZERO, s.capacity());
            assertEquals(0, s.contributionRevision());
        }
    }

    @Test
    public void bigCountsAndTinyPercentagesStayExact() {
        BigInteger huge = BigInteger.ONE.shiftLeft(200);
        CellContribution c = new CellContribution("ae:cell", 0, huge, huge.multiply(BigInteger.valueOf(1024)));
        assertEquals(huge.multiply(BigInteger.valueOf(2)), c.plus(c).count);
        assertEquals("25%", c.percentage(c.bytes.multiply(BigInteger.valueOf(4))));
        assertEquals("<0.01%", c.percentage(c.bytes.multiply(BigInteger.valueOf(1000000))));
        assertEquals("0%", c.percentage(BigInteger.ZERO));
    }
}

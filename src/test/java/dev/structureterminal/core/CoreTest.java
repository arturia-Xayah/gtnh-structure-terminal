package dev.structureterminal.core;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

public final class CoreTest {

    @Test
    public void missingMaterialsStopOnFirstZero() {
        BuildProgress p = new BuildProgress();
        assertEquals(BuildProgress.State.RUNNING, p.accept(25, 10));
        assertEquals(BuildProgress.State.BLOCKED, p.accept(0, 10));
        assertEquals(25, p.placed());
        try {
            p.accept(25, 10);
            fail("Stopped job resumed");
        } catch (IllegalStateException expected) {}
    }

    @Test
    public void completionDoesNotReducePlacedTotal() {
        BuildProgress p = new BuildProgress();
        p.accept(12, 10);
        assertEquals(BuildProgress.State.COMPLETE, p.accept(-1, 10));
        assertEquals(12, p.placed());
    }

    @Test
    public void unsupportedNativeApiStops() {
        assertEquals(BuildProgress.State.UNSUPPORTED, new BuildProgress().accept(-2, 100));
    }

    @Test
    public void positiveButEndlessNativeJobsAreBounded() {
        BuildProgress p = new BuildProgress();
        for (int i = 0; i < 999; i++) assertEquals(BuildProgress.State.RUNNING, p.accept(200, 1000));
        assertEquals(BuildProgress.State.LIMIT, p.accept(200, 1000));
        assertEquals(200000, p.placed());
    }

    @Test
    public void accountingSurvivesManyRandomBatches() {
        Random random = new Random(2902);
        for (int run = 0; run < 1000; run++) {
            BuildProgress p = new BuildProgress();
            long expected = 0;
            int rounds = 1 + random.nextInt(1000);
            for (int i = 0; i < rounds; i++) {
                int n = 1 + random.nextInt(200);
                expected += n;
                p.accept(n, 1001);
            }
            assertEquals(expected, p.placed());
            assertEquals(rounds, p.rounds());
            assertEquals(BuildProgress.State.COMPLETE, p.accept(-1, 1001));
        }
    }

    @Test
    public void networkChannelsRejectBadNamesAndUnboundedNumbers() {
        assertEquals("coil", Limits.channel("coil"));
        assertEquals("gt_no_hatch", Limits.channel("gt_no_hatch"));
        for (String s : new String[] { "", "Coil", "../coil", "coil\n", "glass:uv", "雪" }) {
            try {
                Limits.channel(s);
                fail(s);
            } catch (IllegalArgumentException expected) {}
        }
        for (int n : new int[] { 0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 1000001 }) {
            try {
                Limits.value(n);
                fail(Integer.toString(n));
            } catch (IllegalArgumentException expected) {}
        }
    }

    @Test
    public void controllerReachHandlesNegativeAndLargeCoordinates() {
        assertTrue(Limits.inReach(-100.5, 65.5, 200.5, -101, 65, 200, 8));
        assertFalse(Limits.inReach(100, 65, 200, -101, 65, 200, 8));
        assertFalse(Limits.inReach(30000000, 65, 30000000, -30000000, 65, -30000000, 8));
    }

    @Test
    public void candidateGroupsAreOrderIndependentButKeepMetadata() {
        assertEquals(
            Fingerprints.group(Arrays.asList("gt:coil@0", "gt:coil@1")),
            Fingerprints.group(Arrays.asList("gt:coil@1", "gt:coil@0")));
        assertNotEquals(
            Fingerprints.group(Arrays.asList("gt:glass@0")),
            Fingerprints.group(Arrays.asList("gt:glass@1")));
        assertNotEquals(Fingerprints.group(Arrays.asList("a", "bc")), Fingerprints.group(Arrays.asList("ab", "c")));
    }
}

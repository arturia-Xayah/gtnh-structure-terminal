package dev.aestoragevault.ui.dashboard;

import static org.junit.Assert.*;

import java.math.BigInteger;

import org.junit.Test;

public class VaultAnimationTest {

    private static final BigInteger CAP = BigInteger.valueOf(1000);

    @Test
    public void openingShowsCurrentValuesWithoutPretendingToFillFromZero() {
        VaultAnimation.Frame frame = new VaultAnimation().sample(1000, CAP, BigInteger.valueOf(250), 218, true);
        assertEquals(CAP, frame.capacity);
        assertEquals(BigInteger.valueOf(250), frame.used);
        assertEquals(218, frame.types);
        assertEquals(.25, frame.ratio, 1e-12);
    }

    @Test
    public void interpolationRetargetsFromCurrentPresentationAndSettlesExactly() {
        VaultAnimation a = new VaultAnimation();
        a.sample(1000, CAP, BigInteger.ZERO, 0, true);
        a.sample(2000, CAP, BigInteger.valueOf(800), Integer.MAX_VALUE, true);
        VaultAnimation.Frame half = a.sample(2250, CAP, BigInteger.valueOf(800), Integer.MAX_VALUE, true);
        assertEquals(BigInteger.valueOf(400), half.used);
        assertEquals(.4, half.ratio, 1e-12);
        assertEquals(Integer.MAX_VALUE / 2, half.types);
        VaultAnimation.Frame retarget = a.sample(2250, CAP, BigInteger.valueOf(200), 12, true);
        assertEquals(half.used, retarget.used);
        assertEquals(half.ratio, retarget.ratio, 1e-12);
        assertEquals(BigInteger.valueOf(300), a.sample(2500, CAP, BigInteger.valueOf(200), 12, true).used);
        VaultAnimation.Frame end = a.sample(2750, CAP, BigInteger.valueOf(200), 12, true);
        assertEquals(BigInteger.valueOf(200), end.used);
        assertEquals(12, end.types);
    }

    @Test
    public void hugeCapacitiesNeverGoThroughOverflowingDoubleDivision() {
        BigInteger huge = BigInteger.ONE.shiftLeft(2000);
        assertEquals(.5, VaultAnimation.fraction(huge, huge.shiftRight(1)), 1e-12);
        VaultAnimation a = new VaultAnimation();
        a.sample(0, huge, huge.shiftRight(1), 1, true);
        a.sample(1000, huge.shiftLeft(1), huge, 2, true);
        VaultAnimation.Frame middle = a.sample(1250, huge.shiftLeft(1), huge, 2, true);
        assertTrue(middle.used.signum() >= 0 && middle.used.compareTo(middle.capacity) <= 0);
        assertTrue(Double.isFinite(middle.ratio));
        assertEquals(.5, middle.ratio, 1e-12);
    }

    @Test
    public void reducedMotionAndInvalidStructureSnapValuesAndStopDecorativeMotion() {
        VaultAnimation a = new VaultAnimation();
        a.sample(1000, CAP, BigInteger.ZERO, 0, true);
        a.setEnabled(false);
        VaultAnimation.Frame off = a.sample(1100, CAP, BigInteger.valueOf(800), 10, true);
        assertEquals(BigInteger.valueOf(800), off.used);
        assertFalse(off.moving);
        assertEquals(0, off.phase, 0);
        a.setEnabled(true);
        assertEquals(off.used, a.sample(1200, CAP, off.used, 10, true).used);
        VaultAnimation.Frame invalid = a.sample(1250, CAP, BigInteger.valueOf(400), 5, false);
        assertEquals(BigInteger.valueOf(400), invalid.used);
        assertFalse(invalid.moving);
        assertEquals(0, invalid.phase, 0);
    }

    @Test
    public void emptyAndInconsistentSnapshotsAreClampedForPresentation() {
        VaultAnimation a = new VaultAnimation();
        VaultAnimation.Frame zero = a.sample(1000, BigInteger.ZERO, CAP, -1, true);
        assertEquals(BigInteger.ZERO, zero.used);
        assertEquals(0, zero.types);
        assertEquals(0, zero.ratio, 0);
        assertEquals(1, VaultAnimation.fraction(CAP, CAP.add(BigInteger.ONE)), 0);
        assertEquals(0, VaultAnimation.fraction(CAP, BigInteger.valueOf(-1)), 0);
    }
}

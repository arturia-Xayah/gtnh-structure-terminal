package dev.aestoragevault.ui.dashboard;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/** Client presentation only: no world, networking, storage writes or background tasks. */
public final class VaultAnimation {

    public static final long DURATION_MS = 500;
    private static final int STEPS = 30;
    private boolean enabled = true, initialized;
    private long started;
    private BigInteger fromCapacity = BigInteger.ZERO, toCapacity = BigInteger.ZERO;
    private BigInteger fromUsed = BigInteger.ZERO, toUsed = BigInteger.ZERO;
    private int fromTypes, toTypes;
    private double shownRatio;
    private int cachedStep = -1;
    private BigInteger shownCapacity = BigInteger.ZERO, shownUsed = BigInteger.ZERO;
    private int shownTypes;

    public void setEnabled(boolean value) {
        enabled = value;
        if (!value) snap();
    }

    public boolean enabled() {
        return enabled;
    }

    public Frame sample(long now, BigInteger capacity, BigInteger used, int types, boolean formed) {
        capacity = capacity.max(BigInteger.ZERO);
        used = used.max(BigInteger.ZERO)
            .min(capacity);
        types = Math.max(0, types);
        boolean changed = !capacity.equals(toCapacity) || !used.equals(toUsed) || types != toTypes;
        if (!initialized || changed) {
            Frame before = frame(now, formed);
            fromCapacity = initialized ? before.capacity : capacity;
            fromUsed = initialized ? before.used : used;
            fromTypes = initialized ? before.types : types;
            toCapacity = capacity;
            toUsed = used;
            toTypes = types;
            started = now;
            cachedStep = -1;
            if (!initialized || !enabled || !formed) snap();
            initialized = true;
        }
        if (!enabled || !formed) snap();
        return frame(now, formed);
    }

    private void snap() {
        boolean changed = !fromCapacity.equals(toCapacity) || !fromUsed.equals(toUsed) || fromTypes != toTypes;
        fromCapacity = toCapacity;
        fromUsed = toUsed;
        fromTypes = toTypes;
        if (changed) cachedStep = -1;
    }

    private Frame frame(long now, boolean formed) {
        double linear = enabled ? Math.max(0, Math.min(1, (now - started) / (double) DURATION_MS)) : 1;
        double ease = linear * linear * (3 - 2 * linear);
        int step = (int) Math.round(ease * STEPS);
        if (step != cachedStep) {
            shownCapacity = lerp(fromCapacity, toCapacity, step);
            shownUsed = lerp(fromUsed, toUsed, step).min(shownCapacity);
            shownTypes = (int) (fromTypes + ((long) toTypes - fromTypes) * step / STEPS);
            shownRatio = fraction(shownCapacity, shownUsed);
            cachedStep = step;
        }
        boolean moving = enabled && formed;
        double phase = moving ? Math.floorMod(now, 6000L) / 6000.0 : 0;
        return new Frame(shownCapacity, shownUsed, shownTypes, shownRatio, phase, moving);
    }

    private static BigInteger lerp(BigInteger from, BigInteger to, int step) {
        if (step == 0 || from.equals(to)) return from;
        if (step == STEPS) return to;
        return from.add(
            to.subtract(from)
                .multiply(BigInteger.valueOf(step))
                .divide(BigInteger.valueOf(STEPS)));
    }

    public static double fraction(BigInteger capacity, BigInteger used) {
        if (capacity.signum() <= 0 || used.signum() <= 0) return 0;
        if (used.compareTo(capacity) >= 0) return 1;
        return new BigDecimal(used).divide(new BigDecimal(capacity), MathContext.DECIMAL64)
            .doubleValue();
    }

    public static final class Frame {

        public final BigInteger capacity, used;
        public final int types;
        public final double ratio, phase;
        public final boolean moving;

        private Frame(BigInteger capacity, BigInteger used, int types, double ratio, double phase, boolean moving) {
            this.capacity = capacity;
            this.used = used;
            this.types = types;
            this.ratio = ratio;
            this.phase = phase;
            this.moving = moving;
        }
    }
}

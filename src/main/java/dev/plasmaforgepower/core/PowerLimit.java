package dev.plasmaforgepower.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Immutable power budget; the selected tier is a display unit, not an electrical transformer. */
public final class PowerLimit {

    // Keep existing saved IDs 0..4 stable; append new voltage units.
    public static final String[] TIERS = { "UEV", "UIV", "UMV", "UXV", "MAX", "ULV", "LV", "MV", "HV", "EV", "IV",
        "LuV", "ZPM", "UV", "UHV" };
    private static final long[] VOLTAGES = { 8388608L, 33554432L, 134217728L, 536870912L, 2147483640L, 8L, 32L, 128L,
        512L, 2048L, 8192L, 32768L, 131072L, 524288L, 2097152L };
    private final long eut;
    private final int tier;

    private PowerLimit(long eut, int tier) {
        if (tier < 0 || tier >= VOLTAGES.length || (eut != -1 && eut <= 0)) {
            throw new IllegalArgumentException("Invalid power limit");
        }
        this.eut = eut;
        this.tier = tier;
    }

    public static PowerLimit automatic() {
        return new PowerLimit(-1, 2);
    }

    public static PowerLimit ofEUt(long eut, int tier) {
        return new PowerLimit(eut, tier);
    }

    public static PowerLimit parse(String amps, int tier) {
        if (amps == null || amps.length() > 32 || !amps.matches("-1|[0-9]+(?:\\.[0-9]{1,16})?")) {
            throw new IllegalArgumentException("Invalid amperage");
        }
        if (tier < 0 || tier >= VOLTAGES.length) throw new IllegalArgumentException("Invalid voltage tier");
        BigDecimal current = new BigDecimal(amps);
        if (current.signum() == 0 || current.compareTo(BigDecimal.valueOf(-1)) == 0) return automatic();
        long budget;
        try {
            budget = current.multiply(BigDecimal.valueOf(VOLTAGES[tier]))
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Power exceeds supported range", e);
        }
        return ofEUt(budget, tier);
    }

    public boolean isAuto() {
        return eut == -1;
    }

    public long eut() {
        return eut;
    }

    public int tier() {
        return tier;
    }

    public long effective(long machinePower) {
        return isAuto() ? machinePower : Math.min(eut, machinePower);
    }

    public static long voltage(int tier) {
        return VOLTAGES[tier];
    }

    public static int displayTier(long voltage) {
        int best = 5;
        for (int tier = 0; tier < VOLTAGES.length; tier++)
            if (VOLTAGES[tier] <= voltage && VOLTAGES[tier] > VOLTAGES[best]) best = tier;
        return best;
    }

    public static Integer[] displayOrder() {
        return new Integer[] { 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 0, 1, 2, 3, 4 };
    }

    public String amps() {
        if (isAuto()) return "-1";
        return BigDecimal.valueOf(eut)
            .divide(BigDecimal.valueOf(VOLTAGES[tier]), 16, RoundingMode.DOWN)
            .stripTrailingZeros()
            .toPlainString();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PowerLimit)) return false;
        PowerLimit limit = (PowerLimit) other;
        return eut == limit.eut && tier == limit.tier;
    }

    @Override
    public int hashCode() {
        return Objects.hash(eut, tier);
    }
}

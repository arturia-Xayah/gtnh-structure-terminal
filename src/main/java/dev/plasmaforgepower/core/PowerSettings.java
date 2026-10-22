package dev.plasmaforgepower.core;

import java.util.Arrays;

/** GregTech dye IDs: -1 unpainted, 0 black, 1 red, 2 green, ... 15 white. */
public final class PowerSettings {

    private final PowerLimit[] limits = new PowerLimit[17];

    public PowerSettings() {
        Arrays.fill(limits, PowerLimit.automatic());
    }

    public static boolean validColor(int color) {
        return color >= -1 && color < 16;
    }

    public PowerLimit get(int color) {
        validateColor(color);
        return limits[color + 1];
    }

    public void set(int color, PowerLimit limit) {
        validateColor(color);
        if (limit == null) throw new IllegalArgumentException("Missing limit");
        limits[color + 1] = limit;
    }

    private static void validateColor(int color) {
        if (!validColor(color)) throw new IllegalArgumentException("Invalid dye color");
    }

    public boolean isAllAuto() {
        for (PowerLimit limit : limits) if (!limit.isAuto()) return false;
        return true;
    }

    public PowerSettings copy() {
        PowerSettings copy = new PowerSettings();
        System.arraycopy(limits, 0, copy.limits, 0, limits.length);
        return copy;
    }

    /** Only call with the rated supply of a successfully formed structure. Zero never erases configuration. */
    public int clamp(long ratedPower, long ratedVoltage) {
        if (ratedPower <= 0) return 0;
        int changed = 0;
        for (int i = 0; i < limits.length; i++) {
            if (!limits[i].isAuto() && limits[i].eut() > ratedPower) {
                limits[i] = PowerLimit.ofEUt(ratedPower, PowerLimit.displayTier(ratedVoltage));
                changed++;
            }
        }
        return changed;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PowerSettings && Arrays.equals(limits, ((PowerSettings) other).limits);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(limits);
    }
}

package dev.aestoragevault.core;

/** Positive inventory totals saturate at the native API ceiling rather than wrapping negative. */
public final class VisibleAmount {

    private VisibleAmount() {}

    public static long addition(long current, long delta) {
        return current >= 0 && delta > 0 && delta > Long.MAX_VALUE - current ? Long.MAX_VALUE - current : delta;
    }
}

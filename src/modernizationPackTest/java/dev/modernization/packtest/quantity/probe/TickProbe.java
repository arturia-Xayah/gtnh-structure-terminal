package dev.modernization.packtest.quantity.probe;

/** Test-only whole-method wall clock, excluding the server loop's inter-tick sleep. */
public final class TickProbe {

    private static long started;
    public static volatile long lastNanos;
    public static volatile long ticks;

    public static void begin() {
        started = System.nanoTime();
    }

    public static void end() {
        lastNanos = System.nanoTime() - started;
        ticks++;
    }
}

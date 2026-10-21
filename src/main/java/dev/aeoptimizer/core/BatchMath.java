package dev.aeoptimizer.core;

public final class BatchMath {

    private BatchMath() {}

    public static long ceilDiv(long value, long divisor) {
        if (value < 0 || divisor <= 0) throw new IllegalArgumentException("invalid recipe amount");
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }

    public static long limit(long remaining, long cap, long available, long perCraft) {
        if (remaining < 0 || cap < 0 || available < 0 || perCraft <= 0)
            throw new IllegalArgumentException("invalid batch amount");
        return Math.min(Math.min(remaining, cap), available / perCraft);
    }
}

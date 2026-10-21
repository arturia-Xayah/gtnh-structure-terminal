package dev.aeoptimizer.dispatch;

public final class BatchContext {

    public static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private BatchContext() {}
}

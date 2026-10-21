package dev.aeoptimizer.api;

/** Read-only count of successful bulk transactions on a CPU since it was loaded. */
public interface CpuBatchStats {

    long aeo$getBatches();
}

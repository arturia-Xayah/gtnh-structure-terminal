import dev.aestoragevault.core.Resource;
import dev.aestoragevault.core.VaultStore;

import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/** Small ledger microbenchmark, not a game/network TPS benchmark. */
public final class CapacityBenchmark {
    private static volatile long sink;

    public static void main(String[] args) throws Exception {
        Path folder = Paths.get(args[0]);
        int bits = Integer.parseInt(args[1]);
        Resource r = new Resource("items", 8, new byte[] { 1 }, new byte[] { 1 });
        try (VaultStore store = new VaultStore(folder, UUID.randomUUID(), true)) {
            if (bits <= 63) store.absorb(UUID.randomUUID(), Long.MAX_VALUE, Collections.emptyMap());
            else VaultStore.class.getMethod("absorb", UUID.class, BigInteger.class, Map.class)
                .invoke(store, UUID.randomUUID(), BigInteger.ONE.shiftLeft(bits - 1), Collections.emptyMap());
            store.insert(r, 1000, false);
            for (int warm = 0; warm < 5; warm++) simulate(store, r, 100000);
            double[] simulation = new double[7], journal = new double[5];
            for (int n = 0; n < simulation.length; n++) simulation[n] = simulate(store, r, 200000);
            for (int n = 0; n < journal.length; n++) journal[n] = modulate(store, r, 5000);
            Arrays.sort(simulation);
            Arrays.sort(journal);
            System.out.println("{\"capacity_bits\":" + bits + ",\"simulate_ns_per_op\":"
                + simulation[simulation.length / 2] + ",\"journal_ns_per_op\":"
                + journal[journal.length / 2] + ",\"sink\":" + sink + "}");
        }
    }

    private static double simulate(VaultStore store, Resource r, int count) throws Exception {
        long checksum = 0, start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            checksum += store.insert(r, Long.MAX_VALUE, true) & 65535;
            checksum += store.extract(r, 7, true);
        }
        long elapsed = System.nanoTime() - start;
        sink = checksum;
        return elapsed / (count * 2.0);
    }

    private static double modulate(VaultStore store, Resource r, int count) throws Exception {
        long checksum = 0, start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            checksum += store.insert(r, 1, false);
            checksum += store.extract(r, 1, false);
        }
        store.flush();
        long elapsed = System.nanoTime() - start;
        sink = checksum;
        return elapsed / (count * 2.0);
    }
}

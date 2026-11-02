package dev.aestoragevault.core;

/** Exact registered identities in GTNH 2.9; similarly named materials are never treated as capacity. */
public final class CellPolicy {

    public static final String CHEST = "avaritiaddons:InfinityChest";
    public static final long CHEST_BYTES = (long) Integer.MAX_VALUE - 1;

    private CellPolicy() {}

    public static boolean discounted(String item) {
        return "appliedenergistics2:item.ItemExtremeStorageCell.Singularity".equals(item)
            || "ae2fc:fluid_storage.singularity".equals(item);
    }

    public static boolean requiresEmpty(String item) {
        return discounted(item) || "appliedenergistics2:item.ItemExtremeStorageCell.Universe".equals(item)
            || "ae2fc:fluid_storage.Universe".equals(item);
    }

    public static long contribution(String item, long original) {
        if (original <= 0) throw new IllegalArgumentException("Non-positive cell capacity");
        // Quotient and remainder avoid overflow at Long.MAX_VALUE while computing (original + 1) / 32768.
        return discounted(item) ? original / 32768 + (original % 32768 == 32767 ? 1 : 0) : original;
    }
}

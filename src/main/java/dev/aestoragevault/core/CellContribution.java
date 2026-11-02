package dev.aestoragevault.core;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Immutable contribution, grouped by registered item and metadata, independent of source inventory NBT. */
public final class CellContribution {

    public final String item;
    public final int damage;
    public final BigInteger count, bytes;

    public CellContribution(String item, int damage, BigInteger count, BigInteger bytes) {
        if (item == null || item.length() > 256 || !item.contains(":"))
            throw new IllegalArgumentException("Invalid contribution item");
        if (count.signum() <= 0 || bytes.signum() <= 0) throw new IllegalArgumentException("Non-positive contribution");
        CapacityValue.encode(count);
        CapacityValue.encode(bytes);
        this.item = item;
        this.damage = damage;
        this.count = count;
        this.bytes = bytes;
    }

    public String key() {
        return item + "@" + damage;
    }

    public CellContribution plus(CellContribution other) {
        if (!key().equals(other.key())) throw new IllegalArgumentException("Different cell types");
        return new CellContribution(item, damage, count.add(other.count), bytes.add(other.bytes));
    }

    public String percentage(BigInteger capacity) {
        if (capacity.signum() <= 0) return "0%";
        BigDecimal percent = new BigDecimal(bytes.multiply(BigInteger.valueOf(100)))
            .divide(new BigDecimal(capacity), 2, RoundingMode.HALF_UP);
        return percent.signum() == 0 ? "<0.01%"
            : percent.stripTrailingZeros()
                .toPlainString() + "%";
    }

    public void write(DataOutput out) throws IOException {
        out.writeUTF(item);
        out.writeInt(damage);
        CapacityValue.write(out, count);
        CapacityValue.write(out, bytes);
    }

    public static CellContribution read(DataInput in) throws IOException {
        try {
            return new CellContribution(in.readUTF(), in.readInt(), CapacityValue.read(in), CapacityValue.read(in));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid contribution", e);
        }
    }
}

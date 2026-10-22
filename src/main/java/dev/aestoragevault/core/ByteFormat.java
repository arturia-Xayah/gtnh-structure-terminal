package dev.aestoragevault.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/** Binary capacity labels; formatting does not expand the ledger's signed-long capacity. */
public final class ByteFormat {

    private static final String[] UNITS = { "B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB", "ZiB", "YiB", "RiB" };

    private ByteFormat() {}

    public static String format(long bytes) {
        return format(BigInteger.valueOf(bytes));
    }

    public static String format(BigInteger bytes) {
        if (bytes.signum() < 0) throw new IllegalArgumentException("Negative bytes");
        int index = Math.min(Math.max(0, (bytes.bitLength() - 1) / 10), UNITS.length - 1);
        BigDecimal value = new BigDecimal(bytes)
            .divide(new BigDecimal(BigInteger.ONE.shiftLeft(index * 10)), new MathContext(3));
        return value.stripTrailingZeros()
            .toPlainString() + " "
            + UNITS[index];
    }
}

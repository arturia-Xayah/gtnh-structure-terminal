package dev.aestoragevault.core;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.math.BigInteger;

/** Positive, bounded-length serialization; the bound protects parsers, not the former long ceiling. */
public final class CapacityValue {

    public static final int MAX_ENCODED_BYTES = 4096;

    private CapacityValue() {}

    public static byte[] encode(BigInteger value) {
        if (value.signum() < 0) throw new IllegalArgumentException("Negative capacity");
        byte[] data = value.toByteArray();
        if (data.length > MAX_ENCODED_BYTES) throw new IllegalArgumentException("Capacity encoding exceeds 4096 bytes");
        return data;
    }

    public static BigInteger decode(byte[] data) {
        if (data.length == 0 || data.length > MAX_ENCODED_BYTES)
            throw new IllegalArgumentException("Invalid capacity encoding length");
        BigInteger value = new BigInteger(data);
        if (value.signum() < 0) throw new IllegalArgumentException("Negative capacity");
        return value;
    }

    public static void write(DataOutput out, BigInteger value) throws IOException {
        byte[] data = encode(value);
        out.writeInt(data.length);
        out.write(data);
    }

    public static BigInteger read(DataInput in) throws IOException {
        int length = in.readInt();
        if (length < 1 || length > MAX_ENCODED_BYTES) throw new IOException("Invalid capacity encoding length");
        byte[] data = new byte[length];
        in.readFully(data);
        try {
            return decode(data);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid capacity", e);
        }
    }
}

package dev.aestoragevault.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigInteger;

import org.junit.Test;

public class CapacityValueTest {

    @Test
    public void positiveValuesKeepTheirSignAcrossLongAndUnitBoundaries() {
        for (int bits : new int[] { 0, 63, 64, 80, 90, 200, 1024, 32766 }) {
            BigInteger value = BigInteger.ONE.shiftLeft(bits);
            assertEquals(value, CapacityValue.decode(CapacityValue.encode(value)));
        }
        assertEquals(BigInteger.ZERO, CapacityValue.decode(CapacityValue.encode(BigInteger.ZERO)));
    }

    @Test
    public void corruptNegativeEmptyAndOversizedEncodingsAreRejectedBeforeAllocation() {
        assertThrows(IllegalArgumentException.class, () -> CapacityValue.encode(BigInteger.valueOf(-1)));
        assertThrows(IllegalArgumentException.class, () -> CapacityValue.decode(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> CapacityValue.decode(new byte[] { -1 }));
        assertThrows(IllegalArgumentException.class, () -> CapacityValue.encode(BigInteger.ONE.shiftLeft(32768)));
        assertThrows(
            IOException.class,
            () -> CapacityValue.read(new DataInputStream(new ByteArrayInputStream(new byte[] { 127, -1, -1, -1 }))));
    }
}

package dev.aestoragevault.core;

import static org.junit.Assert.*;

import java.math.BigInteger;

import org.junit.Test;

public class ByteFormatTest {

    @Test
    public void binaryUnitsAndExactBoundaries() {
        String[] names = { "B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB", "ZiB", "YiB", "RiB" };
        for (int i = 0; i < names.length; i++)
            assertEquals("1 " + names[i], ByteFormat.format(BigInteger.ONE.shiftLeft(i * 10)));
        assertEquals("0 B", ByteFormat.format(0));
        assertEquals("256 MiB", ByteFormat.format(268435456));
        assertEquals("8 EiB", ByteFormat.format(Long.MAX_VALUE));
    }

    @Test
    public void valuesBeyondLongDoNotLoseUnitOrOverflow() {
        assertEquals(
            "1.25 ZiB",
            ByteFormat.format(
                BigInteger.ONE.shiftLeft(68)
                    .multiply(BigInteger.valueOf(5))));
        assertEquals("1020 RiB", ByteFormat.format(BigInteger.ONE.shiftLeft(100)));
    }

    @Test
    public void valuesBeyondNamedUnitsStayCompact() {
        String formatted = ByteFormat.format(BigInteger.ONE.shiftLeft(10000));
        assertTrue(formatted.contains("×10^"));
        assertTrue(formatted.endsWith(" RiB"));
        assertTrue(formatted.length() < 25);
    }
}

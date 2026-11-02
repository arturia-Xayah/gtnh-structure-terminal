package dev.aestoragevault.core;

import java.math.BigInteger;

public final class ReturnNumbers {

    private ReturnNumbers() {}

    public static BigInteger clamp(String text, BigInteger maximum) {
        if (text == null || text.length() == 0 || text.length() > 10000 || maximum.signum() < 0)
            throw new IllegalArgumentException("Invalid return quantity");
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) < '0' || text.charAt(i) > '9')
            throw new IllegalArgumentException("Return quantity must be a nonnegative integer");
        return new BigInteger(text).min(maximum);
    }
}

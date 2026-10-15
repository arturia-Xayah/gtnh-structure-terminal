package dev.structureterminal.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class Fingerprints {

    private Fingerprints() {}

    public static String group(Collection<String> keys) {
        List<String> sorted = new ArrayList<String>(keys);
        Collections.sort(sorted);
        StringBuilder encoded = new StringBuilder();
        for (String key : sorted) encoded.append(key.length())
            .append(':')
            .append(key);
        return digest(encoded.toString());
    }

    public static String digest(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8));
            char[] digits = "0123456789abcdef".toCharArray();
            StringBuilder out = new StringBuilder(64);
            for (byte b : bytes) out.append(digits[(b & 255) >>> 4])
                .append(digits[b & 15]);
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}

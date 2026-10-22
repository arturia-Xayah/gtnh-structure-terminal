package dev.aestoragevault.core;

import java.util.Arrays;

/** Immutable logical identity plus a one-unit, channel-native serialization. */
public final class Resource {

    public final String channel;
    public final int perByte;
    private final byte[] key;
    private final byte[] payload;
    private final int hash;

    public Resource(String channel, int perByte, byte[] key, byte[] payload) {
        if (channel == null || channel.length() > 128 || perByte < 1 || 2048 % perByte != 0)
            throw new IllegalArgumentException("Unsupported resource channel/rate");
        if (key.length == 0 || key.length > 4 * 1024 * 1024 || payload.length > 4 * 1024 * 1024)
            throw new IllegalArgumentException("Resource data too large");
        this.channel = channel;
        this.perByte = perByte;
        this.key = key.clone();
        this.payload = payload.clone();
        this.hash = 31 * (31 * channel.hashCode() + perByte) + Arrays.hashCode(key);
    }

    public byte[] key() {
        return key.clone();
    }

    public byte[] payload() {
        return payload.clone();
    }

    public int units() {
        return 2048 / perByte;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Resource)) return false;
        Resource r = (Resource) other;
        return perByte == r.perByte && channel.equals(r.channel) && Arrays.equals(key, r.key);
    }
}

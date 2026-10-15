package dev.structureterminal.core;

/** Bounds also applied to untrusted network requests. */
public final class Limits {

    public static final int CHANNELS = 128;
    public static final int CHOICES = 2048;
    public static final int MAX_VALUE = 1000000;

    private Limits() {}

    public static int value(int value) {
        if (value < 1 || value > MAX_VALUE) throw new IllegalArgumentException("Channel value out of range");
        return value;
    }

    public static String channel(String name) {
        if (name == null || !name.matches("[a-z0-9_]{1,64}")) {
            throw new IllegalArgumentException("Invalid channel name");
        }
        return name;
    }

    public static boolean inReach(double px, double py, double pz, int x, int y, int z, int reach) {
        double dx = px - (x + 0.5), dy = py - (y + 0.5), dz = pz - (z + 0.5);
        return dx * dx + dy * dy + dz * dz <= (double) reach * reach;
    }
}

package dev.structureterminal.projection;

/** Portal camera coordinates use Minecraft yaw order: south, west, north, east. */
public final class SceneView {

    public static final int RANGE = 16, LIVE_TICKS = 5, SNAPSHOT_TICKS = 200, SIDE = 33, VOLUME = SIDE * SIDE * SIDE;

    public static int fx(int turn) {
        return turn == 1 ? -1 : turn == 3 ? 1 : 0;
    }

    public static int fz(int turn) {
        return turn == 0 ? 1 : turn == 2 ? -1 : 0;
    }

    public static int index(int x, int y, int z) {
        return ((y + 16) * SIDE + z + 16) * SIDE + x + 16;
    }

    public static boolean inRange(double dx, double dy, double dz) {
        return dx * dx + dy * dy + dz * dz <= RANGE * RANGE;
    }

    public static double centerY(int y, int height) {
        return y + 1.02 + height * .5;
    }

    /** Input is the vector from the eye to the source centre. Output is a unit vector in destination world axes. */
    public static double[] direction(int entry, int exit, double dx, double dy, double dz, boolean snapshot) {
        double ax = fx(entry), az = fz(entry), bx = fx(exit), bz = fz(exit);
        if (snapshot) return new double[] { bx, 0, bz };
        double normal = dx * ax + dz * az;
        if (normal < 0) {
            ax = -ax;
            az = -az;
        }
        double right = -dx * az + dz * ax, forward = Math.abs(normal);
        double len = Math.sqrt(right * right + dy * dy + forward * forward);
        if (len < 1e-6) return new double[] { bx, 0, bz };
        return new double[] { (-right * bz + forward * bx) / len, dy / len, (right * bx + forward * bz) / len };
    }
}

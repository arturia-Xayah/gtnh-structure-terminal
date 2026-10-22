package dev.aestoragevault.core;

/** World-axis geometry shared by structural checks, connected textures and tests. */
public final class VaultGeometry {

    private static final int[][] NORMALS = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 },
        { 1, 0, 0 } };

    private VaultGeometry() {}

    public static int normal(int side, int axis) {
        return NORMALS[side >= 0 && side < 6 ? side : 2][axis];
    }

    public static boolean shell(int x, int y, int z) {
        return Math.abs(x) <= 1 && Math.abs(y) <= 1 && Math.abs(z) <= 1 && (x != 0 || y != 0 || z != 0);
    }

    /** Vanilla block-face UV orientation, row-major in a complete 3x3 face. */
    public static int tile(int x, int y, int z, int side) {
        int u, v;
        switch (side) {
            case 0:
                u = x + 1;
                v = 1 - z;
                break;
            case 1:
                u = x + 1;
                v = z + 1;
                break;
            case 2:
                u = 1 - x;
                v = 1 - y;
                break;
            case 3:
                u = x + 1;
                v = 1 - y;
                break;
            case 4:
                u = z + 1;
                v = 1 - y;
                break;
            case 5:
                u = 1 - z;
                v = 1 - y;
                break;
            default:
                return 4;
        }
        return u >= 0 && u < 3 && v >= 0 && v < 3 ? v * 3 + u : 4;
    }
}

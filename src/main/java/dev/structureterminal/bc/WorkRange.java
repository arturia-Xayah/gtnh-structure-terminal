package dev.structureterminal.bc;

/** Inclusive BC bounds. Signed widths use the operator's right when looking in the machine's forward direction. */
public final class WorkRange {

    public final int x1, y1, z1, x2, y2, z2;

    private WorkRange(int x, int y, int z, int ex, int ey, int ez) {
        x1 = Math.min(x, ex);
        y1 = Math.min(y, ey);
        z1 = Math.min(z, ez);
        x2 = Math.max(x, ex);
        y2 = Math.max(y, ey);
        z2 = Math.max(z, ez);
    }

    public static WorkRange from(int x, int y, int z, int face, int w, int h, int d, int limit, int worldHeight) {
        if (w == 0 || h == 0 || Math.abs((long) w) > limit || Math.abs((long) h) > limit || d < 1 || d > limit)
            throw new IllegalArgumentException("bc.range.dimensions");
        if (face < 2 || face > 5) throw new IllegalArgumentException("bc.range.rotated");
        int fx = face == 5 ? 1 : face == 4 ? -1 : 0, fz = face == 3 ? 1 : face == 2 ? -1 : 0;
        int lateral = w - Integer.signum(w);
        long ex = (long) x + (long) fx * d - (long) fz * lateral, ez = (long) z + (long) fz * d + (long) fx * lateral;
        long ey = (long) y + h - Integer.signum(h);
        if (ey < 0 || ey >= worldHeight || Math.abs(ex) >= 30000000L || Math.abs(ez) >= 30000000L)
            throw new IllegalArgumentException("bc.range.outside");
        return new WorkRange(x + fx, y, z + fz, (int) ex, (int) ey, (int) ez);
    }

    public int[] coords() {
        return new int[] { x1, y1, z1, x2, y2, z2 };
    }
}

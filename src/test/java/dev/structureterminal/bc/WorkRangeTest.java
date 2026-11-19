package dev.structureterminal.bc;

import static org.junit.Assert.*;

import org.junit.Test;

public class WorkRangeTest {

    @Test
    public void signedExtentsStartOnFrontFaceInEveryOrientation() {
        // North, south, west, east. Test against explicit right-hand vectors.
        int[][] axes = { { 0, -1, 1, 0 }, { 0, 1, -1, 0 }, { -1, 0, 0, -1 }, { 1, 0, 0, 1 } };
        for (int i = 0; i < 4; i++) for (int w : new int[] { -64, -1, 1, 64 })
            for (int h : new int[] { -64, -1, 1, 64 }) for (int d : new int[] { 1, 64 }) {
                WorkRange r = WorkRange.from(100, 100, 100, i + 2, w, h, d, 64, 256);
                int[] a = axes[i];
                int nx = 100 + a[0], nz = 100 + a[1],
                    ex = 100 + a[0] * d + a[2] * (Math.abs(w) - 1) * Integer.signum(w);
                int ez = 100 + a[1] * d + a[3] * (Math.abs(w) - 1) * Integer.signum(w),
                    ey = 100 + h - Integer.signum(h);
                assertArrayEquals(
                    new int[] { Math.min(nx, ex), Math.min(100, ey), Math.min(nz, ez), Math.max(nx, ex),
                        Math.max(100, ey), Math.max(nz, ez) },
                    r.coords());
                assertEquals(Math.abs(w * h) * d, (r.x2 - r.x1 + 1) * (r.y2 - r.y1 + 1) * (r.z2 - r.z1 + 1));
                assertFalse(100 >= r.x1 && 100 <= r.x2 && 100 >= r.z1 && 100 <= r.z2);
            }
    }

    @Test
    public void rejectsInvalidSizesAndBuildHeight() {
        for (int[] v : new int[][] { { 1, 1, -1, 10 }, { 1, 1, 0, 10 }, { 1, 0, 1, 10 }, { 0, 1, 1, 10 },
            { -65, 1, 1, 10 }, { 1, 1, 65, 10 }, { Integer.MIN_VALUE, 1, 1, 10 }, { 1, -2, 1, 0 }, { 1, 2, 1, 255 } })
            assertThrows(
                IllegalArgumentException.class,
                () -> WorkRange.from(0, v[3], 0, 3, v[0], v[1], v[2], 64, 256));
    }
}

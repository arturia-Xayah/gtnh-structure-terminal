package dev.structureterminal.projection;

import static org.junit.Assert.*;

import org.junit.Test;

public class SceneViewTest {

    @Test
    public void mapsBothEntrySidesToEachExitAndSnapshotNeverTurns() {
        for (int a = 0; a < 4; a++) for (int b = 0; b < 4; b++) for (int side : new int[] { -1, 1 }) {
            double ax = side * SceneView.fx(a), az = side * SceneView.fz(a), bx = SceneView.fx(b), bz = SceneView.fz(b);
            assertArrayEquals(new double[] { bx, 0, bz }, SceneView.direction(a, b, ax * 5, 0, az * 5, false), 1e-9);
            assertArrayEquals(new double[] { bx, 0, bz }, SceneView.direction(a, b, -az, -2, ax, true), 1e-9);
            double[] angled = SceneView.direction(a, b, ax * 4 - az, -2, az * 4 + ax, false);
            assertEquals(4 / Math.sqrt(21), angled[0] * bx + angled[2] * bz, 1e-9);
            assertEquals(1 / Math.sqrt(21), -angled[0] * bz + angled[2] * bx, 1e-9);
            assertEquals(-2 / Math.sqrt(21), angled[1], 1e-9);
        }
    }

    @Test
    public void distanceIsSphericalAndIncludesBoundary() {
        assertTrue(SceneView.inRange(0, 16, 0));
        assertFalse(SceneView.inRange(0, 16.001, 0));
        assertFalse(SceneView.inRange(12, 0, 12));
        assertTrue(SceneView.inRange(8, 8, 8));
        assertEquals(0, SceneView.index(-16, -16, -16));
        assertEquals(SceneView.VOLUME - 1, SceneView.index(16, 16, 16));
    }
}

package dev.structureterminal.projection;

import static org.junit.Assert.*;

import org.junit.Test;

public class GateSurfaceTest {

    private static GateSurface gate(Projection.Shape kind, int x, int y, int z, int turn, int size) {
        return new GateSurface(new Projection(kind, size, size, 16, new int[] { 2, 2, 4, 10, 4, 2, 2 }), x, y, z, turn);
    }

    @Test
    public void apertureAndFastCrossing() {
        GateSurface ring = gate(Projection.Shape.RING, 0, 0, 0, 0, 12);
        assertFalse(ring.touches(.5, 6, .5, .5, 6, .5, .3, 1.8));
        assertTrue(ring.touches(5.5, 6, -4, 5.5, 6, 5, .3, 1.8));
        assertFalse(ring.touches(6, 12, -4, 6, 12, 5, .3, 1.8));
        assertTrue(gate(Projection.Shape.HALO, 0, 0, 0, 1, 12).touches(-2, 6, .5, 2, 6, .5, .3, 1.8));
        GateSurface custom = gate(Projection.Shape.CUSTOM, 0, 0, 0, 0, 10);
        assertFalse(custom.touches(4.9, 1.03, -1, 4.9, 1.03, 1, .3, 1));
        assertTrue(custom.touches(4.9, 5.5, -1, 4.9, 5.5, 1, .3, 1));
    }

    @Test
    public void diagonalCrossingUsesOnlyTimeInPlane() {
        GateSurface gate = gate(Projection.Shape.RECTANGLE, 0, 0, 0, 0, 2);
        assertFalse(gate.touches(-5, 1, -1, 1, 1, 5, .1, .2));
        assertTrue(gate.touches(-5, 1, -5, 5, 2, 5, .1, .2));
        assertFalse(gate.touches(-4, 2, .5, 1, 8, .5, .1, .2));
    }

    @Test
    public void nestedAndPerpendicularGates() {
        GateSurface ring = gate(Projection.Shape.RING, 0, 0, 0, 0, 12);
        assertFalse(ring.overlaps(gate(Projection.Shape.DISC, 0, 5, 0, 0, 2)));
        assertFalse(ring.overlaps(gate(Projection.Shape.DISC, 0, 5, 0, 1, 2)));
        assertTrue(ring.overlaps(gate(Projection.Shape.DISC, 5, 5, 0, 0, 2)));
        assertTrue(ring.overlaps(gate(Projection.Shape.DISC, 5, 5, 0, 1, 2)));
        assertFalse(ring.overlaps(gate(Projection.Shape.DISC, 5, 5, 1, 0, 2)));
        assertFalse(ring.overlaps(gate(Projection.Shape.DISC, 0, 16, 0, 1, 2)));
    }
}

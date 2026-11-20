package dev.structureterminal.projection;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class ProjectionTest {

    @Test
    public void shapesStartAtEmitterAndMeetAtCrown() {
        for (Projection.Shape shape : Projection.Shape.values()) {
            Projection g = new Projection(shape, 6, 4, 8, Projection.DEFAULT.profile());
            Projection.Path path = new Projection.Path(g);
            assertEquals(0, path.x[0], 1e-9);
            assertEquals(0, path.y[0], 1e-9);
            int last = path.x.length - 1;
            assertEquals(0, path.x[last], 1e-9);
            assertEquals(g.height, path.y[last], 1e-9);
            assertEquals(1, path.along[last], 1e-9);
            for (int i = 1; i <= last; i++) assertTrue(path.along[i] > path.along[i - 1]);
        }
    }

    private static List<double[]> quads(Projection gate, int tick) {
        List<double[]> quads = new ArrayList<>();
        gate.draw(
            new Projection.Path(gate),
            tick,
            (a, b, c, d, e, f, g, h, colour) -> quads.add(new double[] { a, b, c, d, e, f, g, h }));
        return quads;
    }

    @Test
    public void twoFrontsMeetBeforeMembraneOpens() {
        Projection g = Projection.DEFAULT;
        assertTrue(quads(g, 0).isEmpty());
        for (double[] face : quads(g, 12)) for (int i = 1; i < 8; i += 2) assertTrue(face[i] < g.height * .5);
        assertTrue(quads(g, 44).size() > quads(g, 29).size());
    }

    @Test
    public void bothRingStylesLeaveAnOpenCenter() {
        for (Projection.Shape shape : new Projection.Shape[] { Projection.Shape.RING, Projection.Shape.HALO }) {
            Projection g = new Projection(shape, 6, 11, 9, Projection.DEFAULT.profile());
            assertEquals(6, g.height);
            for (double[] face : quads(g, 70))
                for (int i = 0; i < 8; i += 2) assertTrue(Math.hypot(face[i], face[i + 1] - 3) > 1.9);
        }
    }

    @Test
    public void customProfileCopiesAndClampsInput() {
        int[] profile = { 0, 3, 6, 10, 5, 9, 99 };
        Projection g = new Projection(Projection.Shape.CUSTOM, 100, -4, 99, profile);
        profile[1] = 8;
        assertEquals(12, g.width);
        assertEquals(2, g.height);
        assertEquals(23, g.innerQuarters);
        assertArrayEquals(new int[] { 1, 3, 6, 10, 5, 9, 10 }, g.profile());
    }

    @Test
    public void radiusAndTintAreIndependent() {
        Projection g = new Projection(Projection.Shape.RING, 7, 2, 8, Projection.DEFAULT.profile())
            .recolored(0x8733ff, 0xffb400);
        assertEquals(3.5, g.radius(), 0);
        assertEquals(2, g.innerRadius(), 0);
        Projection small = g.resized(g.shape, 3, 9, g.innerQuarters, g.profile());
        assertEquals(1.25, small.innerRadius(), 0);
        assertEquals(1.5, small.radius(), 0);
        assertEquals(g.primary, small.primary);
        assertEquals(g.secondary, small.secondary);
        java.util.Set<Integer> colors = new java.util.HashSet<>();
        small.draw(new Projection.Path(small), 60, (a, b, c, d, e, f, h, i, col) -> colors.add(col >>> 8));
        assertTrue(colors.contains(g.primary));
        assertTrue(colors.contains(g.secondary));
    }
}

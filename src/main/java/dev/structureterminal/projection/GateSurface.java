package dev.structureterminal.projection;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable world-space portal aperture. Coordinates in the outline are horizontal distance and world Y. */
final class GateSurface {

    final boolean acrossX;
    final double plane;
    final Path2D.Double outline = new Path2D.Double(Path2D.WIND_EVEN_ODD);
    final Rectangle2D bounds;
    private final double[] vertices;

    GateSurface(Projection g, int x, int y, int z, int turn) {
        acrossX = (turn & 1) == 0;
        plane = (acrossX ? z : x) + .5;
        double u = (acrossX ? x : z) + .5, bottom = y + 1.02;
        Projection.Path p = new Projection.Path(g);
        contour(p, u, bottom, 1, g.height);
        if (g.shape == Projection.Shape.RING) contour(p, u, bottom, g.innerRadius() / g.radius(), g.height);
        bounds = outline.getBounds2D();
        // Cache edge endpoints for diagonal sweeps. NaN separates the outer contour from the hole.
        double[] buffer = new double[(p.x.length * 2 + 2) * 2 * (g.shape == Projection.Shape.RING ? 2 : 1)];
        double[] coords = new double[6];
        int count = 0;
        for (PathIterator it = outline.getPathIterator(null); !it.isDone(); it.next()) {
            int kind = it.currentSegment(coords);
            buffer[count++] = kind == PathIterator.SEG_CLOSE ? Double.NaN : coords[0];
            buffer[count++] = coords[1];
        }
        vertices = java.util.Arrays.copyOf(buffer, count);
    }

    private void contour(Projection.Path p, double u, double bottom, double factor, double height) {
        double y = bottom + (1 - factor) * height / 2;
        outline.moveTo(u, y);
        for (int i = 1; i < p.x.length; i++) outline.lineTo(u + p.x[i] * factor, y + p.y[i] * factor);
        for (int i = p.x.length - 2; i >= 0; i--) outline.lineTo(u - p.x[i] * factor, y + p.y[i] * factor);
        outline.closePath();
    }

    /** Sweep the player's box; first clip the movement interval to contact with the portal plane. */
    boolean touches(double x, double y, double z, double nx, double ny, double nz, double half, double height) {
        double normal = acrossX ? z : x, next = acrossX ? nz : nx;
        double lo = 0, hi = 1, d = next - normal;
        if (d == 0) {
            if (Math.abs(normal - plane) > half) return false;
        } else {
            double a = (plane - half - normal) / d, b = (plane + half - normal) / d;
            lo = Math.max(0, Math.min(a, b));
            hi = Math.min(1, Math.max(a, b));
            if (lo > hi) return false;
        }
        double start = acrossX ? x : z, du = (acrossX ? nx : nz) - start;
        double u = start + du * lo, v = y + (ny - y) * lo;
        double end = start + du * hi, top = y + (ny - y) * hi;
        double left = Math.min(u, end) - half, low = Math.min(v, top);
        double w = Math.abs(end - u) + 2 * half, h = Math.abs(top - v) + height;
        if (!bounds.intersects(left, low, w, h) || !outline.intersects(left, low, w, h)) return false;
        if (end == u || top == v) return true;
        if (outline.intersects(u - half, v, 2 * half, height) || outline.intersects(end - half, top, 2 * half, height))
            return true;
        // The swept box is a hexagon: clip each contour edge against the horizontal, vertical and oblique strips.
        double dx = end - u, dy = top - v, diagonal = Math.abs(dy) * half + Math.abs(dx) * height * .5;
        double centerY = v + height * .5;
        for (int i = 2; i < vertices.length; i += 2) {
            double ax = vertices[i - 2], ay = vertices[i - 1], bx = vertices[i], by = vertices[i + 1];
            if (Double.isNaN(ax) || Double.isNaN(bx)) continue;
            double from = 0, to = 1;
            for (int axis = 0; axis < 3; axis++) {
                double origin, delta, min, max;
                if (axis == 0) {
                    origin = ax;
                    delta = bx - ax;
                    min = left;
                    max = left + w;
                } else if (axis == 1) {
                    origin = ay;
                    delta = by - ay;
                    min = low;
                    max = low + h;
                } else {
                    origin = dx * (ay - centerY) - dy * (ax - u);
                    delta = dx * (by - ay) - dy * (bx - ax);
                    min = -diagonal;
                    max = diagonal;
                }
                if (delta == 0) {
                    if (origin < min || origin > max) {
                        to = -1;
                        break;
                    }
                } else {
                    double a = (min - origin) / delta, b = (max - origin) / delta;
                    from = Math.max(from, Math.min(a, b));
                    to = Math.min(to, Math.max(a, b));
                    if (from > to) break;
                }
            }
            if (from <= to) return true;
        }
        return false;
    }

    boolean overlaps(GateSurface other) {
        if (acrossX == other.acrossX) {
            if (plane != other.plane || !bounds.intersects(other.bounds)) return false;
            Area common = new Area(outline);
            common.intersect(new Area(other.outline));
            return !common.isEmpty();
        }
        if (other.plane <= bounds.getMinX() || other.plane >= bounds.getMaxX()
            || plane <= other.bounds.getMinX()
            || plane >= other.bounds.getMaxX()) return false;
        // Both vertical planes share a line. Its inside/outside state changes only at a contour crossing.
        List<Double> cuts = new ArrayList<>();
        crossings(other.plane, cuts);
        other.crossings(plane, cuts);
        Collections.sort(cuts);
        for (int i = 1; i < cuts.size(); i++) {
            if (cuts.get(i)
                .doubleValue()
                == cuts.get(i - 1)
                    .doubleValue())
                continue;
            double y = (cuts.get(i - 1) + cuts.get(i)) * .5;
            if (outline.contains(other.plane, y) && other.outline.contains(plane, y)) return true;
        }
        return false;
    }

    private void crossings(double u, List<Double> cuts) {
        double[] v = new double[6];
        double x = 0, y = 0;
        for (PathIterator it = outline.getPathIterator(null); !it.isDone(); it.next()) {
            int segment = it.currentSegment(v);
            if (segment == PathIterator.SEG_CLOSE) continue; // contours explicitly return to the first vertex
            if (segment == PathIterator.SEG_LINETO && x != v[0] && u >= Math.min(x, v[0]) && u <= Math.max(x, v[0]))
                cuts.add(y + (v[1] - y) * (u - x) / (v[0] - x));
            x = v[0];
            y = v[1];
        }
    }
}

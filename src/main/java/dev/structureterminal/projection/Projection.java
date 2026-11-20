package dev.structureterminal.projection;

/** Geometry in gate-local blocks. The emitter is at (0,0), positive Y is up. */
public final class Projection {

    public enum Shape {
        RECTANGLE,
        DISC,
        RING,
        CUSTOM,
        HALO
    }

    public static final int ROWS = 7, MIN_SIZE = 2, MAX_SIZE = 12;
    public final Shape shape;
    public final int width, height, innerQuarters;
    public final int primary, secondary;
    public static final int ICE_BLUE = 0x67b8ff, FROST = 0xd8faff;
    private final int[] profile;
    public static final Projection DEFAULT = new Projection(Shape.DISC, 4, 4, 6, new int[] { 4, 8, 10, 10, 9, 7, 3 });

    public Projection(Shape shape, int width, int height, int innerQuarters, int[] profile) {
        this(shape, width, height, innerQuarters, profile, ICE_BLUE, FROST);
    }

    public Projection(Shape shape, int width, int height, int innerQuarters, int[] profile, int primary,
        int secondary) {
        this.shape = shape;
        this.width = clamp(width, MIN_SIZE, MAX_SIZE);
        this.height = isRound(shape) ? this.width : clamp(height, MIN_SIZE, MAX_SIZE);
        this.innerQuarters = clamp(innerQuarters, 1, maxInnerQuarters(this.width));
        this.primary = primary & 0xffffff;
        this.secondary = secondary & 0xffffff;
        if (profile.length != ROWS) throw new IllegalArgumentException("profile length");
        this.profile = profile.clone();
        for (int i = 0; i < ROWS; i++) this.profile[i] = clamp(this.profile[i], 1, 10);
    }

    public static int maxInnerQuarters(int diameter) {
        return diameter * 2 - 1;
    }

    public double radius() {
        return width * .5;
    }

    public double innerRadius() {
        return innerQuarters * .25;
    }

    public Projection resized(Shape type, int w, int h, int inner, int[] outline) {
        return new Projection(type, w, h, inner, outline, primary, secondary);
    }

    public Projection recolored(int base, int accent) {
        return new Projection(shape, width, height, innerQuarters, profile, base, accent);
    }

    public static boolean isRound(Shape shape) {
        return shape == Shape.DISC || shape == Shape.RING || shape == Shape.HALO;
    }

    public int[] profile() {
        return profile.clone();
    }

    private static int clamp(int n, int min, int max) {
        return Math.max(min, Math.min(max, n));
    }

    public static final class Path {

        public final double[] x, y, along;

        public Path(Projection gate) {
            boolean round = isRound(gate.shape);
            int n = round ? 65 : gate.shape == Shape.CUSTOM ? ROWS + 2 : 4;
            x = new double[n];
            y = new double[n];
            along = new double[n];
            double half = gate.width * .5;
            if (round) {
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * i / (n - 1);
                    x[i] = half * Math.sin(a);
                    y[i] = half * (1 - Math.cos(a));
                }
            } else {
                for (int i = 1; i < n - 1; i++) {
                    x[i] = half * (gate.shape == Shape.CUSTOM ? gate.profile[i - 1] * .1 : 1);
                    y[i] = gate.height * (i - 1.0) / (n - 3);
                }
                y[n - 1] = gate.height;
            }
            for (int i = 1; i < n; i++) along[i] = along[i - 1] + Math.hypot(x[i] - x[i - 1], y[i] - y[i - 1]);
            double length = along[n - 1];
            for (int i = 1; i < n; i++) along[i] /= length;
        }
    }

    public interface Quad {

        void draw(double ax, double ay, double bx, double by, double cx, double cy, double dx, double dy, int rgba);
    }

    public static final int OPEN_TICKS = 44;

    private static double smooth(double value) {
        double t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }

    /** Draw from bottom centre along both edges, then reveal the membrane behind the rim. */
    public void draw(Path path, double ticks, Quad out) {
        double trace = smooth(ticks / 31), fill = smooth((ticks - 31) / (OPEN_TICKS - 31));
        if (trace <= 0) return;
        double middle = height * .5;
        boolean ring = shape == Shape.RING;
        double hole = ring ? innerRadius() / radius() : 0;
        if (fill > 0 && !ring && shape != Shape.HALO) {
            double previous = halfWidth(0);
            for (int row = 1; row <= 48; row++) {
                double t = row / 48.0, next = halfWidth(t);
                double wave = .5 + .5 * Math.cos(t * 5 - ticks * .035 + Math.sin(t * 4 + ticks * .012));
                double reveal = smooth((fill * 1.20 - t) * 5);
                double crest = fill < 1 ? 1 - Math.min(1, Math.abs(t - fill) * 7) : 0;
                int rgba = shade(
                    primary,
                    .76 + .16 * wave + .08 * crest,
                    (int) ((36 + 54 * wave + 62 * crest) * reveal));
                out.draw(
                    -previous,
                    height * (row - 1) / 48.0,
                    previous,
                    height * (row - 1) / 48.0,
                    next,
                    height * t,
                    -next,
                    height * t,
                    rgba);
                previous = next;
            }
        }
        if (fill > 0 && !ring && shape != Shape.HALO) drawCurrents(ticks, fill, out);
        if (fill > 0 && ring) {
            int bands = 8;
            for (int j = 0; j < bands; j++) {
                double inside = hole + (1 - hole) * j / bands, outside = hole + (1 - hole) * (j + 1) / bands;
                for (int i = 1; i < path.x.length; i++) {
                    double y = (path.y[i] + path.y[i - 1]) * .5;
                    double wave = .5 + .5 * Math.cos(y * 2.5 + j * .65 - ticks * .085);
                    int rgba = shade(primary, .70 + .24 * wave, (int) ((36 + wave * 31 + j * 2) * fill));
                    strip(path, i, 1, inside, outside, middle, rgba, out);
                }
            }
        }
        rim(path, trace, 1, middle, out);
        if (ring) rim(path, trace, hole, middle, out);
    }

    // Sample a handful of curved filaments across the membrane, in the same local coordinates as its silhouette.
    private void drawCurrents(double ticks, double fill, Quad out) {
        for (int line = 0; line < 6; line++) {
            double previousX = 0, previousY = 0, previousBand = 0;
            double anchor = .20 + line * .11;
            double reveal = smooth((fill * 1.2 - anchor) * 5);
            for (int sample = 0; sample <= 24; sample++) {
                double u = sample / 12.0 - 1;
                double phase = u * 3.0 + ticks * .032 + line * .21;
                double t = anchor + .071 * Math.sin(phase)
                    + .055 * Math.sin(u * 4.4 - ticks * .019 + line * .14)
                    + .10 * u;
                double x = u * halfWidth(t) * .965;
                double y = t * height;
                double band = height * (.0016 + .0023 * (1 - u * u));
                if (sample > 0) {
                    double glow = .18 + .82 * (1 - u * u);
                    int rgba = shade(secondary, .8 + line * .04, (int) (glow * reveal * (line % 3 == 0 ? 87 : 46)));
                    out.draw(
                        previousX,
                        previousY - previousBand,
                        x,
                        y - band,
                        x,
                        y + band,
                        previousX,
                        previousY + previousBand,
                        rgba);
                }
                previousX = x;
                previousY = y;
                previousBand = band;
            }
        }
    }

    private double halfWidth(double t) {
        if (shape == Shape.RECTANGLE) return width * .5;
        if (shape == Shape.DISC) return width * Math.sqrt(Math.max(0, t * (1 - t)));
        double index = t * (ROWS - 1);
        int row = (int) index;
        return width * .05 * (profile[row] + (profile[Math.min(row + 1, ROWS - 1)] - profile[row]) * (index - row));
    }

    private void rim(Path path, double progress, double radius, double middle, Quad out) {
        double thickness = .09 / Math.max(width, height);
        for (int i = 1; i < path.x.length && progress > path.along[i - 1]; i++) {
            double partial = Math.min(1, (progress - path.along[i - 1]) / (path.along[i] - path.along[i - 1]));
            strip(path, i, partial, radius - 3 * thickness, radius + 3 * thickness, middle, primary << 8 | 0x49, out);
            strip(path, i, partial, radius - thickness, radius + thickness, middle, primary << 8 | 0xe5, out);
            strip(
                path,
                i,
                partial,
                radius - .32 * thickness,
                radius + .32 * thickness,
                middle,
                secondary << 8 | 255,
                out);
            if (partial < 1 && radius == 1) {
                double x = path.x[i - 1] + partial * (path.x[i] - path.x[i - 1]);
                double y = path.y[i - 1] + partial * (path.y[i] - path.y[i - 1]);
                for (int sign = -1; sign <= 1; sign += 2) {
                    double px = sign * x;
                    out.draw(px - .10, y, px, y + .10, px + .10, y, px, y - .10, primary << 8 | 0x94);
                    out.draw(px - .044, y, px, y + .044, px + .044, y, px, y - .044, secondary << 8 | 255);
                }
            }
        }
    }

    private static int shade(int rgb, double factor, int alpha) {
        return (int) ((rgb >>> 16) * factor) << 24 | (int) ((rgb >>> 8 & 255) * factor) << 16
            | (int) ((rgb & 255) * factor) << 8
            | alpha;
    }

    private static void strip(Path path, int i, double fraction, double inner, double outer, double middle, int rgba,
        Quad out) {
        double x0 = path.x[i - 1], y0 = path.y[i - 1] - middle;
        double x1 = x0 + (path.x[i] - x0) * fraction, y1 = y0 + (path.y[i] - middle - y0) * fraction;
        for (int sign = -1; sign <= 1; sign += 2) out.draw(
            sign * x0 * inner,
            middle + y0 * inner,
            sign * x0 * outer,
            middle + y0 * outer,
            sign * x1 * outer,
            middle + y1 * outer,
            sign * x1 * inner,
            middle + y1 * inner,
            rgba);
    }
}

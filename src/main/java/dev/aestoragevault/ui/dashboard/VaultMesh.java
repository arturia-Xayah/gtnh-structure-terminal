package dev.aestoragevault.ui.dashboard;

/** A bounded reusable 2D triangle mesh; the game and headless preview consume the same geometry. */
public final class VaultMesh {

    public static final int MAX_VERTICES = 8192;
    private final float[] xy = new float[MAX_VERTICES * 2];
    private final int[] colors = new int[MAX_VERTICES];
    private int size;

    public int size() {
        return size;
    }

    public float x(int i) {
        return xy[i * 2];
    }

    public float y(int i) {
        return xy[i * 2 + 1];
    }

    public int color(int i) {
        return colors[i];
    }

    public void build(VaultLayout l, VaultAnimation.Frame f, boolean formed, boolean automatic) {
        size = 0;
        chamfer(0, 0, l.width, l.height, 8, 0xff8ab5d4);
        chamfer(1, 1, l.width - 2, l.height - 2, 8, 0xffe9f1f9);
        rect(12, 1, l.width - 24, 1, 0xfff6fcff);
        rect(12, 29, l.width - 24, 1, 0xffc4d8e8);
        int panelX = l.compact ? 8 : 10, panelW = l.width - panelX * 2;
        chamfer(panelX, l.panelY, panelW, l.panelHeight, 6, 0xff4c7192);
        chamfer(panelX + 1, l.panelY + 1, panelW - 2, l.panelHeight - 2, 5, 0xff10263e);
        for (int x = panelX + 12; x < panelX + panelW - 6; x += 18)
            rect(x, l.panelY + 6, 1, l.panelHeight - 12, 0x0c8fbbd8);
        for (int y = l.panelY + 12; y < l.panelY + l.panelHeight - 6; y += 18)
            rect(panelX + 6, y, panelW - 12, 1, 0x0c8fbbd8);
        if (f.moving) {
            double center = l.panelY + 5 + f.phase * (l.panelHeight - 10);
            for (int n = -6; n <= 6; n++) {
                float y = (float) center + n;
                if (y >= l.panelY + 5 && y < l.panelY + l.panelHeight - 5)
                    rect(panelX + 6, y, panelW - 12, 1, ((7 - Math.abs(n)) * 3 << 24) | 0x72ccff);
            }
        }
        ring(l.gaugeX, l.gaugeY, l.radius - 4, l.radius, -90, 360, 0xff29465f);
        ring(l.gaugeX, l.gaugeY, l.radius - 4, l.radius, -90, 360 * f.ratio, formed ? 0xff77cefa : 0xff7e96ac);
        for (int n = 0; n < 24; n++) ring(l.gaugeX, l.gaugeY, l.radius + 4, l.radius + 4.8, n * 15, 5, 0x426a90b4);
        if (f.moving) {
            ring(l.gaugeX, l.gaugeY, l.radius + 7, l.radius + 8, f.phase * 360 - 90, 24, 0xbcaeacfb);
            ring(l.gaugeX, l.gaugeY, l.radius - 1.3, l.radius, -90, Math.min(360 * f.ratio, 12), 0xc2ecfbff);
        }
        int chipX = l.width - 76;
        chamfer(chipX, 10, 64, 14, 3, formed ? 0xffd7e9f5 : 0xfff2e7d7);
        double pulse = f.moving ? .65 + .35 * Math.sin(f.phase * Math.PI * 4) : 1;
        rect(chipX + 5, 15, 4, 4, ((int) (255 * pulse) << 24) | (formed ? 0x4a9fca : 0xb58651));
        chamfer(l.statusX, l.statusY, l.statusWidth, l.compact ? 94 : 116, 4, 0xffc3d7e8);
        chamfer(l.statusX + 1, l.statusY + 1, l.statusWidth - 2, (l.compact ? 94 : 116) - 2, 3, 0xffe2ecf6);
        rect(l.statusX + 1, l.statusY + 1, 2, 16, 0xff83bde5);
        for (int slot = 0; slot < 36; slot++) {
            int x = l.slotX(slot), y = l.slotY(slot);
            rect(x - 1, y - 1, 18, 18, 0xffa9c2d5);
            rect(x, y, 16, 16, 0xffcfdeeb);
            rect(x, y, 16, 1, 0xffb8cddd);
            rect(x, y + 15, 16, 1, 0xfff3f9fe);
        }
        if (automatic && f.moving) {
            float x = (float) (l.statusX + 9 + f.phase * (l.statusWidth - 18));
            rect(x, l.statusY + (l.compact ? 46 : 51), 7, 1, 0xb67eb9df);
        }
    }

    private void vertex(double x, double y, int color) {
        if (size >= MAX_VERTICES) throw new IllegalStateException("Dashboard mesh budget exceeded");
        xy[size * 2] = (float) x;
        xy[size * 2 + 1] = (float) y;
        colors[size++] = color;
    }

    private void triangle(double ax, double ay, double bx, double by, double cx, double cy, int color) {
        vertex(ax, ay, color);
        vertex(bx, by, color);
        vertex(cx, cy, color);
    }

    public void rect(double x, double y, double w, double h, int color) {
        triangle(x, y, x + w, y, x + w, y + h, color);
        triangle(x, y, x + w, y + h, x, y + h, color);
    }

    private void chamfer(double x, double y, double w, double h, double cut, int color) {
        double[] px = { x + cut, x + w - cut, x + w, x + w, x + w - cut, x + cut, x, x };
        double[] py = { y, y, y + cut, y + h - cut, y + h, y + h, y + h - cut, y + cut };
        for (int i = 0; i < 8; i++)
            triangle(x + w / 2, y + h / 2, px[i], py[i], px[(i + 1) % 8], py[(i + 1) % 8], color);
    }

    private void ring(double x, double y, double inner, double outer, double start, double sweep, int color) {
        int segments = Math.max(1, (int) Math.ceil(Math.abs(sweep) / 3.75));
        for (int i = 0; i < segments; i++) {
            double a = Math.toRadians(start + sweep * i / segments),
                b = Math.toRadians(start + sweep * (i + 1) / segments);
            double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b);
            triangle(
                x + inner * ca,
                y + inner * sa,
                x + outer * ca,
                y + outer * sa,
                x + outer * cb,
                y + outer * sb,
                color);
            triangle(
                x + inner * ca,
                y + inner * sa,
                x + outer * cb,
                y + outer * sb,
                x + inner * cb,
                y + inner * sb,
                color);
        }
    }
}

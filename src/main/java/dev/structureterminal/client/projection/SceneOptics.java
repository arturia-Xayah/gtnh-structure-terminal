package dev.structureterminal.client.projection;

import org.lwjgl.opengl.GL11;

import dev.structureterminal.projection.Projection;

final class SceneOptics {

    private final java.nio.DoubleBuffer plane = org.lwjgl.BufferUtils.createDoubleBuffer(4);
    private final org.lwjgl.util.glu.Sphere rangeShell = new org.lwjgl.util.glu.Sphere();

    void camera(double[] direction, Projection gate) {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        double n = .045, top = n * .70, right = top * gate.width / gate.height;
        GL11.glFrustum(-right, right, -top, top, n, 16);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glRotated(-Math.toDegrees(Math.asin(Math.min(1, Math.max(-1, direction[1])))), 1, 0, 0);
        GL11.glRotated(180 + Math.toDegrees(Math.atan2(-direction[0], direction[2])), 0, 1, 0);
    }

    void clip(int exit) {
        // A depth-only sphere limits all fragments radially, including geometry in the far corners of the view.
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glColorMask(false, false, false, false);
        rangeShell.draw(16, 48, 32);
        GL11.glColorMask(true, true, true, true);
        plane.clear();
        plane.put(dev.structureterminal.projection.SceneView.fx(exit))
            .put(0)
            .put(dev.structureterminal.projection.SceneView.fz(exit))
            .put(0)
            .flip();
        GL11.glClipPlane(GL11.GL_CLIP_PLANE0, plane);
        GL11.glEnable(GL11.GL_CLIP_PLANE0);
    }

    static void aperture(Projection g, Projection.Path path, boolean back) {
        double inner = g.shape == Projection.Shape.RING ? g.innerRadius() / g.radius() : 0;
        GL11.glBegin(GL11.GL_QUADS);
        if (g.shape != Projection.Shape.RING) {
            for (int i = 1; i < path.x.length; i++) {
                vertex(g, -path.x[i - 1], path.y[i - 1], 1, back);
                vertex(g, path.x[i - 1], path.y[i - 1], 1, back);
                vertex(g, path.x[i], path.y[i], 1, back);
                vertex(g, -path.x[i], path.y[i], 1, back);
            }
        } else for (int i = 1; i < path.x.length; i++) for (int sign = -1; sign <= 1; sign += 2) {
            vertex(g, sign * path.x[i - 1], path.y[i - 1], inner, back);
            vertex(g, sign * path.x[i - 1], path.y[i - 1], 1, back);
            vertex(g, sign * path.x[i], path.y[i], 1, back);
            vertex(g, sign * path.x[i], path.y[i], inner, back);
        }
        GL11.glEnd();
    }

    private static void vertex(Projection g, double x, double y, double scale, boolean back) {
        x *= scale;
        y = g.height * .5 + (y - g.height * .5) * scale;
        GL11.glTexCoord2d(.5 + (back ? -x : x) / g.width, y / g.height);
        GL11.glVertex3d(x, y, 0);
    }
}

package dev.structureterminal.client;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

/** Resolution-independent curves, with a half-pixel alpha fringe; no low-resolution UI atlas. */
final class TerminalStyle {

    static int INK = 0xDDE6F7, MUTED = 0x9CAEC7, BLUE = 0xFF507CB9;

    static boolean dark() {
        return dev.aestoragevault.client.VaultClientPreferences.terminalDark();
    }

    static void palette() {
        INK = dark() ? 0xDDE6F7 : 0x27364D;
        MUTED = dark() ? 0x9CAEC7 : 0x526783;
        BLUE = dark() ? 0xFF507CB9 : 0xFF668FC5;
    }

    static int surface(int color) {
        if ((color >>> 24) < 200) return color;
        switch (color) {
            case 0xFFFDFEFF:
                return dark() ? 0xFF232D40 : 0xFFD0D9E7;
            case 0xFFE1EAF9:
                return dark() ? 0xFF50617C : 0xFF9FAFC7;
            case 0xFFEAF1FC:
            case 0xFFE1EDFF:
                return dark() ? 0xFF3C506F : 0xFFB5C8E2;
            case 0xFFF4F7FD:
            case 0xFFF8FAFF:
                return dark() ? 0xFF2C394E : 0xFFC4D1E2;
            case 0xFFFFFFFF:
            case 0xFFF9FBFF:
                return dark() ? 0xFF192338 : 0xFFE0E6EF;
            case 0xFFE3EBF8:
            case 0xFFD8E2F2:
                return dark() ? 0xFF425372 : 0xFFAFC1DB;
            case 0xFFD7E6FC:
            case 0xFFE0EBFC:
            case 0xFFF0F4FC:
                return dark() ? 0xFF486389 : 0xFFB6CBE6;
            default:
                return color;
        }
    }

    static void round(float x, float y, float w, float h, float r, int argb) {
        argb = surface(argb);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_TRIANGLE_FAN);
        t.setColorRGBA_I(argb & 0xffffff, argb >>> 24);
        t.addVertex(x + w / 2, y + h / 2, 0);
        for (int i = 0; i <= 48; i++) vertex(t, x, y, w, h, r, i % 48);
        t.draw();
        t.startDrawing(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= 48; i++) {
            t.setColorRGBA_I(argb & 0xffffff, argb >>> 24);
            vertex(t, x, y, w, h, r, i % 48);
            t.setColorRGBA_I(argb & 0xffffff, 0);
            vertex(t, x - .5f, y - .5f, w + 1, h + 1, r + .5f, i % 48);
        }
        t.draw();
        GL11.glPopAttrib();
    }

    private static void vertex(Tessellator t, float x, float y, float w, float h, float r, int i) {
        int corner = i / 12;
        double a = Math.toRadians(-90 + corner * 90 + (i % 12) * 90d / 11);
        float cx = corner < 2 ? x + w - r : x + r;
        float cy = corner == 0 || corner == 3 ? y + r : y + h - r;
        t.addVertex(cx + Math.cos(a) * r, cy + Math.sin(a) * r, 0);
    }

    static void triangle(float x, float y, boolean left) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_TRIANGLES);
        t.setColorOpaque_I(INK);
        t.addVertex(x + (left ? 6 : 0), y, 0);
        t.addVertex(x + (left ? 0 : 6), y + 4, 0);
        t.addVertex(x + (left ? 6 : 0), y + 8, 0);
        t.draw();
        GL11.glPopAttrib();
    }

    static void check(int x, int y, boolean selected, boolean hover) {
        round(x, y, 16, 16, 5, selected ? BLUE : hover ? 0xFFE0EBFC : 0xFFD8E2F2);
        if (!selected) {
            round(x + 1, y + 1, 14, 14, 4, 0xFFF9FBFF);
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2f);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glVertex2f(x + 4, y + 8);
        GL11.glVertex2f(x + 7, y + 11);
        GL11.glVertex2f(x + 12, y + 5);
        GL11.glEnd();
        GL11.glPopAttrib();
    }

    private TerminalStyle() {}
}

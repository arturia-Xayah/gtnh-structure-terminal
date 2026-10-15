package dev.structureterminal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/** 1.7.10 rendering adapter for GTO's LGPL Ore UI atlas and its nine-slice coordinates. */
public final class OreTheme extends Gui {

    public static final ResourceLocation ATLAS = new ResourceLocation(
        "structureterminal",
        "textures/gui/ore_styles.png");
    public static final int TEXT = 0x202020;
    public static final int SECONDARY = 0x555555;

    private OreTheme() {}

    public static void panel(int x, int y, int w, int h) {
        slice(x, y, w, h, 128, 128, 128, 128, 3, 3, 3, 5, 1);
        drawRect(x + 2, y + 2, x + w - 2, y + h - 4, 0xffc6c6c6);
    }

    public static void groove(int x, int y, int w, int h) {
        drawRect(x, y, x + w, y + h, 0xff8b8b8b);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xffbdbdbd);
    }

    public static void slot(int x, int y) {
        drawRect(x, y, x + 18, y + 18, 0xff373737);
        drawRect(x + 1, y + 1, x + 18, y + 18, 0xffffffff);
        drawRect(x + 1, y + 1, x + 17, y + 17, 0xff8b8b8b);
    }

    public static void slice(int x, int y, int w, int h, int u, int v, int sw, int sh, int left, int top, int right,
        int bottom, float tint) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(ATLAS);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(tint, tint, tint, 1);
        int[] xs = { x, x + left, x + w - right, x + w };
        int[] ys = { y, y + top, y + h - bottom, y + h };
        int[] us = { u, u + left, u + sw - right, u + sw };
        int[] vs = { v, v + top, v + sh - bottom, v + sh };
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            t.addVertexWithUV(xs[col], ys[row + 1], 0, us[col] / 256d, vs[row + 1] / 256d);
            t.addVertexWithUV(xs[col + 1], ys[row + 1], 0, us[col + 1] / 256d, vs[row + 1] / 256d);
            t.addVertexWithUV(xs[col + 1], ys[row], 0, us[col + 1] / 256d, vs[row] / 256d);
            t.addVertexWithUV(xs[col], ys[row], 0, us[col] / 256d, vs[row] / 256d);
        }
        t.draw();
        GL11.glColor4f(1, 1, 1, 1);
    }

    public static final class Button extends GuiButton {

        public int variant;

        public Button(int id, int x, int y, int w, String text) {
            super(id, x, y, w, 14, text);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            if (!visible) return;
            boolean hover = mouseX >= xPosition && mouseX < xPosition + width
                && mouseY >= yPosition
                && mouseY < yPosition + height;
            int u = !enabled ? 10
                : variant == 1 ? (hover ? 31 : 26) : variant == 2 ? (hover ? 41 : 36) : (hover ? 5 : 0);
            slice(xPosition, yPosition, width, height, u, 0, 5, 7, 2, 2, 2, 4, variant == 0 ? 0.75f : 1);
            int color = !enabled ? 0x666666 : variant == 0 ? 0x222222 : 0xffffff;
            mc.fontRenderer.drawString(
                displayString,
                xPosition + (width - mc.fontRenderer.getStringWidth(displayString)) / 2,
                yPosition + 2,
                color);
        }
    }
}

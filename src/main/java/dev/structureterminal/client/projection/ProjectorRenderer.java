package dev.structureterminal.client.projection;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;

import dev.structureterminal.projection.Projection;
import dev.structureterminal.projection.ProjectorTile;

public final class ProjectorRenderer extends TileEntitySpecialRenderer {

    private static final Projection.Quad BATCH = (ax, ay, bx, by, cx, cy, dx, dy, rgba) -> {
        Tessellator t = Tessellator.instance;
        t.setColorRGBA(rgba >>> 24, rgba >>> 16 & 255, rgba >>> 8 & 255, rgba & 255);
        t.addVertex(ax, ay, 0);
        t.addVertex(bx, by, 0);
        t.addVertex(cx, cy, 0);
        t.addVertex(dx, dy, 0);
    };

    static void draw(Projection gate, Projection.Path path, double age) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            Tessellator.instance.startDrawingQuads();
            gate.draw(path, age, BATCH);
            Tessellator.instance.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void stationLabel(String text, Projection gate, boolean back) {
        net.minecraft.client.gui.FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int pixels = font.getStringWidth(text);
        if (pixels <= 0) return;
        double scale = Math.min(.039, gate.width * .95 / pixels);
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glTranslated(0, gate.height + .10, 0);
            if (back) GL11.glRotatef(180, 0, 1, 0);
            GL11.glScaled(scale, -scale, scale);
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            font.drawStringWithShadow(text, -pixels / 2, -font.FONT_HEIGHT, 0xffffff);
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partial) {
        ProjectorTile t = (ProjectorTile) tile;
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        ProjectorScreen editor = screen instanceof ProjectorScreen ? (ProjectorScreen) screen : null;
        boolean draft = editor != null && editor.inWorld && editor.menu.tile == t;
        if (!draft && !t.open) return;
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x + .5, y + 1.02, z + .5);
            GL11.glRotatef(90 * t.getBlockMetadata(), 0, 1, 0);
            double age = t.getWorldObj()
                .getTotalWorldTime() - (draft ? editor.started : t.started)
                + partial;
            Projection gate = draft ? editor.draft : t.gate;
            int turn = t.getBlockMetadata() & 3;
            double dx = net.minecraft.client.renderer.ActiveRenderInfo.objectX - x - .5;
            double dz = net.minecraft.client.renderer.ActiveRenderInfo.objectZ - z - .5;
            double cameraZ = turn == 0 ? dz : turn == 1 ? dx : turn == 2 ? -dz : -dx;
            if (!draft && age >= Projection.OPEN_TICKS) SceneClient.INSTANCE.visit(t, partial, cameraZ < 0);
            draw(gate, draft ? editor.path : t.path, Math.max(0, age));
            if (age >= Projection.OPEN_TICKS && !t.destinationName.isEmpty()
                && (draft ? editor.previewLabel() : t.showDestination)) {
                stationLabel(t.destinationName, gate, cameraZ < 0);
            }
        } finally {
            GL11.glPopMatrix();
        }
    }
}

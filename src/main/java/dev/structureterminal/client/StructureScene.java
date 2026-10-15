package dev.structureterminal.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import org.lwjgl.opengl.GL11;

import dev.structureterminal.TerminalSettings;
import dev.structureterminal.bridge.StructureScanner.Cell;
import dev.structureterminal.bridge.StructureScanner.Scan;

/** Textured orthographic scene. It uses real installed block icons, not a hard-coded GTO palette. */
public final class StructureScene {

    private static final int[][] NORMALS = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 },
        { 1, 0, 0 } };
    private static final int[][][] VERTICES = { { { 0, 0, 0 }, { 1, 0, 0 }, { 1, 0, 1 }, { 0, 0, 1 } },
        { { 0, 1, 1 }, { 1, 1, 1 }, { 1, 1, 0 }, { 0, 1, 0 } }, { { 1, 0, 0 }, { 0, 0, 0 }, { 0, 1, 0 }, { 1, 1, 0 } },
        { { 0, 0, 1 }, { 1, 0, 1 }, { 1, 1, 1 }, { 0, 1, 1 } }, { { 0, 0, 0 }, { 0, 0, 1 }, { 0, 1, 1 }, { 0, 1, 0 } },
        { { 1, 0, 1 }, { 1, 0, 0 }, { 1, 1, 0 }, { 1, 1, 1 } } };
    public double yaw = 45, pitch = 25, zoom = 10, panX, panY;
    public String highlighted;
    public boolean highlight;
    public int layer = Integer.MIN_VALUE;
    public int rendered;
    private double centerX, centerY, centerZ, screenX, screenY;
    private final List<Face> faces = new ArrayList<Face>();
    private long lastMeshKey = Long.MIN_VALUE;

    private static final class Face {

        Cell cell;
        IIcon icon;
        int side;
        double depth;
        double[][] points = new double[4][];
    }

    public void fit(Scan scan, int width, int height) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = minX, maxZ = maxX;
        for (Cell c : scan.cells) {
            minX = Math.min(minX, c.x);
            maxX = Math.max(maxX, c.x);
            minZ = Math.min(minZ, c.z);
            maxZ = Math.max(maxZ, c.z);
        }
        centerX = (minX + maxX + 1) / 2d;
        centerY = (scan.minY + scan.maxY + 1) / 2d;
        centerZ = (minZ + maxZ + 1) / 2d;
        double horizontal = Math.max(1, maxX - minX + maxZ - minZ + 2),
            vertical = Math.max(1, scan.maxY - scan.minY + 1 + horizontal / 2);
        zoom = Math.max(0.25, Math.min(24, Math.min(width / horizontal, height / vertical) * 0.8));
        panX = panY = 0;
    }

    public void draw(Scan scan, TerminalSettings settings, int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        screenX = x + width / 2d + panX;
        screenY = y + height / 2d + panY;
        long meshKey = System.identityHashCode(scan) * 31L + settings.choices.hashCode();
        meshKey = meshKey * 31 + layer;
        meshKey = meshKey * 31 + Double.doubleToLongBits(yaw);
        meshKey = meshKey * 31 + Double.doubleToLongBits(pitch);
        meshKey = meshKey * 31 + Double.doubleToLongBits(zoom);
        meshKey = meshKey * 31 + Double.doubleToLongBits(screenX);
        meshKey = meshKey * 31 + Double.doubleToLongBits(screenY);
        meshKey = meshKey * 31 + width;
        meshKey = meshKey * 31 + height;
        if (meshKey != lastMeshKey) {
            lastMeshKey = meshKey;
            faces.clear();
            rendered = 0;
            for (int cellIndex = 0; cellIndex < scan.cells.size() + 1; cellIndex++) {
                Cell cell = cellIndex == scan.cells.size() ? scan.controller : scan.cells.get(cellIndex);
                if (cell == null) continue;
                if (cell.air) continue;
                if (layer != Integer.MIN_VALUE && cell.y != layer) continue;
                if (rendered >= 24000) break;
                ItemStack stack = cell.selected(settings);
                if (stack == null && !cell.candidates.isEmpty()) stack = cell.candidates.get(0);
                Block block = stack == null ? Blocks.stained_glass : Block.getBlockFromItem(stack.getItem());
                if (block == null || block == Blocks.air) block = Blocks.stained_glass;
                int meta = stack == null ? 7 : stack.getItemDamage();
                double[] mid = project(cell.x + 0.5, cell.y + 0.5, cell.z + 0.5);
                if (mid[0] < x - zoom * 2 || mid[0] > x + width + zoom * 2
                    || mid[1] < y - zoom * 2
                    || mid[1] > y + height + zoom * 2) continue;
                rendered++;
                for (int side = 0; side < 6; side++) {
                    int[] n = NORMALS[side];
                    if (transform(n[0], n[1], n[2])[2] <= 0.001) continue;
                    Face face = new Face();
                    face.cell = cell;
                    face.side = side;
                    try {
                        face.icon = block.getIcon(side, meta);
                    } catch (RuntimeException ignored) {}
                    if (face.icon == null) face.icon = Blocks.stone.getIcon(side, 0);
                    for (int i = 0; i < 4; i++) {
                        int[] v = VERTICES[side][i];
                        face.points[i] = project(cell.x + v[0], cell.y + v[1], cell.z + v[2]);
                        face.depth += face.points[i][2] / 4;
                    }
                    faces.add(face);
                }
            }
            Collections.sort(faces, (a, b) -> Double.compare(a.depth, b.depth));
        }
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        mc.getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
        int scale = mc.displayWidth / Math.max(
            1,
            new net.minecraft.client.gui.ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaledWidth());
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, mc.displayHeight - (y + height) * scale, width * scale, height * scale);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for (Face face : faces) {
            float shade = face.side == 1 ? 1 : face.side == 4 || face.side == 5 ? 0.7f : 0.85f;
            boolean selected = highlight && face.cell.group.equals(highlighted);
            t.setColorRGBA_F(selected ? 0.4f : shade, shade, selected ? 0.4f : shade, 1);
            IIcon icon = face.icon;
            double[][] uv = { { icon.getMinU(), icon.getMaxV() }, { icon.getMaxU(), icon.getMaxV() },
                { icon.getMaxU(), icon.getMinV() }, { icon.getMinU(), icon.getMinV() } };
            for (int i = 0; i < 4; i++) t.addVertexWithUV(face.points[i][0], face.points[i][1], 0, uv[i][0], uv[i][1]);
        }
        t.draw();
        GL11.glPopAttrib();
        GL11.glColor4f(1, 1, 1, 1);
    }

    public Cell pick(int x, int y) {
        for (int i = faces.size() - 1; i >= 0; i--) {
            Face f = faces.get(i);
            boolean inside = false;
            for (int a = 0, b = 3; a < 4; b = a++) {
                double[] p = f.points[a], q = f.points[b];
                if ((p[1] > y) != (q[1] > y) && x < (q[0] - p[0]) * (y - p[1]) / (q[1] - p[1]) + p[0]) inside = !inside;
            }
            if (inside) return f.cell;
        }
        return null;
    }

    private double[] project(double x, double y, double z) {
        double[] p = transform(x - centerX, y - centerY, z - centerZ);
        return new double[] { screenX + p[0] * zoom, screenY - p[1] * zoom, p[2] };
    }

    private double[] transform(double x, double y, double z) {
        double a = Math.toRadians(yaw), b = Math.toRadians(pitch), rx = x * Math.cos(a) + z * Math.sin(a),
            rz = -x * Math.sin(a) + z * Math.cos(a);
        return new double[] { rx, y * Math.cos(b) - rz * Math.sin(b), y * Math.sin(b) + rz * Math.cos(b) };
    }
}

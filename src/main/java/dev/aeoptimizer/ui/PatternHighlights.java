package dev.aeoptimizer.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Client-only temporary outlines. Never loads chunks, changes blocks or teleports the player. */
public final class PatternHighlights {

    public static final PatternHighlights INSTANCE = new PatternHighlights();
    private final List<int[]> points = new ArrayList<>();
    private World world;
    private String product = "";
    private long until;

    public void receive(NBTTagCompound tag) {
        Minecraft mc = Minecraft.getMinecraft();
        points.clear();
        world = mc.theWorld;
        if (world == null) return;
        product = tag.getString("product");
        until = System.currentTimeMillis() + 30000;
        NBTTagList list = tag.getTagList("points", 10);
        for (int i = 0; i < Math.min(32, list.tagCount()); i++) {
            NBTTagCompound pos = list.getCompoundTagAt(i);
            if (pos.getInteger("dim") == world.provider.dimensionId)
                points.add(new int[] { pos.getInteger("x"), pos.getInteger("y"), pos.getInteger("z") });
        }
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (world != mc.theWorld || System.currentTimeMillis() > until) {
            points.clear();
            world = null;
            return;
        }
        if (points.isEmpty() || mc.renderViewEntity == null) return;
        double px = mc.renderViewEntity.lastTickPosX
            + (mc.renderViewEntity.posX - mc.renderViewEntity.lastTickPosX) * event.partialTicks;
        double py = mc.renderViewEntity.lastTickPosY
            + (mc.renderViewEntity.posY - mc.renderViewEntity.lastTickPosY) * event.partialTicks;
        double pz = mc.renderViewEntity.lastTickPosZ
            + (mc.renderViewEntity.posZ - mc.renderViewEntity.lastTickPosZ) * event.partialTicks;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(-px, -py, -pz);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glLineWidth(3f);
            GL11.glColor4f(.3f, .85f, 1f, 1f);
            for (int[] pos : points) {
                if (!world.blockExists(pos[0], pos[1], pos[2])) continue;
                RenderGlobal.drawOutlinedBoundingBox(
                    AxisAlignedBB.getBoundingBox(
                        pos[0] - .003,
                        pos[1] - .003,
                        pos[2] - .003,
                        pos[0] + 1.003,
                        pos[1] + 1.003,
                        pos[2] + 1.003),
                    0x70D9FF);
                GL11.glPushMatrix();
                GL11.glTranslated(pos[0] + .5, pos[1] + 1.35, pos[2] + .5);
                GL11.glRotatef(-RenderManager.instance.playerViewY, 0, 1, 0);
                GL11.glRotatef(RenderManager.instance.playerViewX, 1, 0, 0);
                GL11.glScalef(-.025f, -.025f, .025f);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                String label = product + " (" + pos[0] + ", " + pos[1] + ", " + pos[2] + ")";
                mc.fontRenderer.drawStringWithShadow(label, -mc.fontRenderer.getStringWidth(label) / 2, 0, 0xC9EEFF);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glPopMatrix();
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}

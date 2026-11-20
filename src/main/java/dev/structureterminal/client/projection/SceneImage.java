package dev.structureterminal.client.projection;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import com.mojang.authlib.GameProfile;

import dev.structureterminal.projection.Projection;

/** Single non-recursive off-screen view. Never swaps Minecraft's world, camera or chunk renderer. */
final class SceneImage {

    private Framebuffer image;
    private final SceneOptics optics = new SceneOptics();
    private int mesh;
    private SceneTerrain terrain;
    private boolean dirty;
    private NBTTagCompound view;
    private Map<Integer, EntityLivingBase> actors = new HashMap<>();
    long revision;
    boolean rendered, needsFrame;

    void accept(NBTTagCompound reply) {
        NBTTagCompound next = reply.getCompoundTag("view");
        long id = reply.getLong("revision");
        if (id != revision || view == null
            || !next.getTag("machines")
                .equals(view.getTag("machines"))
            || next.getInteger("biome") != view.getInteger("biome")) {
            int[] cells = reply.hasKey("terrain") ? reply.getIntArray("terrain")
                : terrain == null ? null : terrain.blocks;
            if (cells == null || cells.length != dev.structureterminal.projection.SceneView.VOLUME) {
                reset();
                return;
            }
            terrain = new SceneTerrain(cells, next);
            dirty = true;
        }
        revision = id;
        view = next;
        Map<Integer, EntityLivingBase> nextActors = new HashMap<>();
        NBTTagList list = next.getTagList("actors", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound n = list.getCompoundTagAt(i);
            String kind = n.getString("kind");
            Minecraft mc = Minecraft.getMinecraft();
            int entityId = n.getInteger("id");
            EntityLivingBase actor = actors.get(entityId);
            if (actor == null) {
                Entity e;
                if ("@player".equals(kind)) e = new EntityOtherPlayerMP(
                    mc.theWorld,
                    new GameProfile(UUID.fromString(n.getString("uuid")), n.getString("name")));
                else e = EntityList.createEntityByName(kind, mc.theWorld);
                if (!(e instanceof EntityLivingBase)) continue;
                actor = (EntityLivingBase) e;
            }
            actor.setPosition(n.getFloat("x"), n.getFloat("y"), n.getFloat("z"));
            actor.prevRenderYawOffset = actor.renderYawOffset = actor.prevRotationYaw = actor.rotationYaw = n
                .getFloat("yaw");
            actor.prevRotationYawHead = actor.rotationYawHead = n.getFloat("head");
            actor.limbSwing = n.getFloat("swing");
            actor.prevLimbSwingAmount = actor.limbSwingAmount = n.getFloat("amount");
            nextActors.put(entityId, actor);
        }
        actors = nextActors;
        needsFrame = true;
    }

    int facing() {
        return view == null ? 0 : view.getInteger("facing");
    }

    boolean ready() {
        return terrain != null;
    }

    void reset() {
        if (mesh != 0) {
            GL11.glDeleteLists(mesh, 2);
            mesh = 0;
        }
        if (image != null) {
            int bound = GL11.glGetInteger(0x8ca6);
            GL11.glPushAttrib(GL11.GL_TEXTURE_BIT);
            try {
                image.deleteFramebuffer();
            } finally {
                OpenGlHelper.func_153171_g(0x8d40, bound);
                GL11.glPopAttrib();
            }
            image = null;
        }
        actors.clear();
        view = null;
        terrain = null;
        rendered = false;
        revision = 0;
    }

    void render(double[] direction, Projection gate) {
        if (terrain == null || !OpenGlHelper.isFramebufferEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        int previous = GL11.glGetInteger(0x8ca6), matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int shader = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT | GL11.GL_CLIENT_PIXEL_STORE_BIT);
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            GL20.glUseProgram(0);
            if (image == null) {
                image = new Framebuffer(192, 192, true);
                image.setFramebufferFilter(GL11.GL_LINEAR);
            }
            image.bindFramebuffer(true);
            GL11.glDrawBuffer(0x8ce0);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            float day = 1 - view.getInteger("skyLoss") / 15f;
            if (view.getBoolean("noSky")) GL11.glClearColor(.12f, .08f, .09f, 1);
            else GL11.glClearColor(.12f + .34f * day, .15f + .48f * day, .22f + .63f * day, 1);
            GL11.glClearDepth(1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            optics.camera(direction, gate);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDisable(GL11.GL_CULL_FACE);
            optics.clip(facing());
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            mc.entityRenderer.enableLightmap(0);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, .05f);
            mc.getTextureManager()
                .bindTexture(TextureMap.locationBlocksTexture);
            if (dirty) {
                if (mesh == 0) mesh = GL11.glGenLists(2);
                for (int i = 0; i < 2; i++) {
                    GL11.glNewList(mesh + i, GL11.GL_COMPILE);
                    try {
                        terrain.mesh(i);
                    } finally {
                        GL11.glEndList();
                    }
                }
                dirty = false;
            }
            GL11.glPushMatrix();
            GL11.glTranslated(-.5, -view.getDouble("cy"), -.5);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glCallList(mesh);
            GL11.glPopMatrix();
            for (EntityLivingBase actor : actors.values()) {
                net.minecraft.client.renderer.entity.Render r = RenderManager.instance.getEntityRenderObject(actor);
                if (r != null) {
                    GL11.glPushMatrix();
                    try {
                        r.doRender(actor, actor.posX, actor.posY, actor.posZ, actor.rotationYaw, 0);
                    } finally {
                        GL11.glPopMatrix();
                    }
                }
            }
            mc.getTextureManager()
                .bindTexture(TextureMap.locationBlocksTexture);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glTranslated(-.5, -view.getDouble("cy"), -.5);
            GL11.glCallList(mesh + 1);
            rendered = true;
            needsFrame = false;
        } finally {
            OpenGlHelper.func_153171_g(0x8d40, previous);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glPopMatrix();
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glPopMatrix();
            GL11.glMatrixMode(matrix);
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            GL20.glUseProgram(shader);
        }
    }

    /** Mask the already rendered texture with the same profile used by the luminous rim. */
    void draw(Projection g, Projection.Path path, boolean back) {
        if (!rendered) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            image.bindFramebufferTexture();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            // Opaque destination pixels occlude farther portals independent of tile rendering order.
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
            GL11.glColor4f(1, 1, 1, 1);
            SceneOptics.aperture(g, path, back);
        } finally {
            GL11.glPopAttrib();
        }
    }

}

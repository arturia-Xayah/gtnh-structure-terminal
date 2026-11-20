package dev.structureterminal.client.projection;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.structureterminal.net.TerminalNetwork;
import dev.structureterminal.projection.ProjectorTile;
import dev.structureterminal.projection.SceneView;

/** Each visible source owns its texture, request token and refresh clock. */
public final class SceneClient implements net.minecraft.client.resources.IResourceManagerReloadListener {

    public static final SceneClient INSTANCE = new SceneClient();
    private final Map<ProjectorTile, View> views = new LinkedHashMap<>();
    private final Map<Long, View> pending = new HashMap<>();
    private long frame, token, requestTick = Long.MIN_VALUE;
    private int sentThisTick;

    private static final class View {

        final ProjectorTile gate;
        final SceneImage image = new SceneImage();
        final String peer;
        final long started;
        final boolean still;
        long seen, token, requestedAt, nextRequest, nextFrame;
        boolean failed;

        View(ProjectorTile gate) {
            this.gate = gate;
            peer = gate.scenePeer;
            started = gate.started;
            still = gate.snapshot;
        }

        boolean matches() {
            return gate.open && gate.sceneEnabled
                && !gate.isInvalid()
                && started == gate.started
                && still == gate.snapshot
                && peer.equals(gate.scenePeer);
        }
    }

    @Override
    public void onResourceManagerReload(net.minecraft.client.resources.IResourceManager resources) {
        for (View view : views.values()) view.image.reset();
        views.clear();
        pending.clear();
    }

    private static double eyeY(EntityPlayer p, double fraction) {
        // Client players store their standing eye offset in posY, server players store it in getEyeHeight().
        return p.lastTickPosY + (p.posY - p.lastTickPosY) * fraction + p.getEyeHeight() - p.getDefaultEyeHeight();
    }

    private static double[] ray(ProjectorTile t, float partial) {
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        return new double[] { t.xCoord + .5 - (p.lastTickPosX + (p.posX - p.lastTickPosX) * partial),
            SceneView.centerY(t.yCoord, t.gate.height) - eyeY(p, partial),
            t.zCoord + .5 - (p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * partial) };
    }

    boolean visit(ProjectorTile t, float partial, boolean back) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!OpenGlHelper.isFramebufferEnabled() || !t.sceneEnabled
            || t.scenePeer.isEmpty()
            || mc.thePlayer == null
            || mc.theWorld != t.getWorldObj()) return false;
        double[] d = ray(t, partial);
        if (!SceneView.inRange(d[0], d[1], d[2])) return false;
        View view = views.get(t);
        if (view == null || !view.matches()) {
            if (view != null) release(view);
            view = new View(t);
            views.put(t, view);
        }
        view.seen = frame;
        if (view.image.rendered) {
            view.image.draw(t.gate, t.path, back);
            return true;
        }
        return false;
    }

    private void release(View view) {
        pending.remove(view.token);
        view.image.reset();
    }

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            frame++;
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        Iterator<View> visible = views.values()
            .iterator();
        while (visible.hasNext()) {
            View view = visible.next();
            if (view.seen != frame || mc.thePlayer == null
                || mc.theWorld != view.gate.getWorldObj()
                || !view.matches()) {
                release(view);
                visible.remove();
                continue;
            }
            if (view.failed) continue;
            SceneImage image = view.image;
            if (image.ready()
                && (!image.rendered || (view.still ? image.needsFrame : System.nanoTime() >= view.nextFrame))) {
                double[] d = ray(view.gate, event.renderTickTime);
                try {
                    image.render(
                        SceneView
                            .direction(view.gate.getBlockMetadata() & 3, image.facing(), d[0], d[1], d[2], view.still),
                        view.gate.gate);
                } catch (RuntimeException ex) {
                    fallback(view, ex);
                }
                view.nextFrame = System.nanoTime() + 66_666_667L;
            }
        }
        if (views.isEmpty()) return;
        long tick = mc.theWorld.getTotalWorldTime();
        if (requestTick != tick) {
            requestTick = tick;
            sentThisTick = 0;
        }
        // Oldest deadline first. Frames already received remain visible while other gates obtain their data.
        while (sentThisTick < 2) {
            View due = null;
            for (View view : views.values())
                if (!view.failed && view.nextRequest <= tick && (due == null || view.nextRequest < due.nextRequest))
                    due = view;
            if (due == null) break;
            request(due, tick);
            sentThisTick++;
        }
    }

    private void request(View view, long tick) {
        pending.remove(view.token);
        view.token = ++token;
        pending.put(token, view);
        NBTTagCompound n = new NBTTagCompound();
        n.setBoolean("sceneRequest", true);
        n.setInteger("dim", view.gate.getWorldObj().provider.dimensionId);
        n.setInteger("x", view.gate.xCoord);
        n.setInteger("y", view.gate.yCoord);
        n.setInteger("z", view.gate.zCoord);
        n.setLong("revision", view.image.revision);
        n.setLong("token", token);
        view.requestedAt = tick;
        view.nextRequest = tick + 80;
        TerminalNetwork.channel.sendToServer(new TerminalNetwork.Request(n));
    }

    private void fallback(View view, RuntimeException ex) {
        view.failed = true;
        release(view);
        cpw.mods.fml.common.FMLLog.log(org.apache.logging.log4j.Level.WARN, ex, "Could not draw this portal scene");
    }

    public void receive(NBTTagCompound reply) {
        View view = pending.remove(reply.getLong("token"));
        if (view == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld != view.gate.getWorldObj() || !view.matches()) return;
        double[] d = ray(view.gate, 1);
        if (!SceneView.inRange(d[0], d[1], d[2])) {
            release(view);
            views.remove(view.gate);
            return;
        }
        if (reply.hasKey("view")) {
            try {
                view.image.accept(reply);
            } catch (RuntimeException ex) {
                fallback(view, ex);
                return;
            }
            view.nextRequest = view.requestedAt + Math.max(SceneView.LIVE_TICKS, reply.getInteger("refreshIn"));
        } else {
            boolean busy = reply.getBoolean("busy");
            if (!busy) view.image.reset();
            view.nextRequest = mc.theWorld.getTotalWorldTime()
                + (busy ? Math.max(1, reply.getInteger("refreshIn")) : 20);
        }
    }
}

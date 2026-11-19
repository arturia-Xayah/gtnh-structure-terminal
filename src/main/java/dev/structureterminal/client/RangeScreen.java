package dev.structureterminal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import buildcraft.builders.gui.GuiArchitect;
import buildcraft.builders.gui.GuiFiller;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dev.structureterminal.bc.RangeContainer;
import dev.structureterminal.bc.RangeJobs;
import dev.structureterminal.net.TerminalNetwork;

public final class RangeScreen extends GuiContainer {

    private final RangeContainer menu;
    private final GuiTextField[] fields = new GuiTextField[3];
    private final String[] values = { "1", "1", "1" };
    private int[] bounds;
    private String status = "";
    private boolean waiting;
    private GuiButton preview, apply;

    public RangeScreen(RangeContainer menu) {
        super(menu);
        this.menu = menu;
        xSize = 214;
        ySize = 136;
        int[] size = dev.structureterminal.bc.PortableRange.dimensions(menu.tile);
        if (size.length == 3) {
            for (int i = 0; i < 3; i++) values[i] = String.valueOf(size[i]);
            return;
        }
        int[] b = RangeJobs.bounds(menu.tile);
        if (b.length == 6) {
            int f = menu.front(), fx = f == 5 ? 1 : f == 4 ? -1 : 0, fz = f == 3 ? 1 : f == 2 ? -1 : 0;
            int x = menu.tile.xCoord + fx, y = menu.tile.yCoord, z = menu.tile.zCoord + fz;
            boolean aligned = fx > 0 ? b[0] == x : fx < 0 ? b[3] == x : fz > 0 ? b[2] == z : b[5] == z;
            int low = fx == 0 ? b[0] : b[2], high = fx == 0 ? b[3] : b[5], anchor = fx == 0 ? x : z;
            if (aligned && (anchor == low || anchor == high) && (y == b[1] || y == b[4])) {
                int width = high - low + 1;
                values[0] = String.valueOf(width == 1 ? 1 : width * (anchor == low ? 1 : -1) * (fx == 0 ? -fz : fx));
                values[1] = String.valueOf((b[4] - b[1] + 1) * (y == b[1] ? 1 : -1));
                values[2] = String.valueOf(fx == 0 ? b[5] - b[2] + 1 : b[3] - b[0] + 1);
            }
        }
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal("bc.range." + key);
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        for (int i = 0; i < 3; i++) {
            if (fields[i] != null) values[i] = fields[i].getText();
            fields[i] = new GuiTextField(fontRendererObj, guiLeft + 133, guiTop + 24 + i * 23, 64, 18);
            fields[i].setMaxStringLength(11);
            fields[i].setText(values[i]);
        }
        fields[0].setFocused(true);
        preview = new GuiButton(0, 0, 0, 90, 20, tr("preview"));
        apply = new GuiButton(1, 0, 0, 90, 20, tr("apply"));
        buttonList.add(preview);
        buttonList.add(apply);
        layout();
    }

    private void layout() {
        boolean viewing = bounds != null;
        preview.xPosition = width / 2 - 95;
        apply.xPosition = width / 2 + 5;
        preview.yPosition = apply.yPosition = viewing ? height - 26 : guiTop + 110;
        preview.displayString = tr(viewing ? "edit" : "preview");
        preview.enabled = !waiting;
        apply.enabled = viewing && !waiting;
    }

    public void receive(NBTTagCompound tag) {
        if (tag.getInteger("window") != menu.windowId) return;
        waiting = false;
        int[] c = tag.getIntArray("bounds");
        bounds = c.length == 6 ? c : null;
        status = bounds == null ? StatCollector.translateToLocal(tag.getString("error")) : "";
        layout();
    }

    private static NBTTagCompound command(int window) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("bcRange", true);
        tag.setInteger("window", window);
        return tag;
    }

    private static void send(NBTTagCompound tag) {
        TerminalNetwork.channel.sendToServer(new TerminalNetwork.Request(tag));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (waiting) return;
        if (button == preview && bounds != null) {
            bounds = null;
            layout();
            return;
        }
        NBTTagCompound tag = command(menu.windowId);
        if (button == apply && bounds != null) tag.setBoolean("apply", true);
        else if (button == preview) {
            try {
                tag.setInteger("w", Integer.parseInt(fields[0].getText()));
                tag.setInteger("h", Integer.parseInt(fields[1].getText()));
                tag.setInteger("d", Integer.parseInt(fields[2].getText()));
            } catch (NumberFormatException e) {
                status = tr("dimensions");
                return;
            }
        } else return;
        waiting = true;
        layout();
        send(tag);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public void drawDefaultBackground() {
        if (bounds == null) super.drawDefaultBackground();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mx, int my) {
        if (bounds != null) {
            String extent = "(" + bounds[0]
                + ", "
                + bounds[1]
                + ", "
                + bounds[2]
                + ")  →  ("
                + bounds[3]
                + ", "
                + bounds[4]
                + ", "
                + bounds[5]
                + ")";
            drawCenteredString(fontRendererObj, extent, width / 2, height - 40, 0xffffff);
            return;
        }
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xf0232934);
        drawCenteredString(fontRendererObj, tr("title"), width / 2, guiTop + 7, 0xffffff);
        String[] labels = { "width", "height", "depth" };
        for (int i = 0; i < 3; i++) {
            fontRendererObj.drawString(tr(labels[i]), guiLeft + 9, guiTop + 30 + i * 23, 0xcbd9eb);
            fields[i].drawTextBox();
        }
        drawCenteredString(
            fontRendererObj,
            status.isEmpty() ? tr("anchor") : status,
            width / 2,
            guiTop + 96,
            status.isEmpty() ? 0xd6e8ff : 0xffae86);
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            super.keyTyped(c, key);
            return;
        }
        if (!waiting && bounds == null) {
            for (int i = 0; i < 3; i++) if (fields[i].isFocused()) {
                if (key == Keyboard.KEY_TAB) {
                    fields[i].setFocused(false);
                    fields[(i + 1) % 3].setFocused(true);
                    return;
                }
                if (fields[i].textboxKeyTyped(c, key)) return;
            }
        }
        if (key == mc.gameSettings.keyBindInventory.getKeyCode()) mc.thePlayer.closeScreen();
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        if (bounds == null && !waiting) for (GuiTextField field : fields) field.mouseClicked(x, y, button);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (GuiTextField field : fields) field.updateCursorCounter();
    }

    public static final class Events {

        private GuiButton button;

        @SubscribeEvent
        public void init(GuiScreenEvent.InitGuiEvent.Post e) {
            if (!(e.gui instanceof GuiFiller || e.gui instanceof GuiArchitect)) return;
            GuiContainer g = (GuiContainer) e.gui;
            button = new GuiButton(
                7937,
                g.width / 2 + (g instanceof GuiFiller ? 88 : 128) - 61,
                (g.height - (g instanceof GuiFiller ? 240 : 166)) / 2 + 3,
                58,
                16,
                tr("title"));
            e.buttonList.add(button);
        }

        @SubscribeEvent
        public void click(GuiScreenEvent.ActionPerformedEvent.Pre e) {
            if (e.button != button || !(e.gui instanceof GuiArchitect || e.gui instanceof GuiFiller)) return;
            e.setCanceled(true);
            NBTTagCompound tag = command(((GuiContainer) e.gui).inventorySlots.windowId);
            tag.setBoolean("open", true);
            send(tag);
        }

        @SubscribeEvent
        public void render(RenderWorldLastEvent e) {
            Minecraft mc = Minecraft.getMinecraft();
            if (!(mc.currentScreen instanceof RangeScreen) || mc.renderViewEntity == null) return;
            int[] b = ((RangeScreen) mc.currentScreen).bounds;
            if (b == null) return;
            Entity eye = mc.renderViewEntity;
            double x = eye.lastTickPosX + (eye.posX - eye.lastTickPosX) * e.partialTicks;
            double y = eye.lastTickPosY + (eye.posY - eye.lastTickPosY) * e.partialTicks;
            double z = eye.lastTickPosZ + (eye.posZ - eye.lastTickPosZ) * e.partialTicks;
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(-x, -y, -z);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(false);
                GL11.glLineWidth(2);
                RenderGlobal.drawOutlinedBoundingBox(
                    AxisAlignedBB.getBoundingBox(b[0], b[1], b[2], b[3] + 1, b[4] + 1, b[5] + 1),
                    0x68ddff);
            } finally {
                GL11.glPopMatrix();
                GL11.glPopAttrib();
            }
        }
    }
}

package dev.structureterminal.client.projection;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import dev.structureterminal.net.TerminalNetwork;
import dev.structureterminal.projection.Projection;
import dev.structureterminal.projection.ProjectorMenu;
import dev.structureterminal.projection.ProjectorTile;

public final class ProjectorScreen extends GuiContainer {

    final ProjectorMenu menu;
    Projection draft;
    Projection.Path path;
    boolean inWorld;
    long started;
    private boolean colors, editAccent, connection;
    private String channel, station;
    private boolean showDestination, sceneEnabled, snapshot;
    private net.minecraft.client.gui.GuiTextField stationField;

    boolean previewLabel() {
        return showDestination && channel.trim()
            .equals(menu.tile.frequency);
    }

    private int facing;
    private net.minecraft.client.gui.GuiTextField channelField;
    private net.minecraft.client.gui.GuiTextField hex;
    private static final int[] SWATCHES = { 0x67b8ff, 0xd8faff, 0x38ffe1, 0x996aff, 0xff79aa, 0xffad50, 0xffef88,
        0x68ef81, 0xffffff, 0x26344e };
    private int dragged = -1;
    private static final int[] SHAPE_ORDER = { 0, 1, 4, 2, 3 };

    public ProjectorScreen(ProjectorMenu menu) {
        super(menu);
        this.menu = menu;
        xSize = 296;
        ySize = 207;
        channel = menu.tile.frequency;
        sceneEnabled = menu.tile.sceneEnabled;
        snapshot = menu.tile.snapshot;
        station = menu.tile.stationName;
        showDestination = menu.tile.showDestination;
        facing = menu.tile.exitFacing;
        draft = menu.tile.gate;
        path = menu.tile.path;
        started = menu.tile.getWorldObj()
            .getTotalWorldTime();
    }

    private static String tr(String k) {
        return StatCollector.translateToLocal("projector." + k);
    }

    private void button(int id, int x, int y, int w, String label, boolean enabled) {
        GuiButton b = new GuiButton(id, x, y, w, 18, label);
        b.enabled = enabled;
        buttonList.add(b);
    }

    @Override
    public void initGui() {
        super.initGui();
        hex = new net.minecraft.client.gui.GuiTextField(fontRendererObj, guiLeft + 193, guiTop + 103, 68, 16);
        hex.setMaxStringLength(7);
        stationField = new net.minecraft.client.gui.GuiTextField(fontRendererObj, guiLeft + 15, guiTop + 39, 266, 17);
        stationField.setMaxStringLength(32);
        stationField.setText(station);
        channelField = new net.minecraft.client.gui.GuiTextField(fontRendererObj, guiLeft + 15, guiTop + 75, 266, 17);
        channelField.setMaxStringLength(32);
        channelField.setText(channel);
        loadHex();
        layout();
    }

    private int editingColor() {
        return editAccent ? draft.secondary : draft.primary;
    }

    private void loadHex() {
        hex.setText(String.format(java.util.Locale.ROOT, "%06X", editingColor()));
        hex.setFocused(false);
    }

    private static int parseColor(String text) {
        if (text.startsWith("#")) text = text.substring(1);
        if (text.length() != 6) return -1;
        int rgb = 0;
        for (int i = 0; i < text.length(); i++) {
            int digit = Character.digit(text.charAt(i), 16);
            if (digit < 0) return -1;
            rgb = rgb * 16 + digit;
        }
        return rgb;
    }

    private void tint(int rgb) {
        draft = editAccent ? draft.recolored(draft.primary, rgb) : draft.recolored(rgb, draft.secondary);
    }

    private void layout() {
        buttonList.clear();
        int l = guiLeft, t = guiTop;
        if (!inWorld) button(25, l + 103, t + 2, 93, tr(connection ? "dimensions" : "connection"), true);
        if (!inWorld) button(24, l + 200, t + 2, 90, tr(colors ? "dimensions" : "colors"), true);
        if (!inWorld && !colors && !connection) {
            for (int i = 0; i < SHAPE_ORDER.length; i++) {
                int shape = SHAPE_ORDER[i];
                button(shape, l + 6 + i * 57, t + 20, 55, tr("shape" + shape), shape != draft.shape.ordinal());
            }
            int value = secondValue();
            button(10, l + 48, t + 158, 20, "−", draft.width > Projection.MIN_SIZE);
            button(11, l + 90, t + 158, 20, "+", draft.width < Projection.MAX_SIZE);
            if (hasSecond()) {
                button(
                    12,
                    l + 199,
                    t + 158,
                    20,
                    "−",
                    value > (draft.shape == Projection.Shape.RING ? 1 : Projection.MIN_SIZE));
                button(
                    13,
                    l + 252,
                    t + 158,
                    20,
                    "+",
                    value < (draft.shape == Projection.Shape.RING ? Projection.maxInnerQuarters(draft.width)
                        : Projection.MAX_SIZE));
            }
        }
        if (colors && !inWorld) {
            button(30, l + 175, t + 32, 53, tr("primary"), editAccent);
            button(31, l + 232, t + 32, 53, tr("secondary"), !editAccent);
        }
        if (connection && !inWorld) {
            button(26, l + 15, t + 111, 109, tr("facing" + facing), true);
            button(28, l + 130, t + 111, 151, tr(showDestination ? "labelOn" : "labelOff"), true);
            button(32, l + 15, t + 133, 130, tr(sceneEnabled ? "sceneOn" : "sceneOff"), true);
            button(33, l + 150, t + 133, 131, tr(snapshot ? "snapshot" : "live"), sceneEnabled);
            button(27, l + 150, t + 156, 131, tr(menu.tile.open ? "close" : "saveClosed"), true);
        }
        boolean valid = !colors || parseColor(hex.getText()) >= 0;
        int y = inWorld ? height - 24 : t + 183;
        button(20, l + 6, y, 84, tr("replay"), true);
        button(21, l + 94, y, 103, tr(inWorld ? "edit" : "preview"), valid);
        button(22, l + 201, y, 89, tr("apply"), valid);
    }

    private boolean hasSecond() {
        return !Projection.isRound(draft.shape) || draft.shape == Projection.Shape.RING;
    }

    private int secondValue() {
        return draft.shape == Projection.Shape.RING ? draft.innerQuarters : draft.height;
    }

    private void update(Projection gate) {
        draft = gate;
        path = new Projection.Path(gate);
    }

    private void restart() {
        started = menu.tile.getWorldObj()
            .getTotalWorldTime();
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        int id = b.id;
        if (id == 22 || id == 27) {
            NBTTagCompound cmd = new NBTTagCompound();
            ProjectorTile.encode(draft, cmd);
            cmd.setString("frequency", channel);
            cmd.setBoolean("sceneEnabled", sceneEnabled);
            cmd.setBoolean("snapshot", snapshot);
            cmd.setString("stationName", station);
            cmd.setBoolean("showDestination", showDestination);
            cmd.setInteger("exitFacing", facing);
            cmd.setBoolean("close", id == 27);
            cmd.setBoolean("projector", true);
            cmd.setInteger("window", inventorySlots.windowId);
            TerminalNetwork.channel.sendToServer(new TerminalNetwork.Request(cmd));
            return;
        }
        if (id == 24) {
            connection = false;
            channelField.setFocused(false);
            stationField.setFocused(false);
            colors = !colors;
            loadHex();
        }
        if (id == 25) {
            connection = !connection;
            colors = false;
            hex.setFocused(false);
            channelField.setFocused(false);
            stationField.setFocused(false);
        }
        if (id == 32) sceneEnabled = !sceneEnabled;
        if (id == 33) snapshot = !snapshot;
        if (id == 28) showDestination = !showDestination;
        if (id == 26) facing = (facing + 1) % 5;
        if (id == 30 || id == 31) {
            editAccent = id == 31;
            loadHex();
        }
        if (id == 21) {
            inWorld = !inWorld;
            channelField.setFocused(false);
            stationField.setFocused(false);
            hex.setFocused(false);
        }
        if (id < 5) update(
            draft.resized(
                Projection.Shape.values()[id],
                draft.width,
                draft.height,
                draft.innerQuarters,
                draft.profile()));
        if (id >= 10 && id <= 13) {
            int change = id % 2 == 1 ? 1 : -1;
            update(
                draft.resized(
                    draft.shape,
                    draft.width + (id < 12 ? change : 0),
                    draft.height + (id >= 12 ? change : 0),
                    draft.innerQuarters + (id >= 12 && draft.shape == Projection.Shape.RING ? change : 0),
                    draft.profile()));
        }
        if (id < 5 || id == 20 || id == 21) restart();
        else if (id >= 10 && id <= 13 || id == 24) started = menu.tile.getWorldObj()
            .getTotalWorldTime() - Projection.OPEN_TICKS;
        layout();
    }

    private int originX() {
        return guiLeft + (colors ? 87 : 148);
    }

    private int originY() {
        return guiTop + 141;
    }

    private double scale() {
        return Math.min(16, Math.min((colors ? 144.0 : 205.0) / draft.width, 82.0 / draft.height));
    }

    @Override
    public void drawDefaultBackground() {
        if (!inWorld) super.drawDefaultBackground();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mx, int my) {
        if (inWorld) return;
        int l = guiLeft, t = guiTop;
        drawRect(l, t, l + xSize, t + ySize, 0xff192633);
        drawRect(l, t, l + xSize, t + 2, 0xff91dff7);
        fontRendererObj.drawString(tr("title"), l + 8, t + 7, 0xe1f3ff);
        if (connection) {
            fontRendererObj.drawString(tr("station"), l + 15, t + 27, 0xb7dfec);
            stationField.drawTextBox();
            fontRendererObj.drawString(tr("frequency"), l + 15, t + 63, 0xb7dfec);
            channelField.drawTextBox();
            fontRendererObj.drawString(tr("exitFacing"), l + 15, t + 99, 0xb7dfec);
            if (channel.trim()
                .isEmpty()) fontRendererObj.drawString(tr("sceneEmpty"), l + 15, t + 162, 0xffc577);
            return;
        }
        int right = colors ? l + 164 : l + 290;
        int top = colors ? t + 28 : t + 42;
        drawRect(l + 6, top, right, t + 155, 0xff101b29);
        for (int col = -20; col <= 20; col++) {
            int x = originX() + (int) Math.round(col * scale());
            if (x > l + 6 && x < right) drawRect(x, top + 1, x + 1, t + 154, 0xff192a39);
        }
        for (int row = 0; row <= 16; row++) {
            int y = originY() - (int) Math.round(row * scale());
            if (y > top) drawRect(l + 7, y, right - 1, y + 1, 0xff192a39);
        }
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(originX(), originY(), 0);
            GL11.glScaled(scale(), -scale(), 1);
            ProjectorRenderer.draw(
                draft,
                path,
                menu.tile.getWorldObj()
                    .getTotalWorldTime() - started + partial);
        } finally {
            GL11.glPopMatrix();
        }
        drawRect(originX() - 10, originY() + 1, originX() + 10, originY() + 5, 0xff9ab6c4);
        drawRect(originX() - 6, originY(), originX() + 6, originY() + 2, 0xff9bfaff);
        if (colors) {
            drawRect(l + 176, t + 53, l + 227, t + 58, 0xff000000 | draft.primary);
            drawRect(l + 233, t + 53, l + 284, t + 58, 0xff000000 | draft.secondary);
            for (int i = 0; i < SWATCHES.length; i++) {
                int x = l + 176 + (i % 5) * 22, y = t + 65 + (i / 5) * 14;
                drawRect(x, y, x + 19, y + 11, editingColor() == SWATCHES[i] ? 0xffffffff : 0xff425467);
                drawRect(x + 1, y + 1, x + 18, y + 10, 0xff000000 | SWATCHES[i]);
            }
            fontRendererObj.drawString("#", l + 179, t + 108, 0xb7dfec);
            hex.drawTextBox();
            if (parseColor(hex.getText()) < 0) fontRendererObj.drawString(tr("hexHint"), l + 175, t + 125, 0xff9b91);
            return;
        }
        if (draft.shape == Projection.Shape.CUSTOM) {
            int[] p = draft.profile();
            for (int i = 0; i < p.length; i++) {
                int x = originX() + (int) Math.round(p[i] * draft.width * scale() * .05), y = rowY(i);
                drawRect(x - 2, y - 2, x + 3, y + 3, 0xffdaafff);
            }
        }
        fontRendererObj.drawString(
            tr(
                draft.shape == Projection.Shape.RING ? "outerRadius"
                    : Projection.isRound(draft.shape) ? "radius" : "width"),
            l + 8,
            t + 163,
            0xb7dfec);
        drawCenteredString(
            fontRendererObj,
            Projection.isRound(draft.shape) ? sizeLabel(draft.radius()) : String.valueOf(draft.width),
            l + 79,
            t + 163,
            0xffffff);
        if (hasSecond()) {
            boolean ring = draft.shape == Projection.Shape.RING;
            fontRendererObj.drawString(tr(ring ? "innerRadius" : "height"), l + 142, t + 163, 0xb7dfec);
            drawCenteredString(
                fontRendererObj,
                ring ? sizeLabel(draft.innerRadius()) : String.valueOf(draft.height),
                l + 235,
                t + 163,
                0xffffff);
        }
    }

    // This editor has no inventory slots, including when the world-preview controls are outside the panel.
    @Override
    protected void handleMouseClick(net.minecraft.inventory.Slot slot, int id, int button, int action) {}

    private static String sizeLabel(double n) {
        return n == (int) n ? Integer.toString((int) n) : Double.toString(n);
    }

    @Override
    protected void keyTyped(char key, int code) {
        if (connection && !inWorld
            && (channelField.isFocused() || stationField.isFocused())
            && code != org.lwjgl.input.Keyboard.KEY_ESCAPE) {
            if (code == org.lwjgl.input.Keyboard.KEY_RETURN) {
                channelField.setFocused(false);
                stationField.setFocused(false);
                return;
            }
            if (stationField.isFocused()) stationField.textboxKeyTyped(key, code);
            else channelField.textboxKeyTyped(key, code);
            station = stationField.getText();
            channel = channelField.getText();
            return;
        }
        if (colors && !inWorld && hex.isFocused() && code != org.lwjgl.input.Keyboard.KEY_ESCAPE) {
            if (code == org.lwjgl.input.Keyboard.KEY_RETURN) {
                hex.setFocused(false);
                return;
            }
            if (hex.textboxKeyTyped(key, code)) {
                int rgb = parseColor(hex.getText());
                if (rgb >= 0) tint(rgb);
                layout();
            }
            return;
        }
        super.keyTyped(key, code);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (hex != null) hex.updateCursorCounter();
        if (channelField != null) channelField.updateCursorCounter();
        if (stationField != null) stationField.updateCursorCounter();
    }

    private int rowY(int i) {
        return originY() - (int) Math.round(scale() * draft.height * i / (Projection.ROWS - 1));
    }

    private void drag(int x) {
        int[] p = draft.profile();
        p[dragged] = (int) Math.round((x - originX()) / (scale() * draft.width * .05));
        update(draft.resized(draft.shape, draft.width, draft.height, draft.innerQuarters, p));
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        if (!inWorld && connection && button == 0) {
            channelField.mouseClicked(x, y, button);
            stationField.mouseClicked(x, y, button);
        }
        if (!inWorld && colors && button == 0) {
            hex.mouseClicked(x, y, button);
            for (int i = 0; i < SWATCHES.length; i++) {
                int sx = guiLeft + 176 + i % 5 * 22, sy = guiTop + 65 + i / 5 * 14;
                if (x >= sx && x < sx + 19 && y >= sy && y < sy + 11) {
                    tint(SWATCHES[i]);
                    loadHex();
                    layout();
                    return;
                }
            }
        }
        if (!connection && !colors && !inWorld && button == 0 && draft.shape == Projection.Shape.CUSTOM) {
            int[] p = draft.profile();
            for (int i = 0; i < p.length; i++) {
                int handle = originX() + (int) Math.round(p[i] * scale() * draft.width * .05);
                if (Math.abs(x - handle) <= 5 && Math.abs(y - rowY(i)) <= 5) {
                    dragged = i;
                    started = menu.tile.getWorldObj()
                        .getTotalWorldTime() - Projection.OPEN_TICKS;
                    return;
                }
            }
        }
        super.mouseClicked(x, y, button);
    }

    @Override
    protected void mouseClickMove(int x, int y, int button, long time) {
        if (dragged >= 0 && button == 0) drag(x);
        else super.mouseClickMove(x, y, button, time);
    }

    @Override
    protected void mouseMovedOrUp(int x, int y, int button) {
        dragged = -1;
        super.mouseMovedOrUp(x, y, button);
    }
}

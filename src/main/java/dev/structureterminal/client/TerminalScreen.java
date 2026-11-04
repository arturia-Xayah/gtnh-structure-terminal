package dev.structureterminal.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.structurelib.ChannelDescription;
import com.gtnewhorizon.structurelib.StructureLibAPI;

import dev.structureterminal.StructureTerminal;
import dev.structureterminal.TerminalSettings;
import dev.structureterminal.bridge.ItemKeys;
import dev.structureterminal.bridge.NativeBridge;
import dev.structureterminal.bridge.StructureScanner;
import dev.structureterminal.bridge.StructureScanner.Cell;
import dev.structureterminal.bridge.StructureScanner.Scan;
import dev.structureterminal.net.TerminalNetwork;

/** Backport of the new terminal's full-screen scene / floating configuration card / parts-selection workflow. */
public final class TerminalScreen extends GuiScreen {

    private final int x, y, z, side;
    private NativeBridge bridge;
    private TerminalSettings settings;
    private Scan scan;
    private final StructureScene scene = new StructureScene();
    private final StructureScene minimap = new StructureScene();
    private boolean showMap;
    private Scan minimapScan;
    private int minimapWidth;
    public String status = "";
    private boolean config = true, partsPage, demolish, replace, confirm, help;
    private int cardX, cardY, cardW, cardH, scroll, lastX, lastY, dragButton = -1, dragStartX, dragStartY;
    private GuiTextField sizeField, search;
    private String popupChannel, popupGroup;
    private String popupTarget;
    private int popupScroll;
    private final List<String> channelNames = new ArrayList<String>();
    private final List<String> groups = new ArrayList<String>();
    private final List<Integer> popupIndices = new ArrayList<Integer>();
    private final List<ItemStack> popupStacks = new ArrayList<ItemStack>();
    private boolean initialized;

    public TerminalScreen(int x, int y, int z, int side) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.side = side;
    }

    public static String tr(String key, Object... args) {
        return StatCollector.translateToLocalFormatted("structureterminal." + key, args);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        if (settings == null) settings = TerminalSettings.fromTool(mc.thePlayer.getHeldItem());
        bridge = side < 0 ? null : NativeBridge.resolve(mc.theWorld, x, y, z, side);
        cardW = Math.min(178, Math.max(164, width / 3));
        cardX = width - 14 - cardW;
        cardY = 39;
        cardH = height - cardY - 33;
        sizeField = new GuiTextField(fontRendererObj, cardX + cardW - 58, cardY + 30, 32, 12);
        sizeField.setMaxStringLength(2);
        sizeField.setText(Integer.toString(settings.size));
        search = new GuiTextField(fontRendererObj, 28, 65, 154, 14);
        search.setMaxStringLength(80);
        if (!initialized) {
            refresh(true);
            initialized = true;
        }
        buttons();
    }

    private void buttons() {
        buttonList.clear();
        int offset = partsPage ? 0 : scroll * 23;
        button(0, width - 31, 15, 14, "×").tooltip = tr("close");
        button(1, width - 49, 15, 14, "").iconRow = 11;
        button(3, width - 67, 15, 14, "").iconRow = 10;
        button(4, width - 103, 15, 14, "").viewIcon = 2;
        button(7, width - 85, 15, 14, "").viewIcon = 3;
        button(5, width - 121, 15, 14, "").viewIcon = 1;
        button(6, width - 139, 15, 14, "").viewIcon = 0;
        button(2, width - 157, 15, 14, "").iconRow = 12;
        if (config) {
            button(10, cardX + 8, cardY + 8, cardW - 16, partsPage ? tr("back") : tr("parts"));
            if (!partsPage) {
                button(11, cardX + cardW - 76, cardY + 29 - offset, 14, "−");
                button(12, cardX + cardW - 22, cardY + 29 - offset, 14, "+");
                int modesY = cardY + 57 + channelNames.size() * 23 - offset;
                button(
                    16,
                    cardX + 8,
                    modesY,
                    cardW - 16,
                    tr("layer", scene.layer == Integer.MIN_VALUE ? tr("all") : scene.layer - y));
                OreTheme.Button mirror = button(13, cardX + 8, modesY + 20, cardW - 16, tr("mirror"));
                mirror.enabled = bridge == null || bridge.canMirror(!settings.mirror);
                mirror.switchValue = settings.mirror;
                button(14, cardX + 8, modesY + 39, cardW - 16, tr("network")).switchValue = settings.network;
                button(
                    15,
                    cardX + 8,
                    modesY + 58,
                    cardW - 16,
                    tr("operation") + ": " + tr(demolish ? "demolish" : replace ? "replace" : "build"));
                button(20, cardX + 8, modesY + 78, cardW - 16, tr("more_channels"));
            }
            button(17, cardX + 8, cardY + cardH - 36, (cardW - 20) / 2, tr("project")).enabled = bridge != null
                && scan != null;
            button(
                18,
                cardX + 12 + (cardW - 20) / 2,
                cardY + cardH - 36,
                (cardW - 20) / 2,
                tr(demolish ? "demolish" : replace ? "replace" : "build")).enabled = bridge != null && scan != null;
            ((OreTheme.Button) buttonList.get(buttonList.size() - 1)).variant = demolish || replace ? 2 : 1;
            button(19, cardX + 8, cardY + cardH - 18, cardW - (demolish || replace ? 48 : 16), tr("save_close"));
            if (demolish || replace) button(21, cardX + cardW - 36, cardY + cardH - 18, 28, "■").tooltip = tr("stop");
        }
        for (Object object : buttonList) {
            GuiButton b = (GuiButton) object;
            if (b.id <= 7) {
                String[] tips = { "close", "configuration", "help", "highlight", "fit", "zoom_in", "zoom_out",
                    "minimap" };
                ((OreTheme.Button) b).tooltip = tr(tips[b.id]);
            }
            if (b.id >= 11 && b.id <= 16 || b.id == 20)
                b.visible = b.yPosition >= cardY + 28 && b.yPosition + 14 <= cardY + cardH - 43;
        }
        if (sizeField != null) {
            String text = sizeField.getText();
            boolean focused = sizeField.isFocused();
            sizeField = new GuiTextField(fontRendererObj, cardX + cardW - 58, cardY + 30 - offset, 32, 12);
            sizeField.setMaxStringLength(2);
            sizeField.setText(text);
            sizeField.setFocused(focused && sizeVisible());
        }
    }

    private boolean sizeVisible() {
        int at = cardY + 30 - scroll * 23;
        return !partsPage && at >= cardY + 28 && at + 12 <= cardY + cardH - 43;
    }

    private OreTheme.Button button(int id, int bx, int by, int bw, String text) {
        OreTheme.Button b = new OreTheme.Button(id, bx, by, bw, text);
        buttonList.add(b);
        return b;
    }

    private String on(boolean b) {
        return tr(b ? "on" : "off");
    }

    private void refresh(boolean fit) {
        confirm = false;
        if (bridge == null) {
            status = tr(side < 0 ? "settings_hint" : "unsupported");
            scan = null;
            if (side < 0) {
                scan = new Scan();
                expandChannels();
            }
            return;
        }
        try {
            scan = StructureScanner.scan(bridge, mc.thePlayer, settings);
            channelNames.clear();
            channelNames.addAll(scan.channels.keySet());
            groups.clear();
            groups.addAll(scan.groups.keySet());
            if (fit) scene.fit(scan, Math.max(100, width - cardW - 45), height - 80);
            status = tr("summary", scan.cells.size(), scan.unknown);
        } catch (RuntimeException e) {
            scan = null;
            groups.clear();
            channelNames.clear();
            status = tr("error", e.getMessage());
        }
        scroll = 0;
        buttons();
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (!b.enabled) return;
        switch (b.id) {
            case 0:
                mc.displayGuiScreen(null);
                return;
            case 1:
                config = !config;
                break;
            case 2:
                help = !help;
                break;
            case 3:
                scene.highlight = !scene.highlight;
                break;
            case 4:
                if (bridge != null && scan != null) scene.fit(scan, width - cardW - 45, height - 80);
                break;
            case 5:
                scene.zoom = Math.min(80, scene.zoom * 1.2);
                break;
            case 6:
                scene.zoom = Math.max(0.15, scene.zoom / 1.2);
                break;
            case 7:
                showMap = !showMap;
                break;
            case 10:
                partsPage = !partsPage;
                scroll = 0;
                break;
            case 11:
                settings.size = Math.max(1, settings.size - 1);
                sizeField.setText(Integer.toString(settings.size));
                refresh(false);
                break;
            case 12:
                settings.size = Math.min(64, settings.size + 1);
                sizeField.setText(Integer.toString(settings.size));
                refresh(false);
                break;
            case 13:
                settings.mirror = !settings.mirror;
                refresh(false);
                break;
            case 14:
                settings.network = !settings.network;
                break;
            case 15:
                if (demolish) {
                    demolish = false;
                    replace = false;
                } else if (replace) {
                    replace = false;
                    demolish = true;
                } else replace = true;
                confirm = false;
                break;
            case 16:
                if (scan != null) {
                    if (scene.layer == Integer.MIN_VALUE) scene.layer = scan.minY;
                    else if (scene.layer >= scan.maxY) scene.layer = Integer.MIN_VALUE;
                    else scene.layer++;
                }
                break;
            case 17:
                project();
                return;
            case 18:
                if ((demolish || replace) && !confirm) {
                    confirm = true;
                    status = tr(demolish ? "confirm_demolish" : "confirm_replace");
                    return;
                }
                send(demolish ? 3 : replace ? 4 : 1, demolish || replace);
                status = tr("waiting");
                confirm = false;
                break;
            case 19:
                mc.displayGuiScreen(null);
                return;
            case 21:
                send(2, false);
                break;
            case 20:
                expandChannels();
                break;
            default:
                break;
        }
        buttons();
    }

    private void send(int action, boolean confirmed) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("action", action == 0 ? 5 : action);
        tag.setInteger("x", x);
        tag.setInteger("y", y);
        tag.setInteger("z", z);
        tag.setInteger("side", side);
        tag.setBoolean("confirmed", confirmed);
        tag.setTag("settings", settings.write());
        TerminalNetwork.channel.sendToServer(new TerminalNetwork.Request(tag));
    }

    private void project() {
        if (bridge == null) return;
        if (scan == null) return;
        StructureLibAPI.startHinting(mc.theWorld);
        try {
            for (Cell cell : scan.cells)
                cell.element.spawnHint(bridge.context, mc.theWorld, cell.x, cell.y, cell.z, settings.trigger());
        } finally {
            StructureLibAPI.endHinting(mc.theWorld);
        }
        send(0, false);
        mc.displayGuiScreen(null);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        OreTheme.panel(8, 8, width - 16, height - 16);
        String name = bridge == null ? tr("title")
            : mc.theWorld.getBlock(x, y, z)
                .getLocalizedName();
        if (bridge != null) {
            try {
                java.lang.reflect.Method m = bridge.context.getClass()
                    .getMethod("getLocalName");
                name = String.valueOf(m.invoke(bridge.context));
            } catch (ReflectiveOperationException ignored) {}
        }
        fontRendererObj.drawString(
            fontRendererObj.trimStringToWidth(tr("preview", name), Math.max(35, width - 185)),
            16,
            17,
            OreTheme.TEXT);
        drawRect(14, 34, width - 14, height - 28, 0xffb4b4b4);
        for (int gx = 14; gx < width - 14; gx += 16) drawRect(gx, 34, gx + 1, height - 28, 0x14000000);
        for (int gy = 34; gy < height - 28; gy += 16) drawRect(14, gy, width - 14, gy + 1, 0x14000000);
        if (bridge != null && scan != null) scene.draw(scan, settings, 14, 34, width - 28, height - 62);
        if (showMap && bridge != null && scan != null) {
            int mw = Math.min(100, Math.max(54, width - cardW - 45)), mh = 64;
            OreTheme.panel(20, height - 100, mw, mh);
            if (minimapScan != scan || minimapWidth != mw) {
                minimap.fit(scan, mw - 8, mh - 8);
                minimapScan = scan;
                minimapWidth = mw;
            }
            minimap.yaw = 0;
            minimap.pitch = 90;
            minimap.draw(scan, settings, 24, height - 96, mw - 8, mh - 8);
        }
        if (config) drawCard(mouseX, mouseY);
        for (Object object : buttonList) ((GuiButton) object).drawButton(mc, mouseX, mouseY);
        if (popupChannel == null && popupGroup == null && config && sizeVisible()) sizeField.drawTextBox();
        fontRendererObj
            .drawString(fontRendererObj.trimStringToWidth(status, width - 32), 16, height - 21, OreTheme.SECONDARY);
        if (help) {
            OreTheme.panel(18, 42, Math.min(230, width - 36), 86);
            fontRendererObj.drawSplitString(tr("controls"), 26, 50, Math.min(214, width - 52), OreTheme.TEXT);
        }
        if (scan != null && scene.rendered >= 24000)
            fontRendererObj.drawString(tr("render_limit"), 18, height - 40, 0x884400);
        if (popupChannel == null && popupGroup == null) for (Object o : buttonList) {
            OreTheme.Button b = (OreTheme.Button) o;
            if (b.visible && b.tooltip != null
                && mouseX >= b.xPosition
                && mouseX < b.xPosition + b.width
                && mouseY >= b.yPosition
                && mouseY < b.yPosition + b.height)
                drawHoveringText(Collections.singletonList(b.tooltip), mouseX, mouseY, fontRendererObj);
        }
        if (popupChannel != null || popupGroup != null) {
            drawPopup(mouseX, mouseY);
            search.drawTextBox();
        }
    }

    private void drawCard(int mx, int my) {
        OreTheme.panel(cardX, cardY, cardW, cardH);
        int start = partsPage ? cardY + 31 : cardY + 54 - scroll * 23;
        if (sizeVisible()) fontRendererObj.drawString(tr("size"), cardX + 8, cardY + 32 - scroll * 23, OreTheme.TEXT);
        List<String> rows = partsPage ? groups : channelNames;
        int end = cardY + cardH - 43;
        fontRendererObj.drawString(tr("configuration"), cardX + 8, cardY - 12, OreTheme.TEXT);
        for (int row = partsPage ? scroll : 0; row < rows.size(); row++) {
            int ry = start + (row - (partsPage ? scroll : 0)) * 23;
            if (ry < cardY + 28) continue;
            if (ry + 21 > end) break;
            String key = rows.get(row);
            OreTheme.groove(cardX + 8, ry, cardW - 16, 21);
            ItemStack icon = null;
            String label;
            if (partsPage) {
                Cell cell = scan.groups.get(key);
                icon = cell.selected(settings);
                if (icon == null && !cell.candidates.isEmpty()) icon = cell.candidates.get(0);
                label = icon == null ? tr("native_only") : icon.getDisplayName();
                fontRendererObj
                    .drawString(tr("quantity", scan.quantities.get(key)), cardX + 30, ry + 12, OreTheme.SECONDARY);
            } else {
                int value = settings.channels.containsKey(key) ? settings.channels.get(key) : settings.size;
                List<Integer> vs = scan.channelValues.get(key);
                int at = vs.indexOf(value);
                if (at >= 0) icon = scan.channels.get(key)
                    .get(at);
                String channelLabel = StatCollector.translateToLocal("structurelib.channel." + key);
                if (channelLabel.startsWith("structurelib.channel.")) channelLabel = key;
                label = channelLabel + ": " + value;
            }
            if (icon != null) icon(icon, cardX + 10, ry + 2);
            fontRendererObj
                .drawString(fontRendererObj.trimStringToWidth(label, cardW - 43), cardX + 30, ry + 3, OreTheme.TEXT);
            if (mx >= cardX + 8 && mx < cardX + cardW - 8 && my >= ry && my < ry + 21) {
                if (partsPage) scene.highlighted = key;
            }
        }
        if (partsPage && rows.isEmpty() && start >= cardY + 28 && start + 20 < end) fontRendererObj.drawSplitString(
            tr(partsPage ? "no_parts" : "no_channels"),
            cardX + 9,
            start,
            cardW - 18,
            OreTheme.SECONDARY);
    }

    private void openGroup(String group) {
        if (scan == null) return;
        popupChannel = null;
        popupGroup = group;
        popupTarget = group;
        popupStacks.clear();
        popupIndices.clear();
        Cell cell = scan.groups.get(group);
        if (cell == null || cell.candidates.isEmpty()) {
            popupGroup = null;
            status = tr("native_only_detail");
            return;
        }
        for (int i = 0; i < cell.candidates.size(); i++) {
            popupStacks.add(cell.candidates.get(i));
            popupIndices.add(i);
        }
        popupScroll = 0;
        search.setText("");
    }

    private void openCell(Cell cell) {
        if (scan != null && cell == scan.controller) return;
        openGroup(cell.group);
        popupTarget = cell.selectionKey;
    }

    private void openChannel(String name) {
        popupGroup = null;
        popupChannel = name;
        popupStacks.clear();
        popupIndices.clear();
        if (scan.channels.containsKey(name)) {
            List<ItemStack> ss = scan.channels.get(name);
            List<Integer> values = scan.channelValues.get(name);
            for (int i = 0; i < ss.size(); i++) {
                popupStacks.add(ss.get(i));
                popupIndices.add(values.get(i));
            }
        }
        popupScroll = 0;
        search.setText("");
        if (popupStacks.isEmpty()) status = tr("numeric_channel", name);
    }

    private List<Integer> filtered() {
        String filter = search.getText()
            .trim()
            .toLowerCase(Locale.ROOT);
        List<Integer> found = new ArrayList<Integer>();
        for (int i = 0; i < popupStacks.size(); i++) if (filter.isEmpty() || popupStacks.get(i)
            .getDisplayName()
            .toLowerCase(Locale.ROOT)
            .contains(filter)
            || Integer.toString(popupIndices.get(i))
                .equals(filter))
            found.add(i);
        return found;
    }

    private void drawPopup(int mx, int my) {
        int pw = 180, ph = Math.max(90, height - 100);
        OreTheme.panel(20, 42, pw, ph);
        String title = popupChannel != null ? popupChannel : tr("candidates");
        fontRendererObj.drawString(fontRendererObj.trimStringToWidth(title, 148), 28, 50, OreTheme.TEXT);
        fontRendererObj.drawString("x", 187, 50, OreTheme.TEXT);
        fontRendererObj.drawString(tr("auto"), 152, 50, OreTheme.SECONDARY);
        List<Integer> found = filtered();
        int rows = Math.max(1, (ph - 47) / 18);
        int max = Math.max(0, (found.size() + 8) / 9 - rows);
        popupScroll = Math.max(0, Math.min(max, popupScroll));
        ItemStack hovered = null;
        for (int slot = popupScroll * 9; slot < found.size() && slot < (popupScroll + rows) * 9; slot++) {
            int i = found.get(slot), bx = 28 + (slot % 9) * 18, by = 85 + (slot / 9 - popupScroll) * 18;
            OreTheme.slot(bx, by);
            ItemStack stack = popupStacks.get(i);
            icon(stack, bx + 1, by + 1);
            if (mx >= bx && mx < bx + 18 && my >= by && my < by + 18) hovered = stack;
        }
        if (hovered != null) renderToolTip(hovered, mx, my);
    }

    private void icon(ItemStack stack, int ix, int iy) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        RenderHelper.enableGUIStandardItemLighting();
        try {
            itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack, ix, iy);
        } catch (RuntimeException ignored) {
            fontRendererObj.drawString("?", ix + 4, iy + 4, 0xffffff);
        }
        RenderHelper.disableStandardItemLighting();
        GL11.glPopAttrib();
        GL11.glColor4f(1, 1, 1, 1);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (popupChannel != null || popupGroup != null) {
            search.mouseClicked(mx, my, button);
            if (mx >= 182 && mx < 198 && my >= 45 && my < 62) {
                popupChannel = popupGroup = null;
                return;
            }
            if (mx >= 145 && mx < 180 && my >= 45 && my < 62) {
                if (popupChannel != null) {
                    settings.channels.remove(popupChannel);
                    popupChannel = null;
                    refresh(false);
                } else {
                    settings.choices.remove(popupTarget);
                    popupGroup = null;
                }
                return;
            }
            if (mx >= 28 && mx < 190 && my >= 85 && my < height - 60) {
                int slot = ((my - 85) / 18 + popupScroll) * 9 + (mx - 28) / 18;
                List<Integer> found = filtered();
                if (slot < found.size()) {
                    int i = found.get(slot);
                    if (popupChannel != null) {
                        settings.channels.put(popupChannel, popupIndices.get(i));
                        popupChannel = null;
                        refresh(false);
                    } else {
                        settings.choices.put(popupTarget, ItemKeys.key(popupStacks.get(i)));
                        settings.enableHatchesForSelection(popupStacks.get(i));
                        scene.highlighted = popupGroup;
                        popupGroup = null;
                    }
                }
                return;
            }
            if (mx >= 20 && mx < 200 && my >= 42 && my < height - 58) return;
        }
        for (Object o : buttonList) if (((GuiButton) o).mousePressed(mc, mx, my)) {
            actionPerformed((GuiButton) o);
            return;
        }
        if (config && sizeVisible()) sizeField.mouseClicked(mx, my, button);
        if (config && mx >= cardX && mx < cardX + cardW && my >= cardY && my < cardY + cardH) {
            int start = partsPage ? cardY + 31 : cardY + 54 - scroll * 23;
            if (my >= Math.max(start, cardY + 28) && my < cardY + cardH - 43) {
                int index = (partsPage ? scroll : 0) + (my - start) / 23;
                List<String> rows = partsPage ? groups : channelNames;
                if (index >= 0 && index < rows.size()) {
                    if (partsPage) {
                        Cell cell = scan.groups.get(rows.get(index));
                        boolean hatches = false;
                        for (ItemStack candidate : cell.candidates) {
                            net.minecraft.block.Block block = net.minecraft.block.Block
                                .getBlockFromItem(candidate.getItem());
                            if (block != null && block.hasTileEntity(candidate.getItemDamage())) hatches = true;
                        }
                        if (hatches) status = tr("pick_hatch_position");
                        else openGroup(rows.get(index));
                    } else openChannel(rows.get(index));
                }
            }
            return;
        }
        if (my > 34 && my < height - 28) {
            dragButton = button;
            dragStartX = lastX = mx;
            dragStartY = lastY = my;
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long elapsed) {
        if (dragButton < 0) return;
        int dx = mx - lastX, dy = my - lastY;
        if (button == 1 || button == 2) {
            scene.panX += dx;
            scene.panY += dy;
        } else {
            scene.yaw += dx * 0.7;
            scene.pitch = Math.max(-80, Math.min(80, scene.pitch + dy * 0.7));
        }
        lastX = mx;
        lastY = my;
    }

    @Override
    protected void mouseMovedOrUp(int mx, int my, int button) {
        if (button >= 0 && dragButton == 0 && Math.abs(mx - dragStartX) + Math.abs(my - dragStartY) < 5) {
            Cell cell = scene.pick(mx, my);
            if (cell != null) openCell(cell);
        }
        dragButton = -1;
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth,
            my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if ((popupChannel != null || popupGroup != null) && mx < 205)
            popupScroll = Math.max(0, popupScroll + (wheel < 0 ? 1 : -1));
        else if (config && mx >= cardX && my >= cardY) {
            int maximum = partsPage ? Math.max(0, groups.size() - 1)
                : Math.max(0, (140 + channelNames.size() * 23 - (cardH - 70) + 22) / 23);
            scroll = Math.max(0, Math.min(maximum, scroll + (wheel < 0 ? 1 : -1)));
            buttons();
        } else scene.zoom = Math.max(0.15, Math.min(80, scene.zoom * (wheel > 0 ? 1.15 : 1 / 1.15)));
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_F && !sizeField.isFocused()
            && popupChannel == null
            && popupGroup == null
            && bridge != null
            && scan != null) {
            scene.fit(scan, width - cardW - 45, height - 80);
            return;
        }
        if (key == Keyboard.KEY_ESCAPE && (popupChannel != null || popupGroup != null)) {
            popupChannel = popupGroup = null;
            return;
        }
        if (popupChannel != null || popupGroup != null) {
            if (search.textboxKeyTyped(c, key)) {
                popupScroll = 0;
                return;
            }
            if (popupChannel != null && key == Keyboard.KEY_RETURN) {
                try {
                    int value = Integer.parseInt(search.getText());
                    dev.structureterminal.core.Limits.value(value);
                    settings.channels.put(popupChannel, value);
                    popupChannel = null;
                    refresh(false);
                } catch (IllegalArgumentException e) {
                    status = tr("invalid_value");
                }
                return;
            }
        }
        if (config && !partsPage && sizeField.isFocused()) {
            if (key == Keyboard.KEY_RETURN) {
                try {
                    int value = Integer.parseInt(sizeField.getText());
                    if (value < 1 || value > 64) throw new IllegalArgumentException();
                    settings.size = value;
                    refresh(false);
                } catch (IllegalArgumentException e) {
                    status = tr("invalid_size");
                }
                return;
            }
            if (c >= '0' && c <= '9' || key == Keyboard.KEY_BACK
                || key == Keyboard.KEY_DELETE
                || key == Keyboard.KEY_LEFT
                || key == Keyboard.KEY_RIGHT) {
                sizeField.textboxKeyTyped(c, key);
                return;
            }
        }
        super.keyTyped(c, key);
    }

    @Override
    public void updateScreen() {
        sizeField.updateCursorCounter();
        search.updateCursorCounter();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (settings != null && mc.thePlayer != null
            && mc.thePlayer.getHeldItem() != null
            && mc.thePlayer.getHeldItem()
                .getItem() == StructureTerminal.item)
            send(0, false);
    }

    private void expandChannels() {
        if (scan == null) return;
        List<String> all = new ArrayList<String>(
            ChannelDescription.getAll()
                .keySet());
        Collections.sort(all);
        channelNames.clear();
        channelNames.addAll(all);
        scroll = 0;
        for (String key : all) if (!scan.channels.containsKey(key)) {
            List<ItemStack> ss = new ArrayList<ItemStack>();
            List<Integer> vs = new ArrayList<Integer>();
            for (Map.Entry<ItemStack, Integer> e : ChannelDescription.get(key)
                .getItems()
                .entrySet()) {
                ss.add(e.getKey());
                vs.add(e.getValue());
            }
            scan.channels.put(key, ss);
            scan.channelValues.put(key, vs);
        }
    }
}

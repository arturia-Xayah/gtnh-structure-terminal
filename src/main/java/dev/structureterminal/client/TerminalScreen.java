package dev.structureterminal.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import dev.structureterminal.StructureTerminal;
import dev.structureterminal.TerminalSettings;
import dev.structureterminal.net.TerminalNetwork;
import gregtech.api.enums.GTValues;

/** Rounded terminal settings with native item icons and exclusive material selections. */
public final class TerminalScreen extends GuiScreen {

    private static final int MAIN = 236, SIDE = 172, GAP = 8, HEIGHT = 310, ROWS = 9;
    private TerminalSettings settings;
    private GuiTextField repeats;
    private List<TerminalMaterials.Entry> coils, glasses;
    private int left, top, slot, expanded = -1, scroll, focus;
    private float scale = 1;
    public String status = "";

    public TerminalScreen(int x, int y, int z, int side) {}

    public static String tr(String key, Object... args) {
        return StatCollector.translateToLocalFormatted("structureterminal." + key, args);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        if (settings == null) {
            settings = TerminalSettings.fromTool(mc.thePlayer.getHeldItem());
            if (settings.replacing()) settings.operation = TerminalSettings.REPLACE;
            else settings.replaceCoils = false;
            slot = mc.thePlayer.inventory.currentItem;
            coils = TerminalMaterials.coils();
            glasses = TerminalMaterials.glasses();
        }
        String value = repeats == null ? Integer.toString(settings.repeatCount) : repeats.getText();
        layout();
        repeats = new GuiTextField(fontRendererObj, left + 170, top + 118, 42, 12);
        repeats.setEnableBackgroundDrawing(false);
        repeats.setTextColor(TerminalStyle.INK);
        repeats.setMaxStringLength(2);
        repeats.setText(value);
    }

    private void layout() {
        int total = expanded < 0 ? MAIN : MAIN + GAP + SIDE;
        scale = Math.min(1f, Math.min((width - 12f) / total, (height - 12f) / HEIGHT));
        scale = Math.max(.25f, scale);
        left = Math.round((width / scale - total) / 2);
        top = Math.round((height / scale - HEIGHT) / 2);
    }

    private int row(int i) {
        return top + 54 + i * 28;
    }

    private boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int glass() {
        return settings.channels.getOrDefault("glass", 0);
    }

    private List<TerminalMaterials.Entry> entries() {
        return expanded == 0 ? coils : glasses;
    }

    private int selected() {
        return expanded == 0 ? settings.coilTier : glass();
    }

    private String tier(TerminalMaterials.Entry entry) {
        return entry.value == 0 ? tr("modern.auto")
            : entry.tier < GTValues.VN.length ? GTValues.VN[entry.tier] : "T" + entry.tier;
    }

    private TerminalMaterials.Entry find(List<TerminalMaterials.Entry> entries, int value) {
        for (TerminalMaterials.Entry e : entries) if (e.value == value) return e;
        return entries.get(0);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        int mx = (int) (mouseX / scale), my = (int) (mouseY / scale);
        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1);
        panel(left, top, MAIN);
        fontRendererObj.drawString(tr("advanced.title"), left + 16, top + 17, TerminalStyle.INK);
        fontRendererObj.drawString(tr("modern.subtitle"), left + 16, top + 34, TerminalStyle.MUTED);
        String[] labels = { tr("advanced.1"), tr("modern.glass"), tr("advanced.2"), tr("modern.operation"),
            tr("advanced.3"), tr("modern.coilOnly"), tr("modern.keepHatches"), tr("advanced.5") };
        for (int i = 0; i < labels.length; i++) {
            int y = row(i);
            boolean hover = hit(mx, my, left + 10, y, MAIN - 20, 25);
            TerminalStyle.round(left + 10, y, MAIN - 20, 25, 7, hover || focus == i ? 0xFFEAF1FC : 0xFFF4F7FD);
            fontRendererObj.drawString(
                fontRendererObj.trimStringToWidth(labels[i], i == 3 ? 82 : 133),
                left + 18,
                y + 9,
                i == 5 && currentOperation() != TerminalSettings.REPLACE ? TerminalStyle.MUTED : TerminalStyle.INK);
            if (i < 2) {
                TerminalMaterials.Entry entry = find(i == 0 ? coils : glasses, i == 0 ? settings.coilTier : glass());
                fontRendererObj.drawString(tier(entry), left + 153, y + 9, TerminalStyle.MUTED);
                TerminalStyle.round(left + 200, y + 3, 22, 19, 6, expanded == i ? 0xFFD7E6FC : 0xFFE3EBF8);
                TerminalStyle.triangle(left + 208, y + 9, expanded == i);
            } else if (i == 2) {
                TerminalStyle.round(left + 163, y + 4, 59, 17, 5, 0xFFFFFFFF);
                repeats.drawTextBox();
            } else if (i == 3) {
                for (int mode = 0; mode < 3; mode++) {
                    int bx = left + 105 + mode * 39;
                    TerminalStyle
                        .round(bx, y + 3, 37, 19, 6, currentOperation() == mode ? TerminalStyle.BLUE : 0xFFE3EBF8);
                    String name = tr("modern.mode." + mode);
                    fontRendererObj.drawString(
                        name,
                        bx + (37 - fontRendererObj.getStringWidth(name)) / 2,
                        y + 9,
                        currentOperation() == mode ? 0xFFFFFF : TerminalStyle.INK);
                }
            } else {
                boolean on = i == 4 ? settings.noHatches
                    : i == 5 ? settings.replaceCoils : i == 6 ? settings.keepHatches : settings.network;
                TerminalStyle
                    .check(left + 203, y + 5, on, hover && (i != 5 || currentOperation() == TerminalSettings.REPLACE));
            }
        }
        fontRendererObj.drawString(tr("modern.save"), left + 18, top + 291, TerminalStyle.MUTED);
        List<String> tooltip = null;
        ItemStack hoveredItem = null;
        if (expanded >= 0) {
            int x = left + MAIN + GAP;
            panel(x, top, SIDE);
            fontRendererObj
                .drawString(expanded == 0 ? tr("advanced.1") : tr("modern.glass"), x + 12, top + 17, TerminalStyle.INK);
            fontRendererObj.drawString(tr("modern.order"), x + 12, top + 34, TerminalStyle.MUTED);
            List<TerminalMaterials.Entry> entries = entries();
            for (int n = 0; n < ROWS && scroll + n < entries.size(); n++) {
                TerminalMaterials.Entry entry = entries.get(scroll + n);
                int y = top + 50 + n * 24;
                boolean hover = hit(mx, my, x + 8, y, SIDE - 16, 23);
                TerminalStyle.round(
                    x + 8,
                    y,
                    SIDE - 16,
                    23,
                    6,
                    selected() == entry.value ? 0xFFE1EDFF : hover ? 0xFFF0F4FC : 0xFFF8FAFF);
                if (entry.icon != null) {
                    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
                    codechicken.nei.guihook.GuiContainerManager.drawItem(x + 13, y + 3, entry.icon);
                    GL11.glPopAttrib();
                } else {
                    fontRendererObj.drawString("A", x + 18, y + 8, TerminalStyle.MUTED);
                }
                fontRendererObj.drawString(tier(entry), x + 39, y + 8, TerminalStyle.INK);
                TerminalStyle.check(x + SIDE - 31, y + 4, selected() == entry.value, hover);
                if (hover) {
                    if (entry.icon != null) hoveredItem = entry.icon;
                    else tooltip = Arrays.asList(tr("modern.auto"), tr("modern.auto.tooltip"));
                }
            }
            TerminalStyle.round(x + 9, top + 283, 24, 18, 6, 0xFFE3EBF8);
            TerminalStyle.triangle(x + 18, top + 288, true);
            TerminalStyle.round(x + SIDE - 33, top + 283, 24, 18, 6, 0xFFE3EBF8);
            TerminalStyle.triangle(x + SIDE - 24, top + 288, false);
            String page = (scroll + 1) + "–" + Math.min(scroll + ROWS, entries.size()) + " / " + entries.size();
            fontRendererObj.drawString(
                page,
                x + (SIDE - fontRendererObj.getStringWidth(page)) / 2,
                top + 289,
                TerminalStyle.MUTED);
        }
        if (tooltip == null && hoveredItem == null)
            for (int i = 0; i < 8; i++) if (hit(mx, my, left + 10, row(i), 140, 25)) {
                String key = i == 1 ? "modern.glass.tooltip"
                    : i == 3 ? "modern.operation.tooltip"
                        : i == 5 ? "modern.coilOnly.tooltip"
                            : i == 6 ? "modern.keepHatches.tooltip"
                                : "advanced." + (i == 0 ? 1 : i == 2 ? 2 : i == 4 ? 3 : 5) + ".tooltip";
                tooltip = Arrays.asList(
                    tr(key).replace("\\n", "\n")
                        .split("\n"));
            }
        if (tooltip != null) tooltip(tooltip, mx, my);
        if (!status.isEmpty()) fontRendererObj.drawString(status, left, top + HEIGHT + 4, TerminalStyle.INK);
        GL11.glPopMatrix();
        if (hoveredItem != null) {
            // Same renderer, font choice and tooltip provider as NEI. Read every frame for Shift/Ctrl details.
            List<String> lines = codechicken.nei.guihook.GuiContainerManager
                .itemDisplayNameMultiline(hoveredItem, null, true);
            codechicken.nei.guihook.GuiContainerManager.drawPagedTooltip(
                codechicken.nei.guihook.GuiContainerManager.getFontRenderer(hoveredItem),
                mouseX,
                mouseY,
                lines);
        }
    }

    private int currentOperation() {
        return settings.replacing() ? TerminalSettings.REPLACE : settings.operation;
    }

    private void operation(int value) {
        settings.operation = value;
        if (value != TerminalSettings.REPLACE) settings.replaceCoils = false;
    }

    private void tooltip(List<String> source, int mx, int my) {
        int screenWidth = (int) (width / scale), screenHeight = (int) (height / scale);
        List<String> lines = new ArrayList<String>();
        int w = 0;
        for (String line : source) {
            for (Object wrapped : fontRendererObj.listFormattedStringToWidth(line, Math.min(230, screenWidth - 32))) {
                String text = wrapped.toString();
                lines.add(text);
                w = Math.max(w, fontRendererObj.getStringWidth(text));
            }
        }
        int h = lines.size() * 12 + 12;
        int x = Math.max(4, Math.min(mx + 12, screenWidth - w - 20));
        int y = Math.max(4, Math.min(my + 12, screenHeight - h - 4));
        TerminalStyle.round(x, y, w + 16, h, 8, 0xFF364862);
        for (int i = 0; i < lines.size(); i++)
            fontRendererObj.drawString(lines.get(i), x + 8, y + 7 + i * 12, 0xFFFFFF);
    }

    private void panel(int x, int y, int w) {
        TerminalStyle.round(x, y + 3, w, HEIGHT, 13, 0x2548669C);
        TerminalStyle.round(x, y, w, HEIGHT, 13, 0xFFE1EAF9);
        TerminalStyle.round(x + 1, y + 1, w - 2, HEIGHT - 2, 12, 0xFFFDFEFF);
        TerminalStyle.round(x + w - 45, y + 13, 28, 5, 2.5f, 0xFFD9D1F7);
        TerminalStyle.round(x + w - 36, y + 22, 19, 4, 2, 0xFFC8E2FB);
    }

    private void expand(int which) {
        expanded = expanded == which ? -1 : which;
        scroll = 0;
        if (expanded >= 0) {
            List<TerminalMaterials.Entry> list = entries();
            for (int i = 0; i < list.size(); i++) if (list.get(i).value == selected()) scroll = (i / ROWS) * ROWS;
            scroll = Math.min(scroll, Math.max(0, list.size() - ROWS));
        }
        initGui();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        int x = (int) (mouseX / scale), y = (int) (mouseY / scale);
        repeats.mouseClicked(x, y, button);
        if (button != 0) return;
        for (int i = 0; i < 8; i++) if (hit(x, y, left + 10, row(i), MAIN - 20, 25)) {
            focus = i;
            if (i < 2) expand(i);
            else if (i == 3 && x >= left + 105 && x < left + 222) operation((x - left - 105) / 39);
            else if (i == 4) settings.noHatches = !settings.noHatches;
            else if (i == 5 && currentOperation() == TerminalSettings.REPLACE)
                settings.replaceCoils = !settings.replaceCoils;
            else if (i == 6) settings.keepHatches = !settings.keepHatches;
            else if (i == 7) settings.network = !settings.network;
            return;
        }
        if (expanded >= 0) {
            int sx = left + MAIN + GAP;
            for (int n = 0; n < ROWS && scroll + n < entries().size(); n++)
                if (hit(x, y, sx + 8, top + 50 + n * 24, SIDE - 16, 23)) {
                    choose(entries().get(scroll + n).value);
                    return;
                }
            if (hit(x, y, sx + 9, top + 283, 24, 18)) scroll(-ROWS);
            if (hit(x, y, sx + SIDE - 33, top + 283, 24, 18)) scroll(ROWS);
        }
    }

    private void choose(int value) {
        if (expanded == 0) settings.coilTier = value;
        else if (value == 0) settings.channels.remove("glass");
        else settings.channels.put("glass", value);
    }

    private void scroll(int delta) {
        scroll = Math.max(0, Math.min(Math.max(0, entries().size() - ROWS), scroll + delta));
    }

    private int repeatValue() {
        try {
            return Math.max(0, Math.min(99, Integer.parseInt(repeats.getText())));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN) {
            mc.displayGuiScreen(null);
        } else if (key == Keyboard.KEY_TAB) {
            focus = (focus + (isShiftKeyDown() ? 7 : 1)) % 8;
            repeats.setFocused(focus == 2);
        } else if (repeats.isFocused()) {
            String before = repeats.getText();
            repeats.textboxKeyTyped(c, key);
            if (!repeats.getText()
                .matches("[0-9]{0,2}")) repeats.setText(before);
        } else if (key == Keyboard.KEY_SPACE) {
            if (focus < 2) expand(focus);
            else if (focus == 3) operation((currentOperation() + 1) % 3);
            else if (focus == 4) settings.noHatches = !settings.noHatches;
            else if (focus == 5 && currentOperation() == TerminalSettings.REPLACE)
                settings.replaceCoils = !settings.replaceCoils;
            else if (focus == 6) settings.keepHatches = !settings.keepHatches;
            else if (focus == 7) settings.network = !settings.network;
        } else if (expanded >= 0 && (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN)) {
            List<TerminalMaterials.Entry> list = entries();
            for (int i = 0; i < list.size(); i++) if (list.get(i).value == selected()) {
                int next = Math.max(0, Math.min(list.size() - 1, i + (key == Keyboard.KEY_UP ? -1 : 1)));
                choose(list.get(next).value);
                if (next < scroll) scroll = next;
                if (next >= scroll + ROWS) scroll = next - ROWS + 1;
                break;
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int x = (int) ((Mouse.getEventX() * width / mc.displayWidth) / scale);
        int y = (int) ((height - Mouse.getEventY() * height / mc.displayHeight - 1) / scale);
        if (expanded >= 0 && hit(x, y, left + MAIN + GAP, top, SIDE, HEIGHT)) scroll(wheel > 0 ? -1 : 1);
        else if (hit(x, y, left + 163, row(2), 59, 25))
            repeats.setText(Integer.toString(Math.max(0, Math.min(99, repeatValue() + (wheel > 0 ? 1 : -1)))));
    }

    @Override
    public void updateScreen() {
        repeats.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (settings == null || mc.thePlayer == null || mc.thePlayer.inventory.currentItem != slot) return;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || held.getItem() != StructureTerminal.item) return;
        settings.repeatCount = repeatValue();
        settings.save(held);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("action", 5);
        tag.setInteger("slot", slot);
        tag.setTag("settings", settings.write());
        TerminalNetwork.channel.sendToServer(new TerminalNetwork.Request(tag));
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}

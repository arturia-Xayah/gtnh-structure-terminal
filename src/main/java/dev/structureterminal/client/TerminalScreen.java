package dev.structureterminal.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import dev.structureterminal.StructureTerminal;
import dev.structureterminal.TerminalChannels;
import dev.structureterminal.TerminalSettings;
import dev.structureterminal.net.TerminalNetwork;
import gregtech.api.enums.GTValues;

/** Rounded terminal settings with native item icons and exclusive material selections. */
public final class TerminalScreen extends GuiScreen {

    private static final int MAIN = 236, SIDE = 172, GAP = 8, HEIGHT = 332, ROWS = 9;
    private TerminalSettings settings;
    private GuiTextField repeats;
    private List<TerminalMaterials.Entry> coils, glasses;
    private int left, top, slot, expanded = -1, scroll, focus, glassMenu = -1;
    private int page;
    private final Map<String, List<TerminalMaterials.Entry>> materials = new HashMap<>();
    private final Map<String, GuiTextField> numbers = new HashMap<>();
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
        saveNumbers();
        layout();
        repeats = new GuiTextField(fontRendererObj, left + 170, top + 118, 42, 12);
        repeats.setEnableBackgroundDrawing(false);
        repeats.setTextColor(TerminalStyle.INK);
        repeats.setMaxStringLength(2);
        repeats.setText(value);
        numbers.clear();
        if (page > 0) for (int i = 0; i < rowCount(); i++) {
            String channel = channel(i);
            if (TerminalChannels.numericMax(channel) == 0) continue;
            GuiTextField field = new GuiTextField(fontRendererObj, left + 170, row(i) + 8, 42, 12);
            field.setEnableBackgroundDrawing(false);
            field.setMaxStringLength(2);
            field.setText(Integer.toString(settings.channels.getOrDefault(channel, 0)));
            numbers.put(channel, field);
        }
    }

    private void layout() {
        int total = expanded < 0 ? MAIN : MAIN + GAP + SIDE;
        scale = Math.min(1f, Math.min((width - 12f) / total, (height - 12f) / HEIGHT));
        scale = Math.max(.25f, scale);
        left = Math.round((width / scale - total) / 2);
        top = Math.round((height / scale - HEIGHT) / 2);
    }

    private int rowCount() {
        return page == 0 ? 8 : TerminalChannels.PAGES[page - 1].length;
    }

    private String channel(int index) {
        return page == 0 ? index == 0 ? "coil" : index == 1 ? "glass" : "" : TerminalChannels.PAGES[page - 1][index];
    }

    private String channelName(String key) {
        return tr("channel." + key);
    }

    private List<TerminalMaterials.Entry> materials(String key) {
        return materials.computeIfAbsent(key, TerminalChannels::entries);
    }

    private void saveNumbers() {
        for (Map.Entry<String, GuiTextField> e : numbers.entrySet()) {
            int value = number(e.getValue(), TerminalChannels.numericMax(e.getKey()));
            if (value == 0) settings.channels.remove(e.getKey());
            else settings.channels.put(e.getKey(), value);
        }
    }

    private int number(GuiTextField field, int max) {
        try {
            return Math.max(0, Math.min(max, Integer.parseInt(field.getText())));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void changePage(int delta) {
        settings.repeatCount = repeatValue();
        saveNumbers();
        page = (page + delta + TerminalChannels.PAGES.length + 1) % (TerminalChannels.PAGES.length + 1);
        expanded = -1;
        glassMenu = -1;
        scroll = 0;
        focus = 0;
        initGui();
    }

    private List<String> channelTooltip(String key) {
        List<String> lines = new ArrayList<>();
        lines.add(channelName(key));
        lines.add(tr("channels.key", key));
        lines.add(
            tr(
                TerminalChannels.toggle(key) ? "channels.toggle"
                    : TerminalChannels.numericMax(key) > 0 ? "channels.numeric" : "channels.native"));
        if ("casing".equals(key)) lines.add(tr("channels.shared"));
        return lines;
    }

    private int row(int i) {
        return top + 54 + i * 28;
    }

    private boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int glassTier() {
        if (settings.glassTier >= 0) return settings.glassTier;
        TerminalMaterials.Entry e = find(glasses, glass());
        return e.value == 0 ? -1 : e.tier;
    }

    private int glass() {
        return settings.channels.getOrDefault("glass", 0);
    }

    private List<TerminalMaterials.Entry> entries() {
        if (page > 0) return materials(channel(expanded));
        return expanded == 0 ? coils
            : glassMenu < 0 ? TerminalMaterials.glassTiers() : TerminalMaterials.variants(glassMenu);
    }

    private int selected() {
        if (page > 0) return settings.channels.getOrDefault(channel(expanded), 0);
        return expanded == 0 ? settings.coilTier : glassMenu >= 0 ? glass() : glassTier() < 0 ? 0 : -glassTier() - 1;
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
        TerminalStyle.palette();
        repeats.setTextColor(TerminalStyle.INK);
        drawDefaultBackground();
        int mx = (int) (mouseX / scale), my = (int) (mouseY / scale);
        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1);
        panel(left, top, MAIN);
        fontRendererObj.drawString(tr("advanced.title"), left + 16, top + 17, TerminalStyle.INK);
        TerminalStyle.check(left + MAIN - 33, top + 15, TerminalStyle.dark(), false);
        fontRendererObj.drawString(tr("modern.subtitle"), left + 16, top + 34, TerminalStyle.MUTED);
        String[] labels = { tr("advanced.1"), tr("modern.glass"), tr("advanced.2"), tr("modern.operation"),
            tr("advanced.3"), tr("modern.coilOnly"), tr("modern.keepHatches"), tr("advanced.5") };
        if (page > 0) {
            labels = new String[rowCount()];
            for (int i = 0; i < labels.length; i++) labels[i] = channelName(channel(i));
        }
        for (int i = 0; i < labels.length; i++) {
            int y = row(i);
            boolean hover = hit(mx, my, left + 10, y, MAIN - 20, 25);
            TerminalStyle.round(left + 10, y, MAIN - 20, 25, 7, hover || focus == i ? 0xFFEAF1FC : 0xFFF4F7FD);
            fontRendererObj.drawString(
                fontRendererObj.trimStringToWidth(labels[i], page == 0 && i == 3 ? 82 : 133),
                left + 18,
                y + 9,
                page == 0 && i == 5 && currentOperation() != TerminalSettings.REPLACE ? TerminalStyle.MUTED
                    : TerminalStyle.INK);
            if (page > 0) {
                String key = channel(i);
                int value = settings.channels.getOrDefault(key, 0);
                if (TerminalChannels.toggle(key)) {
                    TerminalStyle.check(left + 203, y + 5, value > 0, hover);
                } else if (numbers.containsKey(key)) {
                    TerminalStyle.round(left + 163, y + 4, 59, 17, 5, 0xFFFFFFFF);
                    numbers.get(key)
                        .setTextColor(TerminalStyle.INK);
                    numbers.get(key)
                        .drawTextBox();
                } else {
                    fontRendererObj.drawString(
                        value == 0 ? tr("modern.auto") : "#" + value,
                        left + 153,
                        y + 9,
                        TerminalStyle.MUTED);
                    TerminalStyle.round(left + 200, y + 3, 22, 19, 6, expanded == i ? 0xFFD7E6FC : 0xFFE3EBF8);
                    TerminalStyle.triangle(left + 208, y + 9, expanded == i);
                }
            } else if (i < 2) {
                TerminalMaterials.Entry entry = find(i == 0 ? coils : glasses, i == 0 ? settings.coilTier : glass());
                fontRendererObj.drawString(
                    i == 1 && glassTier() >= 0 ? GTValues.VN[glassTier()] : tier(entry),
                    left + 153,
                    y + 9,
                    TerminalStyle.MUTED);
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
        TerminalStyle.round(left + 10, top + 283, 24, 18, 6, 0xFFE3EBF8);
        TerminalStyle.triangle(left + 19, top + 288, true);
        TerminalStyle.round(left + MAIN - 34, top + 283, 24, 18, 6, 0xFFE3EBF8);
        TerminalStyle.triangle(left + MAIN - 25, top + 288, false);
        String pageName = tr("channels.page." + page) + "  " + (page + 1) + "/" + (TerminalChannels.PAGES.length + 1);
        fontRendererObj.drawString(
            pageName,
            left + (MAIN - fontRendererObj.getStringWidth(pageName)) / 2,
            top + 289,
            TerminalStyle.INK);
        fontRendererObj.drawString(tr("modern.save"), left + 18, top + 313, TerminalStyle.MUTED);
        List<String> tooltip = hit(mx, my, left + MAIN - 38, top + 10, 28, 28) ? Arrays.asList(tr("modern.dark"))
            : null;
        ItemStack hoveredItem = null;
        if (expanded >= 0) {
            int x = left + MAIN + GAP;
            panel(x, top, SIDE);
            fontRendererObj
                .drawString(
                    page > 0 ? channelName(channel(expanded))
                        : expanded == 0 ? tr("advanced.1")
                            : glassMenu < 0 ? tr("modern.glass")
                                : "‹ " + GTValues.VN[glassMenu] + " " + tr("modern.glass"),
                    x + 12,
                    top + 17,
                    TerminalStyle.INK);
            fontRendererObj
                .drawString(page > 0 ? channel(expanded) : tr("modern.order"), x + 12, top + 34, TerminalStyle.MUTED);
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
                    float oldLightX = net.minecraft.client.renderer.OpenGlHelper.lastBrightnessX;
                    float oldLightY = net.minecraft.client.renderer.OpenGlHelper.lastBrightnessY;
                    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
                    net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
                    GL11.glEnable(GL11.GL_DEPTH_TEST);
                    GL11.glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
                    GL11.glColor4f(1, 1, 1, 1);
                    net.minecraft.client.renderer.OpenGlHelper
                        .setLightmapTextureCoords(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, 240, 240);
                    codechicken.nei.guihook.GuiContainerManager.drawItem(x + 13, y + 3, entry.icon);
                    net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(
                        net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit,
                        oldLightX,
                        oldLightY);
                    GL11.glPopAttrib();
                } else {
                    fontRendererObj.drawString("A", x + 18, y + 8, TerminalStyle.MUTED);
                }
                String name = page == 0 ? tier(entry)
                    : entry.value == 0 ? tr("modern.auto")
                        : "#" + entry.value + " " + (entry.icon == null ? "" : entry.icon.getDisplayName());
                fontRendererObj
                    .drawString(fontRendererObj.trimStringToWidth(name, SIDE - 78), x + 39, y + 8, TerminalStyle.INK);
                TerminalStyle.check(x + SIDE - 31, y + 4, selected() == entry.value, hover);
                if (page == 0 && expanded == 1 && glassMenu < 0 && entry.value < 0)
                    TerminalStyle.triangle(x + SIDE - 48, y + 8, false);
                if (hover) {
                    if (entry.icon != null) hoveredItem = entry.icon;
                    else tooltip = page > 0 ? channelTooltip(channel(expanded))
                        : Arrays.asList(
                            tr("modern.auto"),
                            tr("modern.auto.tooltip"),
                            tr("channels.key", channel(expanded)));
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
            for (int i = 0; i < rowCount(); i++) if (hit(mx, my, left + 10, row(i), MAIN - 20, 25)) {
                if (page > 0) {
                    tooltip = channelTooltip(channel(i));
                    break;
                }
                String key = i == 1 ? "modern.glass.tooltip"
                    : i == 3 ? "modern.operation.tooltip"
                        : i == 5 ? "modern.coilOnly.tooltip"
                            : i == 6 ? "modern.keepHatches.tooltip"
                                : "advanced." + (i == 0 ? 1 : i == 2 ? 2 : i == 4 ? 3 : 5) + ".tooltip";
                tooltip = new ArrayList<>(
                    Arrays.asList(
                        tr(key).replace("\\n", "\n")
                            .split("\n")));
                if (i < 2) tooltip.add(tr("channels.key", channel(i)));
            }
        if (tooltip != null) tooltip(tooltip, mx, my);
        if (!status.isEmpty()) fontRendererObj.drawString(status, left, top + HEIGHT + 4, TerminalStyle.INK);
        GL11.glPopMatrix();
        if (hoveredItem != null) {
            // Same renderer, font choice and tooltip provider as NEI. Read every frame for Shift/Ctrl details.
            List<String> lines = new ArrayList<>(
                codechicken.nei.guihook.GuiContainerManager.itemDisplayNameMultiline(hoveredItem, null, true));
            lines.add("§7" + tr("channels.key", channel(expanded)));
            if (page > 0) lines.add("§7" + tr("channels.native"));
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

    }

    private void expand(int which) {
        expanded = expanded == which ? -1 : which;
        glassMenu = -1;
        scroll = 0;
        if (expanded >= 0) {
            List<TerminalMaterials.Entry> list = entries();
            for (int i = 0; i < list.size(); i++) if (list.get(i).value == selected()) scroll = (i / ROWS) * ROWS;
            scroll = Math.min(scroll, ((list.size() - 1) / ROWS) * ROWS);
        }
        initGui();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        int x = (int) (mouseX / scale), y = (int) (mouseY / scale);
        if (page == 0) repeats.mouseClicked(x, y, button);
        else for (GuiTextField field : numbers.values()) field.mouseClicked(x, y, button);
        if (button != 0) return;
        if (hit(x, y, left + MAIN - 38, top + 10, 28, 28)) {
            dev.aestoragevault.client.VaultClientPreferences.setTerminalDark(!TerminalStyle.dark());
            return;
        }
        if (hit(x, y, left + 10, top + 283, 24, 18)) {
            changePage(-1);
            return;
        }
        if (hit(x, y, left + MAIN - 34, top + 283, 24, 18)) {
            changePage(1);
            return;
        }
        for (int i = 0; i < rowCount(); i++) if (hit(x, y, left + 10, row(i), MAIN - 20, 25)) {
            focus = i;
            if (page > 0) {
                String key = channel(i);
                if (TerminalChannels.toggle(key)) {
                    if (settings.channels.containsKey(key)) settings.channels.remove(key);
                    else settings.channels.put(key, 1);
                } else if (!numbers.containsKey(key)) expand(i);
            } else if (i < 2) expand(i);
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
            if (page == 0 && expanded == 1 && glassMenu >= 0 && hit(x, y, sx + 8, top + 10, SIDE - 16, 26)) {
                glassMenu = -1;
                scroll = 0;
                return;
            }
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
        if (page > 0) {
            if (value == 0) settings.channels.remove(channel(expanded));
            else settings.channels.put(channel(expanded), value);
        } else if (expanded == 0) settings.coilTier = value;
        else if (glassMenu < 0) {
            if (value == 0) {
                settings.glassTier = -1;
                settings.channels.remove("glass");
            } else {
                int tier = -value - 1;
                if (glassTier() != tier) settings.channels.remove("glass");
                settings.glassTier = tier;
                glassMenu = tier;
                scroll = 0;
            }
        } else {
            settings.glassTier = glassMenu;
            if (value == 0) settings.channels.remove("glass");
            else settings.channels.put("glass", value);
        }
    }

    private void scroll(int delta) {
        // Fixed pages: the final page may be short; never pull earlier entries back to fill it.
        int lastPage = Math.max(0, (entries().size() - 1) / ROWS);
        int page = Math.max(0, Math.min(lastPage, scroll / ROWS + Integer.signum(delta)));
        scroll = page * ROWS;
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
        if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN
            || key == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(null);
        } else if (key == Keyboard.KEY_TAB) {
            focus = (focus + (isShiftKeyDown() ? rowCount() - 1 : 1)) % rowCount();
            repeats.setFocused(page == 0 && focus == 2);
            for (Map.Entry<String, GuiTextField> e : numbers.entrySet()) e.getValue()
                .setFocused(
                    e.getKey()
                        .equals(channel(focus)));
        } else if (key == Keyboard.KEY_PRIOR || key == Keyboard.KEY_NEXT) {
            changePage(key == Keyboard.KEY_PRIOR ? -1 : 1);
        } else if (page > 0 && numbers.values()
            .stream()
            .anyMatch(GuiTextField::isFocused)) {
                for (Map.Entry<String, GuiTextField> e : numbers.entrySet()) if (e.getValue()
                    .isFocused()) {
                        GuiTextField field = e.getValue();
                        String before = field.getText();
                        field.textboxKeyTyped(c, key);
                        if (!field.getText()
                            .matches("[0-9]{0,2}") || number(field, 99) > TerminalChannels.numericMax(e.getKey()))
                            field.setText(before);
                        saveNumbers();
                    }
            } else if (page == 0 && repeats.isFocused()) {
                String before = repeats.getText();
                repeats.textboxKeyTyped(c, key);
                if (!repeats.getText()
                    .matches("[0-9]{0,2}")) repeats.setText(before);
            } else if (key == Keyboard.KEY_SPACE) {
                if (page > 0) {
                    String channel = channel(focus);
                    if (TerminalChannels.toggle(channel)) {
                        if (settings.channels.containsKey(channel)) settings.channels.remove(channel);
                        else settings.channels.put(channel, 1);
                    } else if (!numbers.containsKey(channel)) expand(focus);
                } else if (focus < 2) expand(focus);
                else if (focus == 3) operation((currentOperation() + 1) % 3);
                else if (focus == 4) settings.noHatches = !settings.noHatches;
                else if (focus == 5 && currentOperation() == TerminalSettings.REPLACE)
                    settings.replaceCoils = !settings.replaceCoils;
                else if (focus == 6) settings.keepHatches = !settings.keepHatches;
                else if (focus == 7) settings.network = !settings.network;
            } else if (expanded >= 0 && (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN)) {
                List<TerminalMaterials.Entry> list = entries();
                for (int i = 0; i < list.size(); i++) if (list.get(i).value == selected()) {
                    int direction = key == Keyboard.KEY_UP ? -1 : 1;
                    int next = i + direction;
                    // Several native indicator items may represent the same value. Skip those aliases.
                    while (next >= 0 && next < list.size() && list.get(next).value == selected()) next += direction;
                    if (next < 0 || next >= list.size()) break;
                    int oldGlassMenu = glassMenu;
                    choose(list.get(next).value);
                    if (glassMenu == oldGlassMenu) {
                        scroll = (next / ROWS) * ROWS;
                    }
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
        else if (page == 0 && hit(x, y, left + 163, row(2), 59, 25))
            repeats.setText(Integer.toString(Math.max(0, Math.min(99, repeatValue() + (wheel > 0 ? 1 : -1)))));
    }

    @Override
    public void updateScreen() {
        repeats.updateCursorCounter();
        for (GuiTextField field : numbers.values()) field.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (settings == null || mc.thePlayer == null || mc.thePlayer.inventory.currentItem != slot) return;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || held.getItem() != StructureTerminal.item) return;
        settings.repeatCount = repeatValue();
        saveNumbers();
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

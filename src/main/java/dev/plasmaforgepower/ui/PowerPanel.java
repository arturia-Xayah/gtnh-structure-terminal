package dev.plasmaforgepower.ui;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.function.Supplier;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.ObjectValue;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.menu.DropdownWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import dev.plasmaforgepower.core.PowerLimit;
import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTUtility;

/** Native ModularUI 2 panel matching the approved blue/white preview; no embedded browser. */
public final class PowerPanel {

    private final MTEMultiBlockBase multiblock;

    private static final int WIDTH = 386, HEIGHT = 272;
    private static final int TEXT = 0xff203b50, SUB = 0xff536d82, BLUE = 0xff176aab;
    private static final int BG = 0xffe8eef4, PANEL = 0xfff6f9fc, LINE = 0xffb6c8d8, ACTIVE = 0xffd2eafd;
    private static final int[] COLOR = { 0xff292c30, 0xffc5484a, 0xff538548, 0xff805432, 0xff3e59b2, 0xff8142ab,
        0xff308b96, 0xffa6a9a4, 0xff555b61, 0xffe995af, 0xff83b940, 0xffe2cc44, 0xff6cb5db, 0xffba59c4, 0xffe89535,
        0xffeeeeee };

    public PowerPanel(MTEMultiBlockBase machine) {
        this.multiblock = machine;
    }

    public ModularPanel attach(ModularPanel main, PanelSyncManager sync) {
        IPanelHandler power = sync
            .syncedPanel("pfp_color_power", true, (panelSync, handler) -> createPowerPanel(panelSync));
        main.child(
            new ButtonWidget<>().size(18)
                .right(-22)
                .top(23)
                .background(fill(ACTIVE), outline(LINE))
                .overlay(
                    IKey.str("P")
                        .color(BLUE))
                .tooltip(t -> t.addLine(tr("title")))
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    if (power.isPanelOpen()) power.closePanel();
                    else power.openPanel();
                    return true;
                }));
        return main;
    }

    public ModularPanel createPowerPanel(PanelSyncManager sync) {
        PowerEditor editor = new PowerEditor();
        PowerConfigSync config = new PowerConfigSync(multiblock, editor);
        sync.syncValue("pfp_config", config);
        ModularPanel panel = new ModularPanel("pfp_color_power").size(WIDTH, HEIGHT)
            .background(fill(BG), outline(LINE));
        panel.child(
            fill(PANEL).asWidget()
                .pos(1, 1)
                .size(WIDTH - 2, 35));
        panel.child(text(() -> multiblock.getLocalName(), 14, 9, 330, 12).scale(1.1f));
        panel.child(
            text(() -> tr("title"), 14, 24, 330, 9).scale(.8f)
                .color(SUB));
        panel.child(
            new ButtonWidget<>().pos(WIDTH - 24, 8)
                .size(16)
                .background(fill(PANEL))
                .overlay(
                    IKey.str("×")
                        .color(SUB))
                .onMousePressed(button -> {
                    if (button == 0) panel.closeIfOpen();
                    return true;
                }));
        panel.child(
            fill(LINE).asWidget()
                .pos(0, 36)
                .size(WIDTH, 1));
        panel.child(
            text(
                () -> tr(
                    multiblock instanceof dev.plasmaforgepower.api.FusionPowerAccess ? "processing_supply" : "supply")
                    + "  "
                    + supplyText(editor),
                12,
                43,
                285,
                10).scale(.8f)
                    .tooltip(
                        t -> t.addLine(
                            IKey.dynamic(
                                () -> editor.formed ? PowerEditor.format(editor.ratedPower) + " EU/t"
                                    : tr("waiting")))));
        panel.child(
            text(() -> tr(editor.formed ? "formed" : "unformed"), 303, 43, 72, 10).scale(.8f)
                .color(BLUE));
        panel.child(
            fill(LINE).asWidget()
                .pos(0, 59)
                .size(WIDTH, 1));
        panel.child(
            fill(LINE).asWidget()
                .pos(134, 60)
                .size(1, 178));
        panel.child(
            text(() -> tr("groups"), 12, 70, 114, 10).scale(.8f)
                .color(SUB));

        for (int position = 0; position < 16; position++) {
            final int color = 15 - position;
            int x = 10 + (position % 4) * 30, y = 87 + (position / 4) * 29;
            ButtonWidget<?> button = new ButtonWidget<>().pos(x, y)
                .size(26, 26)
                .background(
                    new DynamicDrawable(
                        () -> IDrawable.of(
                            fill(editor.selected == color ? ACTIVE : PANEL),
                            outline(editor.selected == color ? BLUE : LINE))))
                .hoverBackground(fill(ACTIVE), outline(BLUE))
                .onMousePressed(mouse -> {
                    if (mouse == 0 && !editor.pending) editor.selected = color;
                    return true;
                });
            button.child(
                fill(COLOR[color]).asWidget()
                    .pos(9, 4)
                    .size(8));
            button.child(
                text(() -> colorName(color), 1, 15, 24, 8).scale(.65f)
                    .alignment(Alignment.Center));
            button.child(
                new DynamicDrawable(
                    () -> editor.dirty(color) || !editor.saved.get(color)
                        .isAuto() ? fill(BLUE) : IDrawable.EMPTY).asWidget()
                            .pos(22, 2)
                            .size(2));
            panel.child(button);
        }
        panel.child(
            new ButtonWidget<>().pos(10, 207)
                .size(116, 18)
                .background(
                    new DynamicDrawable(
                        () -> IDrawable.of(
                            fill(editor.selected == -1 ? ACTIVE : PANEL),
                            outline(editor.selected == -1 ? BLUE : LINE))))
                .overlay(
                    IKey.dynamic(() -> colorName(-1))
                        .color(TEXT))
                .onMousePressed(mouse -> {
                    if (mouse == 0 && !editor.pending) editor.selected = -1;
                    return true;
                }));
        panel.child(
            text(() -> tr("marker"), 10, 229, 118, 8).scale(.65f)
                .color(SUB));

        panel.child(
            new DynamicDrawable(() -> fill(editor.selected < 0 ? LINE : COLOR[editor.selected])).asWidget()
                .pos(148, 73)
                .size(9));
        panel.child(text(() -> colorName(editor.selected) + tr("input"), 164, 72, 150, 12));
        panel.child(
            text(() -> tr(editor.isAutomatic() ? "auto" : "manual"), 316, 74, 58, 9).scale(.8f)
                .color(BLUE));
        panel.child(
            text(() -> tr("amps"), 148, 99, 100, 10).scale(.8f)
                .color(SUB));
        panel.child(
            text(() -> tr("voltage"), 267, 99, 104, 10).scale(.8f)
                .color(SUB));
        panel.child(
            new TextFieldWidget().pos(148, 113)
                .size(104, 22)
                .setMaxLength(32)
                .autoUpdateOnChange(true)
                .value(new StringValue.Dynamic(editor::amps, editor::setAmps))
                .background(fill(PANEL), outline(LINE))
                .setTextColor(TEXT));
        panel.child(
            new PowerTierDropdown(editor).pos(267, 113)
                .size(105, 22)
                .background(fill(PANEL), outline(LINE))
                .options(PowerLimit.displayOrder())
                .value(new ObjectValue.Dynamic<Integer>(Integer.class, editor::tier, editor::setTier))
                .optionToWidget(
                    (tier,
                        selected) -> text(
                            () -> PowerLimit.TIERS[selected ? editor.tier() : tier] + (selected ? "  ▾" : ""),
                            0,
                            0,
                            103,
                            20).alignment(Alignment.Center)
                                .color(() -> editor.isAutomatic() ? SUB : TEXT)));
        panel.child(
            text(() -> tr("automatic_hint"), 148, 141, 224, 10).scale(.7f)
                .color(SUB));
        panel.child(
            fill(0xffdce6ef).asWidget()
                .pos(148, 160)
                .size(224, 49));
        panel.child(
            text(() -> tr("preview"), 156, 167, 112, 9).scale(.75f)
                .color(SUB));
        panel.child(text(() -> {
            long power = editor.previewPower();
            if (!editor.formed || power < 0 || editor.ratedPower <= 0) return "—";
            double percent = 100d * power / editor.ratedPower;
            return percent > 100 ? ">100%" : String.format(java.util.Locale.ROOT, "%.3f%%", percent);
        }, 288, 167, 76, 9).scale(.75f)
            .alignment(Alignment.CenterRight)
            .color(SUB));
        panel.child(text(editor::previewText, 156, 181, 208, 13).scale(1.05f));
        panel.child(
            fill(0xffc4d1dc).asWidget()
                .pos(156, 201)
                .size(208, 3));
        panel.child(
            fill(BLUE).asWidget()
                .pos(156, 201)
                .size(0, 3)
                .onUpdateListener(widget -> {
                    long power = editor.previewPower();
                    int width = !editor.formed || power < 0 || editor.ratedPower <= 0 ? 0
                        : (int) Math.min(208, 208d * power / editor.ratedPower);
                    widget.width(width);
                }, true));
        panel.child(
            text(() -> status(editor), 148, 215, 224, 18).scale(.75f)
                .color(() -> editor.overBudget() || !editor.valid() ? 0xff8c4c00 : SUB));
        panel.child(
            fill(LINE).asWidget()
                .pos(0, 239)
                .size(WIDTH, 1));
        panel.child(
            text(
                () -> tr(editor.pending ? "saving" : editor.dirtyCount() > 0 ? "unsaved" : "next_recipe"),
                12,
                251,
                276,
                10).scale(.75f)
                    .color(SUB));
        panel.child(
            new ButtonWidget<>().pos(300, 246)
                .size(73, 20)
                .background(fill(0xff246b9e))
                .hoverBackground(fill(BLUE))
                .overlay(
                    IKey.dynamic(() -> tr("save"))
                        .color(0xffffffff))
                .onMousePressed(mouse -> {
                    if (mouse == 0) config.save();
                    return true;
                }));
        return panel;
    }

    private static String supplyText(PowerEditor editor) {
        if (!editor.formed) return tr("waiting");
        String power = supplyPower(editor.ratedPower) + " EU/t";
        if (editor.ratedPower <= 0 || editor.ratedVoltage <= 0) return power;
        int tier = GTUtility.getTier(editor.ratedVoltage);
        String voltage = GTValues.V[tier] == editor.ratedVoltage ? GTValues.VN[tier]
            : PowerEditor.format(editor.ratedVoltage) + "V";
        BigDecimal current = BigDecimal.valueOf(editor.ratedPower)
            .divide(BigDecimal.valueOf(editor.ratedVoltage), 3, RoundingMode.HALF_UP);
        String amps = editor.ratedPower % editor.ratedVoltage == 0 ? current.toBigInteger()
            .toString()
            : current.signum() == 0 ? "<0.001"
                : "≈" + current.stripTrailingZeros()
                    .toPlainString();
        return power + " (" + amps + "A " + voltage + ")";
    }

    private static String supplyPower(long power) {
        if (power < 1_000_000_000L) return PowerEditor.format(power);
        BigDecimal rounded = BigDecimal.valueOf(power)
            .round(new MathContext(3, RoundingMode.HALF_UP));
        int exponent = rounded.precision() - rounded.scale() - 1;
        return "≈" + rounded.movePointLeft(exponent)
            .stripTrailingZeros()
            .toPlainString() + "×10^" + exponent;
    }

    private static String status(PowerEditor editor) {
        if (!editor.valid()) return tr("invalid");
        if (editor.overBudget()) return tr("over_budget");
        if (editor.message.equals("clamped"))
            return StatCollector.translateToLocalFormatted("pfp.clamped", editor.changes);
        if (!editor.formed && editor.message.equals("next_recipe")) return tr("deferred");
        return tr(editor.message);
    }

    private static String colorName(int color) {
        return tr("color." + color);
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal("pfp." + key);
    }

    private static Rectangle fill(int color) {
        return new Rectangle().color(color);
    }

    private static Rectangle outline(int color) {
        return new Rectangle().color(color)
            .hollow(1);
    }

    private static TextWidget<?> text(Supplier<String> value, int x, int y, int width, int height) {
        return new TextWidget<>(IKey.dynamic(value)).pos(x, y)
            .size(width, height)
            .color(TEXT)
            .shadow(false)
            .alignment(Alignment.CenterLeft);
    }

    private static final class PowerTierDropdown extends DropdownWidget<Integer, PowerTierDropdown> {

        private final PowerEditor editor;

        private PowerTierDropdown(PowerEditor editor) {
            super("pfp_voltage", Integer.class);
            this.editor = editor;
        }

        @Override
        public Result onMousePressed(int button) {
            return editor.pending || editor.isAutomatic() ? Result.STOP : super.onMousePressed(button);
        }
    }
}

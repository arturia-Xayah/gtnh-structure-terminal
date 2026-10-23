package dev.aestoragevault.ui;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.aestoragevault.client.VaultButton;
import dev.aestoragevault.client.VaultClientPreferences;
import dev.aestoragevault.core.ByteFormat;
import dev.aestoragevault.ui.dashboard.VaultAnimation;
import dev.aestoragevault.ui.dashboard.VaultLayout;
import dev.aestoragevault.ui.dashboard.VaultMesh;
import dev.aestoragevault.world.TileController;

@SideOnly(Side.CLIENT)
public final class VaultScreen extends GuiContainer {

    private final TileController tile;
    private final VaultAnimation animation = new VaultAnimation();
    private final VaultMesh mesh = new VaultMesh();
    private VaultLayout layout;
    private VaultAnimation.Frame frame;
    private VaultButton autoButton, animationButton;
    private BigInteger lastCapacity, lastUsed;
    private String capacityText = "0 B", usedText = "0 B", freeText = "0 B";

    public VaultScreen(EntityPlayer player, TileController tile) {
        super(new VaultContainer(player, tile));
        this.tile = tile;
    }

    @Override
    public void initGui() {
        layout = VaultLayout.fit(width, height);
        xSize = layout.width;
        ySize = layout.height;
        super.initGui();
        for (int i = 0; i < inventorySlots.inventorySlots.size(); i++) {
            Slot slot = (Slot) inventorySlots.inventorySlots.get(i);
            slot.xDisplayPosition = layout.slotX(i);
            slot.yDisplayPosition = layout.slotY(i);
        }
        buttonList.clear();
        int x = guiLeft + layout.statusX + 10, w = layout.buttonWidth();
        autoButton = new VaultButton(0, x, guiTop + layout.automaticY, w, "");
        animationButton = new VaultButton(2, x + w / 2 + 2, guiTop + layout.controlsY, w - w / 2 - 2, "");
        buttonList.add(autoButton);
        buttonList.add(new VaultButton(1, x, guiTop + layout.controlsY, w / 2 - 2, tr("checkShort")));
        buttonList.add(animationButton);
        animation.setEnabled(VaultClientPreferences.animations());
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 2) {
            VaultClientPreferences.setAnimations(!VaultClientPreferences.animations());
            animation.setEnabled(VaultClientPreferences.animations());
        } else if (button.id == 0 || button.id == 1) {
            mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float ticks, int mouseX, int mouseY) {
        frame = animation.sample(
            System.nanoTime() / 1000000L,
            tile.capacity,
            tile.used,
            tile.types,
            tile.formed && !tile.status.startsWith("LOCKED:"));
        boolean capacityChanged = !frame.capacity.equals(lastCapacity);
        if (capacityChanged) capacityText = ByteFormat.format(frame.capacity);
        if (capacityChanged || !frame.used.equals(lastUsed)) {
            lastCapacity = frame.capacity;
            lastUsed = frame.used;
            usedText = ByteFormat.format(frame.used);
            freeText = ByteFormat.format(frame.capacity.subtract(frame.used));
        }
        autoButton.displayString = tr(tile.automatic ? "absorptionOn" : "absorptionOff");
        autoButton.active = tile.automatic;
        animationButton.displayString = tr(animation.enabled() ? "motionOn" : "motionOff");
        animationButton.active = animation.enabled();
        mesh.build(layout, frame, tile.formed && !tile.status.startsWith("LOCKED:"), tile.automatic);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(guiLeft, guiTop, 0);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            Tessellator t = Tessellator.instance;
            t.startDrawing(GL11.GL_TRIANGLES);
            for (int i = 0; i < mesh.size(); i++) {
                t.setColorRGBA_I(mesh.color(i) & 0xffffff, mesh.color(i) >>> 24);
                t.addVertex(mesh.x(i), mesh.y(i), 0);
            }
            t.draw();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        if (frame == null) return;
        text(I18n.format("vault.title"), 12, 8, 0x244560);
        text("STORAGE CONTROL", 12, 19, 0x6d91af);
        text(
            tr(tile.status.startsWith("LOCKED:") ? "lockedShort" : tile.formed ? "formedShort" : "incompleteShort"),
            layout.width - 63,
            13,
            0x56728b);
        int capLabelY = layout.compact ? 39 : 47;
        text(I18n.format("vault.capacity"), layout.metricX, capLabelY, 0x9cbed6);
        largeText(capacityText, layout.metricX, capLabelY + 14, layout.compact ? 1.25f : 1.55f);
        int usedY = layout.compact ? 77 : 91, freeY = layout.compact ? 94 : 113;
        text(I18n.format("vault.used"), layout.metricX, usedY, 0x9cbed6);
        right(usedText, layout.width - 22, usedY, 0xe1f2ff);
        text(tr("free"), layout.metricX, freeY, 0x9cbed6);
        right(freeText, layout.width - 22, freeY, 0xe1f2ff);
        int tenths = (int) Math.round(frame.ratio * 1000);
        String percentage = frame.capacity.signum() == 0 ? "—" : (tenths / 10) + "." + (tenths % 10) + "%";
        center(percentage, layout.gaugeX, layout.gaugeY - 6, 0xe9f8ff);
        center(tr("utilization"), layout.gaugeX, layout.gaugeY + 9, 0x99bad3);
        if (!layout.compact) text(tr("types"), 12, layout.typesY, 0x456681);
        right(I18n.format("vault.dashboard.typeCount", frame.types), 174, layout.typesY, 0x244560);
        text(I18n.format("container.inventory"), 12, layout.inventoryLabelY, 0x6c87a0);
        int sx = layout.statusX + 10, sy = layout.statusY;
        text(tr("state"), sx, sy + (layout.compact ? 7 : 11), 0x52738f);
        text(
            tile.status.startsWith("LOCKED:") ? tr("lockedShort")
                : I18n.format(tile.formed ? "vault.formed" : "vault.incomplete"),
            sx,
            sy + (layout.compact ? 22 : 29),
            tile.status.startsWith("LOCKED:") ? 0xa05f58 : tile.formed ? 0x345d7c : 0xa07647);
        text(tr(tile.automatic ? "automatic" : "paused"), sx, sy + (layout.compact ? 36 : 44), 0x52738f);
        if (!layout.compact) {
            String status = tile.status.startsWith("LOCKED:") ? tr("locked")
                : !tile.formed ? I18n.format("vault.incomplete") : tr(tile.automatic ? "idle" : "paused");
            text(fontRendererObj.trimStringToWidth(status, layout.width - 24), 12, 273, 0x68849b);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        super.drawScreen(mouseX, mouseY, partial);
        int x = mouseX - guiLeft, y = mouseY - guiTop;
        List<String> tips = new ArrayList<>();
        if (x >= layout.metricX && x < layout.width - 12
            && y >= layout.panelY
            && y < layout.panelY + layout.panelHeight) {
            tips.add(tr("actual"));
            tips.add(I18n.format("vault.capacity") + ": " + exact(tile.capacity));
            tips.add(I18n.format("vault.used") + ": " + exact(tile.used));
            tips.add(
                tr("free") + ": "
                    + exact(
                        tile.capacity.subtract(tile.used)
                            .max(BigInteger.ZERO)));
        } else if (x >= 90 && x < 176 && y >= layout.typesY - 1 && y < layout.typesY + 11) {
            tips.add(tr("types"));
            tips.add(I18n.format("vault.dashboard.typeCount", tile.types));
            tips.add(tr("typeHint"));
        } else if (x >= layout.statusX && x < layout.width - 10 && y >= layout.controlsY && y < layout.controlsY + 18) {
            if (x >= layout.statusX + 10 + layout.buttonWidth() / 2) tips.add(tr("motionHint"));
        } else if ((x >= layout.statusX && x < layout.width - 10 && y >= layout.statusY && y < layout.automaticY)
            || (!layout.compact && x >= 12 && x < layout.width - 12 && y >= 271 && y < layout.height - 1)) {
                tips.addAll(fontRendererObj.listFormattedStringToWidth(tile.status, Math.min(260, width - 30)));
            }
        if (!tips.isEmpty()) drawHoveringText(tips, mouseX, mouseY, fontRendererObj);
    }

    private static String exact(BigInteger value) {
        String text = value.toString();
        return (text.length() <= 36 ? text
            : new BigDecimal(value).round(new MathContext(6))
                .toString())
            + " B";
    }

    private static String tr(String key) {
        return I18n.format("vault.dashboard." + key);
    }

    private void text(String s, int x, int y, int color) {
        fontRendererObj.drawString(s, x, y, color);
    }

    private void right(String s, int right, int y, int color) {
        text(s, right - fontRendererObj.getStringWidth(s), y, color);
    }

    private void center(String s, int x, int y, int color) {
        text(s, x - fontRendererObj.getStringWidth(s) / 2, y, color);
    }

    private void largeText(String s, int x, int y, float scale) {
        float fit = Math.min(scale, (layout.width - x - 20f) / Math.max(1, fontRendererObj.getStringWidth(s)));
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        GL11.glScalef(fit, fit, 1);
        text(s, 0, 0, 0xe7f6ff);
        GL11.glPopMatrix();
    }
}

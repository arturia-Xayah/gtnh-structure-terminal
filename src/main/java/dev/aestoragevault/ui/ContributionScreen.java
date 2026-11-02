package dev.aestoragevault.ui;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.aestoragevault.client.VaultButton;
import dev.aestoragevault.core.ByteFormat;
import dev.aestoragevault.core.CellContribution;
import dev.aestoragevault.ui.dashboard.ContributionLayout;
import dev.aestoragevault.ui.dashboard.VaultAnimation;
import dev.aestoragevault.world.TileController;

@SideOnly(Side.CLIENT)
public final class ContributionScreen extends GuiContainer {

    private final TileController tile;
    private GuiButton previous, next;
    private ContributionLayout layout;
    private List<CellContribution> cachedPage;
    private BigInteger cachedCapacity;
    private String capacityLabel = "0 B";
    private final List<Row> rows = new ArrayList<>();
    private boolean popup, awaiting;
    private String selectedKey, selectedName, feedback = "";
    private BigInteger maximum = BigInteger.ZERO;
    private String maximumLabel = "0";
    private GuiTextField quantity;
    private GuiButton all, confirm, cancel;
    private int seenReply;

    public ContributionScreen(TileController tile) {
        super(new ContributionContainer(tile));
        this.tile = tile;
        xSize = ContributionLayout.WIDTH;
        ySize = 266;
    }

    @Override
    public void initGui() {
        layout = ContributionLayout.fit(height);
        xSize = ContributionLayout.WIDTH;
        ySize = layout.height;
        cachedPage = null;
        super.initGui();
        buttonList.clear();
        buttonList.add(new VaultButton(0, guiLeft + 10, guiTop + layout.controlsY, 64, tr("back")));
        previous = new VaultButton(1, guiLeft + 178, guiTop + layout.controlsY, 52, tr("previous"));
        next = new VaultButton(2, guiLeft + 236, guiTop + layout.controlsY, 58, tr("next"));
        buttonList.add(previous);
        buttonList.add(next);
        all = new VaultButton(3, guiLeft + 179, guiTop + 105, 105, tr("all"));
        confirm = new VaultButton(4, guiLeft + 26, guiTop + 138, 122, tr("eject"));
        cancel = new VaultButton(5, guiLeft + 160, guiTop + 138, 124, tr("cancel"));
        buttonList.add(all);
        buttonList.add(confirm);
        buttonList.add(cancel);
        quantity = new GuiTextField(fontRendererObj, guiLeft + 26, guiTop + 105, 140, 18);
        quantity.setMaxStringLength(10000);
        quantity.setText("1");
        visibility();
    }

    private void visibility() {
        for (Object raw : buttonList) {
            GuiButton b = (GuiButton) raw;
            b.visible = popup ? b.id >= 3 : b.id < 3;
        }
    }

    private void clampQuantity() {
        try {
            BigInteger n = dev.aestoragevault.core.ReturnNumbers.clamp(quantity.getText(), maximum);
            if (new BigInteger(quantity.getText()).compareTo(maximum) > 0) quantity.setText(n.toString());
            quantity.setTextColor(0xffe3f3ff);
            confirm.enabled = !awaiting && n.signum() > 0 && tile.historyReturnBus;
        } catch (IllegalArgumentException e) {
            confirm.enabled = false;
            quantity.setTextColor(0xffa05f58);
        }
        all.enabled = !awaiting && maximum.signum() > 0;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id < 3) mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
        else if (button.id == 3) {
            quantity.setText(maximum.toString());
            clampQuantity();
        } else if (button.id == 5) {
            popup = false;
            visibility();
        } else if (button.id == 4 && !awaiting) {
            clampQuantity();
            if (!confirm.enabled) return;
            ReturnNetwork.channel
                .sendToServer(new ReturnNetwork.Request(inventorySlots.windowId, selectedKey, quantity.getText()));
            awaiting = true;
            seenReply = tile.returnReplyVersion;
            feedback = tr("working");
            clampQuantity();
        }
    }

    private boolean ready() {
        return tile.historyWindow == inventorySlots.windowId;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float ticks, int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xffe4edf6);
        drawRect(guiLeft + 5, guiTop + 5, guiLeft + xSize - 5, guiTop + 49, 0xff192f48);
        for (int i = 0; i < ContributionLayout.ROWS; i++) {
            int y = guiTop + layout.rowY(i), bottom = y + layout.rowHeight - 2;
            drawRect(guiLeft + 8, y, guiLeft + xSize - 8, bottom, i % 2 == 0 ? 0xffd5e2f1 : 0xffdfe9f5);
            if (ready() && i < rows.size()) {
                drawRect(guiLeft + 36, bottom - 2, guiLeft + 291, bottom, 0xffb8cade);
                int length = rows.get(i).barPixels;
                if (length > 0) drawRect(guiLeft + 36, bottom - 2, guiLeft + 36 + length, bottom, 0xff4b9ed9);
            }
        }
        previous.enabled = ready() && tile.historyIndex > 0;
        next.enabled = ready() && tile.historyIndex + 1 < tile.historyPages;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        text(tr("title"), 12, 10, 0xffe9f8ff);
        text(tr("total") + ": " + capacityLabel, 12, 26, 0xffb3d6f3);
        if (!ready()) {
            text(tr("loading"), 12, 64, 0xff496883);
            return;
        }
        if (tile.historyPage.isEmpty()) text(tr("empty"), 12, 64, 0xff496883);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = layout.rowY(i) + 1;
            if (row.icon != null) {
                GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT);
                GL11.glPushMatrix();
                try {
                    RenderHelper.enableGUIStandardItemLighting();
                    itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), row.icon, 12, y + 2);
                } finally {
                    GL11.glPopMatrix();
                    GL11.glPopAttrib();
                }
            }
            int availableName = Math.max(20, 291 - 36 - fontRendererObj.getStringWidth(row.count) - 8);
            text(fontRendererObj.trimStringToWidth(row.name, availableName), 36, y, 0xff244560);
            right(row.count, 291, y, 0xff244560);
            text(row.bytes, 36, y + 10, 0xff496883);
            right(row.percentage, 291, y + 10, 0xff496883);
        }
        text((tile.historyIndex + 1) + " / " + tile.historyPages, 96, layout.controlsY + 6, 0xff496883);
    }

    /** A page is immutable until its replacement packet: format large numbers and resolve icons only on change. */
    private void refreshRows() {
        if (!ready() || cachedPage == tile.historyPage && tile.historyCapacity.equals(cachedCapacity)) return;
        cachedPage = tile.historyPage;
        cachedCapacity = tile.historyCapacity;
        capacityLabel = ByteFormat.format(cachedCapacity);
        rows.clear();
        for (CellContribution c : cachedPage) rows.add(new Row(c, cachedCapacity));
    }

    @Override
    public void drawScreen(int x, int y, float partial) {
        refreshRows();
        if (popup && awaiting
            && tile.returnReplyWindow == inventorySlots.windowId
            && tile.returnReplyVersion != seenReply) {
            awaiting = false;
            seenReply = tile.returnReplyVersion;
            feedback = I18n.format("vault.history.result." + tile.returnMessage, tile.returnedCount);
            clampQuantity();
        }
        if (popup && ready()) {
            CellContribution c = tile.historyPage.stream()
                .filter(
                    entry -> entry.key()
                        .equals(selectedKey))
                .findFirst()
                .orElse(null);
            BigInteger updated = c == null ? BigInteger.ZERO : c.count;
            if (!updated.equals(maximum)) {
                maximum = updated;
                maximumLabel = number(maximum);
                clampQuantity();
            }
        }
        super.drawScreen(x, y, partial);
        if (popup) {
            drawPopup(x, y);
            return;
        }
        if (!ready()) return;
        int row = layout.hoveredRow(x - guiLeft, y - guiTop);
        if (row >= 0 && row < rows.size()) drawHoveringText(rows.get(row).tips, x, y, fontRendererObj);
    }

    private void drawPopup(int mouseX, int mouseY) {
        int x = guiLeft + 12, y = guiTop + 43;
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0x99203145);
        drawRect(x, y, x + 280, y + 137, 0xffe4edf6);
        drawRect(x, y, x + 280, y + 21, 0xff192f48);
        fontRendererObj.drawString(tr("ejectTitle"), x + 12, y + 6, 0xffe9f8ff);
        fontRendererObj.drawString(fontRendererObj.trimStringToWidth(selectedName, 250), x + 12, y + 28, 0xff244560);
        fontRendererObj.drawString(tr("maximum") + ": " + maximumLabel, x + 12, y + 43, 0xff496883);
        quantity.drawTextBox();
        fontRendererObj
            .drawString(fontRendererObj.trimStringToWidth(tr("partialHint"), 252), x + 12, y + 82, 0xff496883);
        fontRendererObj.drawString(fontRendererObj.trimStringToWidth(feedback, 252), x + 12, y + 122, 0xff496883);
        all.drawButton(mc, mouseX, mouseY);
        confirm.drawButton(mc, mouseX, mouseY);
        cancel.drawButton(mc, mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        if (popup) {
            if (x < guiLeft + 12 || x >= guiLeft + 292 || y < guiTop + 43 || y >= guiTop + 180) return;
            quantity.mouseClicked(x, y, button);
            super.mouseClicked(x, y, button);
            return;
        }
        int row = layout.hoveredRow(x - guiLeft, y - guiTop);
        if (button == 0 && ready() && row >= 0 && row < tile.historyPage.size()) {
            if (!tile.historyReturnBus) return;
            CellContribution c = tile.historyPage.get(row);
            selectedKey = c.key();
            maximum = c.count;
            maximumLabel = number(maximum);
            ItemStack icon = icon(c);
            selectedName = icon == null ? c.item : icon.getDisplayName();
            popup = true;
            awaiting = false;
            feedback = "";
            quantity.setText("1");
            quantity.setFocused(true);
            visibility();
            clampQuantity();
            return;
        }
        super.mouseClicked(x, y, button);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (quantity != null && popup) quantity.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (!popup) {
            super.keyTyped(character, key);
            return;
        }
        if (key == 1) {
            popup = false;
            visibility();
            return;
        }
        if (!awaiting && quantity.textboxKeyTyped(character, key)) clampQuantity();
    }

    private static final class Row {

        final ItemStack icon;
        final String name, count, bytes, percentage;
        final List<String> tips = new ArrayList<>();
        final int barPixels;

        Row(CellContribution c, BigInteger capacity) {
            icon = icon(c);
            name = icon == null ? c.item : icon.getDisplayName();
            count = "× " + countLabel(c.count);
            bytes = ByteFormat.format(c.bytes);
            percentage = c.percentage(capacity);
            barPixels = (int) Math.round(255 * VaultAnimation.fraction(capacity, c.bytes));
            tips.add(name);
            tips.add(c.item + "@" + c.damage);
            tips.add(tr("count") + ": " + number(c.count));
            tips.add(tr("contributed") + ": " + number(c.bytes) + " B");
            tips.add(tr("share") + ": " + percentage);
            tips.add(tr("clickReturn"));
        }
    }

    private static ItemStack icon(CellContribution c) {
        Object item = Item.itemRegistry.getObject(c.item);
        return item instanceof Item ? new ItemStack((Item) item, 1, c.damage) : null;
    }

    private static String countLabel(BigInteger n) {
        return n.toString()
            .length() <= 12 ? n.toString()
                : new BigDecimal(n).round(new MathContext(6))
                    .toString();
    }

    private static String number(BigInteger n) {
        return n.toString()
            .length() <= 24 ? n.toString()
                : new BigDecimal(n).round(new MathContext(8))
                    .toString();
    }

    private static String tr(String key) {
        return I18n.format("vault.history." + key);
    }

    private void text(String s, int x, int y, int color) {
        fontRendererObj.drawString(s, x, y, color);
    }

    private void right(String s, int x, int y, int color) {
        text(s, x - fontRendererObj.getStringWidth(s), y, color);
    }
}

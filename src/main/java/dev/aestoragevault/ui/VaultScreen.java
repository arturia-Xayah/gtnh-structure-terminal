package dev.aestoragevault.ui;

import java.util.Locale;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;

import dev.aestoragevault.world.TileController;

public class VaultScreen extends GuiContainer {

    private final TileController tile;

    public VaultScreen(EntityPlayer player, TileController tile) {
        super(new VaultContainer(player, tile));
        this.tile = tile;
        xSize = 176;
        ySize = 214;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.add(new GuiButton(0, guiLeft + 8, guiTop + 104, 78, 20, I18n.format("vault.auto")));
        buttonList.add(new GuiButton(1, guiLeft + 90, guiTop + 104, 78, 20, I18n.format("vault.check")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int x, int y) {
        fontRendererObj.drawString(I18n.format("vault.title"), 8, 7, 0xe8f7ff);
        fontRendererObj.drawString(I18n.format("vault.capacity") + " " + bytes(tile.capacity), 8, 24, 0xe8f7ff);
        fontRendererObj.drawString(I18n.format("vault.used") + " " + bytes(tile.used), 8, 37, 0xe8f7ff);
        fontRendererObj.drawString(I18n.format("vault.types") + " " + tile.types, 8, 50, 0xe8f7ff);
        fontRendererObj.drawString(
            (tile.formed ? I18n.format("vault.formed") : I18n.format("vault.incomplete")) + " / Auto: "
                + tile.automatic,
            8,
            63,
            0x83ccff);
        fontRendererObj.drawSplitString(tile.status, 8, 77, 160, 0xd5e4ee);
    }

    private static String bytes(long bytes) {
        String[] units = { "B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB" };
        double value = bytes;
        int i = 0;
        while (value >= 1024 && i < units.length - 1) {
            value /= 1024;
            i++;
        }
        return String.format(Locale.ROOT, "%.3g %s", value, units[i]);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float ticks, int x, int y) {
        drawRect(guiLeft - 2, guiTop - 2, guiLeft + xSize + 2, guiTop + ySize + 2, 0xff72b9e2);
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xff153044);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) slot(7 + col * 18, 131 + row * 18);
        for (int col = 0; col < 9; col++) slot(7 + col * 18, 189);
    }

    private void slot(int x, int y) {
        drawRect(guiLeft + x, guiTop + y, guiLeft + x + 18, guiTop + y + 18, 0xff486275);
        drawRect(guiLeft + x + 1, guiTop + y + 1, guiLeft + x + 17, guiTop + y + 17, 0xffb5c9d4);
    }
}

package dev.aeoptimizer.ui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.StatCollector;

public class OptimizerScreen extends GuiContainer {

    private final OptimizerContainer state;

    public OptimizerScreen(OptimizerContainer container) {
        super(container);
        state = container;
        xSize = 244;
        ySize = 210;
    }

    private String tr(String key) {
        return StatCollector.translateToLocal("aeo." + key);
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        buttonList.add(new GuiButton(0, guiLeft + 16, guiTop + 46, 212, 20, ""));
        buttonList.add(new GuiButton(1, guiLeft + 16, guiTop + 72, 212, 20, ""));
        buttonList.add(new GuiButton(2, guiLeft + 16, guiTop + 98, 212, 20, ""));
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        ((GuiButton) buttonList.get(0)).displayString = tr("detail") + ": " + tr(state.detailed ? "on" : "off");
        ((GuiButton) buttonList.get(1)).displayString = tr("rename") + ": " + tr(state.autoRename ? "on" : "off");
        GuiButton encode = (GuiButton) buttonList.get(2);
        encode.displayString = tr("rename.encode") + ": " + tr(state.autoEncodeRename ? "on" : "off");
        encode.enabled = state.autoRename;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float ticks, int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xff17374e);
        drawRect(guiLeft + 2, guiTop + 2, guiLeft + xSize - 2, guiTop + ySize - 2, 0xffe8f3fa);
        drawRect(guiLeft + 2, guiTop + 2, guiLeft + xSize - 2, guiTop + 32, 0xffc6e7f7);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(tr("title"), 12, 12, 0x183d58);
        fontRendererObj.drawSplitString(
            tr(state.conflict ? "conflict" : "ready"),
            16,
            188,
            212,
            state.conflict ? 0xb03a30 : 0x36546a);
        fontRendererObj.drawSplitString(tr("rename.encode.help"), 16, 128, 212, 0x36546a);
    }
}

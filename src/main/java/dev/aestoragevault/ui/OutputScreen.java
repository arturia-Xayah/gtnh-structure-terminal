package dev.aestoragevault.ui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.util.item.AEFluidStackType;
import appeng.util.item.AEItemStackType;
import dev.aestoragevault.world.TileOutput;

/** Native AE renderer/phantom-slot interactions, including NEI fluid displays and container right-click. */
public final class OutputScreen extends AEBaseGui {

    private final OutputContainer container;
    private GuiButton fuzzy, ore;

    public OutputScreen(EntityPlayer player, TileOutput tile) {
        super(new OutputContainer(player, tile));
        container = (OutputContainer) inventorySlots;
        xSize = 206;
        ySize = 222;
    }

    @Override
    public void initGui() {
        super.initGui();
        for (int row = 0; row < 4; row++) for (int col = 0; col < 9; col++) registerVirtualSlots(
            new VirtualMEPhantomSlot(
                8 + col * 18,
                26 + row * 18,
                container.tile.filters,
                row * 9 + col,
                (slot, type, button) -> type == AEItemStackType.ITEM_STACK_TYPE
                    || type == AEFluidStackType.FLUID_STACK_TYPE));
        buttonList.add(fuzzy = new GuiImgButton(guiLeft + 8, guiTop + 104, Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL));
        buttonList.add(ore = new GuiButton(1, guiLeft + 104, guiTop + 104, 92, 20, I18n.format("vault.ore")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == fuzzy || button == ore)
            mc.playerController.sendEnchantPacket(inventorySlots.windowId, button == fuzzy ? 0 : 1);
    }

    @Override
    public void drawFG(int x, int y, int mouseX, int mouseY) {
        fontRendererObj.drawString(I18n.format("vault.output.title"), 8, 7, 0xe8f7ff);
        fontRendererObj.drawString(I18n.format("vault.filter"), 8, 16, 0x98c7ec);
        fontRendererObj.drawString(I18n.format("container.inventory"), 8, 130, 0x98c7ec);
        ((GuiImgButton) fuzzy).set(container.fuzzy);
        fuzzy.enabled = container.fuzzyCards > 0;
        ore.enabled = container.oreCards > 0;
    }

    @Override
    public void drawBG(int x, int y, int mouseX, int mouseY) {
        drawRect(x - 1, y - 1, x + xSize + 1, y + ySize + 1, 0xff72b9e2);
        drawRect(x, y, x + xSize, y + ySize, 0xff153044);
        for (int row = 0; row < 4; row++) for (int col = 0; col < 9; col++) slot(x + 7 + col * 18, y + 25 + row * 18);
        for (int i = 0; i < 3; i++) slot(x + 179, y + 25 + i * 18);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) slot(x + 7 + col * 18, y + 139 + row * 18);
        for (int col = 0; col < 9; col++) slot(x + 7 + col * 18, y + 197);
    }

    private void slot(int x, int y) {
        drawRect(x, y, x + 18, y + 18, 0xff486275);
        drawRect(x + 1, y + 1, x + 17, y + 17, 0xffb5c9d4);
    }
}

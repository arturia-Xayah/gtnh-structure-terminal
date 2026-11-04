package dev.structureterminal.client;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import dev.structureterminal.pattern.IntegratedPatternContainer;

/** Vanilla chest panels, with four inventory rows instead of six. */
public final class IntegratedPatternScreen extends GuiContainer {

    private static final ResourceLocation CHEST = new ResourceLocation("textures/gui/container/generic_54.png");

    public IntegratedPatternScreen(EntityPlayer player, int slot) {
        super(new IntegratedPatternContainer(player, slot));
        xSize = 176;
        ySize = 186;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        GL11.glColor4f(1, 1, 1, 1);
        mc.getTextureManager()
            .bindTexture(CHEST);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, 4 * 18 + 17);
        drawTexturedModalRect(guiLeft, guiTop + 89, 0, 126, xSize, 96);
        // Highlight the locked held slot without covering its item.
        int held = ((IntegratedPatternContainer) inventorySlots).heldSlot;
        drawRect(guiLeft + 7 + held * 18, guiTop + 178, guiLeft + 25 + held * 18, guiTop + 180, 0xff58c5d8);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(I18n.format("item.structureterminal.integrated_pattern.name"), 8, 6, 0x404040);
        fontRendererObj.drawString(I18n.format("container.inventory"), 8, 92, 0x404040);
    }
}

package dev.aestoragevault.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Native button hit testing with restrained dashboard styling. */
@SideOnly(Side.CLIENT)
public final class VaultButton extends GuiButton {

    public boolean active;

    public VaultButton(int id, int x, int y, int width, String label) {
        super(id, x, y, width, 18, label);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;
        field_146123_n = mouseX >= xPosition && mouseX < xPosition + width
            && mouseY >= yPosition
            && mouseY < yPosition + height;
        int border = enabled && field_146123_n ? 0xff6daed8 : 0xffa6c5dd;
        int fill = active ? 0xffbedbf0 : 0xffdce9f5;
        drawRect(xPosition, yPosition, xPosition + width, yPosition + height, border);
        drawRect(
            xPosition + 1,
            yPosition + 1,
            xPosition + width - 1,
            yPosition + height - 1,
            enabled && field_146123_n ? 0xffeff9ff : fill);
        drawCenteredString(
            mc.fontRenderer,
            displayString,
            xPosition + width / 2,
            yPosition + 5,
            enabled ? 0xff254c6b : 0xff7a8b9c);
    }
}

package dev.structureterminal.client.update;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import org.lwjgl.opengl.GL11;

/** GTNH main-menu palette, Minecraft font renderer and scalable geometry; no CMM dependency. */
public final class UpdateButton extends GuiButton {

    public UpdateButton(int id, int x, int y, int width, String text) {
        super(id, x, y, width, 20, text);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;
        boolean hover = mouseX >= xPosition && mouseY >= yPosition
            && mouseX < xPosition + width
            && mouseY < yPosition + height;
        GL11.glColor4f(1, 1, 1, 1);
        drawRect(
            xPosition,
            yPosition,
            xPosition + width,
            yPosition + height,
            hover && enabled ? 0xF02C4650 : 0xE017242B);
        drawRect(
            xPosition,
            yPosition + height - 1,
            xPosition + width,
            yPosition + height,
            enabled ? 0xFF40FFFF : 0xFF45545B);
        String text = displayString;
        if (id == 209731) {
            UpdateClient updater = UpdateClient.INSTANCE;
            text = UpdateClient.tr(
                updater.state.equals("available") ? "entryAvailable"
                    : updater.state.equals("ready") ? "entryReady" : "entry");
        }
        String label = mc.fontRenderer.trimStringToWidth(text, width - 8);
        drawCenteredString(
            mc.fontRenderer,
            label,
            xPosition + width / 2,
            yPosition + 6,
            !enabled ? 0x77858C : hover ? 0xFFB83B : 0x40FFFF);
    }
}

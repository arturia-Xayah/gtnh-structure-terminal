package dev.structureterminal.client.ae;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.GL11;

import appeng.client.gui.slots.VirtualMEPatternSlot;
import appeng.client.me.ItemRepo;
import appeng.container.sync.handlers.AEStackInventorySyncHandler;
import dev.aeoptimizer.mixin.client.ItemRepoAccessor;
import dev.structureterminal.ae.CraftableInputs;

/** Native input slot behavior plus a read-only craftable badge. Client only. */
public final class CraftablePatternSlot extends VirtualMEPatternSlot {

    private final ItemRepo repo;

    public CraftablePatternSlot(int x, int y, AEStackInventorySyncHandler sync, int index,
        TypeAcceptPredicate acceptType, ItemRepo repo) {
        super(x, y, sync, index, acceptType);
        this.repo = repo;
    }

    public boolean hasCraftingPattern() {
        return !isHidden() && CraftableInputs.isCraftable(
            repo.hasPower(),
            ((ItemRepoAccessor) repo).structureterminal$getUnfilteredInventory(),
            getAEStack());
    }

    @Override
    public boolean drawStackAndOverlay(Minecraft mc, int mouseX, int mouseY) {
        boolean hovered = super.drawStackAndOverlay(mc, mouseX, mouseY);
        if (hasCraftingPattern()) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
            GL11.glPushMatrix();
            try {
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glColor4f(1, 1, 1, 1);
                GL11.glTranslatef(getX() + 11, getY() - 1, 200);
                GL11.glScalef(0.75F, 0.75F, 1);
                mc.fontRenderer.drawStringWithShadow("+", 0, 0, 0xFFFFFF);
            } finally {
                GL11.glPopMatrix();
                GL11.glPopAttrib();
            }
        }
        return hovered;
    }

}

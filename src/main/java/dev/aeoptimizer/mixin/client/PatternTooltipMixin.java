package dev.aeoptimizer.mixin.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.slots.VirtualMESlot;
import dev.structureterminal.client.ae.CraftablePatternSlot;

/** Append to the actual item/fluid tooltip instead of drawing a second tooltip over the name. */
@Mixin(value = AEBaseGui.class, remap = false)
public abstract class PatternTooltipMixin {

    @Shadow
    public abstract VirtualMESlot getVirtualMESlotUnderMouse();

    @Inject(method = "handleItemTooltip", at = @At("RETURN"), cancellable = true)
    private void structureterminal$craftableTooltip(ItemStack stack, int mouseX, int mouseY, List<String> original,
        CallbackInfoReturnable<List<String>> cir) {
        VirtualMESlot hovered = getVirtualMESlotUnderMouse();
        if (hovered instanceof CraftablePatternSlot && ((CraftablePatternSlot) hovered).hasCraftingPattern()) {
            List<String> lines = new ArrayList<>(cir.getReturnValue());
            lines.add(EnumChatFormatting.AQUA + I18n.format("structureterminal.ae.craftable_input"));
            cir.setReturnValue(lines);
        }
    }
}

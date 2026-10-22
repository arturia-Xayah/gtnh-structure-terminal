package dev.aeoptimizer.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.slots.VirtualMEPatternSlot;
import appeng.client.gui.slots.VirtualMEPhantomSlot.TypeAcceptPredicate;
import appeng.container.sync.handlers.AEStackInventorySyncHandler;
import dev.structureterminal.client.ae.CraftablePatternSlot;

@Mixin(value = GuiPatternTerm.class, remap = false)
public abstract class PatternInputMixin {

    // Only the first allocation site creates INPUT slots. Output slots remain native.
    @Redirect(
        method = "initVirtualSlots",
        at = @At(value = "NEW", target = "appeng/client/gui/slots/VirtualMEPatternSlot", ordinal = 0))
    private VirtualMEPatternSlot structureterminal$inputSlot(int x, int y, AEStackInventorySyncHandler sync, int index,
        TypeAcceptPredicate acceptType) {
        return new CraftablePatternSlot(
            x,
            y,
            sync,
            index,
            acceptType,
            ((TerminalRepoAccessor) this).structureterminal$getRepo());
    }
}

package dev.aeoptimizer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.parts.IPart;
import appeng.parts.CableBusContainer;
import dev.structureterminal.packing.PackedPartContext;

@Mixin(value = CableBusContainer.class, remap = false)
public abstract class PackedPartMixin {

    @Redirect(
        method = "addPart",
        at = @At(value = "INVOKE", target = "Lappeng/api/parts/IPart;addToWorld()V"),
        require = 2)
    private void modernization$restorePackedPart(IPart part) {
        PackedPartContext.addToWorld(part);
    }
}

package dev.plasmaforgepower.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.plasmaforgepower.ui.CompactFusionPowerGui;
import dev.plasmaforgepower.ui.FusionPowerGui;
import goodgenerator.blocks.tileEntity.base.MTELargeFusionComputer;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.tileentities.machines.multi.MTEFusionComputer;
import tectech.thing.metaTileEntity.multi.base.TTMultiblockBase;

@Mixin(value = { MTEFusionComputer.class, TTMultiblockBase.class }, remap = false)
public abstract class FusionGuiMixin {

    @Inject(method = "getGui", at = @At("RETURN"), cancellable = true)
    private void pfp$fusionGui(CallbackInfoReturnable<MTEMultiBlockBaseGui<?>> cir) {
        Object machine = this;
        if (machine instanceof MTEFusionComputer) cir.setReturnValue(new FusionPowerGui((MTEFusionComputer) machine));
        else if (machine instanceof MTELargeFusionComputer)
            cir.setReturnValue(new CompactFusionPowerGui((MTELargeFusionComputer) machine));
    }
}

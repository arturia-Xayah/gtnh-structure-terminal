package dev.structureterminal.bc.mixin;

import java.util.List;

import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.recursive_pineapple.matter_manipulator.common.building.ITileAnalysisIntegration;
import com.recursive_pineapple.matter_manipulator.common.building.PendingBlock;

import dev.structureterminal.bc.MachineRangeCopy;
import dev.structureterminal.bc.PlannedRange;
import dev.structureterminal.bc.PortableRange;
import dev.structureterminal.bc.RangeMachine;

@Mixin(value = PendingBlock.class, remap = false)
public abstract class PendingRangeMixin implements PlannedRange {

    @Unique
    private MachineRangeCopy range$copy;

    public MachineRangeCopy machineRange() {
        return range$copy;
    }

    public void machineRange(MachineRangeCopy copy) {
        range$copy = copy;
    }

    @Inject(method = "analyze", at = @At("RETURN"))
    private void range$analyze(TileEntity tile, int flags, CallbackInfoReturnable<PendingBlock> ci) {
        if (!(tile instanceof RangeMachine)) return;
        int[] size = PortableRange.dimensions(tile);
        if (size.length == 3) range$copy = new MachineRangeCopy(PortableRange.front(tile), size);
    }

    @Inject(method = "getIntegrations", at = @At("RETURN"))
    private void range$list(CallbackInfoReturnable<List<ITileAnalysisIntegration>> ci) {
        if (range$copy != null) ci.getReturnValue()
            .add(range$copy);
    }

    @Inject(
        method = "clone()Lcom/recursive_pineapple/matter_manipulator/common/building/PendingBlock;",
        at = @At("RETURN"))
    private void range$array(CallbackInfoReturnable<PendingBlock> ci) {
        if (range$copy != null) ((PlannedRange) ci.getReturnValue()).machineRange(range$copy.clone());
    }
}

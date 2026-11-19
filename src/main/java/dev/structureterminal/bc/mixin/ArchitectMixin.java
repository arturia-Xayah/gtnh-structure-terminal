package dev.structureterminal.bc.mixin;

import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import buildcraft.builders.TileArchitect;
import buildcraft.builders.blueprints.RecursiveBlueprintReader;
import dev.structureterminal.bc.RangeJobs;
import dev.structureterminal.bc.RangeMachine;

@Mixin(value = TileArchitect.class, remap = false)
public abstract class ArchitectMixin implements RangeMachine {

    @org.spongepowered.asm.mixin.Unique
    private net.minecraft.nbt.NBTTagCompound range$settings = new net.minecraft.nbt.NBTTagCompound();

    public net.minecraft.nbt.NBTTagCompound rangeData() {
        return range$settings;
    }

    @Inject(method = { "readFromNBT", "func_145839_a" }, at = @At("RETURN"))
    private void range$load(net.minecraft.nbt.NBTTagCompound tag, CallbackInfo ci) {
        range$settings = (net.minecraft.nbt.NBTTagCompound) tag.getCompoundTag("modernization.bcRange")
            .copy();
    }

    @Inject(method = { "writeToNBT", "func_145841_b" }, at = @At("RETURN"))
    private void range$save(net.minecraft.nbt.NBTTagCompound tag, CallbackInfo ci) {
        if (!range$settings.hasNoTags()) tag.setTag("modernization.bcRange", range$settings.copy());
    }

    @Shadow
    private RecursiveBlueprintReader reader;

    public boolean rangeBusy() {
        return reader != null;
    }

    public void applyBounds(int[] c) {
        TileArchitect a = (TileArchitect) (Object) this;
        a.box.initialize(c[0], c[1], c[2], c[3], c[4], c[5]);
        a.mode = TileArchitect.Mode.COPY;
        a.sendNetworkUpdate();
    }

    @Inject(method = { "updateEntity", "func_145845_h" }, at = @At("RETURN"))
    private void range$afterScan(CallbackInfo ci) {
        RangeJobs.finish((TileEntity) (Object) this);
    }
}

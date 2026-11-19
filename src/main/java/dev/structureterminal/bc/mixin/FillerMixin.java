package dev.structureterminal.bc.mixin;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import buildcraft.api.core.IAreaProvider;
import buildcraft.builders.TileFiller;
import buildcraft.core.Box;
import buildcraft.core.blueprints.BptBuilderTemplate;
import buildcraft.core.lib.utils.Utils;
import dev.structureterminal.bc.RangeJobs;
import dev.structureterminal.bc.RangeMachine;

@Mixin(value = TileFiller.class, remap = false)
public abstract class FillerMixin implements RangeMachine {

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
    @Final
    private Box box;
    @Shadow
    private boolean done;
    @Shadow
    private BptBuilderTemplate currentTemplate;

    public boolean rangeBusy() {
        return currentTemplate != null && !done;
    }

    public void applyBounds(int[] c) {
        box.initialize(c[0], c[1], c[2], c[3], c[4], c[5]);
        currentTemplate = null;
        done = false;
        ((TileFiller) (Object) this).sendNetworkUpdate();
    }

    @Redirect(
        method = "initialize",
        at = @At(
            value = "INVOKE",
            target = "Lbuildcraft/core/lib/utils/Utils;getNearbyAreaProvider(Lnet/minecraft/world/World;III)Lbuildcraft/api/core/IAreaProvider;"))
    private IAreaProvider range$keepManualBox(World world, int x, int y, int z) {
        return rangeData().getBoolean(RangeJobs.MANUAL) ? null : Utils.getNearbyAreaProvider(world, x, y, z);
    }

    @Inject(method = { "updateEntity", "func_145845_h" }, at = @At("RETURN"))
    private void range$afterWork(CallbackInfo ci) {
        RangeJobs.finish((TileEntity) (Object) this);
    }
}

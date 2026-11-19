package dev.structureterminal.bc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import buildcraft.builders.TileArchitect;
import buildcraft.builders.TileFiller;
import dev.structureterminal.bc.PortableRange;
import dev.structureterminal.bc.RangeMachine;
import io.netty.buffer.ByteBuf;

@Mixin(value = { TileArchitect.class, TileFiller.class }, remap = false)
public abstract class RangeSyncMixin implements RangeMachine {

    @Inject(method = "writeData", at = @At("RETURN"))
    private void range$send(ByteBuf buf, CallbackInfo ci) {
        int[] size = rangeData().getIntArray(PortableRange.KEY);
        if (size.length == 3) for (int n : size) buf.writeInt(n);
    }

    @Inject(method = "readData", at = @At("RETURN"))
    private void range$receive(ByteBuf buf, CallbackInfo ci) {
        if (buf.readableBytes() == 12)
            rangeData().setIntArray(PortableRange.KEY, new int[] { buf.readInt(), buf.readInt(), buf.readInt() });
        else rangeData().removeTag(PortableRange.KEY);
    }
}

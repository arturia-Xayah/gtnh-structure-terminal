package dev.aeoptimizer.mixin;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.github.vfyjxf.nee.network.packet.PacketNEIPatternRecipe;

@Mixin(value = PacketNEIPatternRecipe.class, remap = false)
public interface NEIPatternPacketAccessor {

    @Accessor("input")
    NBTTagCompound aeo$getInput();

    @Accessor("output")
    NBTTagCompound aeo$getOutput();
}

package dev.aeoptimizer.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.helpers.IInterfaceHost;
import appeng.items.tools.ToolMemoryCard;
import dev.structureterminal.pattern.PatternCopy;

@Mixin(value = ToolMemoryCard.class, remap = false)
public abstract class IntegratedPatternMemoryMixin {

    @Inject(method = "savePatterns", at = @At("RETURN"))
    private static void modernization$save(NBTTagCompound data, IInterfaceHost host, CallbackInfo ci) {
        PatternCopy.save(data, host);
    }

    @ModifyVariable(method = "insertPatterns", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static NBTTagCompound modernization$payForHolders(NBTTagCompound data, NBTTagCompound original,
        EntityPlayer player, IInterfaceHost host) {
        return PatternCopy.insert(data, player, host);
    }
}

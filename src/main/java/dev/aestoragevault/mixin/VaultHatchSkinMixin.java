package dev.aestoragevault.mixin;

import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.aestoragevault.world.VaultStructure;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;

/** Render-only override. No hatch tier, saved texture, inventory, facing or dye is changed. */
@Mixin(value = MTEHatch.class, remap = false)
public abstract class VaultHatchSkinMixin {

    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void vault$skin(IGregTechTileEntity base, ForgeDirection side, ForgeDirection facing, int color,
        boolean active, boolean redstone, CallbackInfoReturnable<ITexture[]> cir) {
        if (!((Object) this instanceof MTEHatchInputBus) || !VaultStructure.isFormedBus(base)) return;
        ITexture shell = Textures.BlockIcons.casingTexturePages[0][6];
        MTEHatch hatch = (MTEHatch) (Object) this;
        cir.setReturnValue(
            side != facing ? new ITexture[] { shell }
                : active ? hatch.getTexturesActive(shell) : hatch.getTexturesInactive(shell));
    }
}

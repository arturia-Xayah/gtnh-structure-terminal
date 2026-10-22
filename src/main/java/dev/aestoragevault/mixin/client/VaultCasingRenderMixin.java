package dev.aestoragevault.mixin.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.aestoragevault.VaultMod;
import dev.aestoragevault.client.VaultBlockRenderer;
import dev.aestoragevault.world.VaultStructure;
import gregtech.common.render.GTRendererCasing;

@Mixin(value = GTRendererCasing.class, remap = false)
public abstract class VaultCasingRenderMixin {

    @Inject(method = "renderWorldBlock", at = @At("HEAD"), cancellable = true)
    private void vault$surface(IBlockAccess world, int x, int y, int z, Block block, int model, RenderBlocks renderer,
        CallbackInfoReturnable<Boolean> cir) {
        if (!VaultMod.isLuVCasing(world, x, y, z) || VaultStructure.formedOwner(world, x, y, z) == null) return;
        VaultBlockRenderer.renderShell(world, x, y, z, block, renderer);
        cir.setReturnValue(true);
    }
}

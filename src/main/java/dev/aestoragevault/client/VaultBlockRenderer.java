package dev.aestoragevault.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.aestoragevault.world.VaultBlock;

/** Native LuV shell plus a transparent original front overlay; no baked vanilla textures. */
@SideOnly(Side.CLIENT)
public final class VaultBlockRenderer implements ISimpleBlockRenderingHandler {

    @Override
    public int getRenderId() {
        return VaultBlock.renderId;
    }

    @Override
    public boolean shouldRender3DInInventory(int model) {
        return true;
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int model,
        RenderBlocks renderer) {
        renderer.renderStandardBlock(block, x, y, z);
        VaultBlock vault = (VaultBlock) block;
        int metadata = world.getBlockMetadata(x, y, z);
        for (int side = 0; side < 6; side++) {
            IIcon icon = vault.overlay(side, metadata);
            if (icon == null) continue;
            ForgeDirection normal = ForgeDirection.getOrientation(side);
            if (!block.shouldSideBeRendered(world, x + normal.offsetX, y + normal.offsetY, z + normal.offsetZ, side))
                continue;
            Tessellator.instance.setBrightness(
                block.getMixedBrightnessForBlock(world, x + normal.offsetX, y + normal.offsetY, z + normal.offsetZ));
            float light = side == 0 ? .5f : side == 1 ? 1f : side < 4 ? .8f : .6f;
            Tessellator.instance.setColorOpaque_F(light, light, light);
            face(
                renderer,
                block,
                side,
                x + normal.offsetX * .001,
                y + normal.offsetY * .001,
                z + normal.offsetZ * .001,
                icon);
        }
        return true;
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int model, RenderBlocks renderer) {
        GL11.glPushMatrix();
        GL11.glRotatef(90, 0, 1, 0);
        GL11.glTranslatef(-.5f, -.5f, -.5f);
        renderer.setRenderBounds(0, 0, 0, 1, 1, 1);
        for (int side = 0; side < 6; side++) {
            ForgeDirection normal = ForgeDirection.getOrientation(side);
            Tessellator.instance.startDrawingQuads();
            Tessellator.instance.setNormal(normal.offsetX, normal.offsetY, normal.offsetZ);
            face(renderer, block, side, 0, 0, 0, block.getIcon(side, metadata));
            Tessellator.instance.draw();
            IIcon icon = ((VaultBlock) block).overlay(side, metadata);
            if (icon != null) {
                Tessellator.instance.startDrawingQuads();
                Tessellator.instance.setNormal(normal.offsetX, normal.offsetY, normal.offsetZ);
                face(renderer, block, side, normal.offsetX * .001, normal.offsetY * .001, normal.offsetZ * .001, icon);
                Tessellator.instance.draw();
            }
        }
        GL11.glPopMatrix();
    }

    private void face(RenderBlocks r, Block b, int side, double x, double y, double z, IIcon icon) {
        switch (side) {
            case 0:
                r.renderFaceYNeg(b, x, y, z, icon);
                break;
            case 1:
                r.renderFaceYPos(b, x, y, z, icon);
                break;
            case 2:
                r.renderFaceZNeg(b, x, y, z, icon);
                break;
            case 3:
                r.renderFaceZPos(b, x, y, z, icon);
                break;
            case 4:
                r.renderFaceXNeg(b, x, y, z, icon);
                break;
            case 5:
                r.renderFaceXPos(b, x, y, z, icon);
                break;
            default:
                throw new IllegalArgumentException("Invalid face");
        }
    }
}

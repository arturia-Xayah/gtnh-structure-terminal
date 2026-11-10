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
import dev.aestoragevault.core.VaultGeometry;
import dev.aestoragevault.world.TileController;
import dev.aestoragevault.world.VaultBlock;
import dev.aestoragevault.world.VaultSkin;
import dev.aestoragevault.world.VaultStructure;

/** Native unformed shell; continuous formed skin with separately rendered functional overlays. */
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
        if (skinOwner(world, x, y, z) != null) renderShell(world, x, y, z, block, renderer);
        else renderer.renderStandardBlock(block, x, y, z);
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
        metadata = 3; // Inventory face points toward the viewer; metadata 0 would put the screen underneath.
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glPushMatrix();
        GL11.glRotatef(90, 0, 1, 0);
        GL11.glTranslatef(-.5f, -.5f, -.5f);
        renderer.setRenderBounds(0, 0, 0, 1, 1, 1);
        for (int side = 0; side < 6; side++) {
            ForgeDirection normal = ForgeDirection.getOrientation(side);
            Tessellator.instance.startDrawingQuads();
            Tessellator.instance.setColorOpaque_F(1, 1, 1);
            Tessellator.instance.setBrightness(0xF000F0);
            Tessellator.instance.setNormal(normal.offsetX, normal.offsetY, normal.offsetZ);
            face(renderer, block, side, 0, 0, 0, block.getIcon(side, metadata));
            Tessellator.instance.draw();
            IIcon icon = ((VaultBlock) block).overlay(side, metadata);
            if (icon != null) {
                Tessellator.instance.startDrawingQuads();
                Tessellator.instance.setColorOpaque_F(1, 1, 1);
                Tessellator.instance.setBrightness(0xF000F0);
                Tessellator.instance.setNormal(normal.offsetX, normal.offsetY, normal.offsetZ);
                face(renderer, block, side, normal.offsetX * .001, normal.offsetY * .001, normal.offsetZ * .001, icon);
                Tessellator.instance.draw();
            }
        }
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    private static TileController skinOwner(IBlockAccess world, int x, int y, int z) {
        TileController owner = VaultCasingSkin.owner(x, y, z);
        return owner != null ? owner : VaultStructure.formedOwner(world, x, y, z);
    }

    public static void renderShell(IBlockAccess world, int x, int y, int z, Block block, RenderBlocks renderer) {
        TileController owner = skinOwner(world, x, y, z);
        if (owner == null) return;
        int front = world.getBlockMetadata(owner.xCoord, owner.yCoord, owner.zCoord);
        boolean oldAO = renderer.enableAO;
        renderer.enableAO = false;
        renderer.setRenderBounds(0, 0, 0, 1, 1, 1);
        try {
            for (int side = 0; side < 6; side++) {
                ForgeDirection normal = ForgeDirection.getOrientation(side);
                if (!block
                    .shouldSideBeRendered(world, x + normal.offsetX, y + normal.offsetY, z + normal.offsetZ, side))
                    continue;
                int tile = VaultGeometry.tile(
                    x - owner.xCoord + VaultGeometry.normal(front, 0),
                    y - owner.yCoord + VaultGeometry.normal(front, 1),
                    z - owner.zCoord + VaultGeometry.normal(front, 2),
                    side);
                renderShellFace(world, x, y, z, block, renderer, side, VaultSkin.icon(tile));
            }
        } finally {
            renderer.enableAO = oldAO;
        }
    }

    /** Shared by casing, controller, ME ports and native GT buses, including identical UV orientation. */
    public static void renderShellFace(IBlockAccess world, int x, int y, int z, Block block, RenderBlocks renderer,
        int side, IIcon icon) {
        renderShellFace(world, x, y, z, block, renderer, side, icon, 0);
    }

    public static void renderShellFace(IBlockAccess world, int x, int y, int z, Block block, RenderBlocks renderer,
        int side, IIcon icon, double offset) {
        boolean ao = renderer.enableAO, flip = renderer.flipTexture, mirror = renderer.field_152631_f;
        int bottom = renderer.uvRotateBottom, top = renderer.uvRotateTop, north = renderer.uvRotateNorth;
        int south = renderer.uvRotateSouth, east = renderer.uvRotateEast, west = renderer.uvRotateWest;
        renderer.enableAO = false;
        renderer.flipTexture = false;
        renderer.field_152631_f = false;
        renderer.uvRotateBottom = renderer.uvRotateTop = renderer.uvRotateNorth = 0;
        renderer.uvRotateSouth = renderer.uvRotateEast = renderer.uvRotateWest = 0;
        try {
            ForgeDirection normal = ForgeDirection.getOrientation(side);
            Tessellator.instance.setBrightness(
                block.getMixedBrightnessForBlock(world, x + normal.offsetX, y + normal.offsetY, z + normal.offsetZ));
            float light = side == 0 ? .5f : side == 1 ? 1f : side < 4 ? .8f : .6f;
            Tessellator.instance.setColorOpaque_F(light, light, light);
            face(
                renderer,
                block,
                side,
                x + normal.offsetX * offset,
                y + normal.offsetY * offset,
                z + normal.offsetZ * offset,
                icon);
        } finally {
            renderer.enableAO = ao;
            renderer.flipTexture = flip;
            renderer.field_152631_f = mirror;
            renderer.uvRotateBottom = bottom;
            renderer.uvRotateTop = top;
            renderer.uvRotateNorth = north;
            renderer.uvRotateSouth = south;
            renderer.uvRotateEast = east;
            renderer.uvRotateWest = west;
        }
    }

    private static void face(RenderBlocks r, Block b, int side, double x, double y, double z, IIcon icon) {
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

package dev.aestoragevault.world;

import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.ITexture;
import gregtech.api.render.ISBRContext;
import gregtech.api.render.ISBRWorldContext;

/** One atlas sprite for a whole face: adjacent blocks share UV boundaries and mipmaps. */
public final class VaultSkin {

    @SideOnly(Side.CLIENT)
    private static IIcon[] icons;
    @SideOnly(Side.CLIENT)
    private static IIcon whole;
    private static final ITexture[] TEXTURES = new ITexture[9];

    private VaultSkin() {}

    @SideOnly(Side.CLIENT)
    public static void register(net.minecraft.client.renderer.texture.IIconRegister registry) {
        IIcon face = registry.registerIcon("aestoragevault:formed_shell_smooth");
        whole = face;
        icons = new IIcon[9];
        for (int tile = 0; tile < icons.length; tile++) icons[tile] = new FaceRegion(face, tile);
    }

    @SideOnly(Side.CLIENT)
    public static IIcon whole() {
        return whole;
    }

    @SideOnly(Side.CLIENT)
    public static IIcon icon(int tile) {
        return icons[tile];
    }

    public static synchronized ITexture texture(int tile) {
        if (TEXTURES[tile] == null) TEXTURES[tile] = new ShellTexture(tile, false);
        return TEXTURES[tile];
    }

    public static ITexture overlayTexture(int tile) {
        return new ShellTexture(tile, true);
    }

    private static final class ShellTexture implements ITexture {

        private final int tile;

        private final boolean overlay;

        ShellTexture(int tile, boolean overlay) {
            this.tile = tile;
            this.overlay = overlay;
        }

        @SideOnly(Side.CLIENT)
        private void render(ISBRContext context, int side) {
            if (!(context instanceof ISBRWorldContext) || !context.canRenderInPass(pass -> pass == 0)) return;
            ISBRWorldContext world = (ISBRWorldContext) context;
            // GT marks its native casing vertices for Angelica's deferred AO. Our shared shell
            // renderer already writes diffuse color and neighbor light, just like the controller.
            // Clear that marker or Angelica shades the casing a second time (0.8 -> 0.64 on Z).
            world.finishLighting();
            try {
                dev.aestoragevault.client.VaultBlockRenderer.renderShellFace(
                    world.getBlockAccess(),
                    context.getX(),
                    context.getY(),
                    context.getZ(),
                    context.getBlock(),
                    context.getRenderBlocks(),
                    side,
                    icon(tile),
                    overlay ? .001 : 0);
            } finally {
                // Subsequent native hatch/cover layers still need GT's normal lighting path.
                world.tryStartAngelicaLighting();
            }
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void renderXPos(ISBRContext context) {
            render(context, 5);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void renderXNeg(ISBRContext context) {
            render(context, 4);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void renderYPos(ISBRContext context) {
            render(context, 1);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void renderYNeg(ISBRContext context) {
            render(context, 0);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void renderZPos(ISBRContext context) {
            render(context, 3);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void renderZNeg(ISBRContext context) {
            render(context, 2);
        }

        @Override
        public boolean isValidTexture() {
            return true;
        }
    }

    @SideOnly(Side.CLIENT)
    static final class FaceRegion implements IIcon {

        private final IIcon face;
        private final int column;
        private final int row;

        FaceRegion(IIcon face, int tile) {
            this.face = face;
            column = tile % 3;
            row = tile / 3;
        }

        @Override
        public int getIconWidth() {
            return face.getIconWidth() / 3;
        }

        @Override
        public int getIconHeight() {
            return face.getIconHeight() / 3;
        }

        @Override
        public float getMinU() {
            return getInterpolatedU(0);
        }

        @Override
        public float getMaxU() {
            return getInterpolatedU(16);
        }

        @Override
        public float getMinV() {
            return getInterpolatedV(0);
        }

        @Override
        public float getMaxV() {
            return getInterpolatedV(16);
        }

        @Override
        public float getInterpolatedU(double pixel) {
            return face.getInterpolatedU((column * 16.0 + pixel) / 3.0);
        }

        @Override
        public float getInterpolatedV(double pixel) {
            return face.getInterpolatedV((row * 16.0 + pixel) / 3.0);
        }

        @Override
        public String getIconName() {
            return face.getIconName();
        }
    }
}

package dev.aestoragevault.world;

import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.render.TextureFactory;

/** Nine pieces of one continuous face; shared by native buses and all custom/casing blocks. */
public final class VaultSkin {

    @SideOnly(Side.CLIENT)
    public static IIcon[] icons;
    private static final ITexture[] TEXTURES = new ITexture[9];

    private VaultSkin() {}

    public static synchronized ITexture texture(int tile) {
        if (TEXTURES[tile] == null) TEXTURES[tile] = TextureFactory.of(new Icon(tile));
        return TEXTURES[tile];
    }

    private static final class Icon implements IIconContainer {

        private final int tile;

        Icon(int tile) {
            this.tile = tile;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public IIcon getIcon() {
            return icons[tile];
        }

        @Override
        @SideOnly(Side.CLIENT)
        public IIcon getOverlayIcon() {
            return null;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public ResourceLocation getTextureFile() {
            return net.minecraft.client.renderer.texture.TextureMap.locationBlocksTexture;
        }
    }
}

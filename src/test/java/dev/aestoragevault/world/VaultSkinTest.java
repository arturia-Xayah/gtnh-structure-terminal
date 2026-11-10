package dev.aestoragevault.world;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.io.InputStream;

import javax.imageio.ImageIO;

import net.minecraft.util.IIcon;

import org.junit.Test;

public class VaultSkinTest {

    @Test
    public void regionsShareExactUvEdgesIncludingNonZeroAtlasOffsets() {
        IIcon atlas = new TestIcon();
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
            IIcon tile = new VaultSkin.FaceRegion(atlas, row * 3 + column);
            if (column < 2)
                assertEquals(tile.getMaxU(), new VaultSkin.FaceRegion(atlas, row * 3 + column + 1).getMinU(), 0);
            if (row < 2)
                assertEquals(tile.getMaxV(), new VaultSkin.FaceRegion(atlas, (row + 1) * 3 + column).getMinV(), 0);
            assertEquals(tile.getMinU(), tile.getInterpolatedU(0), 0);
            assertEquals(tile.getMaxV(), tile.getInterpolatedV(16), 0);
        }
        assertEquals(atlas.getMinU(), new VaultSkin.FaceRegion(atlas, 0).getMinU(), 0);
        assertEquals(atlas.getMaxV(), new VaultSkin.FaceRegion(atlas, 8).getMaxV(), 0);
    }

    @Test
    public void reloadReplacesEveryRegionWithoutRetainingOldAtlasCoordinates() {
        VaultSkin.register(name -> new TestIcon());
        IIcon old = VaultSkin.icon(4);
        VaultSkin.register(name -> new TestIcon() {

            @Override
            public float getInterpolatedU(double u) {
                return (float) (.6 + u / 128);
            }
        });
        assertNotSame(old, VaultSkin.icon(4));
        assertTrue(
            VaultSkin.icon(4)
                .getMinU() > old.getMaxU());
    }

    @Test
    public void packedFaceHasOpaqueMatchingGuardBandsOnAllFourEdges() throws Exception {
        try (InputStream in = getClass()
            .getResourceAsStream("/assets/aestoragevault/textures/blocks/formed_shell_smooth.png")) {
            assertNotNull(in);
            BufferedImage image = ImageIO.read(in);
            assertEquals(512, image.getWidth());
            assertEquals(512, image.getHeight());
            for (int d = 0; d < 16; d++) for (int p = 0; p < 512; p++) {
                whiteEdge(image.getRGB(d, p));
                whiteEdge(image.getRGB(511 - d, p));
                whiteEdge(image.getRGB(p, d));
                whiteEdge(image.getRGB(p, 511 - d));
            }
        }
    }

    private static void whiteEdge(int rgba) {
        assertEquals(255, rgba >>> 24);
        // Generated porcelain has at most two levels of subvisible albedo variation at its margins.
        for (int shift = 0; shift < 24; shift += 8) assertTrue(((rgba >>> shift) & 255) >= 253);
    }

    private static class TestIcon implements IIcon {

        public int getIconWidth() {
            return 512;
        }

        public int getIconHeight() {
            return 512;
        }

        public float getMinU() {
            return getInterpolatedU(0);
        }

        public float getMaxU() {
            return getInterpolatedU(16);
        }

        public float getMinV() {
            return getInterpolatedV(0);
        }

        public float getMaxV() {
            return getInterpolatedV(16);
        }

        public float getInterpolatedU(double u) {
            return (float) (.25 + u / 128);
        }

        public float getInterpolatedV(double v) {
            return (float) (.375 + v / 128);
        }

        public String getIconName() {
            return "test";
        }
    }
}

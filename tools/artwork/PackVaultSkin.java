import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Technical texture packing: retain generated artwork, enforce a constant cube-edge guard band. */
public class PackVaultSkin {
    public static void main(String[] args) throws Exception {
        BufferedImage source = ImageIO.read(new File(args[0]));
        BufferedImage packed = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = packed.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(source, 0, 0, 512, 512, null);
        g.dispose();
        // A 16px identical perimeter survives normal mip levels 0..4. Smoothstep
        // reaches the original artwork before the first circuit traces begin.
        int[] edge = {204, 218, 231};
        for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++) {
            int distance = Math.min(Math.min(x, 511 - x), Math.min(y, 511 - y));
            double w = Math.max(0, Math.min(1, (distance - 16) / 56.0));
            w = w * w * (3 - 2 * w);
            int original = packed.getRGB(x, y), rgb = 0;
            for (int channel = 0; channel < 3; channel++) {
                int shift = 16 - channel * 8;
                int value = (int) Math.round(edge[channel] * (1 - w) + ((original >> shift) & 255) * w);
                rgb |= value << shift;
            }
            packed.setRGB(x, y, rgb);
        }
        ImageIO.write(packed, "png", new File(args[1]));
    }
}

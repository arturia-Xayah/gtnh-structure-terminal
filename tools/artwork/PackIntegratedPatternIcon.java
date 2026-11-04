import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Nearest-neighbor export of generated art to a native 32px item and a readable enlarged preview. */
public class PackIntegratedPatternIcon {
    public static void main(String[] args) throws Exception {
        BufferedImage source = ImageIO.read(new File(args[0]));
        BufferedImage item = resize(source, 32);
        ImageIO.write(item, "png", new File(args[1]));
        ImageIO.write(resize(item, 256), "png", new File(args[2]));
    }
    private static BufferedImage resize(BufferedImage source, int size) {
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(source, 0, 0, size, size, null);
        g.dispose();
        return target;
    }
}

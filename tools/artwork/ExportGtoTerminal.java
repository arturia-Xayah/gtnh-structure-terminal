import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Export the unmodified first animation frame for static logo/README use. */
public class ExportGtoTerminal {
    public static void main(String[] args) throws Exception {
        BufferedImage sheet = ImageIO.read(new File(args[0]));
        BufferedImage frame = sheet.getSubimage(0, 0, 16, 16);
        ImageIO.write(frame, "png", new File(args[1]));
        BufferedImage preview = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = preview.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(frame, 0, 0, 256, 256, null);
        g.dispose();
        ImageIO.write(preview, "png", new File(args[2]));
    }
}

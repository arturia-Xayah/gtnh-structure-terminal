import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.*;
import dev.aestoragevault.ui.dashboard.*;
import dev.aestoragevault.core.ByteFormat;

/** Software inspection of the production mesh/layout; never loads Minecraft or creates a window. */
public final class VaultDashboardPreview {
    static final Map<String,String> lang = new HashMap<>();
    static final BigInteger MIB = BigInteger.ONE.shiftLeft(20);
    static boolean english;
    static final Font FONT = new Font("PingFang SC", Font.PLAIN, 9);

    public static void main(String[] args) throws Exception {
        english = args.length > 2;
        Path out = Paths.get(args[0]); Files.createDirectories(out);
        for (String line : Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8)) {
            int i = line.indexOf('='); if (i > 0) lang.put(line.substring(0,i),line.substring(i+1));
        }
        lang.put("container.inventory", args.length > 2 ? "Inventory" : "玩家背包");
        VaultLayout full = VaultLayout.full(), compact = VaultLayout.fit(320,240);
        VaultAnimation a = new VaultAnimation();
        ImageIO.write(render(full,a.sample(1500,MIB.multiply(BigInteger.valueOf(256)),MIB.multiply(BigInteger.valueOf(32)),218,true),true,true,true),"png",out.resolve("full.png").toFile());
        ImageIO.write(render(compact,a.sample(1500,MIB.multiply(BigInteger.valueOf(256)),MIB.multiply(BigInteger.valueOf(32)),218,true),true,true,true),"png",out.resolve("compact.png").toFile());
        VaultAnimation huge = new VaultAnimation(); huge.setEnabled(false);
        ImageIO.write(render(full,huge.sample(0,BigInteger.ONE.shiftLeft(2000),BigInteger.ONE.shiftLeft(1999),Integer.MAX_VALUE,true),true,true,false),"png",out.resolve("huge.png").toFile());
        ImageIO.write(render(full,huge.sample(500,BigInteger.ZERO,BigInteger.ZERO,0,false),false,false,false),"png",out.resolve("invalid.png").toFile());
        a = new VaultAnimation();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out.resolve("animation.gif").toFile())) {
            writer.setOutput(stream); writer.prepareWriteSequence(null);
            for (int n = 0; n < 100; n++) {
                long time = n * 70L;
                boolean motion = time < 4200 || time >= 5250;
                boolean formed = time < 6300;
                a.setEnabled(motion);
                int used = time < 1400 ? 32 : time < 2800 ? 80 : time < 4200 ? 112 : time < 5250 ? 208 : 96;
                VaultAnimation.Frame frame = a.sample(time,MIB.multiply(BigInteger.valueOf(256)),MIB.multiply(BigInteger.valueOf(used)),time < 2800 ? 218 : 12345,formed);
                BufferedImage image = render(full,frame,formed,true,motion);
                IIOMetadata meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image),writer.getDefaultWriteParam());
                String name = meta.getNativeMetadataFormatName(); IIOMetadataNode root = (IIOMetadataNode)meta.getAsTree(name);
                IIOMetadataNode gce = (IIOMetadataNode)root.getElementsByTagName("GraphicControlExtension").item(0);
                gce.setAttribute("disposalMethod","none"); gce.setAttribute("userInputFlag","FALSE"); gce.setAttribute("transparentColorFlag","FALSE"); gce.setAttribute("delayTime","7"); gce.setAttribute("transparentColorIndex","0");
                if (n == 0) {
                    IIOMetadataNode extensions = new IIOMetadataNode("ApplicationExtensions"), loop = new IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID","NETSCAPE"); loop.setAttribute("authenticationCode","2.0"); loop.setUserObject(new byte[]{1,0,0}); extensions.appendChild(loop); root.appendChild(extensions);
                }
                meta.setFromTree(name,root); writer.writeToSequence(new IIOImage(image,null,meta),writer.getDefaultWriteParam());
            }
            writer.endWriteSequence();
        } finally { writer.dispose(); }
        System.out.println(out.toAbsolutePath());
    }

    static String tr(String key) { return lang.getOrDefault(key,key); }
    static String d(String key) { return tr("vault.dashboard."+key); }
    static BufferedImage render(VaultLayout l,VaultAnimation.Frame f,boolean formed,boolean auto,boolean motion) {
        BufferedImage image = new BufferedImage((l.width+24)*2,(l.height+44)*2,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics(); g.setColor(new Color(0xd6e2ee)); g.fillRect(0,0,image.getWidth(),image.getHeight());
        g.scale(2,2); g.translate(12,12); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON); g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON); g.setFont(FONT);
        VaultMesh mesh=new VaultMesh(); mesh.build(l,f,formed,auto);
        for(int i=0;i<mesh.size();i+=3) {
            Path2D.Float p=new Path2D.Float(); p.moveTo(mesh.x(i),mesh.y(i)); p.lineTo(mesh.x(i+1),mesh.y(i+1)); p.lineTo(mesh.x(i+2),mesh.y(i+2)); p.closePath();
            // Adjacent triangles use identical colors; turn off AA to avoid seams at shared edges.
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF); g.setColor(new Color(mesh.color(i),true)); g.fill(p);
        }
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        text(g,tr("vault.title"),12,8,0x244560); text(g,"STORAGE CONTROL",12,19,0x6d91af);
        text(g,d(formed?"formedShort":"incompleteShort"),l.width-63,13,0x56728b);
        int capY=l.compact?39:47;
        text(g,tr("vault.capacity"),l.metricX,capY,0x9cbed6);
        String value=ByteFormat.format(f.capacity);
        double scale=Math.min(l.compact?1.25:1.55,(l.width-l.metricX-20.0)/g.getFontMetrics().stringWidth(value));
        Graphics2D big=(Graphics2D)g.create(); big.translate(l.metricX,capY+14); big.scale(scale,scale); text(big,value,0,0,0xe7f6ff); big.dispose();
        int uy=l.compact?77:91, fy=l.compact?94:113;
        text(g,tr("vault.used"),l.metricX,uy,0x9cbed6); right(g,ByteFormat.format(f.used),l.width-22,uy,0xe1f2ff);
        text(g,d("free"),l.metricX,fy,0x9cbed6); right(g,ByteFormat.format(f.capacity.subtract(f.used)),l.width-22,fy,0xe1f2ff);
        int tenths=(int)Math.round(f.ratio*1000); String percentage=f.capacity.signum()==0?"—":tenths/10+"."+tenths%10+"%";
        center(g,percentage,l.gaugeX,l.gaugeY-6,0xe9f8ff); center(g,d("utilization"),l.gaugeX,l.gaugeY+9,0x99bad3);
        if(!l.compact)text(g,d("types"),12,l.typesY,0x456681);
        right(g,String.format(tr("vault.dashboard.typeCount"),f.types),174,l.typesY,0x244560);
        text(g,tr("container.inventory"),12,l.inventoryLabelY,0x6c87a0);
        int sx=l.statusX+10, sy=l.statusY;
        text(g,d("state"),sx,sy+(l.compact?7:11),0x52738f);
        text(g,tr(formed?"vault.formed":"vault.incomplete"),sx,sy+(l.compact?22:29),formed?0x345d7c:0xa07647);
        text(g,d(auto?"automatic":"paused"),sx,sy+(l.compact?36:44),0x52738f);
        int w=l.buttonWidth(); button(g,sx,l.automaticY,w,d(auto?"absorptionOn":"absorptionOff"),auto);
        button(g,sx,l.controlsY,w/2-2,d("checkShort"),false); button(g,sx+w/2+2,l.controlsY,w-w/2-2,d(motion?"motionOn":"motionOff"),motion);
        if(!l.compact)text(g,d(formed?"idle":"incompleteShort"),12,273,0x68849b);
        text(g,(english ? "Headless geometry render · substitute font · not a game screenshot" : "后台几何渲染 · 字体为离线替代 · 非游戏截图"),0,l.height+15,0x5e748b);
        g.dispose(); return image;
    }
    static void text(Graphics2D g,String s,int x,int y,int color){g.setColor(new Color(color));g.drawString(s,x,y+8);}
    static void right(Graphics2D g,String s,int x,int y,int color){text(g,s,x-g.getFontMetrics().stringWidth(s),y,color);}
    static void center(Graphics2D g,String s,int x,int y,int color){text(g,s,x-g.getFontMetrics().stringWidth(s)/2,y,color);}
    static void button(Graphics2D g,int x,int y,int w,String s,boolean active){g.setColor(new Color(0xa6c5dd));g.fillRect(x,y,w,18);g.setColor(new Color(active?0xbedbf0:0xdce9f5));g.fillRect(x+1,y+1,w-2,16);center(g,s,x+w/2,y+5,0x254c6b);}
}

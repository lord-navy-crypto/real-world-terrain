package org.realscale.neoforge;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Math-only diagnostic, NOT a Minecraft world screenshot or rendered chunk.
 * Generates consistent top-down delta-height previews for all ten archetypes
 * from the actual production pure-Java terrain math, without third-party
 * textures, screenshots, maps, font files or asset downloads.
 */
public final class CinematicLandformPreviewV3 {
    private static final int SIZE=320;
    private static final String[] IDS={
            "sandstone-towers","karst-towers","caprock-mesa",
            "incised-canyon","volcanic-caldera","glacial-u-valley",
            "star-dune-field","twin-tepui-tablelands",
            "glacial-cirque","moraine-ridges"};
    private static final String[] LABELS={
            "SANDSTONE TOWERS","KARST CONES","CAPROCK MESA",
            "INCISED CANYON","VOLCANIC CALDERA","GLACIAL U VALLEY",
            "STAR DUNE FIELD","TWIN TEPUI TABLELANDS",
            "GLACIAL CIRQUE","MORAINE RIDGES"};

    private CinematicLandformPreviewV3() {}
    private static int blend(int left,int right,double amount) {
        return (int)Math.round(left+(right-left)*Math.max(0,Math.min(1,amount)));
    }
    private static Color reliefColor(double delta) {
        // Below-zero terrain as cool, dark rock; uplift as warm mineral.
        // The map represents vertical RELIEF DELTAS, not biome, species,
        // precipitation, per-block Y values or geological accuracy.
        if(delta<=0) {
            double t=Math.max(0,1.0+delta/1.65);
            return new Color(blend(23,111,t),blend(47,127,t),blend(68,120,t));
        }
        double t=Math.min(1.0,delta/3.7);
        return new Color(blend(111,243,t),blend(127,226,t),blend(120,194,t));
    }
    private static void render(Path folder,int kind,long seed) throws IOException {
        long hash=CinematicLandformMathV3.hash((int)(seed>>32),(int)seed);
        double radius=CinematicLandformMathV3.radius(hash);
        BufferedImage image=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++) {
            double dx=((x+0.5)/SIZE*2.30-1.15)*radius;
            double dz=((y+0.5)/SIZE*2.30-1.15)*radius;
            double delta=CinematicLandformMathV3.relief(kind,dx,dz,radius,hash);
            image.setRGB(x,y,reliefColor(delta).getRGB());
        }
        Graphics2D g=image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0,0,0,184));
            g.fillRect(0,0,SIZE,31);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,13));
            g.drawString(LABELS[kind],10,20);
            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(250,250,250,175));
            int scale=80;
            int sx=SIZE-103,sy=SIZE-14;
            g.drawLine(sx,sy,sx+scale,sy);
            g.drawLine(sx,sy-5,sx,sy+5);
            g.drawLine(sx+scale,sy-5,sx+scale,sy+5);
            g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));
            g.drawString("relief delta",10,SIZE-12);
        } finally {
            g.dispose();
        }
        Path target=folder.resolve(IDS[kind]+".png");
        if(!ImageIO.write(image,"png",target.toFile()))
            throw new IOException("JDK PNG writer not installed");
        System.out.println("CINEMATIC_PREVIEW "+target+" radius="+radius);
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Output directory required");
        Path folder=Path.of(args[0]);
        Files.createDirectories(folder);
        for(int k=0;k<IDS.length;k++)
            render(folder,k,0x0a1b2c3d007e5611L);
        System.out.println("CINEMATIC_PREVIEWS_PASS: ten original math-only terrain maps");
    }
}

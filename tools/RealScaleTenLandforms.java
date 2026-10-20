// RealScale: complete self-contained JDK 21 source of ten macro-landform shapes.
// Standalone preview/testing utility. Not a complete NeoForge mod or world preset.
// Production math is adapted directly from CinematicLandformMath.java and V3.
// Minecraft still requires density codecs, registries, datapack JSON & Gradle.
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class RealScaleTenLandforms {
    public static final int COUNT=10;
    public static final String[] NAMES={
        "sandstone-towers","karst-towers","caprock-mesa","incised-canyon",
        "volcanic-caldera","glacial-u-valley","star-dune-field",
        "twin-tepui-tablelands","glacial-cirque","moraine-ridges"};
    private RealScaleTenLandforms(){}
    public static int kind(long hash,double temperature,double humidity){
        return CurrentV3.kind(hash,temperature,humidity);
    }
    public static double relief(int kind,double dx,double dz,double radius,long hash){
        if(kind<0||kind>=COUNT)throw new IllegalArgumentException("kind must be 0..9");
        return CurrentV3.relief(kind,dx,dz,radius,hash);
    }
    public static double apply(int kind,double baseline,double dx,double dz,
                               double radius,long hash,double continentalness) {
        if(kind<0||kind>=COUNT)throw new IllegalArgumentException("kind must be 0..9");
        return CurrentV3.apply(kind,baseline,dx,dz,radius,hash,continentalness);
    }
    public static double sample(int worldX,int worldZ,double temperature,
             double humidity,double baseline,double continentalness) {
        if(baseline<=0.82)return baseline;
        int cx=Math.floorDiv(worldX,CurrentV3.CELL);
        int cz=Math.floorDiv(worldZ,CurrentV3.CELL);
        long hash=CurrentV3.hash(cx,cz);
        double dx=(double)worldX-CurrentV3.siteX(cx,hash);
        double dz=(double)worldZ-CurrentV3.siteZ(cz,hash);
        double radius=CurrentV3.radius(hash);
        if(dx*dx+dz*dz>=radius*radius)return baseline;
        int type=kind(hash,temperature,humidity);
        return type==CurrentV3.NONE?baseline:
            apply(type,baseline,dx,dz,radius,hash,continentalness);
    }


/**
 * Macro-scale original landscape silhouettes. All values are horizontal
 * RealScale height-density units (~128 surface blocks/unit), not block IDs.
 * Pure math: no chunk/world reads, fluid placement or dynamic block edits.
 *
 * Four distinct erosion families, inspired by Earth's sandstone towers,
 * soluble carbonate karst, dry caprock mesas and incised highland gorges.
 */
private static final class LegacyV1 {
    static final int CELL = 4096;
    static final int SANDSTONE_TOWERS = 0;
    static final int KARST_TOWERS = 1;
    static final int CAPROCK_MESA = 2;
    static final int INCISED_CANYON = 3;
    static final int VOLCANIC_CALDERA = 4;
    static final int GLACIAL_U_TROUGH = 5;
    static final int NONE = 6;

    private LegacyV1() {}
    static long mix(long h) {
        h=(h^(h>>>30))*0xbf58476d1ce4e5b9L;
        h=(h^(h>>>27))*0x94d049bb133111ebL;
        return h^(h>>>31);
    }
    static long hash(int x,int z) {
        return mix(((long)x)*0x9e3779b97f4a7c15L
               ^ ((long)z)*0xc2b2ae3d27d4eb4fL
               ^ 0x5d91a6e7b230fc49L);
    }
    static int siteX(int cx,long hash) {
        return (int)((long)cx*CELL + CELL/2
                +Math.floorMod(hash>>>13,401L)-200);
    }
    static int siteZ(int cz,long hash) {
        return (int)((long)cz*CELL + CELL/2
                +Math.floorMod(hash>>>34,401L)-200);
    }
    static double radius(long hash) {
        return 1075.0+Math.floorMod(hash>>>22,276L);
    }
    static double smooth(double a,double b,double x) {
        double t=Math.max(0,Math.min(1,(x-a)/(b-a)));
        return t*t*(3-2*t);
    }
    static double envelope(double r) {
        return 1.0-smooth(0.73,1.0,r);
    }
    static double gate(double continental,double baseline) {
        // No sea-floor alteration or sudden mountain raising on coasts.
        return smooth(0.04,0.31,continental)*smooth(0.82,1.85,baseline);
    }
    static int kind(long h,double temp,double humidity) {
        // World-seeded climate sampled once at site center, not chunk order.
        if(temp>=0.04 && humidity>=0.16)
            return (h&1L)==0 ? SANDSTONE_TOWERS : KARST_TOWERS;
        if(temp>=0.09 && humidity<=-0.09)return CAPROCK_MESA;
        if(humidity<=0.09 && temp>=-0.35)return INCISED_CANYON;
        if(temp<=-0.30)
            return humidity>=-0.04 ? GLACIAL_U_TROUGH : VOLCANIC_CALDERA;
        return NONE;
    }
    static double towerDistance(double u,double v,long h,double spacing) {
        // Nearest jittered lattice site: no uniform Minecraft checkerboard
        // repetition. Jitter is fixed by world-absolute lattice coordinates.
        int cx=(int)Math.floor(u/spacing),cz=(int)Math.floor(v/spacing);
        // Compare squared distances, then take ONE square root. Density
        // sampling calls this for every column: nine hypot/sqrt calls here
        // were unnecessary and dominate generation in tower districts.
        double bestSquared=Double.POSITIVE_INFINITY;
        for(int i=-1;i<=1;i++)for(int j=-1;j<=1;j++) {
            int xx=cx+i,zz=cz+j;
            long k=mix(h ^ (long)xx*0x9e3779b97f4a7c15L
                            ^ (long)zz*0xc2b2ae3d27d4eb4fL);
            double px=(xx+0.5)*spacing
                    +(Math.floorMod(k>>>17,1001L)/1000.0-0.5)*spacing*0.47;
            double pz=(zz+0.5)*spacing
                    +(Math.floorMod(k>>>38,1001L)/1000.0-0.5)*spacing*0.47;
            double du=u-px,dv=v-pz;
            bestSquared=Math.min(bestSquared,du*du+dv*dv);
        }
        return Math.sqrt(bestSquared);
    }
    static double relief(int kind,double dx,double dz,double radius,long h) {
        final double r=Math.hypot(dx,dz)/radius;
        if(r>=1.0 || kind==NONE)return 0;
        final double edge=envelope(r);
        // Direction is district-stable; avoids long world-axis repetition.
        final double angle=(Math.floorMod(h>>>45,6284L)/1000.0);
        final double u=dx*Math.cos(angle)+dz*Math.sin(angle);
        final double v=-dx*Math.sin(angle)+dz*Math.cos(angle);
        return switch(kind) {
            case SANDSTONE_TOWERS -> {
                double d=towerDistance(u,v,h,205.0);
                // Tight, steep-sided separate pillars on a raised wooded
                // platform with winding gaps. Density 2.50 ≈ 320 blocks.
                double pillar=1.0-smooth(48.0,91.0,d);
                yield edge*(0.14+2.65*pillar);
            }
            case KARST_TOWERS -> {
                double d=towerDistance(u,v,h,300.0);
                // Rounder limestone cones with saddle-like valley floors.
                double cone=1.0-smooth(24.0,165.0,d);
                yield edge*(-0.12+2.10*cone);
            }
            case CAPROCK_MESA -> {
                // 900-block plateau with a deliberately sharp but continuous
                // escarpment; no discontinuous floating platform.
                double table=1.0-smooth(0.43,0.51,r);
                yield edge*(0.17+3.05*table);
            }
            case INCISED_CANYON -> {
                double axis=v-125*Math.sin(u/240.0)-42*Math.sin(u/73.0);
                double trench=1.0-smooth(54.0,155.0,Math.abs(axis));
                double shoulders=smooth(105.0,320.0,Math.abs(axis));
                yield edge*(0.55*shoulders-1.35*trench);
            }
            // Broad volcanic caldera: eroded ring wall enclosing a recessed
            // floor. No lava, new water or destructive block placement.
            case VOLCANIC_CALDERA -> {
                double ring=smooth(0.29,0.49,r)
                           *(1.0-smooth(0.68,0.84,r));
                double floor=1.0-smooth(0.12,0.48,r);
                yield edge*(2.85*ring-0.95*floor);
            }
            // Wide glacier-carved U valley with sinuous trough, broad floor
            // and flanking shoulders, rather than a sharp V-shaped ravine.
            case GLACIAL_U_TROUGH -> {
                double axis=Math.abs(v-72*Math.sin(u/360.0));
                double floor=1.0-smooth(105.0,270.0,axis);
                double shoulders=smooth(135.0,430.0,axis)
                        *(1.0-smooth(740.0,1120.0,axis));
                yield edge*(1.80*shoulders-0.78*floor);
            }
            default -> 0.0;
        };
    }
    static double apply(int kind,double before,double dx,double dz,
                        double radius,long hash,double continental) {
        double weight=gate(continental,before);
        return weight==0?before:before+weight*relief(kind,dx,dz,radius,hash);
    }
}

/**
 * Independent v3 cold-north macro morphology. The V1 and V2 codecs and
 * historical V1/V2 noise graphs remain byte-for-byte unchanged.
 * Uses the same world-absolute, seam-free site cells, now with two additional
 * original geological archetypes. No world reads, block writes or fluid edits.
 */
private static final class CurrentV3 {
    static final int CELL=LegacyV1.CELL;
    static final int STAR_DUNE_FIELD=6;
    static final int TEPUI_TABLELANDS=7;
    static final int CIRQUE_BASIN=8;
    static final int MORAINE_RIDGES=9;
    static final int NONE=10;

    private CurrentV3() {}
    // Reuse the exact world-absolute v1 spatial cell layout for continuity.
    // The source functions themselves retain their v1 implementations.
    static long hash(int cx,int cz){return LegacyV1.hash(cx,cz);}
    static int siteX(int cx,long h){return LegacyV1.siteX(cx,h);}
    static int siteZ(int cz,long h){return LegacyV1.siteZ(cz,h);}
    static double radius(long h){return LegacyV1.radius(h);}
    static double gate(double continent,double height) {
        return LegacyV1.gate(continent,height);
    }
    static int kind(long hash,double temperature,double humidity) {
        // Keep existing cold/wet archetypes, but allow climate-matched
        // diversity within broad wet tropics and hot arid environments.
        if(temperature>=0.04 && humidity>=0.16) {
            int variant=(int)Math.floorMod(hash>>>13,3L);
            return variant==0?LegacyV1.SANDSTONE_TOWERS
                   :variant==1?LegacyV1.KARST_TOWERS
                   :TEPUI_TABLELANDS;
        }
        if(temperature>=0.09 && humidity<=-0.09)
            return ((hash>>>21)&1L)==0L
                    ?LegacyV1.CAPROCK_MESA:STAR_DUNE_FIELD;
        if(humidity<=0.09 && temperature>=-0.35)
            return LegacyV1.INCISED_CANYON;
        if(temperature<=-0.30) {
            if(humidity < -0.04)return LegacyV1.VOLCANIC_CALDERA;
            // Cold, moisture-supported uplands cycle through distinct
            // erosional U valleys, amphitheatre cirques and depositional
            // moraines. Selection is per immutable world-coordinate site.
            return switch((int)Math.floorMod(hash>>>27,3L)) {
                case 0 -> LegacyV1.GLACIAL_U_TROUGH;
                case 1 -> CIRQUE_BASIN;
                default -> MORAINE_RIDGES;
            };
        }
        return NONE;
    }

    static double relief(int kind,double dx,double dz,double radius,long hash) {
        double r=Math.hypot(dx,dz)/radius;
        if(r>=1.0 || kind==NONE)return 0.0;
        double envelope=LegacyV1.envelope(r);
        if(kind<STAR_DUNE_FIELD) {
            // V2 scenic erosion: replace perfectly circular rims and orderly
            // dot grids with smooth site-specific folding, without changing
            // a single byte of the V1 production shape function.
            // The original radius gate is applied last so warped shapes NEVER
            // leak across 4-km site boundaries.
            double seed=Math.floorMod(hash>>>36,6284L)/1000.0;
            double wx=dx*1.035+58.0*Math.sin(dz/290.0+seed)
                    +21.0*Math.sin((dx+dz)/124.0-seed);
            double wz=dz*0.955+52.0*Math.cos(dx/325.0-seed)
                    +24.0*Math.sin((dx-dz)/138.0+seed);
            double weather=0.88+0.16*
                    (0.5+0.5*Math.sin(wx/270.0+wz/410.0+seed));
            return envelope*weather*
                    LegacyV1.relief(kind,wx,wz,radius,hash);
        }
        double angle=Math.floorMod(hash>>>45,6284L)/1000.0;
        double u=dx*Math.cos(angle)+dz*Math.sin(angle);
        double v=-dx*Math.sin(angle)+dz*Math.cos(angle);
        return switch(kind) {
            case STAR_DUNE_FIELD -> {
                // An asymmetric wind-aligned dune complex with branching
                // slipface arms, not a single symmetrical conical hill.
                // Positive-only sand relief; natural sea-floor and base
                // continental thresholds are safeguarded by density gate.
                double windward=0.5+0.5*Math.sin(u/195.0+0.33*Math.sin(v/315.0));
                double crossing=0.5+0.5*Math.sin(v/240.0-0.25*Math.sin(u/300.0));
                double ridge=Math.pow(windward,5.0);
                double arms=Math.pow(crossing,7.0);
                double star=Math.pow(Math.max(0.0,
                        Math.cos(5.0*Math.atan2(v,u))),8.0);
                double core=Math.exp(-Math.pow(Math.hypot(u,v)/830.0,2.0));
                yield envelope*(0.07+1.30*ridge+0.66*arms
                        +0.48*core*star);
            }
            case TEPUI_TABLELANDS -> {
                // Two caprock massifs with independent flat tops and steep
                // sides; the saddle between them opens a traverse corridor.
                double first=Math.pow(
                        Math.pow(Math.abs((u+450.0)/480.0),4)
                        +Math.pow(Math.abs((v+95.0)/440.0),4),0.25);
                double second=Math.pow(
                        Math.pow(Math.abs((u-450.0)/455.0),4)
                        +Math.pow(Math.abs((v-130.0)/395.0),4),0.25);
                double plateau1=1.0-LegacyV1.smooth(0.68,0.84,first);
                double plateau2=1.0-LegacyV1.smooth(0.68,0.84,second);
                double capped=Math.max(plateau1,plateau2);
                yield envelope*(0.10+3.15*capped);
            }
            case CIRQUE_BASIN -> {
                // Erosion-style amphitheatre in a cold mountain shoulder:
                // a concave basin, semicircular higher headwall and a
                // low, open outlet extending along +v. This is not a lake.
                double ellipse=Math.hypot(u/440.0,(v+185.0)/380.0);
                double basin=1.0-LegacyV1.smooth(0.18,0.91,ellipse);
                double wall=LegacyV1.smooth(0.54,0.76,ellipse)
                        *(1.0-LegacyV1.smooth(1.00,1.27,ellipse));
                double opening=1.0-LegacyV1.smooth(-40,270,v);
                double outlet=(1.0-LegacyV1.smooth(100,250,
                        Math.abs(u-31*Math.sin(v/210.0))))
                        *LegacyV1.smooth(-190,90,v)
                        *(1.0-LegacyV1.smooth(570,870,v));
                yield envelope*(2.05*wall*opening-0.91*basin
                        -0.36*outlet);
            }
            case MORAINE_RIDGES -> {
                // Glacial till: two irregular side-ridges + arced terminal
                // ridge with a depressed inter-ridge corridor. No water,
                // glacial ice or unsupported floating structures are placed.
                double lateralOffset=Math.abs(u+26*Math.sin(v/170.0))-230.0;
                double leftRight=Math.exp(-Math.pow(lateralOffset/78.0,2))
                        *(1.0-LegacyV1.smooth(620,960,Math.abs(v)));
                double elliptic=Math.hypot(u/495.0,(v-80.0)/520.0);
                double terminal=Math.exp(-Math.pow((elliptic-0.82)/0.12,2))
                        *LegacyV1.smooth(-290,110,v);
                double hollow=(1.0-LegacyV1.smooth(85,220,Math.abs(u)))
                        *(1.0-LegacyV1.smooth(160,490,Math.abs(v)));
                yield envelope*(1.05*leftRight+0.87*terminal-0.24*hollow);
            }
            default -> 0.0;
        };
    }
    static double apply(int kind,double baseline,double dx,double dz,
                        double radius,long hash,double continental) {
        double gate=LegacyV1.gate(continental,baseline);
        return gate==0?baseline:baseline+gate*relief(kind,dx,dz,radius,hash);
    }
}

    private static void check(boolean b,String message) {
        if(!b)throw new AssertionError(message);
    }
    private static void selfTest(){
        boolean[] seen=new boolean[COUNT];
        for(int cx=-22;cx<=22;cx++)for(int cz=-22;cz<=22;cz++){
            long h=CurrentV3.hash(cx,cz);
            seen[kind(h,0.6,0.5)]=true;
            seen[kind(h,0.6,-0.5)]=true;
            seen[kind(h,-0.7,0.5)]=true;
            seen[kind(h,-0.7,-0.5)]=true;
            seen[kind(h,-0.1,-0.5)]=true;
        }
        for(int i=0;i<COUNT;i++)check(seen[i],"Landform not selected: "+NAMES[i]);
        int samples=0;
        for(int cx=-2;cx<=2;cx++)for(int cz=-2;cz<=2;cz++){
            long h=CurrentV3.hash(cx,cz);
            double radius=CurrentV3.radius(h);
            for(int i=0;i<COUNT;i++) {
                check(relief(i,radius,0,radius,h)==0.0,"Seam at radius");
                check(apply(i,3,0,0,radius,h,-0.3)==3,"Marine safety");
                check(apply(i,0.3,0,0,radius,h,0.8)==0.3,"Lowland safety");
                for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){
                    double dx=x*radius/8.0,dz=z*radius/8.0;
                    double result=relief(i,dx,dz,radius,h);
                    check(Double.isFinite(result)&&result>=-1.6&&result<=4.0,
                        "Finite/bounded relief");
                    check(result==relief(i,dx,dz,radius,h),"Not deterministic");
                    samples++;
                }
            }
        }
        check(sample(0,0,0,0,0.2,0.8)==0.2,"Sample land protection");
        check(sample(0,0,0,0,3.0,-0.3)==3.0,"Sample marine protection");
        System.out.println("REAL_SCALE_ALL_TEN_PASS: "+samples+" deterministic geometry checks");
    }
    private static Color color(double d){
        double t=Math.min(1.0,Math.abs(d)/(d<0?1.65:3.7));
        if(d<0)return new Color((int)(110-87*t),(int)(129-82*t),(int)(121-55*t));
        return new Color((int)(111+132*t),(int)(127+99*t),(int)(120+74*t));
    }
    private static void render(Path dir)throws Exception{
        Files.createDirectories(dir);
        long h=CurrentV3.hash(2,1);
        double radius=CurrentV3.radius(h);
        int size=320;
        for(int i=0;i<COUNT;i++) {
            BufferedImage img=new BufferedImage(size,size,BufferedImage.TYPE_INT_RGB);
            for(int z=0;z<size;z++)for(int x=0;x<size;x++){
                double dx=((x+0.5)*2.30/size-1.15)*radius;
                double dz=((z+0.5)*2.30/size-1.15)*radius;
                img.setRGB(x,z,color(relief(i,dx,dz,radius,h)).getRGB());
            }
            Graphics2D g=img.createGraphics();
            try{
                g.setColor(new Color(0,0,0,185));g.fillRect(0,0,size,27);
                g.setColor(Color.WHITE);g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,13));
                g.drawString(NAMES[i],10,19);
            }finally{g.dispose();}
            Path output=dir.resolve(String.format("%02d-%s.png",i+1,NAMES[i]));
            if(!ImageIO.write(img,"png",output.toFile()))
                throw new IllegalStateException("PNG writer unavailable");
            System.out.println(output);
        }
    }
    public static void main(String[] args) throws Exception {
        if(args.length==0||"--test".equals(args[0])){selfTest();return;}
        if("--render".equals(args[0])&&args.length==2){
            selfTest();render(Path.of(args[1]));return;
        }
        if("--sample".equals(args[0])&&args.length==7){
            System.out.printf(java.util.Locale.ROOT,"%.12f%n",sample(
                Integer.parseInt(args[1]),Integer.parseInt(args[2]),
                Double.parseDouble(args[3]),Double.parseDouble(args[4]),
                Double.parseDouble(args[5]),Double.parseDouble(args[6])));
            return;
        }
        throw new IllegalArgumentException("Use --test, --render OUTPUT_DIR, or --sample X Z TEMP HUMIDITY BASE CONTINENTS");
    }
}

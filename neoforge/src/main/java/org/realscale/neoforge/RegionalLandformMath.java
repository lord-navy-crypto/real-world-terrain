package org.realscale.neoforge;

/**
 * Deterministic, bounded regional landform archetypes. Pure Java; no chunk
 * access, block writes, neighbor-world reads or biome replacement.
 * Values are HORIZONTAL RealScale height-signal units, not metres.
 */
final class RegionalLandformMath {
    static final int CELL = 4096;
    static final int TIBETAN_PLATEAU = 0;
    static final int TIBETAN_WIDE_VALLEY = 1;
    static final int ANDEAN_ALTIPLANO = 2;
    static final int EQUATORIAL_VOLCANO = 3;
    static final int SICHUAN_BASIN = 4;
    static final int CHONGQING_RIDGES = 5;
    static final int YUNGUI_KARST = 6;
    static final int YUNGUI_BAZI = 7;
    static final int KINDS = 8;
    // V2 is an opt-in regional atlas; original eight ID values never change.
    static final int CELL_V2 = 8192;
    static final int TIBETAN_GLACIAL_TROUGH = 8;
    static final int TIBETAN_ENDORHEIC_BASIN = 9;
    static final int ANDEAN_SALT_PAN = 10;
    static final int ANDEAN_BOFEDEL_SHELF = 11;
    static final int ECUADOR_VOLCANIC_CHAIN = 12;
    static final int YUNGUI_FENGLIN = 13;
    static final int WULONG_TIAN_KENG = 14;
    static final int SICHUAN_ESCARPMENT = 15;
    static final int KINDS_V2 = 16;
    // v3: worldwide landform atlas. All original 0..15 IDs preserved.
    static final int CELL_V3 = 12288;
    static final int GUIANA_TEPUI = 16;
    static final int TROPICAL_INSELBERGS = 17;
    static final int GLACIAL_DRUMLIN_FIELD = 18;
    static final int GLACIAL_ESKER_RIDGE = 19;
    static final int NAMIB_STAR_DUNES = 20;
    static final int EAST_AFRICAN_RIFT = 21;
    static final int UTAH_SLOT_CANYON = 22;
    static final int ICELAND_CALDERA = 23;
    static final int KINDS_V3 = 24;

    private RegionalLandformMath() {}
    static long mix(long x) {
        x ^= x >>> 30; x *= 0xbf58476d1ce4e5b9L;
        x ^= x >>> 27; x *= 0x94d049bb133111ebL;
        return x ^ (x >>> 31);
    }
    static long hash(int cx, int cz) {
        return mix((long) cx * 0x9e3779b97f4a7c15L
                 ^ (long) cz * 0xc2b2ae3d27d4eb4fL
                 ^ 0x5a78bdf12634c901L);
    }
    static int centerX(int cellX, long h) {
        return Math.toIntExact((long) cellX * CELL + CELL / 2
                + Math.floorMod(h >>> 17, 381L) - 190);
    }
    static int centerZ(int cellZ, long h) {
        return Math.toIntExact((long) cellZ * CELL + CELL / 2
                + Math.floorMod(h >>> 37, 381L) - 190);
    }
    static double radius(long h) {
        return 1100.0 + Math.floorMod(h >>> 10, 421L);
    }
    static int kind(long h, double seededSelector) {
        return Math.floorMod((int) Math.floorMod(h, KINDS)
                + (int) Math.round(GeoMath.clamp(seededSelector,-1,1)*3), KINDS);
    }
    /** New 8-km atlas sites; separate hash and sizing preserve all v1 seeds. */
    static long hashV2(int cx,int cz) {
        return mix((long)cx*0x9e3779b97f4a7c15L
                 ^ (long)cz*0xc2b2ae3d27d4eb4fL
                 ^ 0x1078ed49ab529c03L);
    }
    static int centerXV2(int cx,long h) {
        return Math.toIntExact((long)cx*CELL_V2+CELL_V2/2
                +Math.floorMod(h>>>17,561L)-280);
    }
    static int centerZV2(int cz,long h) {
        return Math.toIntExact((long)cz*CELL_V2+CELL_V2/2
                +Math.floorMod(h>>>37,561L)-280);
    }
    static double radiusV2(long h) {
        return 2450.0 + Math.floorMod(h>>>11,651L);
    }
    static int kindV2(long h,double seededSelector) {
        int candidate=Math.floorMod((int)Math.floorMod(h,KINDS_V2)
                +(int)Math.round(GeoMath.clamp(seededSelector,-1,1)*5),KINDS_V2);
        return candidate;
    }
    /** 12.288km cells, safely bounded and independent of v1/v2 worldgen. */
    static long hashV3(int cx,int cz) {
        return mix((long)cx*0x9e3779b97f4a7c15L
                 ^ (long)cz*0xc2b2ae3d27d4eb4fL
                 ^ 0x740e47a6db15c921L);
    }
    static int centerXV3(int cx,long h) {
        return Math.toIntExact((long)cx*CELL_V3+CELL_V3/2
                +Math.floorMod(h>>>17,721L)-360);
    }
    static int centerZV3(int cz,long h) {
        return Math.toIntExact((long)cz*CELL_V3+CELL_V3/2
                +Math.floorMod(h>>>37,721L)-360);
    }
    static double radiusV3(long h) {
        return 4150.0+Math.floorMod(h>>>11,751L);
    }
    static int kindV3(long h,double seededSelector) {
        return Math.floorMod((int)Math.floorMod(h,KINDS_V3)
                +(int)Math.round(GeoMath.clamp(seededSelector,-1,1)*7),
                KINDS_V3);
    }
    static double gaussian(double x) {
        return Math.exp(-x*x);
    }
    static double gate(double continent,double baseline) {
        // Inland surface ONLY. Preserves ocean floors and low sea-level land.
        return GeoMath.smoothstep(-0.02, 0.28, continent)
                * GeoMath.smoothstep(0.35, 1.25, baseline);
    }
    static double core(double r) {
        return 1.0-GeoMath.smoothstep(0.56,1.02,r);
    }
    static double rim(double r) {
        return GeoMath.smoothstep(0.58,0.81,r)
                *(1.0-GeoMath.smoothstep(0.91,1.08,r));
    }
    static double effect(int kind, double dx, double dz, double radius) {
        double rr=Math.hypot(dx,dz)/radius;
        if(rr>=1.08)return 0.0;
        double core=core(rr);
        double rim=rim(rr);
        double u=dx*0.8+dz*0.6;
        double v=-dx*0.6+dz*0.8;
        return switch(kind) {
            // Tall broad shelf, not a field of unrelated sharp peaks.
            case TIBETAN_PLATEAU ->
                    core*(2.70+0.18*Math.sin(u/210.0)*Math.cos(v/280.0));
            // A U-shaped alpine trough between high plateau shoulders.
            case TIBETAN_WIDE_VALLEY ->
                    core*(0.75+1.55*GeoMath.smoothstep(110,500,Math.abs(v)));
            // Elevated and partially enclosed dry intermontane basin.
            case ANDEAN_ALTIPLANO ->
                    2.45*core +0.75*rim
                    -0.42*(1.0-GeoMath.smoothstep(0.05,0.35,rr))*core;
            // High volcanic cone with a shallow circular crater.
            case EQUATORIAL_VOLCANO ->
                    core*4.20*(1.0-GeoMath.smoothstep(0.05,0.82,rr))
                    -core*1.25*(1.0-GeoMath.smoothstep(0.0,0.095,rr));
            // Encircling hills + subdued interior; keep original runoff.
            case SICHUAN_BASIN ->
                    -1.05*core+0.74*rim+0.15*core*Math.cos(u/180.0);
            // Repeated parallel anticline-like ridges cut by a winding gorge.
            case CHONGQING_RIDGES -> {
                double ridges=Math.pow(Math.max(0,Math.cos(v/85.0)),4.0);
                double channel=u-180.0*Math.sin(v/310.0);
                double gorge=Math.exp(-Math.pow(channel/105.0,2.0));
                yield core*(0.40+1.05*ridges-1.40*gorge);
            }
            // Peak-cluster limestone cones separated by closed depressions.
            case YUNGUI_KARST -> {
                double warpedU=u+48*Math.sin(v/160.0);
                double warpedV=v+54*Math.sin(u/145.0);
                double cone=Math.pow(Math.max(0,Math.cos(warpedU/70.0)),2.0)
                           *Math.pow(Math.max(0,Math.cos(warpedV/75.0)),2.0);
                yield core*(-0.22+2.15*cone);
            }
            // Flat 'bazi'/polje floor surrounded by discontinuous uplands.
            case YUNGUI_BAZI ->
                    core*(-0.70+0.08*Math.cos(u/230.0)) +1.2*rim;
            // Qinghai–Tibet: glacially widened U trough flanked by benches.
            case TIBETAN_GLACIAL_TROUGH ->
                    core*(-0.28+2.25*GeoMath.smoothstep(150,850,Math.abs(v)));
            // High plateau internally drained basin, not a generated lake.
            case TIBETAN_ENDORHEIC_BASIN ->
                    core*(-1.10+0.16*rr*rr)+1.15*rim;
            // Peru–Bolivia Altiplano: smooth salt-pan FLOOR only, not salt water.
            case ANDEAN_SALT_PAN ->
                    core*(-0.90+0.06*Math.sin(u/460.0)*Math.cos(v/420.0))
                    +0.82*rim;
            // Andean bofedal-like wetland bench; no artificial aquifer edits.
            case ANDEAN_BOFEDEL_SHELF ->
                    core*(-0.42+0.45*GeoMath.smoothstep(290,880,Math.abs(v)));
            // Equatorial Andean volcanic arc, three raised cones with craters.
            case ECUADOR_VOLCANIC_CHAIN -> {
                double result=0;
                for(int n=-1;n<=1;n++) {
                    double coneU=(u-n*730.0)/320.0;
                    double coneV=(v-n*115.0)/340.0;
                    double d2=coneU*coneU+coneV*coneV;
                    result+=2.8*Math.exp(-d2*1.45)
                            -0.65*Math.exp(-d2*24.0);
                }
                yield core*result;
            }
            // Guizhou Wanfenglin: separated limestone tower peaks on valley floor.
            case YUNGUI_FENGLIN -> {
                double peaks=Math.pow(Math.max(0,Math.cos(u/195.0)),6.0)
                        *Math.pow(Math.max(0,Math.cos(v/225.0)),6.0);
                yield core*(-0.20+2.65*peaks);
            }
            // Chongqing Wulong: clustered limestone collapse-doline bowls.
            case WULONG_TIAN_KENG -> {
                double bowls=0;
                for(int n=-1;n<=1;n++) {
                    double px=(u-n*590.0)/195.0;
                    double pz=(v-180*Math.cos(n*2.0))/210.0;
                    bowls+=Math.exp(-(px*px+pz*pz)*1.9);
                }
                yield core*(0.35-1.75*bowls)+0.28*rim;
            }
            // Sichuan Basin margin: broad low floor with rising stepped hills.
            case SICHUAN_ESCARPMENT -> {
                double slope=GeoMath.smoothstep(280,1050,Math.abs(v));
                double terrace=0.13*Math.cos(v/160.0)*slope;
                yield core*(-0.42+1.8*slope+terrace);
            }
            // UNESCO Guiana Shield tepui: broad top with rapidly falling sandstone walls.
            case GUIANA_TEPUI -> {
                double shelf=1.0-GeoMath.smoothstep(0.44,0.51,rr);
                yield core*(0.20+3.55*shelf
                        +0.055*Math.sin(u/310.0)*Math.cos(v/360.0));
            }
            // Tropical inselbergs: separate smooth granite domes above flatter ground.
            case TROPICAL_INSELBERGS -> {
                double domes=0;
                for(int n=-2;n<=2;n++) {
                    double px=(u-n*780.0)/410.0;
                    double pz=(v-380*Math.sin(n*2.1))/325.0;
                    domes=Math.max(domes,2.9*gaussian(px)*gaussian(pz));
                }
                yield core*(0.12+domes);
            }
            // Ice-flow aligned drumlins: rounded but elongated till hills.
            case GLACIAL_DRUMLIN_FIELD -> {
                double span=Math.pow(Math.max(0,Math.cos(u/220.0)),8.0);
                double elongate=Math.pow(Math.max(0,
                        Math.cos((v+55*Math.sin(u/330.0))/820.0)),6.0);
                yield core*(0.18+1.75*span*elongate);
            }
            // Esker: a continuous winding, narrow glaciofluvial gravel ridge.
            case GLACIAL_ESKER_RIDGE -> {
                double offset=v-300*Math.sin(u/660.0)-130*Math.sin(u/205.0);
                double crest=gaussian(offset/69.0);
                yield core*(0.05+2.30*crest);
            }
            // Namib-like star dunes: multiple radial dune arms, not a cone.
            case NAMIB_STAR_DUNES -> {
                double theta=Math.atan2(v,u);
                double arms=Math.pow(0.5+0.5*Math.cos(6*theta),5.0);
                double distance=Math.hypot(u,v);
                double dune=gaussian((distance-870.0)/700.0);
                yield core*(0.15+2.75*dune*(0.38+0.62*arms));
            }
            // Rift valley: down-dropped floor, two lifted fault shoulders.
            case EAST_AFRICAN_RIFT -> {
                double transverse=Math.abs(v-140*Math.sin(u/940.0));
                double floor=1.0-GeoMath.smoothstep(165,360,transverse);
                double shoulders=GeoMath.smoothstep(280,530,transverse)
                                  *(1.0-GeoMath.smoothstep(700,1100,transverse));
                yield core*(-1.60*floor+1.40*shoulders);
            }
            // Sandstone slot canyon: narrow meandering incision in plateau.
            case UTAH_SLOT_CANYON -> {
                double channel=v-190*Math.sin(u/510.0)-75*Math.sin(u/160.0);
                yield core*(0.53-2.85*gaussian(channel/82.0));
            }
            // Wide caldera with a ring wall and recessed floor, unlike a cone volcano.
            case ICELAND_CALDERA -> {
                double localR=Math.hypot(u,v)/1900.0;
                double ring=GeoMath.smoothstep(0.60,0.81,localR)
                        *(1.0-GeoMath.smoothstep(0.97,1.16,localR));
                double basin=1.0-GeoMath.smoothstep(0.48,0.84,localR);
                yield core*(2.60*ring-1.35*basin);
            }
            default -> 0;
        };
    }
    static double apply(int kind,double original,double dx,double dz,
                        double radius,double continentalness) {
        double gate=gate(continentalness,original);
        if(gate<=0)return original;
        // Bounded local change, always approaching zero at site boundary.
        return original+gate*effect(kind,dx,dz,radius);
    }
}

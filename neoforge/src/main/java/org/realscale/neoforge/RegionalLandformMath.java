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

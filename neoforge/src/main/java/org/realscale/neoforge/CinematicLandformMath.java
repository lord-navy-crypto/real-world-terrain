package org.realscale.neoforge;

/**
 * Macro-scale original landscape silhouettes. All values are horizontal
 * RealScale height-density units (~128 surface blocks/unit), not block IDs.
 * Pure math: no chunk/world reads, fluid placement or dynamic block edits.
 *
 * Four distinct erosion families, inspired by Earth's sandstone towers,
 * soluble carbonate karst, dry caprock mesas and incised highland gorges.
 */
final class CinematicLandformMath {
    static final int CELL = 4096;
    static final int SANDSTONE_TOWERS = 0;
    static final int KARST_TOWERS = 1;
    static final int CAPROCK_MESA = 2;
    static final int INCISED_CANYON = 3;
    static final int NONE = 4;

    private CinematicLandformMath() {}
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
        return NONE;
    }
    static double towerDistance(double u,double v,long h,double spacing) {
        // Nearest jittered lattice site: no uniform Minecraft checkerboard
        // repetition. Jitter is fixed by world-absolute lattice coordinates.
        int cx=(int)Math.floor(u/spacing),cz=(int)Math.floor(v/spacing);
        double best=Double.POSITIVE_INFINITY;
        for(int i=-1;i<=1;i++)for(int j=-1;j<=1;j++) {
            int xx=cx+i,zz=cz+j;
            long k=mix(h ^ (long)xx*0x9e3779b97f4a7c15L
                            ^ (long)zz*0xc2b2ae3d27d4eb4fL);
            double px=(xx+0.5)*spacing
                    +(Math.floorMod(k>>>17,1001L)/1000.0-0.5)*spacing*0.47;
            double pz=(zz+0.5)*spacing
                    +(Math.floorMod(k>>>38,1001L)/1000.0-0.5)*spacing*0.47;
            best=Math.min(best,Math.hypot(u-px,v-pz));
        }
        return best;
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
            default -> 0.0;
        };
    }
    static double apply(int kind,double before,double dx,double dz,
                        double radius,long hash,double continental) {
        double weight=gate(continental,before);
        return weight==0?before:before+weight*relief(kind,dx,dz,radius,hash);
    }
}

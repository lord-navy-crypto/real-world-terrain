package org.realscale.neoforge;

/**
 * Source-backed real-ocean bathymetric analogues. All values are horizontal
 * terrain-density units (128 vertical Minecraft blocks per unit), NOT metres.
 * No block writes, no neighboring chunk reads, no mob/biome replacement.
 */
final class OceanFloorMorphologyMath {
    static final int CELL=8192;
    static final int ABYSSAL_PLAIN=0;
    static final int MID_OCEAN_RIDGE=1;
    static final int HADAL_TRENCH=2;
    static final int SEAMOUNT_CHAIN=3;
    static final int GUYOT=4;
    static final int SUBMARINE_CANYON=5;
    static final int ABYSSAL_HILLS=6;
    static final int TRANSFORM_FRACTURE_ZONE=7;
    static final int TYPES=8;
    private OceanFloorMorphologyMath(){}

    static long hash(int cx,int cz) {
        return RegionalLandformMath.mix((long)cx*0x9e3779b97f4a7c15L
                 ^ (long)cz*0xc2b2ae3d27d4eb4fL ^ 0x3d408c92a67e2019L);
    }
    static int centerX(int cx,long hash) {
        return Math.toIntExact((long)cx*CELL+CELL/2+
                Math.floorMod(hash>>>17,601L)-300);
    }
    static int centerZ(int cz,long hash) {
        return Math.toIntExact((long)cz*CELL+CELL/2+
                Math.floorMod(hash>>>37,601L)-300);
    }
    static double radius(long hash) {
        return 2300.0+Math.floorMod(hash>>>10,651L);
    }
    static int kind(long hash,double seededSelector) {
        return Math.floorMod((int)Math.floorMod(hash,TYPES)
                +(int)Math.round(GeoMath.clamp(seededSelector,-1,1)*3),TYPES);
    }
    /** 0 on every inland coast, positive only well offshore and underwater. */
    static double marineGate(double continentalness,double originalHeight) {
        return (1.0-GeoMath.smoothstep(-0.38,-0.15,continentalness))
                *(1.0-GeoMath.smoothstep(-0.42,-0.14,originalHeight));
    }
    static double fade(double x,double z,double radius) {
        return 1.0-GeoMath.smoothstep(0.64,1.04,Math.hypot(x,z)/radius);
    }
    static double gaussian(double distance,double width) {
        double u=distance/width;
        return Math.exp(-u*u);
    }
    /** Difference from original height; sign positive means shallower ocean. */
    static double anomaly(int kind,double u,double v,double originalHeight) {
        double rr=Math.hypot(u,v);
        return switch(kind) {
            // Silty, smoothed deep basins. Does not erase regional relief.
            case ABYSSAL_PLAIN ->
                    0.48*(-0.88-originalHeight);
            // Spreading-center mountain chain with an axial depression.
            case MID_OCEAN_RIDGE -> {
                double axis=v-185*Math.sin(u/680.0);
                yield 0.96*gaussian(axis,690)
                       -0.57*gaussian(axis,108);
            }
            // Trench under a subduction boundary: extremely narrow floor.
            case HADAL_TRENCH -> {
                double channel=v-110*Math.sin(u/710.0);
                yield -1.32*gaussian(channel,145)
                        +0.22*gaussian(channel-550,310);
            }
            // Oceanic volcanic islands that REMAIN submerged.
            case SEAMOUNT_CHAIN -> {
                double cones=0.0;
                for(int n=-2;n<=2;n++) {
                    double x=(u-n*560.0)/320.0;
                    double z=(v-310*Math.sin(1.7*n))/370.0;
                    cones=Math.max(cones,1.12*Math.exp(-x*x-z*z));
                }
                yield cones;
            }
            // Eroded, flat-topped submerged volcanic seamount.
            case GUYOT -> 1.05*(1.0-GeoMath.smoothstep(420,720,rr));
            // Eroded submarine slope: a long incised meandering canyon.
            case SUBMARINE_CANYON -> {
                double channel=v-180*Math.sin(u/480.0)-67*Math.sin(u/170.0);
                yield -0.93*gaussian(channel,118);
            }
            // Broken but gently corrugated abyssal hill terrain.
            case ABYSSAL_HILLS ->
                    0.27*Math.sin(u/280.0)*Math.cos(v/355.0)
                    +0.12*Math.sin((u+v)/175.0);
            // Transform boundary: two staggered seabed steps/fault lines.
            case TRANSFORM_FRACTURE_ZONE -> {
                double fault=v-210*Math.sin(u/610.0);
                yield 0.38*gaussian(fault-180.0,175.0)
                        -0.45*gaussian(fault+190.0,145.0)
                        +0.11*Math.tanh(u/440.0)*gaussian(fault,640);
            }
            default -> 0.0;
        };
    }
    static double delta(int kind,double dx,double dz,double radius,
                        double continentalness,double originalHeight,
                        double measuredProtection) {
        double gate=marineGate(continentalness,originalHeight);
        if(gate<=0.0 || measuredProtection>=1.0)return 0.0;
        double rim=fade(dx,dz,radius);
        if(rim<=0.0)return 0.0;
        double u=dx*0.8+dz*0.6;
        double v=-dx*0.6+dz*0.8;
        double target=originalHeight+rim*anomaly(kind,u,v,originalHeight);
        // Avoid accidental dry-ocean / sky islands: stop elevation above
        // -0.30 horizontal signal (~38 blocks BELOW sea level).
        target=Math.min(target,-0.30);
        return gate*(1.0-GeoMath.clamp(measuredProtection,0,1))*
                (target-originalHeight);
    }
}

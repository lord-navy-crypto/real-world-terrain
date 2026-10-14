package org.realscale.neoforge;

/**
 * Continuous 3D rock-void network for Grand Nether's upper strata.
 *
 * Every 256x256 horizontal cell has two underground halls. Each hall joins
 * neighbors in all four cardinal directions, and the two levels have a
 * vertical diagonal connector. Geometry is calculated from absolute world
 * coordinates, so independent chunk-generation order cannot cut tunnels.
 *
 * Restricted to Y144..440: original lava sea, normal Nether structures and
 * top bedrock remain untouched by this decorator.
 */
public final class GrandNetherCaveNetwork {
    private GrandNetherCaveNetwork() {}
    public static final int CELL = 256;
    public static final int MIN_Y = 144;
    public static final int MAX_Y = 440;
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};

    public record Hall(double x, double y, double z, double radiusX,
                       double radiusY, double radiusZ) {}

    private static long mix(long k) {
        k ^= k >>> 30; k *= 0xbf58476d1ce4e5b9L;
        k ^= k >>> 27; k *= 0x94d049bb133111ebL;
        return k ^ (k >>> 31);
    }

    public static Hall hall(int gx, int gz, int layer) {
        long hash = mix((long)gx * 0x9e3779b97f4a7c15L
                   ^ (long)gz * 0xc2b2ae3d27d4eb4fL
                   ^ (layer == 0 ? 0x15b9ce514c94a3d7L : 0x5c9a3cb6af76d27bL));
        int sx = (int) Math.floorMod(hash, 69L) - 34;
        int sz = (int) Math.floorMod(hash >>> 18, 69L) - 34;
        double altitude = (layer == 0 ? 205 : 348)
                         + (int) Math.floorMod(hash >>> 36, 39L) - 19;
        return new Hall(((long)gx)*CELL + 128 + sx, altitude,
                ((long)gz)*CELL + 128 + sz,
                52 + (int)Math.floorMod(hash >>> 27, 20L),
                31 + (int)Math.floorMod(hash >>> 48, 10L),
                52 + (int)Math.floorMod(hash >>> 53, 11L));
    }

    private static double chamber(double x, double y, double z, Hall h) {
        double dx = (x-h.x())/h.radiusX();
        double dy = (y-h.y())/h.radiusY();
        double dz = (z-h.z())/h.radiusZ();
        double normalized = dx*dx+dy*dy+dz*dz;
        return normalized >= 1 ? 0 : 1 - normalized;
    }

    private static double tube(double x, double y, double z, Hall a, Hall b,
                               double radius) {
        double vx=b.x()-a.x(), vy=b.y()-a.y(), vz=b.z()-a.z();
        double t=((x-a.x())*vx+(y-a.y())*vy+(z-a.z())*vz)
                  /(vx*vx+vy*vy+vz*vz);
        if(t<=0 || t>=1)return 0;
        double dx=x-(a.x()+t*vx);
        double dy=y-(a.y()+t*vy);
        double dz=z-(a.z()+t*vz);
        double ratio=(dx*dx+dy*dy+dz*dz)/(radius*radius);
        return ratio>=1 ? 0 : 1-ratio;
    }

    /** Positive strength means excavated air; zero means original density. */
    public static double strength(int x, int y, int z) {
        if(y<MIN_Y || y>MAX_Y)return 0;
        int gx=Math.floorDiv(x,CELL);
        int gz=Math.floorDiv(z,CELL);
        double answer=0;
        for(int layer=0;layer<2;layer++) {
            Hall center=hall(gx,gz,layer);
            answer=Math.max(answer,chamber(x,y,z,center));
            if(answer>=1)return 1;
            // The same world-space edge is evaluated from either adjoining
            // cell. Only nearby cells are needed; no chunks are read.
            for(int edge=0;edge<4;edge++) {
                Hall target=hall(gx+DX[edge],gz+DZ[edge],layer);
                answer=Math.max(answer,tube(x,y,z,center,target,11));
            }
            // A diagonal chimney links the low and high halls, building a
            // vertically navigable network through elevated Nether mountains.
            if(layer==0)
                answer=Math.max(answer,tube(x,y,z,center,hall(gx,gz,1),10));
        }
        return answer;
    }
}

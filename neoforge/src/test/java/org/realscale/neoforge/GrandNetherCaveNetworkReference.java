package org.realscale.neoforge;

/**
 * Continuous 3D rock-void network for Grand Nether's upper strata.
 *
 * Every 256x256 horizontal cell has two underground halls and four smaller
 * satellite galleries. Main halls join neighbors in four cardinal directions
 * and both levels are linked by a vertical diagonal connector. Every satellite
 * connects to its own layer's main hall. Absolute coordinates determine world
 * coordinates, so independent chunk-generation order cannot cut tunnels.
 *
 * Restricted to Y144..440: original lava sea, normal Nether structures and
 * top bedrock remain untouched by this decorator.
 */
/** Frozen pre-optimization oracle: verifies no Nether cave strength output changes. */
public final class GrandNetherCaveNetworkReference {
    private GrandNetherCaveNetworkReference() {}
    public static final int CELL = 256;
    public static final int MIN_Y = 144;
    public static final int MAX_Y = 440;
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};
    // Cache the 10 main hall sites and 4 local satellite galleries per worker.
    // No neighboring chunks are loaded and nothing is shared between threads.
    private static final class CellCache {
        int gx = Integer.MIN_VALUE, gz = Integer.MIN_VALUE;
        final Hall[][] halls = new Hall[2][5];
        final Hall[][] satellites = new Hall[2][2];
        void initialize(int x, int z) {
            if (gx == x && gz == z) return;
            gx = x; gz = z;
            for (int layer = 0; layer < 2; layer++) {
                halls[layer][0] = hall(x,z,layer);
                for (int i = 0; i < 4; i++)
                    halls[layer][i+1] = hall(x+DX[i],z+DZ[i],layer);
                for (int i = 0; i < 2; i++)
                    satellites[layer][i] = satellite(halls[layer][0],x,z,layer,i);
            }
        }
    }
    private static final ThreadLocal<CellCache> CACHED_HALLS =
            ThreadLocal.withInitial(CellCache::new);

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

    /**
     * Deterministic smaller cave attached to a main hall.
     * Offsets are diagonal and bounded: even after ±5 blocks of density warp,
     * the gallery stays inside its own 256×256 cell, so one cell's gallery
     * cannot disappear when evaluated from its neighbor.
     */
    public static Hall satellite(int gx,int gz,int layer,int index) {
        if(layer<0 || layer>1 || index<0 || index>1)
            throw new IllegalArgumentException("Expected layer and index 0 or 1");
        return satellite(hall(gx,gz,layer),gx,gz,layer,index);
    }

    private static Hall satellite(Hall center,int gx,int gz,int layer,int index) {
        long hash=mix((long)gx*0x632be59bd9b4e019L
                 ^ (long)gz*0x94d049bb133111ebL
                 ^ (layer==0 ? 0x7e2f93a16172c307L : 0x5a91da132775b693L));
        int sx=((hash & 1L)==0 ? -1 : 1)*(50+(int)Math.floorMod(hash >>> 8,7L));
        int sz=((hash & 2L)==0 ? -1 : 1)*(50+(int)Math.floorMod(hash >>> 18,7L));
        if(index==1) {sx=-sx;sz=-sz;}
        int dy=(index==0 ? 1:-1)*(6+(int)Math.floorMod(hash >>> 28,7L));
        long variation=mix(hash ^ ((long)index*0xbf58476d1ce4e5b9L));
        return new Hall(center.x()+sx,center.y()+dy,center.z()+sz,
                14+(int)Math.floorMod(variation,8L),
                11+(int)Math.floorMod(variation >>> 12,6L),
                14+(int)Math.floorMod(variation >>> 23,8L));
    }

    private static double chamber(double x, double y, double z, Hall h) {
        // Cheap per-axis rejection avoids evaluating full quadratic distances
        // for the much more numerous points outside small galleries.
        double dx = (x-h.x())/h.radiusX();
        if(Math.abs(dx)>=1)return 0;
        double dy = (y-h.y())/h.radiusY();
        if(Math.abs(dy)>=1)return 0;
        double dz = (z-h.z())/h.radiusZ();
        if(Math.abs(dz)>=1)return 0;
        double normalized = dx*dx+dy*dy+dz*dz;
        return normalized >= 1 ? 0 : 1 - normalized;
    }

    private static double tube(double x, double y, double z, Hall a, Hall b,
                               double radius) {
        // Canonicalize the direction: each side of a chunk boundary must use
        // exactly the same curved centerline, independent of which hall owns it.
        if (a.x() > b.x() || (a.x() == b.x() && a.z() > b.z())
                || (a.x() == b.x() && a.z() == b.z() && a.y() > b.y())) {
            Hall temp = a; a = b; b = temp;
        }
        // Bends never exceed four blocks, so expand the early rejection box.
        double reach = radius + 4.0;
        if (x < Math.min(a.x(),b.x())-reach
                || x > Math.max(a.x(),b.x())+reach
                || y < Math.min(a.y(),b.y())-reach
                || y > Math.max(a.y(),b.y())+reach
                || z < Math.min(a.z(),b.z())-reach
                || z > Math.max(a.z(),b.z())+reach) return 0;
        double vx=b.x()-a.x(), vy=b.y()-a.y(), vz=b.z()-a.z();
        double t=((x-a.x())*vx+(y-a.y())*vy+(z-a.z())*vz)
                  /(vx*vx+vy*vy+vz*vz);
        if (t <= 0 || t >= 1) return 0;

        // One deterministic, smooth sideways bend per passage. The parabola
        // goes to zero at both halls, retaining the original continuous graph.
        long keyA=mix((long)a.x()*0x9e3779b97f4a7c15L
                ^ (long)a.y()*0xc2b2ae3d27d4eb4fL ^ (long)a.z());
        long keyB=mix((long)b.x()*0x9e3779b97f4a7c15L
                ^ (long)b.y()*0xc2b2ae3d27d4eb4fL ^ (long)b.z());
        long key=mix(keyA ^ Long.rotateLeft(keyB,21));
        double curve=4.0*t*(1.0-t);
        double bend1=((int)Math.floorMod(key,9L)-4)*curve;
        double bend2=((int)Math.floorMod(key >>> 24,7L)-3)*curve;
        double cx=a.x()+t*vx, cy=a.y()+t*vy, cz=a.z()+t*vz;
        if (Math.abs(vx)>=Math.abs(vz) && Math.abs(vx)>=Math.abs(vy)) {
            cy+=bend2; cz+=bend1;
        } else if (Math.abs(vz)>=Math.abs(vy)) {
            cx+=bend1; cy+=bend2;
        } else {
            // Narrower diagonal shafts also receive the seeded noise warp.
            // Halve their lateral bend to keep the original core traversable.
            cx+=bend1*0.5; cz+=bend2*0.5;
        }
        double dx=x-cx, dy=y-cy, dz=z-cz;
        double ratio=(dx*dx+dy*dy+dz*dz)/(radius*radius);
        return ratio>=1 ? 0 : 1-ratio;
    }

    /** Positive strength means excavated air; zero means original density. */
    public static double strength(int x, int y, int z) {
        return strength((double)x, (double)y, (double)z);
    }

    /** Sub-block sampling keeps noise-warped cave walls smooth. */
    public static double strength(double x, double y, double z) {
        if(y<MIN_Y || y>MAX_Y)return 0;
        int gx=(int)Math.floor(x/CELL);
        int gz=(int)Math.floor(z/CELL);
        CellCache cache=CACHED_HALLS.get();
        cache.initialize(gx,gz);
        double answer=0;
        for(int layer=0;layer<2;layer++) {
            Hall center=cache.halls[layer][0];
            // Height cull avoids most local geometry evaluations.
            if(Math.abs(y-center.y()) < center.radiusY()+1)
                answer=Math.max(answer,chamber(x,y,z,center));
            if(answer>=1)return 1;
            for(int edge=0;edge<4;edge++)
                answer=Math.max(answer,tube(x,y,z,center,
                          cache.halls[layer][edge+1],11));
            // All satellite chambers and connectors lie within ±80 X/Z and
            // ±30 Y of their parent. Skip secondary graph work far from them.
            if (Math.abs(x-center.x())<=80 && Math.abs(z-center.z())<=80
                    && Math.abs(y-center.y())<=30) {
                for(int gallery=0;gallery<2;gallery++) {
                    Hall side=cache.satellites[layer][gallery];
                    if(Math.abs(y-side.y())<side.radiusY()+1)
                        answer=Math.max(answer,chamber(x,y,z,side));
                    answer=Math.max(answer,tube(x,y,z,center,side,7));
                }
            }
        }
        // A diagonal chimney links low and high halls. The edge is evaluated
        // only from its own cell, so no additional worldgen/chunk access occurs.
        answer=Math.max(answer,tube(x,y,z,cache.halls[0][0],
                                    cache.halls[1][0],10));
        return answer;
    }
}

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
public final class GrandNetherCaveNetwork {
    private GrandNetherCaveNetwork() {}
    public static final int CELL = 256;
    public static final int MIN_Y = 144;
    public static final int MAX_Y = 440;
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};
    // Cache the 10 main hall sites and 4 local satellite galleries per worker.
    // No neighboring chunks are loaded and nothing is shared between threads.
    private static final class CellCache {
        int gx = Integer.MIN_VALUE, gz = Integer.MIN_VALUE;
        private static final int COLUMN_SLOTS = 512;
        private static final int COLUMN_BUCKETS = COLUMN_SLOTS / 2;
        // A/B columns that hash to the same bucket should not repeatedly
        // evict each other's 19-component masks. Two ways share the ORIGINAL
        // 512-slot memory bound; replacement only affects CPU work.
        final byte[] columnVictim = new byte[COLUMN_BUCKETS];
        final int[] columnX = new int[COLUMN_SLOTS];
        final int[] columnZ = new int[COLUMN_SLOTS];
        final int[] columnEpoch = new int[COLUMN_SLOTS];
        // Up to 19 individual rooms and connectors fit in one bit mask.
        final int[] columnComponents = new int[COLUMN_SLOTS];
        int epoch=1;
        final Hall[][] halls = new Hall[2][5];
        final Hall[][] satellites = new Hall[2][2];
        // Corridor bend seeds depend only on hall endpoints, never on voxels.
        final long[][] corridorKeys = new long[2][4];
        final long[][] satelliteKeys = new long[2][2];
        long interlayerKey;
        // The tunnel endpoints, axis bounds and squared centerline length
        // depend solely on this region's generated hall graph. Keep these
        // alongside the keys rather than recomputing them at every voxel.
        final TubeSegment[][] corridors = new TubeSegment[2][4];
        final TubeSegment[][] branches = new TubeSegment[2][2];
        TubeSegment interlayer;
        // For each integer world Y in the protected Nether cavern band,
        // the bits identify which of the 19 components could intersect a
        // fully +/-2 Y-warped sample. Built once per seeded cell graph;
        // no per-voxel scanning/allocation and no change to cached columns.
        int[] heightComponents;
        boolean heightComponentsReady;
        void ensureHeightComponents() {
            if(heightComponentsReady)return;
            // A one-off worldgen probe pays neither the 297-int allocation
            // nor the vertical scan unless its source column is positive.
            // Recycled snapshots reuse their existing primitive storage.
            if(heightComponents==null)
                heightComponents=new int[MAX_Y-MIN_Y+1];
            for(int y=MIN_Y;y<=MAX_Y;y++)
                heightComponents[y-MIN_Y]=verticalComponentMask(this,y);
            heightComponentsReady=true;
        }
        void initialize(int x, int z) {
            if (gx == x && gz == z) return;
            gx = x; gz = z;
            if (++epoch == 0) {
                java.util.Arrays.fill(columnEpoch,0);
                epoch=1;
            }
            for (int layer = 0; layer < 2; layer++) {
                halls[layer][0] = hall(x,z,layer);
                for (int i = 0; i < 4; i++)
                    halls[layer][i+1] = hall(x+DX[i],z+DZ[i],layer);
                for (int i = 0; i < 2; i++)
                    satellites[layer][i] = satellite(halls[layer][0],x,z,layer,i);
                for (int i = 0; i < 4; i++)
                    corridorKeys[layer][i] = tubeBendKey(halls[layer][0], halls[layer][i+1]);
                for (int i = 0; i < 2; i++)
                    satelliteKeys[layer][i] = tubeBendKey(halls[layer][0], satellites[layer][i]);
                for (int i = 0; i < 4; i++)
                    corridors[layer][i] = new TubeSegment(
                            halls[layer][0], halls[layer][i+1], 11, corridorKeys[layer][i]);
                for (int i = 0; i < 2; i++)
                    branches[layer][i] = new TubeSegment(
                            halls[layer][0], satellites[layer][i], 7, satelliteKeys[layer][i]);
            }
            interlayerKey = tubeBendKey(halls[0][0], halls[1][0]);
            interlayer = new TubeSegment(halls[0][0], halls[1][0], 10, interlayerKey);
            // The next eligible nonempty source-column query will lazily
            // refresh the height table; no stale previous-region values leak.
            heightComponentsReady=false;
        }
    }
    /**
     * Structure height probes and parallel cave requests can revisit several
     * 256-block Nether cells in A -> B -> A order. The previous one-cell
     * worker-local cache rebuilt all halls, bend keys and column masks on
     * every transition, even when the same geography was visited again.
     *
     * Four lazy, two-way snapshots per worker retain identical immutable
     * geometry; evictions only cause the original CellCache.initialize()
     * to run again. No cross-thread or unbounded world memory is shared.
     */
    private static final class RegionCache {
        private static final int SLOTS = 4;
        private static final int BUCKETS = SLOTS / 2;
        private final CellCache[] regions = new CellCache[SLOTS];
        private final byte[] victim = new byte[BUCKETS];
        private CellCache last;

        CellCache get(int gx,int gz) {
            CellCache recent=last;
            if(recent!=null && recent.gx==gx && recent.gz==gz)
                return recent;
            int hash=gx*0x9e3779b9 ^ Integer.rotateLeft(gz*0x85ebca6b,13);
            int bucket=(hash^(hash>>>16))&(BUCKETS-1);
            int first=bucket<<1;
            CellCache a=regions[first];
            if(a!=null && a.gx==gx && a.gz==gz) {
                last=a;
                return a;
            }
            CellCache b=regions[first+1];
            if(b!=null && b.gx==gx && b.gz==gz) {
                last=b;
                return b;
            }
            int slot;
            if(a==null)slot=first;
            else if(b==null)slot=first+1;
            else {
                slot=first+(victim[bucket]&1);
                victim[bucket]^=1;
            }
            CellCache c=regions[slot];
            if(c==null) {
                c=new CellCache();
                regions[slot]=c;
            }
            c.initialize(gx,gz);
            last=c;
            return c;
        }
    }

    private static final ThreadLocal<RegionCache> CACHED_HALLS =
            ThreadLocal.withInitial(RegionCache::new);

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

    /** Precompute deterministic bend seeds for all corridors of one cave cell. */
    static long tubeBendKey(Hall a, Hall b) {
        if (a.x() > b.x() || (a.x() == b.x() && a.z() > b.z())
                || (a.x() == b.x() && a.z() == b.z() && a.y() > b.y())) {
            Hall temp = a; a = b; b = temp;
        }
        long keyA=mix((long)a.x()*0x9e3779b97f4a7c15L
                ^ (long)a.y()*0xc2b2ae3d27d4eb4fL ^ (long)a.z());
        long keyB=mix((long)b.x()*0x9e3779b97f4a7c15L
                ^ (long)b.y()*0xc2b2ae3d27d4eb4fL ^ (long)b.z());
        return mix(keyA ^ Long.rotateLeft(keyB,21));
    }

    /**
     * Immutable per-corridor geometry. All arithmetic is the original tube()
     * arithmetic in the SAME order; crucially, retain division by lenSq
     * instead of replacing it with multiplication by an approximate inverse.
     * The segment is rebuilt only when CellCache.initialize changes regions.
     */
    private static final class TubeSegment {
        final Hall a, b;
        final double radius, radiusSquared, vx, vy, vz, lenSq;
        final double minX, maxX, minY, maxY, minZ, maxZ;
        // Original fixed-seed floorMod and axis selection are identical
        // for every voxel traversing this segment. Calculate once per
        // cave-cell graph, without changing the floating-point curve order.
        final int bendSeed1, bendSeed2, bendAxis;
        final long key;
        TubeSegment(Hall first, Hall second, double radius, long key) {
            Hall a=first,b=second;
            if (a.x() > b.x() || (a.x() == b.x() && a.z() > b.z())
                    || (a.x() == b.x() && a.z() == b.z() && a.y() > b.y())) {
                Hall temp=a; a=b; b=temp;
            }
            this.a=a;this.b=b;this.radius=radius;this.key=key;
            double reach=radius+4.0;
            this.minX=Math.min(a.x(),b.x())-reach;
            this.maxX=Math.max(a.x(),b.x())+reach;
            this.minY=Math.min(a.y(),b.y())-reach;
            this.maxY=Math.max(a.y(),b.y())+reach;
            this.minZ=Math.min(a.z(),b.z())-reach;
            this.maxZ=Math.max(a.z(),b.z())+reach;
            this.vx=b.x()-a.x();
            this.vy=b.y()-a.y();
            this.vz=b.z()-a.z();
            this.lenSq=vx*vx+vy*vy+vz*vz;
            this.radiusSquared=radius*radius;
            this.bendSeed1=(int)Math.floorMod(key,9L)-4;
            this.bendSeed2=(int)Math.floorMod(key >>> 24,7L)-3;
            this.bendAxis=Math.abs(vx)>=Math.abs(vz) && Math.abs(vx)>=Math.abs(vy)
                    ? 0 : Math.abs(vz)>=Math.abs(vy) ? 1 : 2;
        }
    }

    private static double tube(double x, double y, double z, TubeSegment segment) {
        // Same canonical endpoints and inclusive boundary box as the
        // original method. Bounds are calculated once, not per voxel.
        if (x < segment.minX || x > segment.maxX
                || y < segment.minY || y > segment.maxY
                || z < segment.minZ || z > segment.maxZ) return 0;
        Hall a=segment.a;
        double vx=segment.vx,vy=segment.vy,vz=segment.vz;
        double t=((x-a.x())*vx+(y-a.y())*vy+(z-a.z())*vz)
                /segment.lenSq;
        if (t <= 0 || t >= 1) return 0;

        double curve=4.0*t*(1.0-t);
        double bend1=segment.bendSeed1*curve;
        double bend2=segment.bendSeed2*curve;
        double cx=a.x()+t*vx, cy=a.y()+t*vy, cz=a.z()+t*vz;
        if (segment.bendAxis==0) {
            cy+=bend2; cz+=bend1;
        } else if (segment.bendAxis==1) {
            cx+=bend1; cy+=bend2;
        } else {
            cx+=bend1*0.5; cz+=bend2*0.5;
        }
        double dx=x-cx, dy=y-cy, dz=z-cz;
        double ratio=(dx*dx+dy*dy+dz*dz)/segment.radiusSquared;
        return ratio>=1 ? 0 : 1-ratio;
    }

    /**
     * Conservative X/Z broad-phase test, independent of world seed and Y.
     * When false, every chamber and corridor is geometrically unreachable
     * for ALL +/-5 X and +/-4 Z seeded noise displacements.
     *
     * At cell edges return true without checking: a warped sample could
     * select a neighboring 256x256 cave graph. Never use a shared cache.
     */
    static boolean mayContainVoidNearColumn(int x,int z) {
        return componentMaskForColumn(x,z)!=0;
    }

    static int componentMaskForColumn(int x,int z) {
        int dx=Math.floorMod(x,CELL),dz=Math.floorMod(z,CELL);
        // A +/-5 or +/-4 seeded warp can choose a different cave cell.
        if(dx<=5 || dx>=CELL-6 || dz<=4 || dz>=CELL-5)return -1;
        CellCache c=CACHED_HALLS.get().get(
                Math.floorDiv(x,CELL),Math.floorDiv(z,CELL));
        int bucket=(x*0x9e3779b9 ^ z*0x85ebca6b)
                &(CellCache.COLUMN_BUCKETS-1);
        int first=bucket<<1, second=first+1;
        if(c.columnEpoch[first]==c.epoch && c.columnX[first]==x
                && c.columnZ[first]==z)return c.columnComponents[first];
        if(c.columnEpoch[second]==c.epoch && c.columnX[second]==x
                && c.columnZ[second]==z)return c.columnComponents[second];
        int mask=componentMask(c,x-5.0,x+5.0,z-4.0,z+4.0);
        // Prefer a free way. A third conflicting column replaces one way
        // deterministically, but never returns a mask for a different X/Z.
        int target;
        if(c.columnEpoch[first]!=c.epoch)target=first;
        else if(c.columnEpoch[second]!=c.epoch)target=second;
        else {
            target=first+(c.columnVictim[bucket]&1);
            c.columnVictim[bucket]^=1;
        }
        c.columnEpoch[target]=c.epoch;
        c.columnX[target]=x;
        c.columnZ[target]=z;
        c.columnComponents[target]=mask;
        return mask;
    }

    /**
     * Build the conservative 19-bit height filter ONCE per worker-local
     * 256-block cave graph. This is the Test.27 comparison sequence with
     * the source-column bit tests removed: a bit is set iff the original
     * inclusive [Y-2,Y+2] check could touch the corresponding component.
     * It is a superset of all actual +/-2 seeded-warped cave strengths.
     */
    private static int verticalComponentMask(CellCache c,int y) {
        double lowY=(double)y-2.0, highY=(double)y+2.0;
        int bits=0;
        for(int layer=0;layer<2;layer++) {
            int shift=layer*9;
            Hall center=c.halls[layer][0];
            if(highY>=center.y()-center.radiusY() &&
                    lowY<=center.y()+center.radiusY())
                bits|=1<<shift;
            for(int direction=0;direction<4;direction++) {
                TubeSegment tube=c.corridors[layer][direction];
                if(highY>=tube.minY && lowY<=tube.maxY)
                    bits|=1<<(shift+1+direction);
            }
            for(int gallery=0;gallery<2;gallery++) {
                Hall side=c.satellites[layer][gallery];
                if(highY>=side.y()-side.radiusY() &&
                        lowY<=side.y()+side.radiusY())
                    bits|=1<<(shift+5+gallery*2);
                TubeSegment tube=c.branches[layer][gallery];
                if(highY>=tube.minY && lowY<=tube.maxY)
                    bits|=1<<(shift+6+gallery*2);
            }
        }
        TubeSegment tube=c.interlayer;
        if(highY>=tube.minY && lowY<=tube.maxY)
            bits|=1<<18;
        return bits;
    }

    /**
     * The Test.27 per-voxel 19-component Y loop is now one primitive
     * intersection against a frozen region-local vertical component table.
     * A negative mask means the X/Z warp may reach the NEIGHBOR region,
     * where using this table would be unsafe: retain the full path.
     *
     * Other callers outside the normal Nether Y range also retain the full
     * path instead of indexing an unsupported table entry. This helper
     * never mutates a chunk, seeded noise or the original strength formula.
     */
    static boolean mayContainVoidAtHeight(int sourceX,int y,int sourceZ,int mask) {
        if(mask==-1)return true;
        if(mask==0)return false;
        if(y<MIN_Y || y>MAX_Y)return true;
        CellCache c=CACHED_HALLS.get().get(
                Math.floorDiv(sourceX,CELL),Math.floorDiv(sourceZ,CELL));
        c.ensureHeightComponents();
        return (mask & c.heightComponents[y-MIN_Y])!=0;
    }

    /**
     * Re-evaluates the original 3D network in its exact component order,
     * but only for the 19 chambers and tubes whose broad-phase bounds
     * can intersect the seeded-warped X/Z source column. Original strength()
     * remains the canonical fallback for warp overflow and cell edges.
     */
    static double strengthFromColumn(int originalX,int originalZ,
                                     double x,double y,double z) {
        // Compatibility/reference entrypoint: resolve the mask as before.
        // The production density wrapper passes its already known mask to
        // the exact same evaluator, avoiding a second column-cache lookup.
        if(y<MIN_Y||y>MAX_Y)return 0;
        if(x<originalX-5.0||x>originalX+5.0
                ||z<originalZ-4.0||z>originalZ+4.0
                ||Math.floorDiv(originalX,CELL)!=(int)Math.floor(x/CELL)
                ||Math.floorDiv(originalZ,CELL)!=(int)Math.floor(z/CELL))
            return strength(x,y,z);
        return strengthFromColumnWithMask(originalX,originalZ,x,y,z,
                componentMaskForColumn(originalX,originalZ));
    }

    /**
     * Use the 19-bit candidate mask already obtained in the same
     * GrandNetherCaverns.compute call. This mask is only valid for its exact
     * source X/Z and worker's region graph. Validate the full requested warp
     * and Y BEFORE trusting even a zero mask, so unsupported warps always
     * run the original full-strength fallback.
     *
     * No mask caching across threads or distinct source-column queries.
     */
    static double strengthFromColumnWithMask(int originalX,int originalZ,
            double x,double y,double z,int mask) {
        if(y<MIN_Y||y>MAX_Y)return 0;
        if(x<originalX-5.0||x>originalX+5.0
                ||z<originalZ-4.0||z>originalZ+4.0
                ||Math.floorDiv(originalX,CELL)!=(int)Math.floor(x/CELL)
                ||Math.floorDiv(originalZ,CELL)!=(int)Math.floor(z/CELL))
            return strength(x,y,z);
        if(mask==-1)return strength(x,y,z);
        if(mask==0)return 0;
        // Always select the source cell explicitly; another height probe
        // may have accessed a different cached region in the same worker.
        CellCache cache=CACHED_HALLS.get().get(
                Math.floorDiv(originalX,CELL),Math.floorDiv(originalZ,CELL));
        double answer=0;
        for(int layer=0;layer<2;layer++) {
            int shift=layer*9;
            Hall center=cache.halls[layer][0];
            if((mask&(1<<shift))!=0
                    && Math.abs(y-center.y())<center.radiusY()+1)
                answer=Math.max(answer,chamber(x,y,z,center));
            if(answer>=1)return 1;
            for(int edge=0;edge<4;edge++) {
                if((mask&(1<<(shift+1+edge)))!=0)
                    answer=Math.max(answer,tube(x,y,z,cache.corridors[layer][edge]));
            }
            if(Math.abs(x-center.x())<=80&&Math.abs(z-center.z())<=80
                    && Math.abs(y-center.y())<=30) {
                for(int gallery=0;gallery<2;gallery++) {
                    Hall side=cache.satellites[layer][gallery];
                    if((mask&(1<<(shift+5+gallery*2)))!=0
                            && Math.abs(y-side.y())<side.radiusY()+1)
                        answer=Math.max(answer,chamber(x,y,z,side));
                    if((mask&(1<<(shift+6+gallery*2)))!=0)
                        answer=Math.max(answer,tube(x,y,z,cache.branches[layer][gallery]));
                }
            }
        }
        if((mask&(1<<18))!=0)
            answer=Math.max(answer,tube(x,y,z,cache.interlayer));
        return answer;
    }

    private static boolean roomCouldTouch(Hall h,
            double loX,double hiX,double loZ,double hiZ) {
        return loX<=h.x()+h.radiusX() && hiX>=h.x()-h.radiusX()
            && loZ<=h.z()+h.radiusZ() && hiZ>=h.z()-h.radiusZ();
    }

    private static boolean tubeCouldTouch(Hall a,Hall b,double radius,
            double loX,double hiX,double loZ,double hiZ) {
        // Exactly the reach of tube(), before its projected 3D evaluation.
        double reach=radius+4.0;
        return loX<=Math.max(a.x(),b.x())+reach
            && hiX>=Math.min(a.x(),b.x())-reach
            && loZ<=Math.max(a.z(),b.z())+reach
            && hiZ>=Math.min(a.z(),b.z())-reach;
    }

    /**
     * The Test.20 corridor descriptor already contains the conservative
     * horizontal reach used by the original Hall/Hall/radius overload.
     * Avoid recomputing min/max and radius for all 13 corridor candidates
     * each time a new source X/Z column needs its 19-component mask.
     * Keep the original overload above as the frozen arithmetic reference.
     */
    private static boolean tubeCouldTouch(TubeSegment segment,
            double loX,double hiX,double loZ,double hiZ) {
        return loX<=segment.maxX && hiX>=segment.minX
            && loZ<=segment.maxZ && hiZ>=segment.minZ;
    }

    private static int componentMask(CellCache c,
            double loX,double hiX,double loZ,double hiZ) {
        int bits=0;
        for(int layer=0;layer<2;layer++) {
            int shift=layer*9;
            Hall center=c.halls[layer][0];
            if(roomCouldTouch(center,loX,hiX,loZ,hiZ))bits|=1<<shift;
            for(int dir=0;dir<4;dir++)
                if(tubeCouldTouch(c.corridors[layer][dir],
                        loX,hiX,loZ,hiZ))bits|=1<<(shift+1+dir);
            for(int gallery=0;gallery<2;gallery++) {
                Hall satellite=c.satellites[layer][gallery];
                if(roomCouldTouch(satellite,loX,hiX,loZ,hiZ))
                    bits|=1<<(shift+5+gallery*2);
                if(tubeCouldTouch(c.branches[layer][gallery],
                        loX,hiX,loZ,hiZ))bits|=1<<(shift+6+gallery*2);
            }
        }
        if(tubeCouldTouch(c.interlayer,
                loX,hiX,loZ,hiZ))bits|=1<<18;
        return bits;
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
        CellCache cache=CACHED_HALLS.get().get(gx,gz);
        double answer=0;
        for(int layer=0;layer<2;layer++) {
            Hall center=cache.halls[layer][0];
            // Height cull avoids most local geometry evaluations.
            if(Math.abs(y-center.y()) < center.radiusY()+1)
                answer=Math.max(answer,chamber(x,y,z,center));
            if(answer>=1)return 1;
            for(int edge=0;edge<4;edge++)
                answer=Math.max(answer,tube(x,y,z,cache.corridors[layer][edge]));
            // All satellite chambers and connectors lie within ±80 X/Z and
            // ±30 Y of their parent. Skip secondary graph work far from them.
            if (Math.abs(x-center.x())<=80 && Math.abs(z-center.z())<=80
                    && Math.abs(y-center.y())<=30) {
                for(int gallery=0;gallery<2;gallery++) {
                    Hall side=cache.satellites[layer][gallery];
                    if(Math.abs(y-side.y())<side.radiusY()+1)
                        answer=Math.max(answer,chamber(x,y,z,side));
                    answer=Math.max(answer,tube(x,y,z,cache.branches[layer][gallery]));
                }
            }
        }
        // A diagonal chimney links low and high halls. The edge is evaluated
        // only from its own cell, so no additional worldgen/chunk access occurs.
        answer=Math.max(answer,tube(x,y,z,cache.interlayer));
        return answer;
    }
}

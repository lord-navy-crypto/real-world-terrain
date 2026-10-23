package org.realscale.neoforge;

/**
 * Pure Java USGS DEM interpolation and transition mathematics.
 * Independently JUnit-testable without Minecraft/NeoForge on test classpath.
 * Production DensityFunction delegates to exactly these same computations.
 */
final class RealDemSeamlessMath {
    private static final RealDemDataSource DEM = RealDemDataSource.loadMeasured();
    private static final double CELL = DEM.cellMeters();
    private static final int GRID = DEM.width();
    private static final double[][] HEIGHTS = loadHeights();

    /*
     * Minecraft re-evaluates final_density at many Y cells for each X/Z.
     * Bicubic DEM interpolation depends ONLY on X/Z. Avoid repeating four
     * spline evaluations for each of ~2096 vertical sample heights.
     *
     * Fixed-size two-way set associative per-thread cache: no unbounded world state,
     * no synchronization on multi-threaded worldgen, no seed dependence.
     * Hash collisions can only lower hit rate, not alter a computed value.
     */
    // Jigsaw height probes can alternate coordinates with identical hashes.
    // Two-way buckets retain both sites WITHOUT increasing total capacity.
    // Misses evaluate the EXACT original bicubic function and retain bitwise
    // identity; eviction changes cache hit rate only.
    private static final int CACHE_SIZE = 4096;
    private static final int HEIGHT_BUCKETS = CACHE_SIZE / 2;
    private static final class HeightCache {
        final int[] xs = new int[CACHE_SIZE];
        final int[] zs = new int[CACHE_SIZE];
        final double[] values = new double[CACHE_SIZE];
        final boolean[] valid = new boolean[CACHE_SIZE];
        final byte[] victim = new byte[HEIGHT_BUCKETS];
        boolean lastValid;
        int lastX, lastZ;
        double lastValue;
    }
    private static final ThreadLocal<HeightCache> HEIGHT_CACHE =
        ThreadLocal.withInitial(HeightCache::new);

    // Package-visible so parity tests can select real colliding columns.
    static int heightBucket(int x,int z) {
        int hash=x*0x9e3779b9 ^ Integer.rotateLeft(z*0x85ebca6b,13);
        return (hash^(hash>>>16))&(HEIGHT_BUCKETS-1);
    }

    static double cachedExtendedHeight(int x, int z) {
        HeightCache cache = HEIGHT_CACHE.get();
        if (cache.lastValid && cache.lastX==x && cache.lastZ==z)
            return cache.lastValue;
        final int bucket=heightBucket(x,z);
        final int first=bucket<<1,second=first+1;
        final double height;
        if (cache.valid[first] && cache.xs[first]==x && cache.zs[first]==z) {
            height=cache.values[first];
        } else if (cache.valid[second] && cache.xs[second]==x
                && cache.zs[second]==z) {
            height=cache.values[second];
        } else {
            height=extendedHeight(x,z);
            final int slot;
            if (!cache.valid[first]) slot=first;
            else if (!cache.valid[second]) slot=second;
            else {
                slot=first+(cache.victim[bucket]&1);
                cache.victim[bucket]^=1;
            }
            cache.valid[slot]=true;
            cache.xs[slot]=x;
            cache.zs[slot]=z;
            cache.values[slot]=height;
        }
        cache.lastValid=true;
        cache.lastX=x;
        cache.lastZ=z;
        cache.lastValue=height;
        return height;
    }

    /*
     * The DEM fade weight is constant for every vertical density evaluation
     * at the same X/Z and blend width. Cache only the transition ring:
     * - tile interior is exactly 1.0, without allocating a cache;
     * - at or beyond the fade distance it is exactly 0.0;
     * - the ring retains the original weightOutside() arithmetic on misses.
     *
     * Per-thread, fixed-size two-way buckets are lock-free and bounded. Hash
     * collisions can affect performance but never alter terrain results.
     */
    private static final int WEIGHT_CACHE_SIZE = 1024;
    private static final int WEIGHT_BUCKETS = WEIGHT_CACHE_SIZE / 2;
    private static final class WeightCache {
        final long[] coordinates = new long[WEIGHT_CACHE_SIZE];
        final int[] fadeWidths = new int[WEIGHT_CACHE_SIZE];
        final double[] weights = new double[WEIGHT_CACHE_SIZE];
        final boolean[] valid = new boolean[WEIGHT_CACHE_SIZE];
        final byte[] victim = new byte[WEIGHT_BUCKETS];
        boolean lastValid;
        long lastCoordinate;
        int lastFadeWidth;
        double lastWeight;
    }
    private static final ThreadLocal<WeightCache> WEIGHT_CACHE =
            ThreadLocal.withInitial(WeightCache::new);

    // Cache set membership has no effect on the original fade arithmetic.
    static int weightBucket(int x,int z,int fadeMeters) {
        int hash = x * 0x9e3779b9
                ^ Integer.rotateLeft(z * 0x85ebca6b, 13)
                ^ fadeMeters * 0xc2b2ae35;
        return (hash ^ (hash >>> 16)) & (WEIGHT_BUCKETS - 1);
    }

    static double cachedWeightOutside(int x, int z, int fadeMeters) {
        // The codec requires positive widths; fall back to the original
        // formula for other callers rather than changing its edge semantics.
        if (fadeMeters <= 0) return weightOutside(x, z, fadeMeters);

        final int minX = DEM.originX(), maxX = DEM.maxX();
        final int minZ = DEM.originZ(), maxZ = DEM.maxZ();
        if (x >= minX && x <= maxX && z >= minZ && z <= maxZ)
            return 1.0;
        // Use 64-bit bounds: Java int addition can overflow in very distant
        // exploration, incorrectly turning an out-of-DEM world column into
        // a transition-ring candidate (or vice versa). This is an exact,
        // allocation-free far-field reject with unchanged in-range results.
        if ((long)x <= (long)minX - fadeMeters
                || (long)x >= (long)maxX + fadeMeters
                || (long)z <= (long)minZ - fadeMeters
                || (long)z >= (long)maxZ + fadeMeters)
            return 0.0;

        final long coordinate = ((long) x << 32) | (z & 0xffffffffL);
        final WeightCache cache = WEIGHT_CACHE.get();
        if (cache.lastValid && cache.lastCoordinate == coordinate
                && cache.lastFadeWidth == fadeMeters)
            return cache.lastWeight;

        final int bucket=weightBucket(x,z,fadeMeters);
        final int first=bucket<<1,second=first+1;
        final double result;
        if (cache.valid[first] && cache.coordinates[first] == coordinate
                && cache.fadeWidths[first] == fadeMeters) {
            result = cache.weights[first];
        } else if (cache.valid[second] && cache.coordinates[second] == coordinate
                && cache.fadeWidths[second] == fadeMeters) {
            result = cache.weights[second];
        } else {
            result = weightOutside(x, z, fadeMeters);
            final int slot;
            if (!cache.valid[first]) slot=first;
            else if (!cache.valid[second]) slot=second;
            else {
                slot=first+(cache.victim[bucket]&1);
                cache.victim[bucket]^=1;
            }
            cache.coordinates[slot] = coordinate;
            cache.fadeWidths[slot] = fadeMeters;
            cache.weights[slot] = result;
            cache.valid[slot] = true;
        }
        cache.lastCoordinate = coordinate;
        cache.lastFadeWidth = fadeMeters;
        cache.lastWeight = result;
        cache.lastValid = true;
        return result;
    }

    private RealDemSeamlessMath() {}

    private static double[][] loadHeights() {
        double[][] h = new double[GRID][GRID];
        for (int z=0; z<GRID; z++)
            for (int x=0; x<GRID; x++)
                h[z][x] = DEM.worldSolidYAt(
                    DEM.originX()+CELL*x, DEM.originZ()+CELL*z);
        return h;
    }

    private static double limit(double value,double low,double high) {
        return Math.max(low,Math.min(high,value));
    }

    private static double monotoneSlope(double left,double right) {
        if (left*right<=0.0) return 0.0;
        return 2.0*left*right/(left+right);
    }

    private static double smoothSegment(double prev,double a,double b,
                                         double next,double t) {
        double d=b-a;
        if (d==0.0) return a;
        double before=a-prev;
        double after=next-b;
        double m0=monotoneSlope(before,d);
        double m1=monotoneSlope(d,after);
        double t2=t*t,t3=t2*t;
        double result=(2*t3-3*t2+1)*a+(t3-2*t2+t)*m0
                     +(-2*t3+3*t2)*b+(t3-t2)*m1;
        return limit(result,Math.min(a,b),Math.max(a,b));
    }

    private static double corner(int row,int col) {
        return HEIGHTS[(int)limit(row,0,GRID-1)][(int)limit(col,0,GRID-1)];
    }

    private static double sampleRow(int row,int ix,double a) {
        double p0=corner(row,ix),p1=corner(row,ix+1);
        double pm=ix>0?corner(row,ix-1):2*p0-p1;
        double pp=ix<GRID-2?corner(row,ix+2):2*p1-p0;
        return smoothSegment(pm,p0,p1,pp,a);
    }

    static double measuredHeight(double x,double z) {
        double cx=limit(x,DEM.originX(),DEM.maxX());
        double cz=limit(z,DEM.originZ(),DEM.maxZ());
        double gx=(cx-DEM.originX())/CELL, gz=(cz-DEM.originZ())/CELL;
        int ix=Math.min(GRID-2,(int)Math.floor(gx));
        int iz=Math.min(GRID-2,(int)Math.floor(gz));
        double a=gx-ix,b=gz-iz;
        double r0=sampleRow(iz-1,ix,a);
        double r1=sampleRow(iz,ix,a);
        double r2=sampleRow(iz+1,ix,a);
        double r3=sampleRow(iz+2,ix,a);
        double lower=iz>0?r0:2*r1-r2;
        double upper=iz<GRID-2?r3:2*r2-r1;
        return smoothSegment(lower,r1,r2,upper,b);
    }

    static double weightOutside(double x,double z,int fadeMeters) {
        double dx=x-limit(x,DEM.originX(),DEM.maxX());
        double dz=z-limit(z,DEM.originZ(),DEM.maxZ());
        double t=limit(Math.hypot(dx,dz)/fadeMeters,0.0,1.0);
        return 1.0-t*t*(3.0-2.0*t);
    }

    static double taperedOutsideDisplacement(double distance) {
        double length=Math.min(Math.abs(distance),128.0);
        double softened=length-length*length/256.0;
        return Math.copySign(softened,distance);
    }

    static double extendedHeight(double x,double z) {
        double cx=limit(x,DEM.originX(),DEM.maxX());
        double cz=limit(z,DEM.originZ(),DEM.maxZ());
        double center=measuredHeight(cx,cz);
        double originalCenter=center;
        double dx=x-cx,dz=z-cz;
        if(dx!=0.0) {
            double neighbor=measuredHeight(cx+(dx<0?CELL:-CELL),cz);
            double slope=(neighbor-center)/(dx<0?CELL:-CELL);
            center+=limit(slope,-1.25,1.25)*taperedOutsideDisplacement(dx);
        }
        if(dz!=0.0) {
            double neighbor=measuredHeight(cx,cz+(dz<0?CELL:-CELL));
            // Keep Z gradient independent of the X-axis extrapolation.
            double slope=(neighbor-originalCenter)/(dz<0?CELL:-CELL);
            center+=limit(slope,-1.25,1.25)*taperedOutsideDisplacement(dz);
        }
        return limit(center,-64.0,2031.0);
    }

}

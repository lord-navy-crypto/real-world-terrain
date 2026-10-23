package org.realscale.neoforge;

/** Check the restored measured DEM source and continuous 2048-block transition. */
public final class RealDemSeamlessMathSmoke {
    private RealDemSeamlessMathSmoke() {}
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    /**
     * Stronger than numerical parity alone: prove that two actual colliding
     * coordinate keys occupy both ways of the same cache set and stay
     * available during alternating reads, including in the fade ring.
     * Reflection is confined to this standalone test, never production.
     */
    private static int[] findHeightCollision() {
        java.util.Map<Integer,int[]> first=new java.util.HashMap<>();
        for(int z=-2200;z<=2200;z+=13)
            for(int x=-2200;x<=2200;x+=11) {
                int bucket=RealDemSeamlessMath.heightBucket(x,z);
                int[] a=first.putIfAbsent(bucket,new int[]{x,z});
                if(a!=null && (a[0]!=x||a[1]!=z))
                    return new int[]{a[0],a[1],x,z};
            }
        throw new AssertionError("Unable to find a height cache collision");
    }

    private static int[] findWeightCollision(RealDemTile tile,int fade) {
        java.util.Map<Integer,int[]> first=new java.util.HashMap<>();
        for(int z=tile.originZ()+16;z<tile.maxZ()-16;z+=7)
            for(int x=tile.maxX()+8;x<tile.maxX()+fade-8;x+=7) {
                int bucket=RealDemSeamlessMath.weightBucket(x,z,fade);
                int[] a=first.putIfAbsent(bucket,new int[]{x,z});
                if(a!=null && (a[0]!=x||a[1]!=z))
                    return new int[]{a[0],a[1],x,z};
            }
        throw new AssertionError("Unable to find a fade cache collision");
    }

    private static Object privateField(Object target,String name) throws Exception {
        java.lang.reflect.Field field=target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void checkCollisionRetained(RealDemTile tile) throws Exception {
        int[] hp=findHeightCollision();
        int hBucket=RealDemSeamlessMath.heightBucket(hp[0],hp[1]);
        require(hBucket==RealDemSeamlessMath.heightBucket(hp[2],hp[3]),
                "Height collision setup invalid");
        for(int n=0;n<4;n++){
            int x=hp[2*(n&1)], z=hp[2*(n&1)+1];
            long expected=Double.doubleToRawLongBits(RealDemSeamlessMath.extendedHeight(x,z));
            long actual=Double.doubleToRawLongBits(RealDemSeamlessMath.cachedExtendedHeight(x,z));
            require(expected==actual,"Height collision changed USGS bits");
        }
        java.lang.reflect.Field hField=RealDemSeamlessMath.class.getDeclaredField("HEIGHT_CACHE");
        hField.setAccessible(true);
        Object hc=((ThreadLocal<?>)hField.get(null)).get();
        int[] xs=(int[])privateField(hc,"xs"), zs=(int[])privateField(hc,"zs");
        boolean[] valid=(boolean[])privateField(hc,"valid");
        int hFirst=hBucket*2;
        for(int n=0;n<2;n++){
            int x=hp[n*2],z=hp[n*2+1];
            require((valid[hFirst]&&xs[hFirst]==x&&zs[hFirst]==z)
                    ||(valid[hFirst+1]&&xs[hFirst+1]==x&&zs[hFirst+1]==z),
                    "Colliding height column was not retained in two-way set");
        }

        int fade=2048;
        int[] wp=findWeightCollision(tile,fade);
        int wBucket=RealDemSeamlessMath.weightBucket(wp[0],wp[1],fade);
        require(wBucket==RealDemSeamlessMath.weightBucket(wp[2],wp[3],fade),
                "Fade collision setup invalid");
        for(int n=0;n<4;n++){
            int x=wp[2*(n&1)],z=wp[2*(n&1)+1];
            long expected=Double.doubleToRawLongBits(RealDemSeamlessMath.weightOutside(x,z,fade));
            long actual=Double.doubleToRawLongBits(RealDemSeamlessMath.cachedWeightOutside(x,z,fade));
            require(expected==actual,"Fade collision changed USGS bits");
        }
        java.lang.reflect.Field wField=RealDemSeamlessMath.class.getDeclaredField("WEIGHT_CACHE");
        wField.setAccessible(true);
        Object wc=((ThreadLocal<?>)wField.get(null)).get();
        long[] coordinates=(long[])privateField(wc,"coordinates");
        int[] fadeWidths=(int[])privateField(wc,"fadeWidths");
        boolean[] flags=(boolean[])privateField(wc,"valid");
        int wFirst=wBucket*2;
        for(int n=0;n<2;n++){
            int x=wp[n*2],z=wp[n*2+1];
            long coordinate=((long)x<<32)|(z&0xffffffffL);
            require((flags[wFirst]&&coordinates[wFirst]==coordinate
                        &&fadeWidths[wFirst]==fade)
                    ||(flags[wFirst+1]&&coordinates[wFirst+1]==coordinate
                        &&fadeWidths[wFirst+1]==fade),
                    "Colliding fade column was not retained in two-way set");
        }
    }


    /**
     * Independent frozen original corner interpolation compared at raw
     * IEEE-754 bit level to the optimized sampler. The production sampler
     * cannot change DEM heights at raster rows, endpoints, or fractions.
     */
    private static void checkRowSamplerParity() throws Exception {
        Class<?> type=RealDemSeamlessMath.class;
        java.lang.reflect.Field gridField=type.getDeclaredField("GRID");
        gridField.setAccessible(true);
        int grid=gridField.getInt(null);
        java.lang.reflect.Method corner=type.getDeclaredMethod("corner",int.class,int.class);
        java.lang.reflect.Method smooth=type.getDeclaredMethod("smoothSegment",
                double.class,double.class,double.class,double.class,double.class);
        java.lang.reflect.Method sampler=type.getDeclaredMethod("sampleRow",
                int.class,int.class,double.class);
        corner.setAccessible(true);
        smooth.setAccessible(true);
        sampler.setAccessible(true);
        int checks=0;
        for(int row:new int[]{-1,0,1,grid-3,grid-2,grid-1,grid}) {
            for(int ix:new int[]{0,1,grid/2,grid-3,grid-2}) {
                double p0=(double)corner.invoke(null,row,ix);
                double p1=(double)corner.invoke(null,row,ix+1);
                double pm=ix>0?(double)corner.invoke(null,row,ix-1):2*p0-p1;
                double pp=ix<grid-2?(double)corner.invoke(null,row,ix+2):2*p1-p0;
                for(double a:new double[]{0.0,Math.nextUp(0.0),0.137,0.5,
                                          Math.nextDown(1.0),1.0}) {
                    double expected=(double)smooth.invoke(null,pm,p0,p1,pp,a);
                    double actual=(double)sampler.invoke(null,row,ix,a);
                    require(Double.doubleToRawLongBits(expected)
                            ==Double.doubleToRawLongBits(actual),
                            "USGS sampleRow changed interpolation bits row="+row
                            +" x="+ix+" fraction="+a);
                    checks++;
                }
            }
        }
        require(checks==210,"Missing sampler boundary tests");
    }

    public static void main(String[] args) throws Exception {
        RealDemTile source = RealDemTile.load();
        int n = 0;
        for (int x = source.originX(); x <= source.maxX(); x += 32) {
            for (int z = source.originZ(); z <= source.maxZ(); z += 32) {
                double measured = source.worldSolidYAt(x, z);
                double actual = RealDemSeamlessMath.measuredHeight(x, z);
                require(Math.abs(measured - actual) < 1.e-6,
                        "DEM measurement changed at " + x + ", " + z);
                require(RealDemSeamlessMath.weightOutside(x,z,2048)==1.0,
                        "Measured tile lost full strength");
                n++;
            }
        }
        require(n == 256, "Missing measured DEM grid samples: " + n);
        require(RealDemSeamlessMath.weightOutside(source.maxX()+2048,0,2048)==0.0,
                "East transition fails to reach procedural terrain");
        require(RealDemSeamlessMath.weightOutside(source.originX()-2048,0,2048)==0.0,
                "West transition fails to reach procedural terrain");
        require(RealDemSeamlessMath.weightOutside(0,source.maxZ()+2048,2048)==0.0,
                "South transition fails to reach procedural terrain");
        require(RealDemSeamlessMath.weightOutside(0,source.originZ()-2048,2048)==0.0,
                "North transition fails to reach procedural terrain");
        for (int x = -2288; x <= 2288; x += 61) {
            for (int z = -2288; z <= 2288; z += 79) {
                double v = RealDemSeamlessMath.extendedHeight(x,z);
                require(Double.isFinite(v) && v >= -64 && v <= 2031,
                        "USGS exterior extrapolation exceeded height limits");
                require(Double.doubleToLongBits(v) == Double.doubleToLongBits(
                        RealDemSeamlessMath.cachedExtendedHeight(x,z)),
                        "USGS per-thread height cache changed computed result");
            }
        }
        // The optimized transition-weight lookup must reproduce every bit of
        // the original formula across the tile, fade ring and far field.
        for (int fade : new int[]{256, 768, 2048, 4096}) {
            for (int x = -4608; x <= 4608; x += 109) {
                for (int z = -4608; z <= 4608; z += 127) {
                    long original = Double.doubleToRawLongBits(
                            RealDemSeamlessMath.weightOutside(x, z, fade));
                    long cached = Double.doubleToRawLongBits(
                            RealDemSeamlessMath.cachedWeightOutside(x, z, fade));
                    require(original == cached,
                            "Fade weight changed at " + x + "," + z + " width=" + fade);
                }
            }
        }
        // Far-away exploration never pulls the small USGS pilot tile into
        // distant procedural regions. These signed and near-world-border
        // samples also protect overflow-safe arithmetic in the fade cache.
        for (int fade : new int[]{256,768,2048,4096}) {
            for (int x : new int[]{-30_000_000,-1_048_576,-65_536,-8192,
                                    8192,65_536,1_048_576,30_000_000,
                                    Integer.MIN_VALUE,Integer.MAX_VALUE}) {
                for (int z : new int[]{-30_000_000,-8192,0,8192,
                                        30_000_000,Integer.MAX_VALUE}) {
                    long original=Double.doubleToRawLongBits(
                            RealDemSeamlessMath.weightOutside(x,z,fade));
                    long optimized=Double.doubleToRawLongBits(
                            RealDemSeamlessMath.cachedWeightOutside(x,z,fade));
                    require(original==optimized,
                            "Far-distance USGS fade regression at "+x+","+z);
                    require(original==Double.doubleToRawLongBits(0.0),
                            "Distant procedural chunk mistakenly influenced by DEM");
                }
            }
        }
        // Revisit 256 far-separated DEM/fade columns while sweeping more
        // than 4096 other columns through the bounded cache. Neither
        // collisions nor evictions may change measured height raw bits.
        final int nProbe=256;
        int[] px=new int[nProbe],pz=new int[nProbe];
        long[] originalBits=new long[nProbe];
        for(int k=0;k<nProbe;k++) {
            px[k]=((k*149)%4200)-2100;
            pz[k]=((k*257)%4300)-2150;
            originalBits[k]=Double.doubleToRawLongBits(
                    RealDemSeamlessMath.extendedHeight(px[k],pz[k]));
        }
        for(int pass=0;pass<4;pass++) {
            for(int k=0;k<8192;k++) {
                int x=((k*79+pass*31)%4800)-2400;
                int z=((k*173+pass*43)%4800)-2400;
                RealDemSeamlessMath.cachedExtendedHeight(x,z);
            }
            for(int k=nProbe-1;k>=0;k--)
                require(Double.doubleToRawLongBits(
                        RealDemSeamlessMath.cachedExtendedHeight(px[k],pz[k]))
                        ==originalBits[k],
                        "DEM cache churn changed height at "+px[k]+","+pz[k]);
        }
        checkCollisionRetained(source);
        checkRowSamplerParity();
        System.out.println("REALSCALE_USGS_V2_TWO_WAY_PARITY_PASS: 256 measured elevations, four fade sides, bitwise parity, height/fade collision retention, 210 row raw-bit checks");
    }
}

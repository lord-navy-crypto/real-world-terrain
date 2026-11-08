package org.realscale.neoforge;

/**
 * Experimental seed-stable additional caves for the separate Cave Exploration
 * preset. Each existing 768-block cell gains four loops and four terminal
 * grottos. No chunk loading, block writes, global random state or world data.
 */
public final class GrandOverworldCaveDesignV2 {
    private GrandOverworldCaveDesignV2() {}
    static final int MIN_EXTRA_Y=0,MAX_EXTRA_Y=1600;

    private static long mix(long value) {
        value^=value>>>30;value*=0xbf58476d1ce4e5b9L;
        value^=value>>>27;value*=0x94d049bb133111ebL;
        return value^(value>>>31);
    }

    /** Exact conservative bounds of the existing tunnel/hall strength kernels. */
    private static final class Bounds {
        final double minX,maxX,minY,maxY,minZ,maxZ;
        Bounds(double minX,double maxX,double minY,double maxY,
               double minZ,double maxZ) {
            this.minX=minX;this.maxX=maxX;this.minY=minY;this.maxY=maxY;
            this.minZ=minZ;this.maxZ=maxZ;
        }
        static Bounds tunnel(GrandOverworldCaveNetwork.Hall a,
                GrandOverworldCaveNetwork.Hall b,
                GrandOverworldCaveNetwork.Passage section) {
            double h=section.radius()+17.0;
            double v=section.radius()*section.verticalScale()+16.0;
            return new Bounds(Math.min(a.x(),b.x())-h,
                    Math.max(a.x(),b.x())+h,
                    Math.min(a.y(),b.y())-v,
                    Math.max(a.y(),b.y())+v,
                    Math.min(a.z(),b.z())-h,
                    Math.max(a.z(),b.z())+h);
        }
        static Bounds hall(GrandOverworldCaveNetwork.Hall h) {
            return new Bounds(h.x()-h.radiusX(),h.x()+h.radiusX(),
                    h.y()-h.radiusY(),h.y()+h.radiusY(),
                    h.z()-h.radiusZ(),h.z()+h.radiusZ());
        }
        boolean intersectsSourceColumn(int x,int z) {
            // Account for every legal +/-6 X and +/-5 Z density warp.
            return x+6.0>=minX && x-6.0<=maxX
                    && z+5.0>=minZ && z-5.0<=maxZ;
        }
        boolean intersectsSourceHeight(int y) {
            // Account for the legal +/-2 Y density warp.
            return y+2.0>=minY && y-2.0<=maxY;
        }
        boolean containsY(double y) {return y>=minY && y<=maxY;}
    }

    private static final class Layer {
        final GrandOverworldCaveNetwork.Hall first,second,source,annex;
        final GrandOverworldCaveNetwork.Passage loop,spur;
        final long loopKey,spurKey;
        final Bounds loopBounds,spurBounds,annexBounds;
        // Retain the original combined bounds for the topology reference test.
        final double minX,maxX,minY,maxY,minZ,maxZ;
        Layer(int gx,int gz,int level) {
            var center=GrandOverworldCaveNetwork.hall(gx,gz,level);
            first=GrandOverworldCaveNetwork.satellite(gx,gz,level,0);
            second=GrandOverworldCaveNetwork.satellite(gx,gz,level,1);
            long seed=mix((long)gx*0x9e3779b97f4a7c15L
                    ^(long)gz*0xc2b2ae3d27d4eb4fL
                    ^(long)level*0x632be59bd9b4e019L
                    ^0x68e31b7c5f9ad204L);
            source=(seed&1L)==0L?first:second;
            double signX=Math.copySign(1.0,source.x()-center.x());
            double signZ=Math.copySign(1.0,source.z()-center.z());
            boolean alongX=(seed&2L)==0L;
            double mainOffset=59+(int)Math.floorMod(seed>>>11,23L);
            double sideOffset=9+(int)Math.floorMod(seed>>>26,14L);
            double x=source.x()+signX*(alongX?mainOffset:sideOffset);
            double z=source.z()+signZ*(alongX?sideOffset:mainOffset);
            double y=source.y()+(int)Math.floorMod(seed>>>40,29L)-14;
            // Four tiers retain distinct silhouettes and vertical scales.
            double rx=switch(level){case 0->42;case 1->35;case 2->24;default->47;};
            double ry=switch(level){case 0->18;case 1->28;case 2->44;default->31;};
            double rz=switch(level){case 0->33;case 1->35;case 2->19;default->43;};
            annex=new GrandOverworldCaveNetwork.Hall(x,y,z,rx,ry,rz);
            loopKey=mix(seed^0x4a7c15a64d3be290L);
            spurKey=mix(seed^0x235abfe0dc71a495L);
            loop=new GrandOverworldCaveNetwork.Passage(
                    switch(level){case 0->14;case 1->18;case 2->19;default->23;},
                    switch(level){case 0->0.9;case 1->1.3;case 2->2.1;default->1.5;},5);
            spur=new GrandOverworldCaveNetwork.Passage(
                    switch(level){case 0->14;case 1->16;case 2->12;default->19;},
                    switch(level){case 0->0.9;case 1->1.25;case 2->1.9;default->1.4;},6);
            loopBounds=Bounds.tunnel(first,second,loop);
            spurBounds=Bounds.tunnel(source,annex,spur);
            annexBounds=Bounds.hall(annex);
            double reach=Math.max(loop.radius(),spur.radius())+18.0;
            minX=Math.min(Math.min(first.x(),second.x()),annex.x())
                    -Math.max(reach,annex.radiusX());
            maxX=Math.max(Math.max(first.x(),second.x()),annex.x())
                    +Math.max(reach,annex.radiusX());
            minZ=Math.min(Math.min(first.z(),second.z()),annex.z())
                    -Math.max(reach,annex.radiusZ());
            maxZ=Math.max(Math.max(first.z(),second.z()),annex.z())
                    +Math.max(reach,annex.radiusZ());
            double vertical=Math.max(loop.radius()*loop.verticalScale(),
                    spur.radius()*spur.verticalScale())+18.0;
            minY=Math.min(Math.min(first.y(),second.y()),annex.y())
                    -Math.max(vertical,annex.radiusY());
            maxY=Math.max(Math.max(first.y(),second.y()),annex.y())
                    +Math.max(vertical,annex.radiusY());
        }
        boolean couldTouch(double x,double y,double z) {
            return x>=minX && x<=maxX && y>=minY && y<=maxY
                    && z>=minZ && z<=maxZ;
        }
    }

    private static final class Region {
        final int gx,gz;
        final Layer[] tiers=new Layer[GrandOverworldCaveNetwork.LAYERS];
        Region(int gx,int gz) {
            this.gx=gx;this.gz=gz;
            for(int i=0;i<tiers.length;i++)tiers[i]=new Layer(gx,gz,i);
        }
    }

    private static final class Worker {
        final Region[] regions=new Region[8];
        final byte[] victim=new byte[4];
        Region recent;
        // A 512-entry, two-way, worker-local memo. A column is sampled at
        // many Y values, so cache its exact 12-feature X/Z candidate mask.
        private static final int COLUMN_SLOTS=2048,COLUMN_BUCKETS=COLUMN_SLOTS/2;
        final int[] columnX=new int[COLUMN_SLOTS];
        final int[] columnZ=new int[COLUMN_SLOTS];
        final int[] columnMask=new int[COLUMN_SLOTS];
        final boolean[] columnValid=new boolean[COLUMN_SLOTS];
        final byte[] columnVictim=new byte[COLUMN_BUCKETS];
        int lastColumnX,lastColumnZ,lastColumnMask;
        boolean lastColumnValid;

        Region get(int gx,int gz) {
            if(recent!=null && recent.gx==gx && recent.gz==gz)return recent;
            int hash=gx*0x9e3779b9 ^ Integer.rotateLeft(gz*0x85ebca6b,13);
            int bucket=(hash^(hash>>>16))&3,first=bucket*2;
            Region a=regions[first],b=regions[first+1];
            if(a!=null && a.gx==gx && a.gz==gz)return recent=a;
            if(b!=null && b.gx==gx && b.gz==gz)return recent=b;
            int slot;
            if(a==null)slot=first;
            else if(b==null)slot=first+1;
            else {slot=first+(victim[bucket]&1);victim[bucket]^=1;}
            return recent=regions[slot]=new Region(gx,gz);
        }

        int featureMask(int x,int z) {
            if(lastColumnValid && lastColumnX==x && lastColumnZ==z) {
                DensityPerfSampler.caveV2ColumnCacheProbe(true);
                return lastColumnMask;
            }
            int hash=x*0x9e3779b9 ^ Integer.rotateLeft(z*0x85ebca6b,13);
            int bucket=(hash^(hash>>>16))&(COLUMN_BUCKETS-1);
            int first=bucket<<1;
            for(int slot=first;slot<=first+1;slot++) {
                if(columnValid[slot] && columnX[slot]==x && columnZ[slot]==z) {
                    lastColumnX=x;lastColumnZ=z;
                    lastColumnMask=columnMask[slot];lastColumnValid=true;
                    DensityPerfSampler.caveV2ColumnCacheProbe(true);
                    return lastColumnMask;
                }
            }
            Region region=get(Math.floorDiv(x,GrandOverworldCaveNetwork.CELL),
                    Math.floorDiv(z,GrandOverworldCaveNetwork.CELL));
            int mask=0;
            for(int i=0;i<region.tiers.length;i++) {
                Layer layer=region.tiers[i];
                int bit=i*3;
                if(layer.loopBounds.intersectsSourceColumn(x,z))mask|=1<<bit;
                if(layer.spurBounds.intersectsSourceColumn(x,z))mask|=1<<(bit+1);
                if(layer.annexBounds.intersectsSourceColumn(x,z))mask|=1<<(bit+2);
            }
            int target;
            if(!columnValid[first])target=first;
            else if(!columnValid[first+1])target=first+1;
            else {target=first+(columnVictim[bucket]&1);columnVictim[bucket]^=1;}
            columnX[target]=x;columnZ[target]=z;columnMask[target]=mask;
            columnValid[target]=true;
            lastColumnX=x;lastColumnZ=z;lastColumnMask=mask;lastColumnValid=true;
            DensityPerfSampler.caveV2ColumnCacheProbe(false);
            return mask;
        }
    }

    private static final ThreadLocal<Worker> LOCAL=ThreadLocal.withInitial(Worker::new);

    /** Exact conservative X/Z mask; cached for repeated vertical samples. */
    static int componentMaskForColumn(int x,int z) {
        return LOCAL.get().featureMask(x,z);
    }

    static boolean mayTouchColumn(int x,int y,int z) {
        if(y<MIN_EXTRA_Y-2 || y>MAX_EXTRA_Y+2)return false;
        return mayTouchColumn(x,y,z,componentMaskForColumn(x,z));
    }

    /** Height-aware rejection over only the X/Z-selected features. */
    static boolean mayTouchColumn(int x,int y,int z,int mask) {
        if(mask==0)return false;
        Region site=LOCAL.get().get(Math.floorDiv(x,GrandOverworldCaveNetwork.CELL),
                Math.floorDiv(z,GrandOverworldCaveNetwork.CELL));
        for(int i=0;i<site.tiers.length;i++) {
            int bit=i*3;
            Layer layer=site.tiers[i];
            if((mask&(1<<bit))!=0 && layer.loopBounds.intersectsSourceHeight(y))return true;
            if((mask&(1<<(bit+1)))!=0 && layer.spurBounds.intersectsSourceHeight(y))return true;
            if((mask&(1<<(bit+2)))!=0 && layer.annexBounds.intersectsSourceHeight(y))return true;
        }
        return false;
    }

    /**
     * Masked evaluator. AABB bounds are the exact conservative support used
     * by tunnelStrength/hallStrength, not a visual approximation. If a future
     * caller exceeds the declared warp or crosses a cell, fall back to the
     * full reference evaluator rather than trusting a stale/partial mask.
     */
    static double strengthFromColumn(int sourceX,int sourceZ,
            double x,double y,double z,int mask) {
        if(y<MIN_EXTRA_Y || y>MAX_EXTRA_Y)return 0;
        if(x<sourceX-6.0 || x>sourceX+6.0
                || z<sourceZ-5.0 || z>sourceZ+5.0
                || Math.floorDiv(sourceX,GrandOverworldCaveNetwork.CELL)
                    !=(int)Math.floor(x/GrandOverworldCaveNetwork.CELL)
                || Math.floorDiv(sourceZ,GrandOverworldCaveNetwork.CELL)
                    !=(int)Math.floor(z/GrandOverworldCaveNetwork.CELL))
            return strength(x,y,z);
        if(mask==0)return 0;
        Region site=LOCAL.get().get(
                Math.floorDiv(sourceX,GrandOverworldCaveNetwork.CELL),
                Math.floorDiv(sourceZ,GrandOverworldCaveNetwork.CELL));
        double result=0;
        int evaluated=0;
        for(int i=0;i<site.tiers.length;i++) {
            int bit=i*3;
            Layer layer=site.tiers[i];
            if((mask&(1<<bit))!=0 && layer.loopBounds.containsY(y)) {
                result=Math.max(result,GrandOverworldCaveNetwork.tunnelStrength(
                        x,y,z,layer.first,layer.second,layer.loop,layer.loopKey));
                evaluated++;
            }
            if((mask&(1<<(bit+1)))!=0 && layer.spurBounds.containsY(y)) {
                result=Math.max(result,GrandOverworldCaveNetwork.tunnelStrength(
                        x,y,z,layer.source,layer.annex,layer.spur,layer.spurKey));
                evaluated++;
            }
            if((mask&(1<<(bit+2)))!=0 && layer.annexBounds.containsY(y)) {
                result=Math.max(result,hallStrength(x,y,z,layer.annex));
                evaluated++;
            }
        }
        DensityPerfSampler.caveV2FeatureEvaluations(evaluated);
        return result;
    }

    /** Full, unmasked reference evaluator retained for parity tests. */
    static double strength(double x,double y,double z) {
        if(y<MIN_EXTRA_Y || y>MAX_EXTRA_Y)return 0;
        int bx=(int)Math.floor(x),bz=(int)Math.floor(z);
        int gx=Math.floorDiv(bx,GrandOverworldCaveNetwork.CELL);
        int gz=Math.floorDiv(bz,GrandOverworldCaveNetwork.CELL);
        Region site=LOCAL.get().get(gx,gz);
        double result=0;
        for(Layer layer:site.tiers) {
            if(!layer.couldTouch(x,y,z))continue;
            result=Math.max(result,GrandOverworldCaveNetwork.tunnelStrength(
                    x,y,z,layer.first,layer.second,layer.loop,layer.loopKey));
            result=Math.max(result,GrandOverworldCaveNetwork.tunnelStrength(
                    x,y,z,layer.source,layer.annex,layer.spur,layer.spurKey));
            result=Math.max(result,hallStrength(x,y,z,layer.annex));
        }
        return result;
    }

    private static double hallStrength(double x,double y,double z,
            GrandOverworldCaveNetwork.Hall hall) {
        double a=(x-hall.x())/hall.radiusX();
        if(Math.abs(a)>=1)return 0;
        double b=(y-hall.y())/hall.radiusY();
        if(Math.abs(b)>=1)return 0;
        double c=(z-hall.z())/hall.radiusZ();
        if(Math.abs(c)>=1)return 0;
        double q=a*a+b*b+c*c;
        return q>=1?0:1-q;
    }

    /** Package-private inspection for deterministic topology QA. */
    static GrandOverworldCaveNetwork.Hall annexAt(int gx,int gz,int layer) {
        if(layer<0||layer>=GrandOverworldCaveNetwork.LAYERS)
            throw new IllegalArgumentException("layer");
        return LOCAL.get().get(gx,gz).tiers[layer].annex;
    }
    static boolean geometryFitsInsideCell(int gx,int gz,int layer) {
        Layer l=LOCAL.get().get(gx,gz).tiers[layer];
        double lowX=(long)gx*GrandOverworldCaveNetwork.CELL+24;
        double highX=(long)(gx+1)*GrandOverworldCaveNetwork.CELL-24;
        double lowZ=(long)gz*GrandOverworldCaveNetwork.CELL+24;
        double highZ=(long)(gz+1)*GrandOverworldCaveNetwork.CELL-24;
        return l.minX>lowX && l.maxX<highX
                && l.minZ>lowZ && l.maxZ<highZ
                && l.minY>=MIN_EXTRA_Y && l.maxY<=MAX_EXTRA_Y;
    }
}

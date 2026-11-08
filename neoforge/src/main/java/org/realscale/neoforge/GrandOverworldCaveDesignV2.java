package org.realscale.neoforge;

/**
 * Experimental, seed-stable additional caves for the separate Cave Exploration
 * world preset. Existing worlds do not instantiate this design path.
 *
 * Each of the four pre-existing 768-block karst tiers gains a satellite-to-
 * satellite loop and a side tunnel ending at a differently shaped grotto.
 * This reuses the proven canonical curved passage geometry and never loads
 * neighboring chunks or mutates blocks. The extra caves remain inside their
 * owning cell, so chunk generation order cannot cut off corridors.
 */
public final class GrandOverworldCaveDesignV2 {
    private GrandOverworldCaveDesignV2() {}
    static final int MIN_EXTRA_Y=0,MAX_EXTRA_Y=1600;

    private static long mix(long value) {
        value^=value>>>30;value*=0xbf58476d1ce4e5b9L;
        value^=value>>>27;value*=0x94d049bb133111ebL;
        return value^(value>>>31);
    }

    /** Immutable once generated, held per worldgen worker only. */
    private static final class Layer {
        final GrandOverworldCaveNetwork.Hall first,second,source,annex;
        final GrandOverworldCaveNetwork.Passage loop,spur;
        final long loopKey,spurKey;
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
            // Same network, clearly different silhouettes in the four tiers:
            // low flat galleries, broad mid-level grottos, vertical alpine
            // rifts and airy upper-tier alcoves.
            double rx=switch(level){case 0->42;case 1->35;case 2->24;default->47;};
            double ry=switch(level){case 0->18;case 1->28;case 2->44;default->31;};
            double rz=switch(level){case 0->33;case 1->35;case 2->19;default->43;};
            annex=new GrandOverworldCaveNetwork.Hall(x,y,z,rx,ry,rz);
            loopKey=mix(seed^0x4a7c15a64d3be290L);
            spurKey=mix(seed^0x235abfe0dc71a495L);
            loop=new GrandOverworldCaveNetwork.Passage(
                    switch(level){case 0->14;case 1->18;case 2->19;default->23;},
                    switch(level){case 0->0.9;case 1->1.3;case 2->2.1;default->1.5;},
                    5);
            spur=new GrandOverworldCaveNetwork.Passage(
                    switch(level){case 0->14;case 1->16;case 2->12;default->19;},
                    switch(level){case 0->0.9;case 1->1.25;case 2->1.9;default->1.4;},
                    6);
            // AABB guards overestimate, never underestimate, the same
            // canonical tunnelStrength radius + warp/bend support.
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
            else {
                slot=first+(victim[bucket]&1);
                victim[bucket]^=1;
            }
            return recent=regions[slot]=new Region(gx,gz);
        }
    }
    private static final ThreadLocal<Worker> LOCAL=ThreadLocal.withInitial(Worker::new);

    /**
     * Source-column conservative test for all allowed +/-6 X, +/-5 Z,
     * +/-2 Y seeded warps. False means the expanded design is exactly zero.
     * An empty candidate needs no noise lookup or cave topology evaluation.
     */
    static boolean mayTouchColumn(int x,int y,int z) {
        if(y<MIN_EXTRA_Y-2 || y>MAX_EXTRA_Y+2)return false;
        int localX=Math.floorMod(x,GrandOverworldCaveNetwork.CELL);
        int localZ=Math.floorMod(z,GrandOverworldCaveNetwork.CELL);
        // Each extra feature is >24 blocks from the cell edge, even after
        // seeded displacements. Do not pull in any neighbor's region.
        if(localX<24 || localX>=GrandOverworldCaveNetwork.CELL-24
                ||localZ<24 ||localZ>=GrandOverworldCaveNetwork.CELL-24)
            return false;
        int gx=Math.floorDiv(x,GrandOverworldCaveNetwork.CELL);
        int gz=Math.floorDiv(z,GrandOverworldCaveNetwork.CELL);
        Region site=LOCAL.get().get(gx,gz);
        for(Layer layer:site.tiers) {
            if(x+6.0>=layer.minX && x-6.0<=layer.maxX
                    && y+2.0>=layer.minY && y-2.0<=layer.maxY
                    && z+5.0>=layer.minZ && z-5.0<=layer.maxZ)
                return true;
        }
        return false;
    }

    /** Read-only terrain density. Never removes an existing passage. */
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

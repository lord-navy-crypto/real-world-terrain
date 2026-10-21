package org.realscale.neoforge;

/**
 * Grand Overworld karst-inspired cave network for 2096-high RealScale worlds.
 *
 * Not a simulation of chemical erosion: this builds a deterministic network
 * of horizontal phreatic galleries, tall vadose canyons, shafts, variable
 * passage cross-sections and large chambers inspired by surveyed caves.
 *
 * Crucial invariants:
 *  - world-absolute cell hashes, NEVER chunk-local or order-dependent RNG;
 *  - all shared edges are canonicalized and visible from either cell;
 *  - rooms/satellites stay within their owning 768x768 cell;
 *  - immutable parameters cached per worker; no neighboring chunk access;
 *  - density-only: never changes blocks, dimension heights or fluids itself.
 */
public final class GrandOverworldCaveNetwork {
    private GrandOverworldCaveNetwork() {}
    // The original 512-block grid over-carved some alpine volumes.
    // A 768-block grid retains huge halls and long tunnels with fewer
    // excavated chambers per square kilometer.
    public static final int CELL = 768;
    public static final int MIN_Y = -40;
    public static final int MAX_Y = 1920;
    // Conservative geometric upper bound for connected halls/tunnels.
    // Highest layer-3 hall center <=1439; any passage expands its endpoint
    // bounding box by at most 93*1.45+16 (<151). Tall canyons expand
    // by at most 50*2.25+16 (<129). Other arches and shafts are lower.
    // No strength can exist above 1590; 1700 retains a >110 block
    // safety margin and does NOT affect independently sampled entrances.
    static final int MAX_GEOMETRY_Y = 1700;
    public static final int LAYERS = 4;
    private static final int[] DIR_X = {1,-1,0,0};
    private static final int[] DIR_Z = {0,0,1,-1};
    private static final int[] HEIGHT_LEVELS = {135,395,790,1330};
    private static final int[] HEIGHT_VARIATIONS = {75,110,180,220};

    public record Hall(double x, double y, double z,
                       double radiusX, double radiusY, double radiusZ) {}
    public record Passage(double radius, double verticalScale, int kind) {}
    private static final class Cache {
        int gx=Integer.MIN_VALUE,gz=Integer.MIN_VALUE;
        // Direct-mapped, epoch-tagged per-worker X/Z reject cache. An entry
        // only says that a column MAY contain a cave, never that it must.
        private static final int COLUMN_SLOTS = 512;
        private static final int COLUMN_BUCKETS = COLUMN_SLOTS / 2;
        // Two colliding hot columns may retain separate 47-component masks.
        // Eight region snapshots still own only 512 entries each; no growth.
        final byte[] columnVictim=new byte[COLUMN_BUCKETS];
        final int[] columnX=new int[COLUMN_SLOTS];
        final int[] columnZ=new int[COLUMN_SLOTS];
        final int[] columnEpoch=new int[COLUMN_SLOTS];
        // 47 independently masked chambers and tunnels fit into one long.
        final long[] columnComponents=new long[COLUMN_SLOTS];
        int epoch=1;
        final Hall[][] halls=new Hall[LAYERS][5];
        final Hall[][] galleries=new Hall[LAYERS][2];
        final long[][] corridorKeys=new long[LAYERS][4];
        final long[][] sideKeys=new long[LAYERS][2];
        final Passage[][] widths=new Passage[LAYERS][4];
        final Passage[][] sideWidths=new Passage[LAYERS][2];
        final long[] shaftKeys=new long[LAYERS-1];
        final Passage[] shafts=new Passage[LAYERS-1];
        // Two tiers, two axes, and the current or previous 3-cell group.
        final Hall[][][] arteryA=new Hall[2][2][2];
        final Hall[][][] arteryB=new Hall[2][2][2];
        final Passage[][][] arteryWidth=new Passage[2][2][2];
        final long[][][] arteryKey=new long[2][2][2];
        void load(int x,int z) {
            if(gx==x && gz==z)return;
            gx=x;gz=z;
            // Entries for a different 768x768 network cell are invalid.
            // No cross-thread access or world-wide unbounded state.
            if (++epoch == 0) {
                java.util.Arrays.fill(columnEpoch,0);
                epoch=1;
            }
            for(int layer=0;layer<LAYERS;layer++) {
                Hall center=hall(x,z,layer);
                halls[layer][0]=center;
                for(int i=0;i<4;i++) {
                    Hall other=hall(x+DIR_X[i],z+DIR_Z[i],layer);
                    halls[layer][i+1]=other;
                    long key=passageKey(center,other);
                    corridorKeys[layer][i]=key;
                    widths[layer][i]=passage(key,false);
                }
                for(int i=0;i<2;i++) {
                    Hall side=satellite(x,z,layer,i);
                    galleries[layer][i]=side;
                    long key=passageKey(center,side);
                    sideKeys[layer][i]=key;
                    sideWidths[layer][i]=passage(key,true);
                }
            }
            for(int layer=0;layer<LAYERS-1;layer++) {
                shaftKeys[layer]=passageKey(halls[layer][0],halls[layer+1][0]);
                shafts[layer]=new Passage(20+Math.floorMod(shaftKeys[layer]>>>12,17L),1.0,3);
            }
            for(int tier=0;tier<2;tier++) for(int axis=0;axis<2;axis++)
                for(int previous=0;previous<2;previous++) {
                    int ax=axis==0 ? Math.floorDiv(x,3)*3-previous*3 : x;
                    int az=axis==1 ? Math.floorDiv(z,3)*3-previous*3 : z;
                    if(arterialActive(ax,az,tier+1,axis==0)) {
                        Hall a=hall(ax,az,tier+1);
                        Hall b=hall(ax+(axis==0?3:0),az+(axis==1?3:0),tier+1);
                        long key=passageKey(a,b);
                        arteryA[tier][axis][previous]=a;
                        arteryB[tier][axis][previous]=b;
                        arteryKey[tier][axis][previous]=key;
                        arteryWidth[tier][axis][previous]=
                                new Passage(47+Math.floorMod(key>>>11,37L),1.65,4);
                    } else {
                        arteryA[tier][axis][previous]=null;
                        arteryB[tier][axis][previous]=null;
                        arteryWidth[tier][axis][previous]=null;
                    }
                }
        }
    }
    /**
     * A jigsaw structure height probe alternates between several distant
     * 768-block cells. Rebuilding a single global "last cell" creates all
     * 4-tier hall, gallery and arterial geometry again at every switch.
     *
     * Retain eight independent, bounded region snapshots per worker. Each
     * snapshot also retains its own per-column broad-phase masks and epoch,
     * so switching A->B->A reuses exactly the same seeded geometry without
     * rehashing/reallocating the corridor graph. Evictions change ONLY CPU
     * work; they cannot affect world seed, biome, terrain or cave topology.
     */
    private static final class RegionCache {
        // Eight snapshots per worker, with two candidates per hash bucket.
        // Alternating nearby jigsaw probes can share a direct-map slot and
        // otherwise rebuild the entire seeded multi-layer cave graph.
        // Victim replacement recomputes geometry only; it never changes it.
        private static final int REGION_SLOTS=8;
        private static final int REGION_BUCKETS=REGION_SLOTS/2;
        private final Cache[] regions=new Cache[REGION_SLOTS];
        private final byte[] victim=new byte[REGION_BUCKETS];
        private Cache last;

        Cache get(int gx,int gz) {
            Cache recent=last;
            if(recent!=null && recent.gx==gx && recent.gz==gz)return recent;
            int hash=gx*0x9e3779b9 ^ Integer.rotateLeft(gz*0x85ebca6b,13);
            int bucket=(hash^(hash>>>16))&(REGION_BUCKETS-1);
            int first=bucket<<1;
            Cache a=regions[first];
            if(a!=null && a.gx==gx && a.gz==gz) {
                last=a;
                return a;
            }
            Cache b=regions[first+1];
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
            Cache c=regions[slot];
            if(c==null) {
                c=new Cache();
                regions[slot]=c;
            }
            c.load(gx,gz);
            last=c;
            return c;
        }
    }
    private static final ThreadLocal<RegionCache> LOCAL=
            ThreadLocal.withInitial(RegionCache::new);
    /**
     * Last-site-only, thread-confined memo for the rare cave entrance.
     * This is a much cheaper lookup than repeatedly hashing the same cell
     * and reconstructing its Hall during every Y sample of getBaseHeight.
     * Key and hall never depend on world seed, chunk order, dimension state
     * or current player location; cache eviction only affects CPU work.
     */
    private static final class EntranceSite {
        int gx, gz;
        boolean valid, active;
        Hall anchor;
        double width;

        void load(int x,int z) {
            if(valid && gx==x && gz==z)return;
            gx=x; gz=z; valid=true;
            long key=cellKey(x,z,0);
            active=Math.floorMod(key,9L)==0;
            if(!active) {
                anchor=null;
                width=0;
                return;
            }
            int layer=(key&3L)==0 ? 1 : 0;
            anchor=hall(x,z,layer);
            width=11+Math.floorMod(key>>>17,8L);
        }
    }
    private static final ThreadLocal<EntranceSite> ENTRANCES=
            ThreadLocal.withInitial(EntranceSite::new);


    private static long mix(long value) {
        value^=value>>>30;value*=0xbf58476d1ce4e5b9L;
        value^=value>>>27;value*=0x94d049bb133111ebL;
        return value^(value>>>31);
    }
    private static long cellKey(int gx,int gz,int layer) {
        return mix((long)gx*0x9e3779b97f4a7c15L ^
                (long)gz*0xc2b2ae3d27d4eb4fL ^
                ((long)layer*0x632be59bd9b4e019L) ^
                0x39ce758a1d4e2b67L);
    }

    public static Hall hall(int gx,int gz,int layer) {
        if(layer<0 || layer>=LAYERS)throw new IllegalArgumentException("layer");
        long h=cellKey(gx,gz,layer);
        int jitterX=(int)Math.floorMod(h,111L)-55;
        int jitterZ=(int)Math.floorMod(h>>>19,111L)-55;
        // The high tiers only carve where the terrain has actual rock.
        // Lower ancient fossil galleries, active underground river level,
        // middle mountain interiors, and rarer lofty alpine strata.
        double y=HEIGHT_LEVELS[layer]+(int)Math.floorMod(h>>>37,(long)HEIGHT_VARIATIONS[layer])
                 -HEIGHT_VARIATIONS[layer]/2;
        int scale=(int)Math.floorMod(h>>>48,100L);
        // Preserve broad and monumental halls, but place them farther apart.
        double rx=scale<13 ? 145+scale : 78+(scale%54);
        double ry=scale<13 ? 105+scale/2.0 : 44+(scale%45);
        double rz=scale<13 ? 135+scale : 75+(scale%52);
        return new Hall((long)gx*CELL+CELL/2+jitterX,y,
                (long)gz*CELL+CELL/2+jitterZ,rx,ry,rz);
    }

    public static Hall satellite(int gx,int gz,int layer,int index) {
        if(layer<0||layer>=LAYERS||index<0||index>1)
            throw new IllegalArgumentException("gallery");
        Hall origin=hall(gx,gz,layer);
        long h=mix(cellKey(gx,gz,layer)^((long)index*0x94d049bb133111ebL));
        int signX=(h&1)==0 ? 1:-1;
        int signZ=(h&2)==0 ? 1:-1;
        double x=origin.x()+signX*(116+(int)Math.floorMod(h>>>13,44L));
        double z=origin.z()+signZ*(116+(int)Math.floorMod(h>>>21,44L));
        double y=origin.y()+(int)Math.floorMod(h>>>33,61L)-30;
        double r=27+(int)Math.floorMod(h>>>42,35L);
        return new Hall(x,y,z,r,r*0.82,r*0.95);
    }

    /** True for the exact 3-cell arterial segments used during density sampling. */
    static boolean arterialActive(int anchorGX,int anchorGZ,int layer,boolean xAxis) {
        if(layer!=1 && layer!=2) return false;
        long key=mix(cellKey(anchorGX,anchorGZ,layer)^
                (xAxis ? 0x7ef6c15cf3b8ad91L : 0x4bd83e9a6c157f20L));
        return Math.floorMod(key,6L)==0;
    }

    private static long passageKey(Hall a,Hall b) {
        if(a.x()>b.x() || (a.x()==b.x()&&a.z()>b.z()) ||
                (a.x()==b.x()&&a.z()==b.z()&&a.y()>b.y())) {
            Hall t=a;a=b;b=t;
        }
        long k1=mix(Double.doubleToLongBits(a.x())^
                Long.rotateLeft(Double.doubleToLongBits(a.y()),13)^
                Double.doubleToLongBits(a.z()));
        long k2=mix(Double.doubleToLongBits(b.x())^
                Long.rotateLeft(Double.doubleToLongBits(b.y()),13)^
                Double.doubleToLongBits(b.z()));
        return mix(k1^Long.rotateLeft(k2,23));
    }

    private static Passage passage(long key,boolean gallery) {
        int type=(int)Math.floorMod(key,100L);
        if(gallery) {
            return new Passage(12+Math.floorMod(key>>>11,20L),1.0,0);
        }
        if(type<16) // Classic tight passage, retained for exploration variety.
            return new Passage(10+Math.floorMod(key>>>11,11L),1.0,0);
        if(type<58) // Broad tube passage; wide and accessible in giant terrain.
            return new Passage(29+Math.floorMod(key>>>11,23L),1.0,0);
        if(type<86) // Tall stream-incised canyon / composite passage.
            return new Passage(24+Math.floorMod(key>>>11,27L),2.25,1);
        // Rare extraordinary avenue, far wider than older 7/11-block tubes.
        return new Passage(61+Math.floorMod(key>>>11,33L),1.45,2);
    }

    static Passage corridor(int gx,int gz,int layer,int direction) {
        Hall a=hall(gx,gz,layer);
        Hall b=hall(gx+DIR_X[direction],gz+DIR_Z[direction],layer);
        return passage(passageKey(a,b),false);
    }

    private static double hallStrength(double x,double y,double z,Hall hall) {
        double a=(x-hall.x())/hall.radiusX();
        if(Math.abs(a)>=1)return 0;
        double b=(y-hall.y())/hall.radiusY();
        if(Math.abs(b)>=1)return 0;
        double c=(z-hall.z())/hall.radiusZ();
        if(Math.abs(c)>=1)return 0;
        double q=a*a+b*b+c*c;
        return q>=1?0:1-q;
    }

    static double tunnelStrength(double x,double y,double z,Hall a,Hall b,
                                 Passage section,long key) {
        if(a.x()>b.x()||(a.x()==b.x()&&a.z()>b.z())||
                (a.x()==b.x()&&a.z()==b.z()&&a.y()>b.y())) {
            Hall tmp=a;a=b;b=tmp;
        }
        double horizontalReach=section.radius()+17;
        double verticalReach=section.radius()*section.verticalScale()+16;
        if(x<Math.min(a.x(),b.x())-horizontalReach||
           x>Math.max(a.x(),b.x())+horizontalReach||
           z<Math.min(a.z(),b.z())-horizontalReach||
           z>Math.max(a.z(),b.z())+horizontalReach||
           y<Math.min(a.y(),b.y())-verticalReach||
           y>Math.max(a.y(),b.y())+verticalReach) return 0;
        double vx=b.x()-a.x(),vy=b.y()-a.y(),vz=b.z()-a.z();
        double len=vx*vx+vy*vy+vz*vz;
        if(len<1)return 0;
        double t=((x-a.x())*vx+(y-a.y())*vy+(z-a.z())*vz)/len;
        if(t<=0||t>=1)return 0;
        // Large smooth bends are stable across chunk boundaries and vary by
        // passage. Endpoints connect precisely to the chamber centers.
        double curve=4*t*(1-t);
        double displacementA=((int)Math.floorMod(key>>>19,29L)-14)*curve;
        double displacementB=((int)Math.floorMod(key>>>39,21L)-10)*curve;
        double cx=a.x()+t*vx,cy=a.y()+t*vy,cz=a.z()+t*vz;
        if(Math.abs(vy)>Math.max(Math.abs(vx),Math.abs(vz))) {
            cx+=displacementA*0.6;cz+=displacementB*0.6;
        } else if(Math.abs(vx)>=Math.abs(vz)) {
            cz+=displacementA;cy+=displacementB;
        } else {
            cx+=displacementA;cy+=displacementB;
        }
        // Changes continuously, keeping cross-sections unlike uniform pipes.
        double widthScale=0.80+0.20*Math.sin(Math.PI*t)*Math.sin(Math.PI*t);
        double width=section.radius()*widthScale;
        double height=width*section.verticalScale();
        double xx=x-cx, yy=y-cy, zz=z-cz;
        double q;
        if(Math.abs(vy)>Math.max(Math.abs(vx),Math.abs(vz))) {
            // Shaft orientation: a vertical connector remains round-ish.
            q=(xx*xx+zz*zz)/(width*width)+yy*yy/(height*height);
        } else {
            // Project to cross-axis in X/Z so corridor length is not counted
            // as radial distance; vertical canyon passages may be very tall.
            double horiz=Math.hypot(vx,vz);
            if(horiz<1)return 0;
            double lateral=(-vz*xx+vx*zz)/horiz;
            q=(lateral*lateral)/(width*width)+(yy*yy)/(height*height);
        }
        return q>=1?0:1-q;
    }

    /**
     * Select occasional vertical karst entrances on highland rock. These
     * emerge from an existing underground hall instead of being independent
     * surface holes. The final density wrapper additionally guards y>=110,
     * positive terrain density and the world surface / aquatic boundary.
     *
     * About 1 in 9 cells gets one; the rest have intact geological roofs.
     */
    static double entranceStrength(double x,double y,double z) {
        if(y<110 || y>MAX_Y)return 0;
        int gx=(int)Math.floor(x/CELL),gz=(int)Math.floor(z/CELL);
        // A 2096-high height query re-enters this same horizontal cell
        // hundreds of times. Cache ONLY immutable entrance parameters per
        // worker; the original sin/winding and voxel density remain exact.
        EntranceSite e=ENTRANCES.get();
        e.load(gx,gz);
        if(!e.active)return 0;
        Hall anchor=e.anchor;
        if(y<anchor.y())return 0;
        double width=e.width;
        // With a maximum winding offset of 5/4 blocks, the vast majority
        // of voxels can reject the shaft without trigonometry or carving.
        if(Math.abs(x-anchor.x())>width+5 || Math.abs(z-anchor.z())>width+4)
            return 0;
        double rise=y-anchor.y();
        double centerX=anchor.x()+Math.sin(rise/92.0)*5;
        double centerZ=anchor.z()+Math.sin(rise/119.0)*4;
        double dx=(x-centerX)/width;
        if(Math.abs(dx)>=1)return 0;
        double dz=(z-centerZ)/width;
        double q=dx*dx+dz*dz;
        return q>=1 ? 0 : 1-q;
    }


    /**
     * Conservative X/Z broad phase for the giant cave density network.
     *
     * The caller's density warp shifts X by <=6 and Z by <=5 blocks.
     * If either shifted point could cross a 768-block cell boundary, do
     * not reject it: the neighboring cell has different cached corridors.
     *
     * A false result PROVES strength(x+warpX,y,z+warpZ)==0 for every Y and
     * every allowed shift. It therefore permits skipping the noise warp
     * and the full cave graph while keeping the original density bitwise
     * unchanged. A true result leaves the original computation untouched.
     */
    static boolean mayContainVoidNearColumn(int x,int z) {
        return componentMaskForColumn(x,z)!=0;
    }

    static int columnBucket(int x,int z) {
        return (x*0x9e3779b9 ^ z*0x85ebca6b)
                & (Cache.COLUMN_BUCKETS - 1);
    }

    private static long componentMaskForColumn(int x,int z) {
        int localX=Math.floorMod(x,CELL),localZ=Math.floorMod(z,CELL);
        // A displaced coordinate may select another cell's distinct
        // tunnels. Never prune at those edges; retain the original path.
        if(localX<=6 || localX>=CELL-7 || localZ<=5 || localZ>=CELL-6)
            return -1L;
        Cache c=LOCAL.get().get(Math.floorDiv(x,CELL),Math.floorDiv(z,CELL));
        int bucket=columnBucket(x,z);
        int first=bucket<<1,second=first+1;
        if(c.columnEpoch[first]==c.epoch && c.columnX[first]==x
                && c.columnZ[first]==z)
            return c.columnComponents[first];
        if(c.columnEpoch[second]==c.epoch && c.columnX[second]==x
                && c.columnZ[second]==z)
            return c.columnComponents[second];
        long mask=columnComponentMask(c,x-6.0,x+6.0,z-5.0,z+5.0);
        final int target;
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
     * Evaluate the SAME cave network as strength(), but only after excluding
     * components whose horizontal axis-aligned bounds cannot intersect this
     * full +/-6 X and +/-5 Z seed-warped column. A skipped component must
     * have returned precisely zero in the original function at EVERY Y.
     *
     * Each mask is immutable for a site cell; collisions evict data but
     * never change output. Near 768-cell edges use the original evaluator.
     */
    static double strengthFromColumn(int sourceX,int sourceZ,
                                     double x,double y,double z) {
        if(y<MIN_Y || y>MAX_GEOMETRY_Y)return 0;
        // Do not trust a source-column mask if any caller supplies an
        // unsupported warp. Validate BEFORE zero-mask early returns.
        if(x<sourceX-6.0 || x>sourceX+6.0
                || z<sourceZ-5.0 || z>sourceZ+5.0
                || Math.floorDiv(sourceX,CELL)!=(int)Math.floor(x/CELL)
                || Math.floorDiv(sourceZ,CELL)!=(int)Math.floor(z/CELL))
            return strength(x,y,z);
        long mask=componentMaskForColumn(sourceX,sourceZ);
        if(mask==-1L)return strength(x,y,z);
        if(mask==0L)return 0;
        Cache c=LOCAL.get().get(Math.floorDiv(sourceX,CELL),Math.floorDiv(sourceZ,CELL));
        double value=0;
        for(int layer=0;layer<LAYERS;layer++) {
            Hall center=c.halls[layer][0];
            if(Math.abs(y-center.y())>235 && layer<3)continue;
            if(Math.abs(y-center.y())>350 && layer==3)continue;
            int shift=layer*9;
            if((mask&(1L<<shift))!=0)
                value=Math.max(value,hallStrength(x,y,z,center));
            for(int direction=0;direction<4;direction++) {
                if((mask&(1L<<(shift+1+direction)))!=0)
                    value=Math.max(value,tunnelStrength(x,y,z,center,
                            c.halls[layer][direction+1],c.widths[layer][direction],
                            c.corridorKeys[layer][direction]));
            }
            if(Math.abs(x-center.x())<225&&Math.abs(z-center.z())<225&&
                    Math.abs(y-center.y())<150) {
                for(int g=0;g<2;g++) {
                    if((mask&(1L<<(shift+5+g*2)))!=0)
                        value=Math.max(value,hallStrength(x,y,z,c.galleries[layer][g]));
                    if((mask&(1L<<(shift+6+g*2)))!=0)
                        value=Math.max(value,tunnelStrength(x,y,z,center,
                                c.galleries[layer][g],c.sideWidths[layer][g],
                                c.sideKeys[layer][g]));
                }
            }
        }
        for(int layer=0;layer<LAYERS-1;layer++) {
            if((mask&(1L<<(36+layer)))!=0)
                value=Math.max(value,tunnelStrength(x,y,z,c.halls[layer][0],
                        c.halls[layer+1][0],c.shafts[layer],c.shaftKeys[layer]));
        }
        for(int tier=0;tier<2;tier++) for(int axis=0;axis<2;axis++)
            for(int group=0;group<2;group++) {
                int shift=39+tier*4+axis*2+group;
                if((mask&(1L<<shift))==0)continue;
                Hall a=c.arteryA[tier][axis][group];
                if(a!=null)
                    value=Math.max(value,tunnelStrength(x,y,z,a,
                            c.arteryB[tier][axis][group],
                            c.arteryWidth[tier][axis][group],
                            c.arteryKey[tier][axis][group]));
            }
        return value;
    }

    private static boolean hallCouldTouch(Hall hall,
            double loX,double hiX,double loZ,double hiZ) {
        return loX<=hall.x()+hall.radiusX() && hiX>=hall.x()-hall.radiusX()
            && loZ<=hall.z()+hall.radiusZ() && hiZ>=hall.z()-hall.radiusZ();
    }

    private static boolean tunnelCouldTouch(Hall a,Hall b,Passage section,
            double loX,double hiX,double loZ,double hiZ) {
        // tunnelStrength uses exactly this conservative horizontal reach,
        // including both seeded centerline offsets and all cross-sections.
        double reach=section.radius()+17.0;
        return loX<=Math.max(a.x(),b.x())+reach
            && hiX>=Math.min(a.x(),b.x())-reach
            && loZ<=Math.max(a.z(),b.z())+reach
            && hiZ>=Math.min(a.z(),b.z())-reach;
    }

    private static long columnComponentMask(Cache c,
            double loX,double hiX,double loZ,double hiZ) {
        long mask=0L;
        for(int layer=0;layer<LAYERS;layer++) {
            int shift=layer*9;
            Hall center=c.halls[layer][0];
            if(hallCouldTouch(center,loX,hiX,loZ,hiZ))
                mask|=1L<<shift;
            for(int dir=0;dir<4;dir++) {
                if(tunnelCouldTouch(center,c.halls[layer][dir+1],
                        c.widths[layer][dir],loX,hiX,loZ,hiZ))
                    mask|=1L<<(shift+1+dir);
            }
            for(int i=0;i<2;i++) {
                Hall satellite=c.galleries[layer][i];
                if(hallCouldTouch(satellite,loX,hiX,loZ,hiZ))
                    mask|=1L<<(shift+5+2*i);
                if(tunnelCouldTouch(center,satellite,
                        c.sideWidths[layer][i],loX,hiX,loZ,hiZ))
                    mask|=1L<<(shift+6+2*i);
            }
        }
        for(int layer=0;layer<LAYERS-1;layer++) {
            if(tunnelCouldTouch(c.halls[layer][0],c.halls[layer+1][0],
                    c.shafts[layer],loX,hiX,loZ,hiZ))
                mask|=1L<<(36+layer);
        }
        for(int tier=0;tier<2;tier++) for(int axis=0;axis<2;axis++)
            for(int group=0;group<2;group++) {
                Hall a=c.arteryA[tier][axis][group];
                if(a!=null && tunnelCouldTouch(a,c.arteryB[tier][axis][group],
                        c.arteryWidth[tier][axis][group],
                        loX,hiX,loZ,hiZ))
                    mask|=1L<<(39+tier*4+axis*2+group);
            }
        return mask;
    }

    /**
     * Positive values indicate a requested void. 0 leaves original noise
     * untouched. No block/biome writes and no chunk loading.
     */
    public static double strength(double x,double y,double z) {
        if(y<MIN_Y||y>MAX_GEOMETRY_Y)return 0;
        int gx=(int)Math.floor(x/CELL),gz=(int)Math.floor(z/CELL);
        Cache cache=LOCAL.get().get(gx,gz);
        double value=0;
        for(int layer=0;layer<LAYERS;layer++) {
            Hall center=cache.halls[layer][0];
            // Cheap altitude band reject: most 3D voxels never evaluate this
            // tier's corridors or satellites.
            if(Math.abs(y-center.y())>235 && layer<3)continue;
            if(Math.abs(y-center.y())>350 && layer==3)continue;
            value=Math.max(value,hallStrength(x,y,z,center));
            for(int direction=0;direction<4;direction++) {
                value=Math.max(value,tunnelStrength(x,y,z,center,
                    cache.halls[layer][direction+1],cache.widths[layer][direction],
                    cache.corridorKeys[layer][direction]));
            }
            if(Math.abs(x-center.x())<225&&Math.abs(z-center.z())<225&&
                    Math.abs(y-center.y())<150) {
                for(int g=0;g<2;g++) {
                    Hall gallery=cache.galleries[layer][g];
                    value=Math.max(value,hallStrength(x,y,z,gallery));
                    value=Math.max(value,tunnelStrength(x,y,z,center,gallery,
                        cache.sideWidths[layer][g],cache.sideKeys[layer][g]));
                }
            }
        }
        // Interlayer vertical chimneys: travel through the mountain interior
        // instead of disconnected stacks of flat horizontal passageways.
        for(int layer=0;layer<LAYERS-1;layer++) {
            value=Math.max(value,tunnelStrength(x,y,z,cache.halls[layer][0],
                    cache.halls[layer+1][0],cache.shafts[layer],cache.shaftKeys[layer]));
        }
        for(int tier=0;tier<2;tier++) for(int axis=0;axis<2;axis++)
            for(int group=0;group<2;group++) {
                Hall a=cache.arteryA[tier][axis][group];
                if(a!=null)
                    value=Math.max(value,tunnelStrength(x,y,z,a,
                            cache.arteryB[tier][axis][group],
                            cache.arteryWidth[tier][axis][group],
                            cache.arteryKey[tier][axis][group]));
            }
        return value;
    }
}

package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Frozen pre-Test.36 strengthFromColumnWithMask() independently replayed
 * against the optimized production evaluator. The frozen evaluator invokes
 * the original unchanged chamber() and tube() private functions. It counts
 * genuine tube() calls that the new exact Y guard can avoid. Those counts
 * are workload-specific, NOT a Minecraft chunks/second speedup.
 */
public final class GrandNetherEvaluatorCallPruneSmoke {
    private GrandNetherEvaluatorCallPruneSmoke() {}
    private static final Class<?> NETWORK=GrandNetherCaveNetwork.class;
    private static final Method TUBE,CHAMBER,REGION;
    private static final ThreadLocal<?> LOCAL;
    private static final Field MIN_Y,MAX_Y;
    private static long checked,rawFullChecks,positive,oldTubeCalls,
            eligibleTubeCalls,avoidedLayers,skippedCorridorGroups,
            skippedSatelliteGroups,borderFallbacks,zeroMasks;

    static {
        try {
            Class<?> tube=Class.forName(NETWORK.getName()+"$TubeSegment");
            TUBE=NETWORK.getDeclaredMethod("tube",double.class,double.class,
                    double.class,tube);
            CHAMBER=NETWORK.getDeclaredMethod("chamber",double.class,
                    double.class,double.class,GrandNetherCaveNetwork.Hall.class);
            TUBE.setAccessible(true);CHAMBER.setAccessible(true);
            Field thread=NETWORK.getDeclaredField("CACHED_HALLS");
            thread.setAccessible(true);
            LOCAL=(ThreadLocal<?>)thread.get(null);
            Class<?> region=Class.forName(NETWORK.getName()+"$RegionCache");
            REGION=region.getDeclaredMethod("get",int.class,int.class);
            REGION.setAccessible(true);
            MIN_Y=tube.getDeclaredField("minY");
            MAX_Y=tube.getDeclaredField("maxY");
            MIN_Y.setAccessible(true);MAX_Y.setAccessible(true);
        } catch (Exception e) {throw new ExceptionInInitializerError(e);}
    }

    private static Field field(Class<?> type,String name)throws Exception {
        Field f=type.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Object get(Object o,String name)throws Exception {
        return field(o.getClass(),name).get(o);
    }
    private static long raw(double v){return Double.doubleToRawLongBits(v);}
    private static void require(boolean b,String description) {
        if(!b)throw new AssertionError(description);
    }
    private static double tube(double x,double y,double z,Object segment)
            throws Exception {
        oldTubeCalls++;
        // Same conditional as the production fast path. The ONLY counts
        // removed from production are disjoint-Y tube() invocations.
        if(!(y<MIN_Y.getDouble(segment)||y>MAX_Y.getDouble(segment)))
            eligibleTubeCalls++;
        return (double)TUBE.invoke(null,x,y,z,segment);
    }
    private static double chamber(double x,double y,double z,
            GrandNetherCaveNetwork.Hall room)throws Exception {
        return (double)CHAMBER.invoke(null,x,y,z,room);
    }

    /**
     * Verbatim Test.35 traversal semantics, including Math.max ordering,
     * early strength=1 returns, gate order, and satellite-group bounds.
     * This intentionally does NOT use the new layer/group or Y-call guards.
     */
    private static double frozen(int sourceX,int sourceZ,
            double x,double y,double z,int mask)throws Exception {
        if(y<GrandNetherCaveNetwork.MIN_Y||
                y>GrandNetherCaveNetwork.MAX_Y)return 0;
        int cell=GrandNetherCaveNetwork.CELL;
        if(x<sourceX-5.0||x>sourceX+5.0
                ||z<sourceZ-4.0||z>sourceZ+4.0
                ||Math.floorDiv(sourceX,cell)!=(int)Math.floor(x/cell)
                ||Math.floorDiv(sourceZ,cell)!=(int)Math.floor(z/cell))
            return GrandNetherCaveNetwork.strength(x,y,z);
        if(mask==-1)return GrandNetherCaveNetwork.strength(x,y,z);
        if(mask==0)return 0;
        Object cache=REGION.invoke(LOCAL.get(),
                Math.floorDiv(sourceX,cell),Math.floorDiv(sourceZ,cell));
        var halls=(GrandNetherCaveNetwork.Hall[][])get(cache,"halls");
        var satellites=(GrandNetherCaveNetwork.Hall[][])get(cache,"satellites");
        var corridors=(Object[][])get(cache,"corridors");
        var branches=(Object[][])get(cache,"branches");
        Object interlayer=get(cache,"interlayer");
        double answer=0;
        for(int layer=0;layer<2;layer++) {
            int shift=layer*9;
            if((mask&(0x1FF<<shift))==0)avoidedLayers++;
            GrandNetherCaveNetwork.Hall center=halls[layer][0];
            if((mask&(1<<shift))!=0
                    &&Math.abs(y-center.y())<center.radiusY()+1)
                answer=Math.max(answer,chamber(x,y,z,center));
            if(answer>=1)return 1;
            if(((mask>>>(shift+1))&0xF)==0)skippedCorridorGroups++;
            for(int edge=0;edge<4;edge++) {
                if((mask&(1<<(shift+1+edge)))!=0)
                    answer=Math.max(answer,
                            tube(x,y,z,corridors[layer][edge]));
            }
            if(((mask>>>(shift+5))&0xF)==0)skippedSatelliteGroups++;
            if(Math.abs(x-center.x())<=80&&Math.abs(z-center.z())<=80
                    &&Math.abs(y-center.y())<=30) {
                for(int gallery=0;gallery<2;gallery++) {
                    var side=satellites[layer][gallery];
                    if((mask&(1<<(shift+5+gallery*2)))!=0
                            &&Math.abs(y-side.y())<side.radiusY()+1)
                        answer=Math.max(answer,chamber(x,y,z,side));
                    if((mask&(1<<(shift+6+gallery*2)))!=0)
                        answer=Math.max(answer,
                                tube(x,y,z,branches[layer][gallery]));
                }
            }
        }
        if((mask&(1<<18))!=0)
            answer=Math.max(answer,tube(x,y,z,interlayer));
        return answer;
    }

    private static void sample(int x,int y,int z,int mask,double fraction,
            boolean verifyFull)throws Exception {
        double wx=x+fraction*5.0,wy=y+fraction*2.0,
                wz=z-fraction*4.0;
        double baseline=frozen(x,z,wx,wy,wz,mask);
        double current=GrandNetherCaveNetwork.strengthFromColumnWithMask(
                x,z,wx,wy,wz,mask);
        require(raw(baseline)==raw(current),
                "Frozen Test.35 evaluator vs Test.36 differed at "
                +x+","+y+","+z+" mask="+mask+" warp="+fraction
                +" old="+baseline+" new="+current);
        checked++;
        if(current>0)positive++;
        if(mask==-1)borderFallbacks++;
        if(mask==0)zeroMasks++;
        if(verifyFull) {
            double complete=GrandNetherCaveNetwork.strength(wx,wy,wz);
            require(raw(complete)==raw(current),
                    "Original unmasked 3D Nether cave graph changed at "
                    +x+","+y+","+z+" mask="+mask);
            rawFullChecks++;
        }
    }

    public static void main(String[] args)throws Exception {
        LOCAL.remove();
        Random random=new Random(0x36261024L);
        final double[] displacements={-1.0,-0.75,-0.25,0.0,0.37,0.75,1.0};
        // Same real source-mask path as Minecraft final density, including
        // negative/far X/Z, source region borders, and all cave heights.
        for(int n=0;n<950;n++) {
            int x,z;
            if(n%9==0) {
                x=(random.nextInt(31)-15)*256+(n%2==0?2:254);
                z=random.nextInt(20001)-10000;
            } else if(n%9==1) {
                x=random.nextInt(20001)-10000;
                z=(random.nextInt(31)-15)*256+(n%2==0?3:255);
            } else {
                x=random.nextInt(16001)-8000;
                z=random.nextInt(16001)-8000;
            }
            int y=144+random.nextInt(297);
            int mask=GrandNetherCaveNetwork.componentMaskForColumnAtHeight(
                    x,y,z);
            sample(x,y,z,mask,displacements[n%displacements.length],true);
        }

        // Hall-rich samples exercise real carved voxels and the actual
        // horizontal/vertical 19-bit mask, not just empty random terrain.
        for(int gx=-4;gx<=4;gx++)for(int gz=-4;gz<=4;gz++)
            for(int layer=0;layer<2;layer++){
                var h=GrandNetherCaveNetwork.hall(gx,gz,layer);
                int x=(int)h.x(),z=(int)h.z(),center=(int)h.y();
                for(int step=-5;step<=5;step++){
                    int y=center+step*18;
                    for(double fraction:displacements) {
                        int mask=GrandNetherCaveNetwork.componentMaskForColumnAtHeight(
                                x,y,z);
                        sample(x,y,z,mask,fraction,true);
                    }
                }
            }

        // Force many authentic tube() calls at unreachable Y: an ALL-bits
        // mask is a valid superset of the original cached X/Z candidates.
        // The geometry tester itself determines whether each tube carves.
        for(int gx=-2;gx<=2;gx++)for(int gz=-2;gz<=2;gz++) {
            var hall=GrandNetherCaveNetwork.hall(gx,gz,0);
            int x=(int)hall.x(),z=(int)hall.z();
            for(int y=144;y<=440;y+=14)
                sample(x,y,z,0x7ffff,0.0,true);
        }

        require(checked>13000&&rawFullChecks>13000
                    &&positive>1000&&oldTubeCalls>eligibleTubeCalls+1000
                    &&avoidedLayers>1000&&skippedCorridorGroups>1000
                    &&skippedSatelliteGroups>1000&&borderFallbacks>50
                    &&zeroMasks>200,
                "Insufficient original-evaluator strength parity or actual"
                +" avoided tube calls: checked="+checked
                +" truePositive="+positive
                +" oldTubeCalls="+oldTubeCalls
                +" guardedTubeCalls="+eligibleTubeCalls
                +" borderFallbacks="+borderFallbacks
                +" zeroMasks="+zeroMasks);
        System.out.println("NETHER_TEST36_EVALUATOR_CALL_PRUNE_PASS"
                +" strictFrozenRawDouble="+checked
                +" fullUnmaskedRawDouble="+rawFullChecks
                +" positiveCaveSamples="+positive
                +" oldTubeCalls="+oldTubeCalls
                +" newYGatedTubeCalls="+eligibleTubeCalls
                +" tubeCallsAvoided="+(oldTubeCalls-eligibleTubeCalls)
                +" emptyNineBitLayers="+avoidedLayers
                +" emptyCorridorGroups="+skippedCorridorGroups
                +" emptyGalleryGroups="+skippedSatelliteGroups
                +" crossingFallbacks="+borderFallbacks
                +" callCountsOnlyNotChunkSpeed=true");
    }
}

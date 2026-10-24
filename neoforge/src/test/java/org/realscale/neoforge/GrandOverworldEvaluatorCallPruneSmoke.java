package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Strict Test.36-vs-Test.37 Overworld 47-bit evaluator regression.
 * Replays the original strengthFromColumnWithMask loops independently,
 * invoking the untouched hallStrength and cachedTunnelStrength methods.
 * The old actual tube calls and newly Y-eligible calls are counted.
 * Counts are workload-specific; they are NOT measured Minecraft chunk/s.
 */
public final class GrandOverworldEvaluatorCallPruneSmoke {
    private GrandOverworldEvaluatorCallPruneSmoke() {}
    private static final Class<?> NET=GrandOverworldCaveNetwork.class;
    private static final ThreadLocal<?> LOCAL;
    private static final Method SELECT,HALL,TUBE;
    private static final Field MIN_Y,MAX_Y;
    private static long strict,originalFull,realCarved,oldTubeCalls,
            newEligibleTubeCalls,emptyLayers,emptyCorridorGroups,
            emptyGalleryGroups,borderFallbacks,emptyMasks;

    static {
        try {
            Field l=NET.getDeclaredField("LOCAL");l.setAccessible(true);
            LOCAL=(ThreadLocal<?>)l.get(null);
            Object cache=LOCAL.get();
            SELECT=cache.getClass().getDeclaredMethod("get",int.class,int.class);
            SELECT.setAccessible(true);
            Class<?> geometry=Class.forName(NET.getName()+"$TunnelGeometry");
            HALL=NET.getDeclaredMethod("hallStrength",double.class,
                    double.class,double.class,GrandOverworldCaveNetwork.Hall.class);
            TUBE=NET.getDeclaredMethod("cachedTunnelStrength",double.class,
                    double.class,double.class,geometry);
            HALL.setAccessible(true);TUBE.setAccessible(true);
            MIN_Y=geometry.getDeclaredField("minY");
            MAX_Y=geometry.getDeclaredField("maxY");
            MIN_Y.setAccessible(true);MAX_Y.setAccessible(true);
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    private static Field field(Class<?> type,String name)throws Exception {
        Field f=type.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Object get(Object obj,String name)throws Exception {
        return field(obj.getClass(),name).get(obj);
    }
    private static void require(boolean valid,String message) {
        if(!valid)throw new AssertionError(message);
    }
    private static long raw(double v){return Double.doubleToRawLongBits(v);}
    private static double originalHall(double x,double y,double z,
            GrandOverworldCaveNetwork.Hall hall)throws Exception {
        return (double)HALL.invoke(null,x,y,z,hall);
    }
    private static double originalTube(double x,double y,double z,
            Object tube)throws Exception {
        oldTubeCalls++;
        if(!(y<MIN_Y.getDouble(tube)||y>MAX_Y.getDouble(tube)))
            newEligibleTubeCalls++;
        return (double)TUBE.invoke(null,x,y,z,tube);
    }
    /**
     * Exact pre-Test.37 47-bit component traversal. Only this independent
     * test implements the old behavior; no permanent duplicated production
     * math and no test-only counter in the hot production code.
     */
    private static double frozen(int sourceX,int sourceZ,
            double x,double y,double z,long mask)throws Exception {
        int cell=GrandOverworldCaveNetwork.CELL;
        if(y<GrandOverworldCaveNetwork.MIN_Y
                ||y>GrandOverworldCaveNetwork.MAX_GEOMETRY_Y)return 0;
        if(x<sourceX-6.0||x>sourceX+6.0
                ||z<sourceZ-5.0||z>sourceZ+5.0
                ||Math.floorDiv(sourceX,cell)!=(int)Math.floor(x/cell)
                ||Math.floorDiv(sourceZ,cell)!=(int)Math.floor(z/cell))
            return GrandOverworldCaveNetwork.strength(x,y,z);
        if(mask==-1L)return GrandOverworldCaveNetwork.strength(x,y,z);
        if(mask==0L)return 0;
        Object cache=SELECT.invoke(LOCAL.get(),
                Math.floorDiv(sourceX,cell),Math.floorDiv(sourceZ,cell));
        var centers=(GrandOverworldCaveNetwork.Hall[][])get(cache,"halls");
        var galleries=(GrandOverworldCaveNetwork.Hall[][])get(cache,"galleries");
        var corridors=(Object[][])get(cache,"corridorTubes");
        var sides=(Object[][])get(cache,"sideTubes");
        var shafts=(Object[])get(cache,"shaftTubes");
        var arteries=(Object[][][])get(cache,"arteryTubes");
        var arteryA=(GrandOverworldCaveNetwork.Hall[][][])get(cache,"arteryA");
        double value=0;
        for(int layer=0;layer<GrandOverworldCaveNetwork.LAYERS;layer++){
            GrandOverworldCaveNetwork.Hall center=centers[layer][0];
            if(Math.abs(y-center.y())>235 && layer<3)continue;
            if(Math.abs(y-center.y())>350 && layer==3)continue;
            int shift=layer*9;
            if((mask&(0x1FFL<<shift))==0L)emptyLayers++;
            if((mask&(1L<<shift))!=0)
                value=Math.max(value,originalHall(x,y,z,center));
            if(((mask>>>(shift+1))&0xFL)==0L)emptyCorridorGroups++;
            for(int direction=0;direction<4;direction++){
                if((mask&(1L<<(shift+1+direction)))!=0)
                    value=Math.max(value,originalTube(x,y,z,corridors[layer][direction]));
            }
            if(((mask>>>(shift+5))&0xFL)==0L)emptyGalleryGroups++;
            if(Math.abs(x-center.x())<225&&Math.abs(z-center.z())<225&&
                    Math.abs(y-center.y())<150){
                for(int g=0;g<2;g++){
                    if((mask&(1L<<(shift+5+g*2)))!=0)
                        value=Math.max(value,originalHall(x,y,z,galleries[layer][g]));
                    if((mask&(1L<<(shift+6+g*2)))!=0)
                        value=Math.max(value,originalTube(x,y,z,sides[layer][g]));
                }
            }
        }
        for(int layer=0;layer<GrandOverworldCaveNetwork.LAYERS-1;layer++){
            if((mask&(1L<<(36+layer)))!=0)
                value=Math.max(value,originalTube(x,y,z,shafts[layer]));
        }
        for(int tier=0;tier<2;tier++)for(int axis=0;axis<2;axis++)
            for(int group=0;group<2;group++){
                int shift=39+tier*4+axis*2+group;
                if((mask&(1L<<shift))==0)continue;
                GrandOverworldCaveNetwork.Hall a=arteryA[tier][axis][group];
                if(a!=null)
                    value=Math.max(value,originalTube(
                            x,y,z,arteries[tier][axis][group]));
            }
        return value;
    }

    private static void sample(int x,int y,int z,long mask,double offset,
            boolean verifyFull)throws Exception {
        double wx=x+offset*6.0,wy=y+offset*2.0,
                wz=z-offset*5.0;
        double old=frozen(x,z,wx,wy,wz,mask);
        double candidate=GrandOverworldCaveNetwork.strengthFromColumnWithMask(
                x,z,wx,wy,wz,mask);
        require(raw(old)==raw(candidate),
                "Frozen 47-bit evaluator differs at source "
                +x+","+y+","+z+" mask="+mask+" offset="+offset
                +" old="+old+" new="+candidate);
        strict++;
        if(candidate>0)realCarved++;
        if(mask==-1L)borderFallbacks++;
        if(mask==0L)emptyMasks++;
        if(verifyFull) {
            double full=GrandOverworldCaveNetwork.strength(wx,wy,wz);
            require(raw(full)==raw(candidate),
                    "Optimized evaluator changed complete unmasked 3D strength"
                    +" at "+x+","+y+","+z+" mask="+mask);
            originalFull++;
        }
    }

    public static void main(String[] args)throws Exception {
        LOCAL.remove();
        Random random=new Random(0x37261024L);
        double[] offsets={-1.0,-0.71,-0.25,0.0,0.41,0.82,1.0};
        for(int i=0;i<1100;i++){
            int x,z;
            if(i%11==0){
                x=(random.nextInt(41)-20)*768+(i%2==0?3:766);
                z=random.nextInt(20001)-10000;
            }else if(i%11==1){
                x=random.nextInt(20001)-10000;
                z=(random.nextInt(41)-20)*768+(i%2==0?4:765);
            }else{
                x=random.nextInt(22001)-11000;
                z=random.nextInt(22001)-11000;
            }
            int y=GrandOverworldCaveNetwork.MIN_Y+
                    random.nextInt(GrandOverworldCaveNetwork.MAX_GEOMETRY_Y-
                            GrandOverworldCaveNetwork.MIN_Y+1);
            long mask=GrandOverworldCaveNetwork.componentMaskForColumnAtHeight(
                    x,y,z);
            sample(x,y,z,mask,offsets[i%offsets.length],true);
        }
        // All four strata, actual halls, offset heights, and warped samples.
        for(int gx=-4;gx<=4;gx++)for(int gz=-4;gz<=4;gz++)
            for(int layer=0;layer<GrandOverworldCaveNetwork.LAYERS;layer++){
                var hall=GrandOverworldCaveNetwork.hall(gx,gz,layer);
                int x=(int)hall.x(),z=(int)hall.z();
                for(int t=-5;t<=5;t++) {
                    int y=(int)hall.y()+t*36;
                    if(y<GrandOverworldCaveNetwork.MIN_Y
                            ||y>GrandOverworldCaveNetwork.MAX_GEOMETRY_Y)continue;
                    long mask=GrandOverworldCaveNetwork.componentMaskForColumnAtHeight(
                            x,y,z);
                    for(double o:offsets)sample(x,y,z,mask,o,true);
                }
            }
        // The all-component mask is deliberately wider than normal
        // production XYZ masks. It proves the early per-tube Y guard
        // skips genuine disjoint-Y tube calls while keeping ALL output
        // unchanged from the full original cave graph.
        for(int gx=-2;gx<=2;gx++)for(int gz=-2;gz<=2;gz++) {
            var hall=GrandOverworldCaveNetwork.hall(gx,gz,1);
            int x=(int)hall.x(),z=(int)hall.z();
            for(int y=-40;y<=1700;y+=80)
                sample(x,y,z,(1L<<47)-1L,0.0,true);
        }
        require(strict>20000&&originalFull>20000&&realCarved>500
                        &&oldTubeCalls>newEligibleTubeCalls+500
                        &&emptyLayers>500&&emptyCorridorGroups>500
                        &&emptyGalleryGroups>500&&borderFallbacks>25
                        &&emptyMasks>200,
                "Missing giant-cave true-positive, border or independent"
                +" evaluator call coverage: checked="+strict
                +" carved="+realCarved+" oldTubeCalls="+oldTubeCalls
                +" eligibleTubeCalls="+newEligibleTubeCalls
                +" borders="+borderFallbacks+" zero="+emptyMasks);
        System.out.println("OVERWORLD_TEST37_EVALUATOR_CALL_PRUNE_PASS"
                +" frozenRawParity="+strict
                +" originalUnmaskedRawParity="+originalFull
                +" carvedFullGraphSamples="+realCarved
                +" oldTubeCalls="+oldTubeCalls
                +" newYGatedTubeCalls="+newEligibleTubeCalls
                +" tubeCallsAvoided="+(oldTubeCalls-newEligibleTubeCalls)
                +" emptyNineComponentLayers="+emptyLayers
                +" emptyCorridorGroups="+emptyCorridorGroups
                +" emptyGalleryGroups="+emptyGalleryGroups
                +" borderFallbackSamples="+borderFallbacks
                +" callCountsOnlyNotChunkSpeed=true");
    }
}

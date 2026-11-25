package org.realscale.neoforge;

/** Determinism, zero-change protected biomes and nontrivial cave coverage. */
public final class CaveBiomeRegionMathSmoke {
    public static void main(String[] args){
        String[] protectedIDs={"deep_dark","ocean","deep_ocean","warm_ocean",
                "frozen_ocean","river","frozen_river","mushroom_fields"};
        for(String id:protectedIDs)for(int x=-110;x<=110;x+=11)
            for(int y=-16;y<=16;y+=4)
                if(CaveBiomeRegionMath.select(x,y,-x,id)!=
                        CaveBiomeRegionMath.Region.NONE)
                    throw new AssertionError("Protected "+id);
        for(int x=-1000;x<1000;x+=17)for(int y=19;y<120;y+=13)
            if(CaveBiomeRegionMath.select(x,y,-x,"dripstone_caves")!=
                    CaveBiomeRegionMath.Region.NONE)
                throw new AssertionError("Exposed upper biome replaced");
        boolean[] covered=new boolean[CaveBiomeRegionMath.Region.values().length];
        for(int x=-600;x<=600;x+=5)for(int z=-600;z<=600;z+=7)
            for(String id:new String[]{"lush_caves","dripstone_caves","snowy_plains",
                "badlands","jungle","plains","desert","stony_peaks"}){
                var r=CaveBiomeRegionMath.select(x,-4,z,id);
                covered[r.ordinal()]=true;
                if(r!=CaveBiomeRegionMath.select(x,-4,z,id))
                    throw new AssertionError("Non-deterministic "+id);
            }
        for(int i=0;i<covered.length;i++)if(!covered[i])
            throw new AssertionError("Missing biome region "+i);
        for(int dx=-100;dx<=100;dx+=19)for(int z=-100;z<=100;z+=23) {
            var r=CaveBiomeRegionMath.select(dx,-4,z,"plains");
            if(r!=CaveBiomeRegionMath.select(dx,-4,z,"plains"))
                throw new AssertionError("cache/order dependence");
        }
        System.out.println("REALSCALE_REAL_CAVE_BIOMES_SMOKE_PASS regions="+covered.length);
    }
}

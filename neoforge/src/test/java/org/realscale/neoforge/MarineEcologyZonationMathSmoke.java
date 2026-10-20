package org.realscale.neoforge;

/** Pure-Java marine stratification tests, no Minecraft library dependency. */
public final class MarineEcologyZonationMathSmoke {
    private static void eq(Object actual,Object expected,String why){
        if(!actual.equals(expected))throw new AssertionError(why+": "+actual+" != "+expected);
    }
    private static void yes(boolean good,String why){if(!good)throw new AssertionError(why);}
    private static void zone(String biome,int depth,boolean rock,double patch,
                             MarineEcologyZonationMath.Habitat expected){
        eq(MarineEcologyZonationMath.classify(biome,depth,rock,patch),expected,
           biome+" at depth "+depth);
    }
    public static void main(String[] args){
        var H=MarineEcologyZonationMath.Habitat.values();
        yes(H.length==12,"Expected original eight strata plus three geological habitats and none");
        zone("minecraft:warm_ocean",10,true,.9,H[0]);
        zone("minecraft:lukewarm_ocean",12,false,.75,H[1]);
        zone("minecraft:ocean",10,true,.9,H[2]);
        zone("minecraft:deep_cold_ocean",12,true,.9,H[2]);
        zone("minecraft:ocean",8,false,.8,H[3]);
        zone("minecraft:cold_ocean",25,true,.8,H[4]);
        zone("minecraft:deep_cold_ocean",48,true,.8,H[5]);
        zone("minecraft:deep_lukewarm_ocean",75,true,.8,H[5]);
        zone("minecraft:deep_frozen_ocean",14,true,.8,H[6]);
        zone("minecraft:deep_frozen_ocean",72,false,.8,H[7]);
        zone("minecraft:deep_ocean",78,true,.8,H[5]);
        zone("minecraft:deep_ocean",88,false,.8,H[7]);
        zone("minecraft:river",15,true,.8,MarineEcologyZonationMath.Habitat.NONE);
        zone("realscale:end_crystal_barrens",15,true,.8,H[8]);
        zone("minecraft:deep_cold_ocean",3,true,.8,H[8]);
        zone("minecraft:deep_cold_ocean",161,true,.8,H[8]);
        zone("minecraft:deep_cold_ocean",70,true,0,H[7]);
        zone("minecraft:warm_ocean",40,true,.9,H[5]);
        // Seaweed photosynthesis cannot go into a dark deep zone.
        for(String biome:new String[]{"minecraft:ocean","minecraft:cold_ocean",
                                      "minecraft:warm_ocean","minecraft:frozen_ocean"}){
            for(int depth=35;depth<=150;depth+=7){
                var h=MarineEcologyZonationMath.classify(biome,depth,true,.99);
                yes(h!=H[0] && h!=H[1] && h!=H[2] && h!=H[3],
                    "Photosynthetic organisms in deep dark water");
            }
        }
        for(int z=-300;z<=300;z+=17)for(int x=-300;x<=300;x+=29){
            double a=MarineEcologyZonationMath.patch(x,z,785312L);
            double b=MarineEcologyZonationMath.patch(x,z,785312L);
            yes(a==b,"Seed-stable ocean habitat patch not deterministic");
            yes(a>=0&&a<=1&&Double.isFinite(a),"Invalid patch probability");
            yes(Math.abs(a-MarineEcologyZonationMath.patch(x+1,z,785312L))<.035,
                "Chunk seam in ecological habitat patch");
        }
        yes(MarineEcologyZonationMath.patch(100,200,42)!=
            MarineEcologyZonationMath.patch(100,200,785312L),
            "Different seeds must not produce identical patches");
        System.out.println("MARINE_ECOLOGY_LAYERS_PASS: 11 visual strata, cold reefs, seamounts, seeps and seed-safe groves");
    }
}

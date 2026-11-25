package org.realscale.neoforge;

/**
 * Chunk-order-independent cave biome region selection.
 *
 * Cave biome source cannot read carved air/stone, geologic material, active
 * aquifers or a real cave roof. This class therefore chooses possible
 * UNDERGROUND biome regions only; decoration still requires actual cave air,
 * compatible natural substrate, and existing worldgen features.
 *
 * No arrays, world reads, RNG state, noise access or allocations per lookup.
 */
public final class CaveBiomeRegionMath {
    private CaveBiomeRegionMath(){}
    public enum Region { NONE, DAMP_LIMESTONE, DEEP_FUNGAL, DRY_KARST,
        UNDERGROUND_SPRINGS, VOLCANIC, GLACIAL, GLOW_GROTTO, FOSSIL_CAVES }
    private static long avalanche(long x) {
        x^=x>>>30;x*=0xbf58476d1ce4e5b9L;
        x^=x>>>27;x*=0x94d049bb133111ebL;
        return x^(x>>>31);
    }
    /**
     * @param quartX coordinate in 4-block biome sampling units
     * @param quartY coordinate in 4-block biome sampling units
     * @param quartZ coordinate in 4-block biome sampling units
     * @param original Vanilla biome PATH, not ID (ocean/river/deep_dark held)
     */
    public static Region select(int quartX,int quartY,int quartZ,String original){
        if(original==null||original.isEmpty())return Region.NONE;
        int y=quartY*4;
        // Below World Surface near normal sea levels only. Stronger highland
        // caves are already decorated by the independent two-band features.
        // Do not alter ancient cities' Deep Dark or any ocean or river biome.
        if(y>64||y < -60 || original.equals("deep_dark")
                ||original.endsWith("ocean")||original.contains("river")
                ||original.equals("mushroom_fields"))return Region.NONE;

        boolean cave=original.equals("lush_caves")||original.equals("dripstone_caves");
        if(!cave && y>24)return Region.NONE;
        int cellX=Math.floorDiv(quartX,12),cellZ=Math.floorDiv(quartZ,12);
        int cellY=Math.floorDiv(quartY,8);
        long h=avalanche(((long)cellX*0x9e3779b97f4a7c15L)
                ^((long)cellZ*0xc2b2ae3d27d4eb4fL)
                ^((long)cellY*0x94d049bb133111ebL)
                ^0x647a1683ef22a1b7L);
        // Most input biome sites stay vanilla. Spatially grouped sites:
        // same 48x48x32 patch never depends on order or chunk boundary.
        int rarity=cave?4:9;
        if(Math.floorMod(h,rarity)!=0)return Region.NONE;
        int type=(int)Math.floorMod(h>>>15,11L);
        if(original.equals("lush_caves")){
            return type<3?Region.DAMP_LIMESTONE
                    :type<6?Region.DEEP_FUNGAL
                    :type<9?Region.UNDERGROUND_SPRINGS:Region.GLOW_GROTTO;
        }
        if(original.equals("dripstone_caves")){
            return type<4?Region.DRY_KARST
                    :type<7?Region.DAMP_LIMESTONE
                    :type<9?Region.VOLCANIC:Region.FOSSIL_CAVES;
        }
        boolean frozen=original.startsWith("snowy")
                ||original.contains("frozen")||original.equals("ice_spikes")
                ||original.equals("grove")||original.equals("jagged_peaks");
        if(frozen)return Region.GLACIAL;
        boolean arid=original.contains("badlands")||original.equals("desert")
                ||original.equals("stony_peaks")||original.startsWith("windswept");
        if(arid)return type<5?Region.VOLCANIC
                :type<9?Region.DRY_KARST:Region.FOSSIL_CAVES;
        boolean moist=original.contains("jungle")||original.contains("swamp")
                ||original.equals("dark_forest")||original.equals("mangrove_swamp");
        if(moist)return type<3?Region.DEEP_FUNGAL
                :type<6?Region.UNDERGROUND_SPRINGS
                :type<9?Region.DAMP_LIMESTONE:Region.GLOW_GROTTO;
        return type<4?Region.DAMP_LIMESTONE
                :type<7?Region.DRY_KARST
                :type<9?Region.DEEP_FUNGAL
                :type==9?Region.GLOW_GROTTO:Region.FOSSIL_CAVES;
    }
}

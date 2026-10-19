package org.realscale.neoforge;

/**
 * Earth-inspired marine benthic ecology zones in Minecraft depth units.
 * Depth here is gameplay geometry, NOT metres of real-ocean light penetration.
 * Warm surface water can be cold and non-photosynthetic at greater depth.
 *
 * Exactly one of these zones is chosen at a seafloor column; distinct seed-
 * stable 96-block patch probabilities avoid confetti decoration.
 */
final class MarineEcologyZonationMath {
    enum Habitat {
        TROPICAL_REEF, TROPICAL_SEAGRASS, TEMPERATE_KELP,
        TEMPERATE_SEAGRASS, COLD_SPONGE_GARDEN, COLD_CORAL_GARDEN,
        POLAR_BENTHIC, DEEP_SILTY_FLOOR, NONE
    }
    private MarineEcologyZonationMath() {}

    static boolean ocean(String biome) {
        if(biome==null)return false;
        String name=biome.startsWith("minecraft:") ? biome.substring(10):biome;
        return name.equals("ocean") || name.endsWith("_ocean")
                && (name.startsWith("warm_") || name.startsWith("lukewarm_")
                || name.startsWith("deep_") || name.startsWith("cold_")
                || name.startsWith("frozen_"));
    }
    private static boolean tropical(String name){
        return name.contains("warm_ocean") || name.contains("lukewarm_ocean");
    }
    private static boolean polar(String name){
        return name.contains("frozen_ocean");
    }
    private static boolean cold(String name){
        return name.contains("cold_ocean") || polar(name);
    }

    static Habitat classify(String biome,int depth,boolean hardBottom,double patch) {
        if(!ocean(biome) || depth<4 || depth>160)return Habitat.NONE;
        // The Minecraft low-light proxy is ~32+ blocks, not real-world metres.
        // Photosynthesis stops after 30 Minecraft blocks here.
        if(depth>=64){
            return hardBottom && patch>0.15
                    ? Habitat.COLD_CORAL_GARDEN : Habitat.DEEP_SILTY_FLOOR;
        }
        if(depth>=30) {
            return hardBottom && patch>0.30
                    ? Habitat.COLD_CORAL_GARDEN
                    : Habitat.DEEP_SILTY_FLOOR;
        }
        if(polar(biome)){
            return depth<=19 && patch>0.20
                    ? Habitat.POLAR_BENTHIC
                    : hardBottom ? Habitat.COLD_SPONGE_GARDEN : Habitat.DEEP_SILTY_FLOOR;
        }
        if(cold(biome)){
            if(depth<=23 && hardBottom && patch>0.32)return Habitat.TEMPERATE_KELP;
            if(hardBottom && patch>0.15)return Habitat.COLD_SPONGE_GARDEN;
            return depth<=16 ? Habitat.TEMPERATE_SEAGRASS:Habitat.DEEP_SILTY_FLOOR;
        }
        if(tropical(biome)){
            if(depth<=18 && hardBottom && patch>0.29)return Habitat.TROPICAL_REEF;
            return depth<=28 && !hardBottom
                    ? Habitat.TROPICAL_SEAGRASS : Habitat.DEEP_SILTY_FLOOR;
        }
        if(depth<=22 && hardBottom && patch>0.31)return Habitat.TEMPERATE_KELP;
        if(depth<=16 && !hardBottom)return Habitat.TEMPERATE_SEAGRASS;
        return hardBottom && patch>0.24
                ? Habitat.COLD_SPONGE_GARDEN : Habitat.DEEP_SILTY_FLOOR;
    }

    static long mix(long n) {
        n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;
        n=(n^(n>>>27))*0x94d049bb133111ebL;
        return n^(n>>>31);
    }
    static double unit(long n) { return (mix(n)>>>11)*0x1.0p-53; }
    static double smooth(double t) {
        t=Math.max(0,Math.min(1,t));
        return t*t*(3-2*t);
    }
    static double patch(int x,int z,long seed){
        int sx=Math.floorDiv(x,96),sz=Math.floorDiv(z,96);
        double fx=smooth(Math.floorMod(x,96)/96.0);
        double fz=smooth(Math.floorMod(z,96)/96.0);
        long key=mix(seed ^ 0x46e9db97c67ec361L);
        double a=unit(key ^ (long)sx*0x9e3779b97f4a7c15L ^ (long)sz*0x632be59bd9b4e019L);
        double b=unit(key ^ (long)(sx+1)*0x9e3779b97f4a7c15L ^ (long)sz*0x632be59bd9b4e019L);
        double c=unit(key ^ (long)sx*0x9e3779b97f4a7c15L ^ (long)(sz+1)*0x632be59bd9b4e019L);
        double d=unit(key ^ (long)(sx+1)*0x9e3779b97f4a7c15L ^ (long)(sz+1)*0x632be59bd9b4e019L);
        return (a+(b-a)*fx)*(1-fz)+(c+(d-c)*fx)*fz;
    }
}

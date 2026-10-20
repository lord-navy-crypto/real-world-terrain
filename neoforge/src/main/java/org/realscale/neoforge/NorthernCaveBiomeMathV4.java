package org.realscale.neoforge;

/** Deterministic pure-Java cave habitat classification for opt-in northern v4. */
public final class NorthernCaveBiomeMathV4 {
    private NorthernCaveBiomeMathV4(){}
    private static long mix(long h) {
        h=(h^(h>>>30))*0xbf58476d1ce4e5b9L;
        h=(h^(h>>>27))*0x94d049bb133111ebL;
        return h^(h>>>31);
    }
    public static long regionHash(int qx,int qz) {
        long x=Math.floorDiv(qx,80),z=Math.floorDiv(qz,80);
        return mix(x*0x9e3779b97f4a7c15L^z*0xc2b2ae3d27d4eb4fL^0x54ca4e7f18bdbac3L);
    }
    /** Null delegates original biome exactly, including Deep Dark and sea. */
    public static String select(String original,String surface,int quartY,long h) {
        if(quartY>9||quartY< -12||original==null||surface==null)return null;
        if(surface.contains("ocean")||surface.endsWith(":river")
                ||surface.endsWith(":frozen_river")
                ||surface.endsWith(":beach")||surface.endsWith(":stony_shore")
                ||surface.endsWith(":swamp")||surface.endsWith(":mangrove_swamp"))
            return null;
        if(original.equals("minecraft:deep_dark"))return null;
        int v=(int)Math.floorMod(h>>>17,8L);
        if(original.equals("minecraft:lush_caves")) {
            if(v<=1)return "realscale:underground_springs";
            if(v<=5)return "realscale:deep_fungal_caverns";
        }
        if(original.equals("minecraft:dripstone_caves")) {
            if(quartY>=-4 && v<4 && (surface.contains("snow")
                ||surface.contains("frozen")||surface.contains("ice")
                ||surface.contains("grove")))
                return "realscale:glacial_subsurface";
            if(v<3)return "realscale:damp_limestone_caverns";
            if(v<6)return "realscale:dry_karst_caves";
        }
        if(quartY<=4 && v==0 && (surface.equals("minecraft:badlands")
                ||surface.equals("minecraft:eroded_badlands")
                ||surface.equals("minecraft:stony_peaks")))
            return "realscale:volcanic_caverns";
        return null;
    }
}

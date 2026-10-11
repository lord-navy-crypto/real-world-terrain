package org.realscale.geo;

/**
 * Vanilla-only palette projection from hydrology/geology onto 1x1 metre columns.
 * Reuses one RegionalWatershed.Result for BOTH predicted structure height and
 * actual column blocks. This is engine-agnostic; no false claim of registration
 * as a NeoForge ChunkGenerator.
 *
 * Naming reflects Minecraft vanilla block IDs; never registers new blocks.
 */
public final class VanillaColumnAdapter {
    private VanillaColumnAdapter() {}
    public static final int MIN_Y=-64,MAX_Y=2031,SEA_Y=63;
    public static final String AIR="minecraft:air", WATER="minecraft:water";
    public record Column(int x,int z,int solidY,int waterY,boolean river,boolean pond,
                         String surface,String subsurface,String bedrock,
                         String sediment) {
        /** Minecraft-style first free height, or water top + 1 for WORLD_SURFACE. */
        public int worldSurfaceFirstFreeY(){return Math.max(solidY,waterY)+1;}
        public int oceanFloorFirstFreeY(){return solidY+1;}
        public String blockAt(int y) {
            if(y<MIN_Y||y>MAX_Y)return AIR;
            if(y>solidY)return y<=waterY?WATER:AIR;
            if(y==solidY)return surface;
            if(y>=solidY-2)return subsurface;
            if(y<=-16)return "minecraft:deepslate";
            if(y<=solidY-9 && y>=solidY-17)return bedrock;
            return bedrock;
        }
    }
    /**
     * 1 block = 1m; exactly the same column instance can be used to populate
     * a Minecraft Chunk and query getBaseColumn/getBaseHeight.
     */
    public static Column column(RegionalWatershed.Result snap,int x,int z) {
        return column(snap,null,x,z);
    }
    /**
     * Reintroduces true 1m sampling of the original v0.7 geological baseline.
     * Region-wide hydrology changes are interpolated as a correction, so the
     * original fine-scale relief is not lost on the 64m drainage grid.
     */
    public static Column column(RegionalWatershed.Result snap,GeoTerrainEngine geology,int x,int z) {
        double wx=x+.5,wz=z+.5;
        double elevation=snap.heightAt(wx,wz);
        if(geology!=null)
            elevation += geology.geologyBaselineAt(wx,wz)-snap.originalBaselineAt(wx,wz);
        int solid=(int)Math.floor(elevation);
        if(solid<MIN_Y||solid>MAX_Y)
            throw new IllegalStateException("No silent 1:1 height compression: surface Y="+solid);
        boolean river=snap.isChannelAt(wx,wz) && solid>SEA_Y+1;
        boolean pond=snap.isPondAt(wx,wz) && !snap.isOceanAt(wx,wz);
        double discharge=snap.dischargeAt(wx,wz);
        // Existing vanilla blocks only; geology is internal computational metadata.
        String stone=(solid>1250?"minecraft:andesite":(solid>680?"minecraft:stone": "minecraft:granite"));
        String surface=pond?"minecraft:clay":solid<SEA_Y-5?"minecraft:gravel":
            solid>1200?"minecraft:snow_block":solid>920?"minecraft:stone":
            river?"minecraft:gravel":solid>SEA_Y+18?"minecraft:grass_block":
            "minecraft:sand";
        String below=pond?"minecraft:clay":river?"minecraft:gravel":solid<=SEA_Y+8?"minecraft:sand":
            solid>1150?"minecraft:stone":"minecraft:dirt";
        int water=Integer.MIN_VALUE;
        if(snap.isOceanAt(wx,wz) && solid<SEA_Y) water=SEA_Y;
        else if(pond)water=Math.max(solid+1,(int)Math.floor(snap.spillLevelAt(wx,wz)));
        else if(river && discharge>0)water=solid+1;
        if(water>MAX_Y)throw new IllegalStateException("Lake exceeds true world height ceiling");
        return new Column(x,z,solid,water,river,pond,surface,below,stone,
            (river||pond)?"minecraft:gravel":"minecraft:sand");
    }
    public static int structureBaseHeight(RegionalWatershed.Result snap,GeoTerrainEngine geology,
                                           int x,int z,boolean oceanFloor){
        var c=column(snap,geology,x,z);
        return oceanFloor?c.oceanFloorFirstFreeY():c.worldSurfaceFirstFreeY();
    }
    public static int structureBaseHeight(RegionalWatershed.Result snap,int x,int z,boolean oceanFloor){
        var c=column(snap,x,z);
        return oceanFloor?c.oceanFloorFirstFreeY():c.worldSurfaceFirstFreeY();
    }
}

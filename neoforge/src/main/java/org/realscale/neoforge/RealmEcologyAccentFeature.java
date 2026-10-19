package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Large, coherent but bounded Nether and End habitat mosaics. Each chunk
 * contributes to a 64-block-wide, seed-stable landscape district; neighboring
 * chunks share a palette. No cave carving, ore replacement or foreign blocks.
 */
public final class RealmEcologyAccentFeature extends Feature<NoneFeatureConfiguration> {
    public enum Realm { NETHER, END }
    private final Realm realm;
    public RealmEcologyAccentFeature(Realm realm) {
        super(NoneFeatureConfiguration.CODEC);
        this.realm=realm;
    }
    private static boolean nativeFloor(BlockState s, Realm realm) {
        return realm==Realm.END ? s.is(Blocks.END_STONE)
                : s.is(Blocks.NETHERRACK)||s.is(Blocks.CRIMSON_NYLIUM)
                ||s.is(Blocks.WARPED_NYLIUM)||s.is(Blocks.SOUL_SAND)
                ||s.is(Blocks.SOUL_SOIL)||s.is(Blocks.BASALT)
                ||s.is(Blocks.BLACKSTONE);
    }
    private static int region(int x,int z,long seed){
        long hash=MarineEcologyZonationMath.mix(seed^0x6c8e9cf570932bd1L
                ^(long)Math.floorDiv(x,64)*0x9e3779b97f4a7c15L
                ^(long)Math.floorDiv(z,64)*0x632be59bd9b4e019L);
        return (int)Math.floorMod(hash,4L);
    }
    private static boolean endBiome(String b){
        return b.equals("minecraft:end_highlands")||b.equals("minecraft:end_midlands")
                ||b.startsWith("realscale:end_");
    }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if(!TVAWorldGuard.hasRealScaleGenerator(ctx))return false;
        WorldGenLevel w=ctx.level();
        if(realm==Realm.NETHER && !w.getLevel().dimension().equals(Level.NETHER))return false;
        if(realm==Realm.END && !w.getLevel().dimension().equals(Level.END))return false;
        ChunkPos chunk=new ChunkPos(ctx.origin());
        int placed=0;
        // A fixed upper bound on placement and queries, regardless of world height.
        for(int i=0;i<64 && placed<28;i++){
            int x=chunk.getMinBlockX()+ctx.random().nextInt(16);
            int z=chunk.getMinBlockZ()+ctx.random().nextInt(16);
            int y=realm==Realm.END
                    ?w.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z)
                    :ctx.origin().getY()+ctx.random().nextInt(41)-20;
            if(y<w.getMinBuildHeight()+3 || y>=w.getMaxBuildHeight()-5)continue;
            BlockPos p=new BlockPos(x,y,z);
            if(!w.getBlockState(p).isAir()||!w.getFluidState(p).isEmpty())continue;
            BlockPos ground=p.below();
            BlockState floor=w.getBlockState(ground);
            if(!nativeFloor(floor,realm))continue;
            String biome=w.getBiome(p).unwrapKey()
                    .map(k->k.location().toString()).orElse("");
            int region=region(x,z,w.getSeed());
            if(realm==Realm.END){
                if(!endBiome(biome) || !floor.is(Blocks.END_STONE))continue;
                if(region==0 || region==1){
                    // True plants, not a flower floating over end stone.
                    int height=region==0?2+ctx.random().nextInt(3):2;
                    if(p.getY()+height>=w.getMaxBuildHeight())continue;
                    boolean empty=true;
                    for(int j=0;j<height;j++)
                        if(!w.getBlockState(p.above(j)).isAir())empty=false;
                    if(!empty)continue;
                    for(int j=0;j<height-1;j++)
                        w.setBlock(p.above(j),Blocks.CHORUS_PLANT.defaultBlockState(),2);
                    w.setBlock(p.above(height-1),Blocks.CHORUS_FLOWER.defaultBlockState(),2);
                    placed++;
                }else{
                    // Pale crystal barrens vs fractured purpur plains: coherent
                    // surface palettes that follow the underlying End island.
                    BlockState accent=region==2
                            ?(ctx.random().nextBoolean()?Blocks.CALCITE:Blocks.AMETHYST_BLOCK).defaultBlockState()
                            :(ctx.random().nextBoolean()?Blocks.END_STONE_BRICKS:Blocks.PURPUR_BLOCK).defaultBlockState();
                    w.setBlock(ground,accent,2);
                    placed++;
                }
                continue;
            }
            BlockState accent=null;
            if(biome.equals("minecraft:crimson_forest") && floor.is(Blocks.CRIMSON_NYLIUM))
                accent=(region==0?Blocks.CRIMSON_FUNGUS:Blocks.CRIMSON_ROOTS).defaultBlockState();
            else if(biome.equals("minecraft:warped_forest") && floor.is(Blocks.WARPED_NYLIUM))
                accent=(region==0?Blocks.WARPED_FUNGUS:Blocks.WARPED_ROOTS).defaultBlockState();
            else if(biome.equals("minecraft:soul_sand_valley")
                    && (floor.is(Blocks.SOUL_SAND)||floor.is(Blocks.SOUL_SOIL))){
                // Barren soul valleys: scattered fossil-like pale/black rock,
                // never living overworld plants or extra ore.
                if(region==0 || region==2){
                    w.setBlock(ground,(region==0?Blocks.BONE_BLOCK
                            :Blocks.BASALT).defaultBlockState(),2);
                    placed++;
                }
                continue;
            }else if(biome.equals("minecraft:basalt_deltas")
                    && (floor.is(Blocks.BASALT)||floor.is(Blocks.BLACKSTONE))){
                w.setBlock(ground,(region==0?Blocks.SMOOTH_BASALT
                        :region==1?Blocks.TUFF:Blocks.BLACKSTONE).defaultBlockState(),2);
                placed++;
                continue;
            }else if(biome.equals("minecraft:nether_wastes") && floor.is(Blocks.NETHERRACK)){
                if(region==0){
                    w.setBlock(ground,Blocks.MAGMA_BLOCK.defaultBlockState(),2);
                    placed++;
                }else if(region==1){
                    w.setBlock(ground,Blocks.BLACKSTONE.defaultBlockState(),2);
                    placed++;
                }
                continue;
            }
            if(accent!=null && accent.canSurvive(w,p)){
                w.setBlock(p,accent,2);
                placed++;
            }
        }
        return placed>0;
    }
}

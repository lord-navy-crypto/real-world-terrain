package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Bounded vanilla-block ecological accents; NO terrain carving or new biome IDs. */
public final class RealmEcologyAccentFeature extends Feature<NoneFeatureConfiguration> {
    public enum Realm { NETHER, END }
    private final Realm realm;
    public RealmEcologyAccentFeature(Realm realm) {
        super(NoneFeatureConfiguration.CODEC);
        this.realm=realm;
    }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if(!TVAWorldGuard.hasRealScaleGenerator(ctx))return false;
        WorldGenLevel w=ctx.level();
        if(realm==Realm.NETHER && !w.getLevel().dimension().equals(Level.NETHER))return false;
        if(realm==Realm.END && !w.getLevel().dimension().equals(Level.END))return false;
        ChunkPos chunk=new ChunkPos(ctx.origin());
        int count=0;
        for(int i=0;i<10 && count<4;i++){
            int x=chunk.getMinBlockX()+ctx.random().nextInt(16);
            int z=chunk.getMinBlockZ()+ctx.random().nextInt(16);
            int y=realm==Realm.END
                    ? w.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z)
                    : ctx.origin().getY()+ctx.random().nextInt(17)-8;
            if(y<w.getMinBuildHeight()+3 || y>=w.getMaxBuildHeight()-3)continue;
            BlockPos p=new BlockPos(x,y,z);
            if(!w.getBlockState(p).isAir() || !w.getFluidState(p).isEmpty())continue;
            BlockState floor=w.getBlockState(p.below());
            String biome=w.getBiome(p).unwrapKey().map(k->k.location().toString()).orElse("");
            BlockState accent=null;
            long zone=MarineEcologyZonationMath.mix(
                    ((long)Math.floorDiv(x,64)*0x9e3779b97f4a7c15L)
                    ^((long)Math.floorDiv(z,64)*0x632be59bd9b4e019L)
                    ^w.getSeed());
            int variant=(int)Math.floorMod(zone,4);
            if(realm==Realm.NETHER){
                if(biome.equals("minecraft:crimson_forest") && floor.is(Blocks.CRIMSON_NYLIUM))
                    accent=variant==0?Blocks.CRIMSON_FUNGUS.defaultBlockState()
                            :Blocks.CRIMSON_ROOTS.defaultBlockState();
                else if(biome.equals("minecraft:warped_forest") && floor.is(Blocks.WARPED_NYLIUM))
                    accent=variant==0?Blocks.WARPED_FUNGUS.defaultBlockState()
                            :Blocks.WARPED_ROOTS.defaultBlockState();
                else if(biome.equals("minecraft:soul_sand_valley")
                        && (floor.is(Blocks.SOUL_SOIL)||floor.is(Blocks.SOUL_SAND))){
                    // Rare fossil-like basalt/blackstone fragments on existing ground.
                    accent=variant==0?Blocks.BASALT.defaultBlockState()
                            :variant==1?Blocks.BLACKSTONE.defaultBlockState():null;
                }else if(biome.equals("minecraft:basalt_deltas")
                        && (floor.is(Blocks.BASALT)||floor.is(Blocks.BLACKSTONE))
                        && variant<=1){
                    accent=variant==0?Blocks.SMOOTH_BASALT.defaultBlockState()
                            :Blocks.BLACKSTONE.defaultBlockState();
                }else if(biome.equals("minecraft:nether_wastes")
                        && floor.is(Blocks.NETHERRACK) && variant==0){
                    accent=Blocks.NETHER_GOLD_ORE.defaultBlockState();
                    // This would alter ore economy: DO NOT place it.
                    accent=null;
                }
            }else if((biome.equals("minecraft:end_highlands")
                        ||biome.equals("minecraft:end_midlands")
                        ||biome.startsWith("realscale:end_"))
                    && floor.is(Blocks.END_STONE)){
                // A flower alone cannot grow on End Stone. Instead place
                // a short rooted chorus stem with a supported flower.
                if(variant<=1 && ctx.random().nextInt(3)==0
                        && w.getBlockState(p.above()).isAir()
                        && w.getBlockState(p.above(2)).isAir()){
                    w.setBlock(p,Blocks.CHORUS_PLANT.defaultBlockState(),2);
                    w.setBlock(p.above(),Blocks.CHORUS_PLANT.defaultBlockState(),2);
                    w.setBlock(p.above(2),Blocks.CHORUS_FLOWER.defaultBlockState(),2);
                    count++;
                    continue;
                }
                if(variant==2)accent=Blocks.AMETHYST_BLOCK.defaultBlockState();
                if(variant==3)accent=Blocks.PURPUR_BLOCK.defaultBlockState();
            }
            if(accent==null || !accent.canSurvive(w,p))continue;
            w.setBlock(p,accent,2);
            count++;
        }
        return count>0;
    }
}

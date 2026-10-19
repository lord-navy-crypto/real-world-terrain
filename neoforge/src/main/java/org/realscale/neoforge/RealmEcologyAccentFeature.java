package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
            int y=ctx.origin().getY()+ctx.random().nextInt(17)-8;
            if(y<w.getMinBuildHeight()+3 || y>=w.getMaxBuildHeight()-3)continue;
            BlockPos p=new BlockPos(x,y,z);
            if(!w.getBlockState(p).isAir() || !w.getFluidState(p).isEmpty())continue;
            BlockState floor=w.getBlockState(p.below());
            String biome=w.getBiome(p).unwrapKey().map(k->k.location().toString()).orElse("");
            BlockState accent=null;
            if(realm==Realm.NETHER){
                if(biome.equals("minecraft:crimson_forest") && floor.is(Blocks.CRIMSON_NYLIUM))
                    accent=ctx.random().nextBoolean()?Blocks.CRIMSON_ROOTS.defaultBlockState():
                            Blocks.CRIMSON_FUNGUS.defaultBlockState();
                else if(biome.equals("minecraft:warped_forest") && floor.is(Blocks.WARPED_NYLIUM))
                    accent=ctx.random().nextBoolean()?Blocks.WARPED_ROOTS.defaultBlockState():
                            Blocks.WARPED_FUNGUS.defaultBlockState();
                else if(biome.equals("minecraft:soul_sand_valley")
                        && (floor.is(Blocks.SOUL_SOIL)||floor.is(Blocks.SOUL_SAND)))
                    accent=Blocks.DEAD_BUSH.defaultBlockState();
            }else if((biome.equals("minecraft:end_highlands")
                        ||biome.equals("minecraft:end_midlands")
                        ||biome.startsWith("realscale:end_"))
                    && floor.is(Blocks.END_STONE) && ctx.random().nextInt(4)==0){
                accent=Blocks.CHORUS_FLOWER.defaultBlockState();
                // Flower requires a valid supporting chorus stem; don't place floating plants.
                if(!accent.canSurvive(w,p))accent=null;
            }
            if(accent==null || !accent.canSurvive(w,p))continue;
            w.setBlock(p,accent,2);
            count++;
        }
        return count>0;
    }
}

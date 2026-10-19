package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Very light geological accents on EXISTING natural cave surfaces.
 * Never carves rock, adds ore, moves liquids, replaces ore or touches
 * arbitrary blocks. All writes stay within the feature's origin chunk.
 */
public final class CaveGeologyAccentFeature extends Feature<NoneFeatureConfiguration> {
    public CaveGeologyAccentFeature() { super(NoneFeatureConfiguration.CODEC); }

    private static boolean replaceable(BlockState s) {
        return s.is(Blocks.STONE) || s.is(Blocks.DEEPSLATE)
                || s.is(Blocks.TUFF) || s.is(Blocks.ANDESITE)
                || s.is(Blocks.DIORITE) || s.is(Blocks.GRANITE)
                || s.is(Blocks.CALCITE);
    }
    private static boolean dry(WorldGenLevel world,BlockPos p) {
        return world.getBlockState(p).isAir() && world.getFluidState(p).isEmpty();
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if(!TVAWorldGuard.hasRealScaleGenerator(context))return false;
        WorldGenLevel w=context.level();
        if(!w.getLevel().dimension().location().toString().equals("minecraft:overworld"))
            return false;
        ChunkPos chunk=new ChunkPos(context.origin());
        int changed=0;
        // Bounded probes only; no tunnel excavation and no expanding searches.
        for(int attempt=0;attempt<8;attempt++) {
            int x=chunk.getMinBlockX()+context.random().nextInt(16);
            int z=chunk.getMinBlockZ()+context.random().nextInt(16);
            int y=context.origin().getY()+context.random().nextInt(25)-12;
            if(y<w.getMinBuildHeight()+5 || y>w.getMaxBuildHeight()-6)continue;
            BlockPos air=new BlockPos(x,y,z);
            int surface=w.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z);
            if(y>surface-16 || !dry(w,air) || !dry(w,air.above()))continue;
            String biome=w.getBiome(air).unwrapKey()
                    .map(k->k.location().toString()).orElse("");
            boolean karst=biome.equals("minecraft:dripstone_caves")
                    ||biome.equals("realscale:dry_karst_caves")
                    ||biome.equals("realscale:damp_limestone_caverns");
            boolean fungal=biome.equals("minecraft:lush_caves")
                    ||biome.equals("realscale:deep_fungal_caverns");
            boolean deep=biome.equals("minecraft:deep_dark");
            if(!karst && !fungal && !deep)continue;
            // Exposed floor/wall geology; only ordinary rock is recolored.
            // Small dry mineral crusts on existing karst cave ceilings.
            boolean ceiling=karst && context.random().nextInt(4)==0;
            BlockPos rock=ceiling?air.above(2):air.below();
            BlockState previous=w.getBlockState(rock);
            if(!replaceable(previous))continue;
            if(ceiling && !dry(w,rock.below()))continue;
            BlockState accent;
            if(karst)accent=ceiling
                    ? Blocks.CALCITE.defaultBlockState()
                    : context.random().nextBoolean()
                    ? Blocks.CALCITE.defaultBlockState()
                    : Blocks.DRIPSTONE_BLOCK.defaultBlockState();
            else if(fungal)accent=context.random().nextBoolean()
                    ? Blocks.MOSS_BLOCK.defaultBlockState()
                    : Blocks.CLAY.defaultBlockState();
            else accent=context.random().nextBoolean()
                    ? Blocks.TUFF.defaultBlockState()
                    : Blocks.DEEPSLATE.defaultBlockState();
            if(previous.equals(accent))continue;
            w.setBlock(rock,accent,2);
            changed++;
            if(changed>=3)break;
        }
        return changed>0;
    }
}

package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** V4-only cave floor palette, never carving cavities or replacing liquids/ores. */
public final class NorthernCaveHabitatFeatureV4 extends Feature<NoneFeatureConfiguration> {
    public NorthernCaveHabitatFeatureV4(){super(NoneFeatureConfiguration.CODEC);}
    private static boolean rock(BlockState s) {
        return s.is(Blocks.STONE)||s.is(Blocks.DEEPSLATE)||s.is(Blocks.TUFF)
            ||s.is(Blocks.ANDESITE)||s.is(Blocks.DIORITE)
            ||s.is(Blocks.GRANITE)||s.is(Blocks.CALCITE);
    }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if(!TVAWorldGuard.hasNorthernFjordV4(ctx))return false;
        WorldGenLevel w=ctx.level();
        if(!"minecraft:overworld".equals(w.getLevel().dimension().location().toString()))
            return false;
        int x0=Math.floorDiv(ctx.origin().getX(),16)*16;
        int z0=Math.floorDiv(ctx.origin().getZ(),16)*16;
        int changed=0;
        for(int n=0;n<16&&changed<4;n++) {
            int x=x0+ctx.random().nextInt(16),z=z0+ctx.random().nextInt(16);
            int y=ctx.origin().getY()+ctx.random().nextInt(49)-24;
            if(y<=w.getMinBuildHeight()+5||y>=36)continue;
            BlockPos air=new BlockPos(x,y,z),floor=air.below();
            if(!w.getBlockState(air).isAir()||!w.getBlockState(air.above()).isAir()
                    ||!w.getFluidState(air).isEmpty()
                    ||!w.getFluidState(floor).isEmpty())continue;
            BlockState prior=w.getBlockState(floor);
            if(!rock(prior))continue;
            String biome=w.getBiome(air).unwrapKey()
                 .map(k->k.location().toString()).orElse("");
            BlockState next=switch(biome) {
                case "realscale:underground_springs" -> Blocks.CLAY.defaultBlockState();
                case "realscale:deep_fungal_caverns" -> Blocks.MOSS_BLOCK.defaultBlockState();
                case "realscale:glacial_subsurface" -> Blocks.PACKED_ICE.defaultBlockState();
                case "realscale:damp_limestone_caverns" -> Blocks.CALCITE.defaultBlockState();
                case "realscale:dry_karst_caves" -> Blocks.DRIPSTONE_BLOCK.defaultBlockState();
                case "realscale:volcanic_caverns" -> Blocks.SMOOTH_BASALT.defaultBlockState();
                default -> null;
            };
            if(next!=null&&!prior.equals(next) && w.setBlock(floor,next,2))changed++;
        }
        return changed>0;
    }
}

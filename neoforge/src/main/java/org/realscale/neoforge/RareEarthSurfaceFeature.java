package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Biome-specific surface mosaic for the ten rare Earth biomes.
 * Decorative and strictly chunk-local: no pools, excavation, sources or ores.
 * Only runs when a biome source has actually selected one of these biome IDs.
 */
public final class RareEarthSurfaceFeature extends Feature<NoneFeatureConfiguration> {
    public RareEarthSurfaceFeature() { super(NoneFeatureConfiguration.CODEC); }
    private static boolean natural(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK)||s.is(Blocks.DIRT)||s.is(Blocks.SAND)
            ||s.is(Blocks.RED_SAND)||s.is(Blocks.GRAVEL)||s.is(Blocks.CLAY)
            ||s.is(Blocks.MUD)||s.is(Blocks.STONE)||s.is(Blocks.TERRACOTTA)
            ||s.is(Blocks.COARSE_DIRT)||s.is(Blocks.PODZOL);
    }
    private static BlockState accent(String biome, int pattern) {
        return switch(biome) {
            case "geothermal_sulfur_basin" -> switch(pattern%4) {
                case 0 -> Blocks.YELLOW_TERRACOTTA.defaultBlockState();
                case 1 -> Blocks.ORANGE_TERRACOTTA.defaultBlockState();
                case 2 -> Blocks.CALCITE.defaultBlockState();
                default -> Blocks.TUFF.defaultBlockState();
            };
            case "acidic_geothermal_mudflat" -> pattern%3==0
                ? Blocks.CLAY.defaultBlockState() : Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();
            case "white_dune_lagoon" -> pattern%4==0
                ? Blocks.CALCITE.defaultBlockState() : Blocks.SAND.defaultBlockState();
            case "steppe_saline_lake" -> pattern%3==0
                ? Blocks.WHITE_TERRACOTTA.defaultBlockState() : Blocks.CALCITE.defaultBlockState();
            case "congo_peat_swamp_forest" -> pattern%3==0
                ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.MUD.defaultBlockState();
            case "sundarbans_tidal_mangrove" -> pattern%3==0
                ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.MUD.defaultBlockState();
            case "socotra_dragonblood_woodland" -> pattern%3==0
                ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
            case "montane_mist_forest" -> pattern%3==0
                ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.PODZOL.defaultBlockState();
            case "permafrost_fen" -> pattern%3==0
                ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
            case "limestone_island_scrub" -> pattern%3==0
                ? Blocks.CALCITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
            default -> null;
        };
    }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if(!TVAWorldGuard.hasRealScaleGenerator(ctx))return false;
        WorldGenLevel w=ctx.level();
        if(!w.getLevel().dimension().equals(Level.OVERWORLD))return false;
        int cx=Math.floorDiv(ctx.origin().getX(),16)*16;
        int cz=Math.floorDiv(ctx.origin().getZ(),16)*16;
        int count=0;
        for(int i=0;i<48 && count<20;i++) {
            int x=cx+ctx.random().nextInt(16);
            int z=cz+ctx.random().nextInt(16);
            int y=w.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z);
            if(y<=w.getMinBuildHeight()+2||y>=w.getMaxBuildHeight()-2)continue;
            BlockPos above=new BlockPos(x,y,z), ground=above.below();
            if(!w.getBlockState(above).isAir()||!w.getFluidState(above).isEmpty())continue;
            BlockState existing=w.getBlockState(ground);
            if(!natural(existing)||!w.getFluidState(ground).isEmpty())continue;
            String biome=w.getBiome(above).unwrapKey()
                    .map(k->k.location().getNamespace().equals("realscale")
                            ?k.location().getPath():"").orElse("");
            int band=Math.floorDiv(x,12)*37+Math.floorDiv(z,12)*17;
            BlockState next=accent(biome,band);
            if(next==null||next.equals(existing))continue;
            w.setBlock(ground,next,2);
            count++;
        }
        return count>0;
    }
}

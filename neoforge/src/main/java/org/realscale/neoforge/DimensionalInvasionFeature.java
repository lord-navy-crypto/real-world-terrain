package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Optional three-realm invasion remnants. An uncommon local scar of another
 * realm's block palette, not a change of biome source, portals or world seed.
 * Safe for vanilla oceans/caves: only air is filled, substrate is untouched,
 * and writes never cross the originating chunk.
 */
public final class DimensionalInvasionFeature extends Feature<NoneFeatureConfiguration> {
    public enum Invader { NETHER, END, OVERWORLD }
    private final Invader invader;

    public DimensionalInvasionFeature(Invader invader) {
        super(NoneFeatureConfiguration.CODEC);
        this.invader = invader;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!TVAWorldGuard.hasRealScaleGenerator(context)) return false;
        WorldGenLevel level = context.level();
        ResourceLocation realm = level.getLevel().dimension().location();
        String home = realm.getPath();
        if (!"minecraft".equals(realm.getNamespace())) return false;
        if (invader == Invader.NETHER && !"overworld".equals(home) && !"the_end".equals(home))
            return false;
        if (invader == Invader.END && !"overworld".equals(home) && !"the_nether".equals(home))
            return false;
        if (invader == Invader.OVERWORLD && !"the_nether".equals(home) && !"the_end".equals(home))
            return false;
        BlockPos origin = context.origin();
        // The placement heightmap may resolve foliage or air cavities.
        // A natural, solid substrate and 3 clear overhead blocks are mandatory.
        if (origin.getY() < level.getMinBuildHeight()+4
                || origin.getY()+3 >= level.getMaxBuildHeight()) return false;
        BlockState floor = level.getBlockState(origin.below());
        if (!level.getFluidState(origin.below()).isEmpty()
                || !(floor.is(Blocks.STONE) || floor.is(Blocks.GRASS_BLOCK)
                    || floor.is(Blocks.DIRT) || floor.is(Blocks.NETHERRACK)
                    || floor.is(Blocks.END_STONE) || floor.is(Blocks.BASALT)
                    || floor.is(Blocks.BLACKSTONE) || floor.is(Blocks.GRAVEL)))
            return false;
        for (int dy=0;dy<3;dy++)
            if (!level.getBlockState(origin.above(dy)).isAir()
                    || !level.getFluidState(origin.above(dy)).isEmpty())
                return false;
        BlockState core = switch(invader) {
            case NETHER -> Blocks.NETHERRACK.defaultBlockState();
            case END -> Blocks.END_STONE.defaultBlockState();
            case OVERWORLD -> Blocks.MOSS_BLOCK.defaultBlockState();
        };
        BlockState crest = switch(invader) {
            case NETHER -> Blocks.BLACKSTONE.defaultBlockState();
            case END -> Blocks.PURPUR_BLOCK.defaultBlockState();
            case OVERWORLD -> Blocks.OAK_LOG.defaultBlockState();
        };
        // A small three-block landmark, intentionally no lava or spawned mobs.
        // No world reads outside this column and no excavation.
        level.setBlock(origin, core, 2);
        level.setBlock(origin.above(), core, 2);
        level.setBlock(origin.above(2), crest, 2);
        return true;
    }
}

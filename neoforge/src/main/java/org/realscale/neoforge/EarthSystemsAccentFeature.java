package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Grounded, single-column vanilla-block accents for named geological habitats.
 * These are NOT substitutes for basin hydrology, lava-flow simulation,
 * multi-chunk erosion or a climate model. Existing block/fluid states remain
 * unchanged; only empty air above compatible natural substrates is decorated.
 */
public final class EarthSystemsAccentFeature extends Feature<NoneFeatureConfiguration> {
    public enum Kind {
        COLD_DESERT, ROCKY_PLATEAU, SALT_PLAYA, CLOUD_FOREST, ALLUVIAL_FAN,
        VOLCANIC_HIGHLAND, BADLANDS, YARDANG, KARST, GLACIAL,
        WETLAND, ASH_FIELD
    }
    private final Kind kind;

    public EarthSystemsAccentFeature(Kind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind = kind;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!TVAWorldGuard.hasRealScaleGenerator(context)) return false;
        WorldGenLevel world = context.level();
        BlockPos position = context.origin();
        if (position.getY() <= world.getMinBuildHeight() + 3
                || position.getY() >= world.getMaxBuildHeight() - 3) return false;
        if (!world.getBlockState(position).isAir()
                || !world.getFluidState(position).isEmpty()
                || !world.getBlockState(position.above()).isAir()) return false;
        BlockPos floorPos = position.below();
        BlockState floor = world.getBlockState(floorPos);
        if (!world.getFluidState(floorPos).isEmpty()
                || !floor.isCollisionShapeFullBlock(world, floorPos)) return false;

        BlockState accent = switch (kind) {
            case COLD_DESERT -> (floor.is(Blocks.GRAVEL) || floor.is(Blocks.STONE)
                    || floor.is(Blocks.SNOW_BLOCK) || floor.is(Blocks.COARSE_DIRT))
                    ? Blocks.COBBLESTONE.defaultBlockState() : null;
            case ROCKY_PLATEAU -> (floor.is(Blocks.STONE) || floor.is(Blocks.GRAVEL)
                    || floor.is(Blocks.TERRACOTTA))
                    ? Blocks.ANDESITE.defaultBlockState() : null;
            case SALT_PLAYA -> (floor.is(Blocks.SAND) || floor.is(Blocks.CLAY)
                    || floor.is(Blocks.TERRACOTTA))
                    ? Blocks.CALCITE.defaultBlockState() : null;
            case CLOUD_FOREST -> (floor.is(Blocks.GRASS_BLOCK) || floor.is(Blocks.PODZOL)
                    || floor.is(Blocks.MOSS_BLOCK))
                    ? Blocks.MOSS_BLOCK.defaultBlockState() : null;
            case ALLUVIAL_FAN -> (floor.is(Blocks.GRAVEL) || floor.is(Blocks.SAND)
                    || floor.is(Blocks.CLAY))
                    ? Blocks.GRAVEL.defaultBlockState() : null;
            case VOLCANIC_HIGHLAND, ASH_FIELD -> (floor.is(Blocks.STONE)
                    || floor.is(Blocks.GRAVEL) || floor.is(Blocks.BASALT))
                    ? (kind == Kind.ASH_FIELD ? Blocks.TUFF.defaultBlockState()
                    : Blocks.BASALT.defaultBlockState()) : null;
            case BADLANDS -> (floor.is(Blocks.TERRACOTTA)
                    || floor.is(Blocks.RED_SAND) || floor.is(Blocks.RED_SANDSTONE))
                    ? Blocks.TERRACOTTA.defaultBlockState() : null;
            case YARDANG -> (floor.is(Blocks.SANDSTONE)
                    || floor.is(Blocks.RED_SANDSTONE) || floor.is(Blocks.TERRACOTTA))
                    ? Blocks.SANDSTONE.defaultBlockState() : null;
            case KARST -> (floor.is(Blocks.STONE)
                    || floor.is(Blocks.CALCITE))
                    ? Blocks.CALCITE.defaultBlockState() : null;
            case GLACIAL -> (floor.is(Blocks.STONE) || floor.is(Blocks.GRAVEL)
                    || floor.is(Blocks.SNOW_BLOCK))
                    ? Blocks.COBBLESTONE.defaultBlockState() : null;
            case WETLAND -> (floor.is(Blocks.MUD) || floor.is(Blocks.CLAY)
                    || floor.is(Blocks.DIRT))
                    ? Blocks.MOSS_BLOCK.defaultBlockState() : null;
        };
        if (accent == null) return false;
        // Only commit above the existing surface; never modify groundwater,
        // active river sources, the substrate, or other structures.
        return world.setBlock(position, accent, 2);
    }
}

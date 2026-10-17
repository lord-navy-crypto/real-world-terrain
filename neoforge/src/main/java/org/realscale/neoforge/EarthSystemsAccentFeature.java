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

        // Reject inappropriate terrain before any block writes. Use local
        // biome temperature and elevation only as conservative guards;
        // precipitation, lithology and hydrology remain independent systems.
        float temperature = world.getBiome(position).value()
                .getBaseTemperature();
        if (kind == Kind.COLD_DESERT && temperature > 0.35f) return false;
        if (kind == Kind.GLACIAL && temperature > 0.25f) return false;
        if (kind == Kind.CLOUD_FOREST && temperature < 0.25f) return false;
        if (kind == Kind.SALT_PLAYA && position.getY() > 210) return false;

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
        if (!world.setBlock(position, accent, 2)) return false;
        // Natural Wonders batch A: a tiny, deterministic deposit around an
        // existing valid accent. Each column is checked independently and
        // strictly constrained to the current chunk; waterways stay intact.
        if (TVAWorldGuard.hasExperimentalWonders(context)
                && (kind == Kind.SALT_PLAYA || kind == Kind.ALLUVIAL_FAN
                || kind == Kind.COLD_DESERT)) {
            int cx = Math.floorDiv(position.getX(), 16);
            int cz = Math.floorDiv(position.getZ(), 16);
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (dx*dx + dz*dz > 10) continue;
                int x=position.getX()+dx,z=position.getZ()+dz;
                if (Math.floorDiv(x,16)!=cx || Math.floorDiv(z,16)!=cz) continue;
                // Ragged, reproducible edge without depending on feature order.
                if (Math.floorMod(x*31+z*17,7)==0) continue;
                BlockPos target=new BlockPos(x,position.getY(),z);
                BlockPos under=target.below();
                if (!world.getBlockState(target).isAir()
                        || !world.getFluidState(target).isEmpty()
                        || !world.getBlockState(target.above()).isAir()
                        || !world.getFluidState(under).isEmpty()) continue;
                BlockState base=world.getBlockState(under);
                if (!base.isCollisionShapeFullBlock(world,under)) continue;
                BlockState material=null;
                if (kind==Kind.SALT_PLAYA
                        && (base.is(Blocks.SAND) || base.is(Blocks.CLAY)
                            || base.is(Blocks.TERRACOTTA))) {
                    material=Math.floorMod(x+z,5)==0
                            ? Blocks.WHITE_TERRACOTTA.defaultBlockState()
                            : Blocks.CALCITE.defaultBlockState();
                } else if (kind==Kind.ALLUVIAL_FAN
                        && (base.is(Blocks.GRAVEL) || base.is(Blocks.SAND)
                            || base.is(Blocks.CLAY))) {
                    material=Math.floorMod(x*13+z,4)==0
                            ? Blocks.COARSE_DIRT.defaultBlockState()
                            : Blocks.GRAVEL.defaultBlockState();
                } else if (kind==Kind.COLD_DESERT
                        && (base.is(Blocks.GRAVEL) || base.is(Blocks.STONE)
                            || base.is(Blocks.SNOW_BLOCK)
                            || base.is(Blocks.COARSE_DIRT))) {
                    material=Math.floorMod(x-z,3)==0
                            ? Blocks.ANDESITE.defaultBlockState()
                            : Blocks.COBBLESTONE.defaultBlockState();
                }
                if (material!=null) world.setBlock(target,material,2);
            }
        }
        return true;
    }
}

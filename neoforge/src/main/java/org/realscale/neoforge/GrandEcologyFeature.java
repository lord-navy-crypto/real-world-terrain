package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional APES-inspired landscape accents, using vanilla blocks only.
 *
 * All features are surface-only, never carve terrain or change fluids, and
 * only place blocks in the originating chunk. Large vegetation first checks
 * its entire footprint so rejected attempts cannot leave partial trees.
 */
public final class GrandEcologyFeature extends Feature<NoneFeatureConfiguration> {
    public enum Kind {
        TEMPERATE_RAINFOREST, SAVANNA_CANOPY, CHAPARRAL_SCRUB,
        GRASSLAND_MOSAIC, ALPINE_TUNDRA, FLOODPLAIN_BANK
    }

    private final Kind kind;

    public GrandEcologyFeature(Kind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind = kind;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        if (origin.getY() < level.getMinBuildHeight() + 3
                || origin.getY() >= level.getMaxBuildHeight() - 36) return false;
        if (!level.getFluidState(origin).isEmpty()) return false;
        return switch (kind) {
            case TEMPERATE_RAINFOREST -> giantConifer(level, origin, random);
            case SAVANNA_CANOPY -> umbrellaAcacia(level, origin, random);
            case CHAPARRAL_SCRUB -> shrubIsland(level, origin, random);
            case GRASSLAND_MOSAIC -> herbMosaic(level, origin, random, false);
            case ALPINE_TUNDRA -> alpinePatch(level, origin, random);
            case FLOODPLAIN_BANK -> herbMosaic(level, origin, random, true);
        };
    }

    private static boolean sameChunk(BlockPos origin, BlockPos pos) {
        return (origin.getX() >> 4) == (pos.getX() >> 4)
                && (origin.getZ() >> 4) == (pos.getZ() >> 4);
    }

    private static boolean dryNaturalSurface(WorldGenLevel level, BlockPos origin) {
        BlockPos below = origin.below();
        BlockState state = level.getBlockState(below);
        return level.getFluidState(below).isEmpty()
                && state.isCollisionShapeFullBlock(level, below)
                && (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                    || state.is(Blocks.PODZOL) || state.is(Blocks.COARSE_DIRT));
    }

    private static BlockState leaves(BlockState state) {
        // Avoid detached leaf decay during world generation.
        return state.setValue(LeavesBlock.PERSISTENT, true);
    }

    /** Check all voxels before changing any, including chunk and height limits. */
    private static boolean commitTree(WorldGenLevel level, BlockPos origin,
                                      Map<BlockPos, BlockState> plan) {
        if (!dryNaturalSurface(level, origin)) return false;
        for (BlockPos pos : plan.keySet()) {
            if (!sameChunk(origin, pos)
                    || pos.getY() <= level.getMinBuildHeight()
                    || pos.getY() >= level.getMaxBuildHeight() - 1
                    || !level.getFluidState(pos).isEmpty()
                    || !level.getBlockState(pos).isAir()) return false;
        }
        for (Map.Entry<BlockPos, BlockState> entry : plan.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 2);
        }
        return true;
    }

    /** Rare 18-29-block conifer silhouette for old-growth wet forests. */
    private static boolean giantConifer(WorldGenLevel level, BlockPos origin,
                                       RandomSource random) {
        int height = 18 + random.nextInt(12);
        Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
        BlockState foliage = leaves(Blocks.SPRUCE_LEAVES.defaultBlockState());
        for (int y = height - 15; y <= height + 1; y++) {
            int below = height - y;
            int radius = below < 1 ? 1 : below < 5 ? 2 : below < 10 ? 3 : 4;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius + 1) continue;
                    if (Math.abs(dx) + Math.abs(dz) > 2
                            && random.nextInt(9) == 0) continue;
                    plan.put(origin.offset(dx, y, dz), foliage);
                }
            }
        }
        BlockState trunk = Blocks.SPRUCE_LOG.defaultBlockState();
        for (int y = 0; y <= height; y++) {
            plan.put(origin.above(y), trunk);
        }
        return commitTree(level, origin, plan);
    }

    /** Broad, flat-topped acacia silhouette with a slight angled trunk. */
    private static boolean umbrellaAcacia(WorldGenLevel level, BlockPos origin,
                                         RandomSource random) {
        int height = 10 + random.nextInt(7);
        int leanX = random.nextBoolean() ? 1 : -1;
        int leanZ = random.nextBoolean() ? 1 : -1;
        BlockPos crown = origin.offset(leanX, height, leanZ);
        Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
        BlockState foliage = leaves(Blocks.ACACIA_LEAVES.defaultBlockState());
        for (int layer = -1; layer <= 1; layer++) {
            int radius = layer == 0 ? 4 : 3;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius + 2) continue;
                    if (Math.abs(dx) + Math.abs(dz) > 3
                            && random.nextInt(12) == 0) continue;
                    plan.put(crown.offset(dx, layer, dz), foliage);
                }
            }
        }
        BlockState log = Blocks.ACACIA_LOG.defaultBlockState();
        for (int y = 0; y <= height; y++) {
            plan.put(origin.offset(y >= height - 3 ? leanX : 0, y,
                    y >= height - 3 ? leanZ : 0), log);
        }
        plan.put(origin.offset(0, height - 3, 0), log);
        plan.put(origin.offset(leanX, height - 3, 0), log);
        plan.put(origin.offset(leanX, height - 3, leanZ), log);
        return commitTree(level, origin, plan);
    }

    /** Low shrub clusters; no new plants or terrain remapping required. */
    private static boolean shrubIsland(WorldGenLevel level, BlockPos origin,
                                       RandomSource random) {
        Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
        BlockState foliage = leaves(Blocks.OAK_LEAVES.defaultBlockState());
        int height = 1 + random.nextInt(2);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5 || random.nextInt(5) == 0) continue;
                plan.put(origin.offset(dx, height, dz), foliage);
            }
        }
        for (int y = 0; y <= height; y++) {
            plan.put(origin.above(y), Blocks.OAK_LOG.defaultBlockState());
        }
        return commitTree(level, origin, plan);
    }

    private static boolean safeHerbSpot(WorldGenLevel level, BlockPos origin,
                                        BlockPos pos) {
        if (!sameChunk(origin, pos) || !level.getBlockState(pos).isAir()
                || !level.getFluidState(pos).isEmpty()) return false;
        BlockState under = level.getBlockState(pos.below());
        return level.getFluidState(pos.below()).isEmpty()
                && (under.is(Blocks.GRASS_BLOCK) || under.is(Blocks.DIRT)
                    || under.is(Blocks.PODZOL));
    }

    /** Only inspect water in the same chunk; never write to waterways. */
    private static boolean nearbyWater(WorldGenLevel level, BlockPos origin) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (dx * dx + dz * dz > 25) continue;
                for (int dy = -2; dy <= 0; dy++) {
                    BlockPos sample = origin.offset(dx, dy, dz);
                    if (sameChunk(origin, sample)
                            && level.getBlockState(sample).is(Blocks.WATER)) return true;
                }
            }
        }
        return false;
    }

    /** Patchy understory: surface vegetation, not altered water or soil. */
    private static boolean herbMosaic(WorldGenLevel level, BlockPos origin,
                                      RandomSource random, boolean floodplain) {
        if (floodplain && !nearbyWater(level, origin)) return false;
        int placed = 0;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (dx * dx + dz * dz > 25 || random.nextInt(4) != 0) continue;
                BlockPos pos = origin.offset(dx, 0, dz);
                if (!safeHerbSpot(level, origin, pos)) continue;
                BlockState plant = floodplain
                        ? (random.nextInt(4) == 0 ? Blocks.MOSS_CARPET.defaultBlockState()
                            : Blocks.FERN.defaultBlockState())
                        : (random.nextInt(6) == 0 ? Blocks.POPPY.defaultBlockState()
                            : Blocks.SHORT_GRASS.defaultBlockState());
                if (!plant.canSurvive(level, pos)) continue;
                level.setBlock(pos, plant, 2);
                placed++;
            }
        }
        return placed > 0;
    }

    /** Stone-and-tundra texture. Never excavate, place falling rubble, or cover snow. */
    private static boolean alpinePatch(WorldGenLevel level, BlockPos origin,
                                       RandomSource random) {
        int placed = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 9 || random.nextInt(3) != 0) continue;
                BlockPos pos = origin.offset(dx, 0, dz);
                if (!sameChunk(origin, pos) || !level.getBlockState(pos).isAir()
                        || !level.getFluidState(pos).isEmpty()) continue;
                BlockPos below = pos.below();
                BlockState under = level.getBlockState(below);
                if (!level.getFluidState(below).isEmpty()
                        || !under.isCollisionShapeFullBlock(level, below)) continue;
                if (under.is(Blocks.STONE) || under.is(Blocks.ANDESITE)
                        || under.is(Blocks.GRAVEL)) {
                    level.setBlock(pos, random.nextBoolean()
                            ? Blocks.STONE.defaultBlockState()
                            : Blocks.ANDESITE.defaultBlockState(), 2);
                    placed++;
                } else if (under.is(Blocks.GRASS_BLOCK) && random.nextInt(3) == 0) {
                    BlockState plant = random.nextBoolean()
                            ? Blocks.MOSS_CARPET.defaultBlockState()
                            : Blocks.SHORT_GRASS.defaultBlockState();
                    if (plant.canSurvive(level, pos)) {
                        level.setBlock(pos, plant, 2);
                        placed++;
                    }
                }
            }
        }
        return placed > 0;
    }
}

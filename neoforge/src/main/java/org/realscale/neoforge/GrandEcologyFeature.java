package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
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
        GRASSLAND_MOSAIC, ALPINE_TUNDRA, FLOODPLAIN_BANK,
        TROPICAL_RAINFOREST, TEMPERATE_SEASONAL_FOREST, BOREAL_TAIGA,
        DESERT_OUTCROPS, ARCTIC_TUNDRA,
        FRESHWATER_BED, COASTAL_SEAGRASS, CORAL_REEF_FLOOR, ESTUARY_SHALLOWS
    }

    private final Kind kind;
    private final EcologyDistributionMath.Habitat habitat;

    public GrandEcologyFeature(Kind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind = kind;
        this.habitat = EcologyDistributionMath.Habitat.valueOf(kind.name());
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        // Limit global NeoForge biome-modifier decorations to RealScale worlds.
        if (!TVAWorldGuard.hasRealScaleGenerator(context)) return false;
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        // Tree clearance is much taller than alpine/riverbank ground cover.
        int overhead = switch (kind) {
            case TEMPERATE_RAINFOREST, TROPICAL_RAINFOREST -> 32;
            case TEMPERATE_SEASONAL_FOREST -> 25;
            case SAVANNA_CANOPY -> 20;
            case CHAPARRAL_SCRUB -> 6;
            case GRASSLAND_MOSAIC, ALPINE_TUNDRA, FLOODPLAIN_BANK,
                 BOREAL_TAIGA, DESERT_OUTCROPS, ARCTIC_TUNDRA,
                 FRESHWATER_BED, COASTAL_SEAGRASS, CORAL_REEF_FLOOR,
                 ESTUARY_SHALLOWS -> 3;
        };
        if (origin.getY() < level.getMinBuildHeight() + 3
                || origin.getY() >= level.getMaxBuildHeight() - overhead) return false;
        boolean underwater = switch (kind) {
            case FRESHWATER_BED, COASTAL_SEAGRASS, CORAL_REEF_FLOOR,
                 ESTUARY_SHALLOWS -> true;
            default -> false;
        };
        if (underwater ? !level.getFluidState(origin).is(FluidTags.WATER)
                : !level.getFluidState(origin).isEmpty()) return false;
        if (underwater && !EcologyDistributionMath.aquaticDepthSuitable(
                habitat, origin.getY(), level.getSeaLevel())) return false;

        // A continuous 768/128-block site-suitability field clusters plants
        // into groves, open meadows and sparse strips. No chunk lookups,
        // climate remapping, terrain changes or additional registry nodes.
        double chance = EcologyDistributionMath.placementProbability(
                habitat, origin.getX(), origin.getY(), origin.getZ(), level.getSeed());
        if (random.nextDouble() >= chance) return false;
        return switch (kind) {
            case TEMPERATE_RAINFOREST -> giantConifer(level, origin, random);
            case SAVANNA_CANOPY -> umbrellaAcacia(level, origin, random);
            case CHAPARRAL_SCRUB -> shrubIsland(level, origin, random);
            case GRASSLAND_MOSAIC -> herbMosaic(level, origin, random, false);
            case ALPINE_TUNDRA -> alpinePatch(level, origin, random);
            case FLOODPLAIN_BANK -> herbMosaic(level, origin, random, true);
            case TROPICAL_RAINFOREST -> tropicalCanopy(level, origin, random);
            case TEMPERATE_SEASONAL_FOREST -> seasonalBroadleaf(level, origin, random);
            case BOREAL_TAIGA -> borealUnderstory(level, origin, random);
            case DESERT_OUTCROPS -> desertOutcrop(level, origin, random);
            case ARCTIC_TUNDRA -> arcticStones(level, origin, random);
            case FRESHWATER_BED -> underwaterMeadow(level, origin, random, 0);
            case COASTAL_SEAGRASS -> underwaterMeadow(level, origin, random, 1);
            case CORAL_REEF_FLOOR -> underwaterMeadow(level, origin, random, 2);
            case ESTUARY_SHALLOWS -> underwaterMeadow(level, origin, random, 3);
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

    /** Tropical wet forest: occasional emergent jungle-tree crown using vanilla logs. */
    private static boolean tropicalCanopy(WorldGenLevel level, BlockPos origin,
                                          RandomSource random) {
        int height = 18 + random.nextInt(12);
        Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
        BlockState foliage = leaves(Blocks.JUNGLE_LEAVES.defaultBlockState());
        for (int dy = -4; dy <= 2; dy++) {
            int radius = (dy == -4 || dy == 2) ? 2 : (dy == 1 ? 3 : 4);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx*dx + dz*dz > radius*radius + 1) continue;
                    if (Math.abs(dx) + Math.abs(dz) > 2 && random.nextInt(9) == 0) continue;
                    plan.put(origin.offset(dx, height + dy, dz), foliage);
                }
            }
        }
        for (int y = 0; y <= height; y++) {
            plan.put(origin.above(y), Blocks.JUNGLE_LOG.defaultBlockState());
        }
        return commitTree(level, origin, plan);
    }

    /** Irregular 14-21-block broadleaf canopy; biome tint still controls color. */
    private static boolean seasonalBroadleaf(WorldGenLevel level, BlockPos origin,
                                             RandomSource random) {
        int height = 14 + random.nextInt(8);
        Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
        BlockState foliage = leaves((random.nextBoolean()
                ? Blocks.OAK_LEAVES : Blocks.BIRCH_LEAVES).defaultBlockState());
        for (int dy = -6; dy <= 2; dy++) {
            int radius = Math.abs(dy) > 4 ? 2 : dy == 2 ? 2 : 4;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius + 1) continue;
                    if (Math.abs(dx) + Math.abs(dz) > 2 && random.nextInt(7) == 0) continue;
                    plan.put(origin.offset(dx, height + dy, dz), foliage);
                }
            }
        }
        for (int y = 0; y <= height; y++) {
            plan.put(origin.above(y), Blocks.OAK_LOG.defaultBlockState());
        }
        return commitTree(level, origin, plan);
    }

    /** Boreal taiga shaded floor: fern and moss clusters, no soil destruction. */
    private static boolean borealUnderstory(WorldGenLevel level, BlockPos origin,
                                            RandomSource random) {
        int placed = 0;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx * dx + dz * dz > 16 || random.nextInt(3) != 0) continue;
                BlockPos pos = origin.offset(dx, 0, dz);
                if (!safeHerbSpot(level, origin, pos)) continue;
                BlockState plant = random.nextBoolean()
                        ? Blocks.FERN.defaultBlockState()
                        : Blocks.MOSS_CARPET.defaultBlockState();
                if (!plant.canSurvive(level, pos)) continue;
                level.setBlock(pos, plant, 2);
                placed++;
            }
        }
        return placed > 0;
    }

    /** Sparse stable sandstone rocky outcrop, with complete footprint checking. */
    private static boolean desertOutcrop(WorldGenLevel level, BlockPos origin,
                                         RandomSource random) {
        int height = 1 + random.nextInt(2);
        Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                if (random.nextInt(4) == 0) continue;
                BlockPos base = origin.offset(dx, 0, dz);
                BlockState ground = level.getBlockState(base.below());
                if (!sameChunk(origin, base)
                        || !level.getFluidState(base.below()).isEmpty()
                        || !(ground.is(Blocks.SAND) || ground.is(Blocks.RED_SAND)
                            || ground.is(Blocks.SANDSTONE) || ground.is(Blocks.RED_SANDSTONE))
                        || !ground.isCollisionShapeFullBlock(level, base.below())) return false;
                BlockState rock = ground.is(Blocks.RED_SAND)
                        || ground.is(Blocks.RED_SANDSTONE)
                        ? Blocks.RED_SANDSTONE.defaultBlockState()
                        : Blocks.SANDSTONE.defaultBlockState();
                for (int y = 0; y < height; y++) {
                    if (y > 0 && random.nextBoolean()) continue;
                    plan.put(base.above(y), rock);
                }
            }
        }
        if (plan.isEmpty()) return false;
        for (BlockPos pos : plan.keySet()) {
            if (!sameChunk(origin, pos) || !level.getBlockState(pos).isAir()
                    || !level.getFluidState(pos).isEmpty()
                    || pos.getY() >= level.getMaxBuildHeight() - 1) return false;
        }
        plan.forEach((pos,rock) -> level.setBlock(pos,rock,2));
        return true;
    }

    /** Rare frost-resistant low rocklets, avoiding snow destruction. */
    private static boolean arcticStones(WorldGenLevel level, BlockPos origin,
                                        RandomSource random) {
        int placed = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 4 || random.nextInt(4) != 0) continue;
                BlockPos pos = origin.offset(dx, 0, dz);
                if (!sameChunk(origin, pos) || !level.getBlockState(pos).isAir()
                        || !level.getFluidState(pos).isEmpty()) continue;
                BlockPos groundPos = pos.below();
                BlockState ground = level.getBlockState(groundPos);
                BlockPos target = pos;
                if (ground.is(Blocks.SNOW)) {
                    // Thin snow on top of solid frozen ground: replace only
                    // that snow layer with grounded exposed rock, never hover.
                    BlockPos belowSnow = groundPos.below();
                    BlockState actualGround = level.getBlockState(belowSnow);
                    if (!sameChunk(origin, groundPos)
                            || !level.getFluidState(belowSnow).isEmpty()
                            || !actualGround.isCollisionShapeFullBlock(level, belowSnow)
                            || !(actualGround.is(Blocks.GRASS_BLOCK)
                                || actualGround.is(Blocks.DIRT)
                                || actualGround.is(Blocks.STONE)
                                || actualGround.is(Blocks.SNOW_BLOCK)
                                || actualGround.is(Blocks.COARSE_DIRT))) continue;
                    target = groundPos;
                } else if (!level.getFluidState(groundPos).isEmpty()
                        || !ground.isCollisionShapeFullBlock(level, groundPos)
                        || !(ground.is(Blocks.SNOW_BLOCK) || ground.is(Blocks.GRASS_BLOCK)
                            || ground.is(Blocks.STONE) || ground.is(Blocks.GRAVEL)
                            || ground.is(Blocks.COARSE_DIRT))) continue;
                level.setBlock(target, Blocks.COBBLESTONE.defaultBlockState(), 2);
                placed++;
            }
        }
        return placed > 0;
    }

    /**
     * Real freshwater, coastal, tropical reef and brackish-edge plant mosaics.
     * Changes WATER to native water-compatible seagrass or sea pickle ONLY;
     * never changes sediments, seafloor geometry, aquifers or flowing channels.
     * These are visual habitat proxies, not a salinity or tidal simulation.
     */
    private static boolean underwaterMeadow(WorldGenLevel level, BlockPos origin,
                                            RandomSource random, int habitat) {
        int placed = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 9 || random.nextInt(3) != 0) continue;
                BlockPos pos = origin.offset(dx, 0, dz);
                if (!sameChunk(origin, pos) || !level.getBlockState(pos).is(Blocks.WATER)
                        || !level.getFluidState(pos).is(FluidTags.WATER)) continue;
                BlockPos floor = pos.below();
                BlockState ground = level.getBlockState(floor);
                if (!level.getFluidState(floor).isEmpty()
                        || !ground.isCollisionShapeFullBlock(level, floor)
                        || !(ground.is(Blocks.SAND) || ground.is(Blocks.GRAVEL)
                            || ground.is(Blocks.CLAY) || ground.is(Blocks.MUD)
                            || ground.is(Blocks.STONE) || ground.is(Blocks.DIRT))) continue;
                // Do not use a marine decorative species in freshwater.
                BlockState vegetation = habitat == 2 && random.nextInt(5) == 0
                        ? Blocks.SEA_PICKLE.defaultBlockState()
                        : Blocks.SEAGRASS.defaultBlockState();
                if (!vegetation.canSurvive(level,pos)) continue;
                // Brackish fringe is a sparse mosaic; no claim of true estuarine salinity.
                if (habitat == 3 && random.nextBoolean()) continue;
                level.setBlock(pos, vegetation, 2);
                placed++;
            }
        }
        return placed > 0;
    }
}

package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Compact cave and ocean-floor microhabitats. Entirely decorative: never
 * carves caves, changes liquids, removes ores, or cuts the ocean surface.
 */
public final class HabitatPatchFeature extends Feature<NoneFeatureConfiguration> {
    public enum Kind { CAVE_MOSS, CAVE_DRIPSTONE, OCEAN_SEAGRASS, OCEAN_STONE }
    private final Kind kind;
    private static final int CAVE_SCAN_RADIUS_Y = 18;
    private static final int HIGHLAND_SCAN_RADIUS_Y = 40;
    private static final int MIN_ROOF_DEPTH = 20;
    // Preserve the existing cave patch trial order without allocating a new
    // three-element array for every decorated candidate column.
    private static final int[] FLOOR_OFFSETS = {0, 1, -1};
    public HabitatPatchFeature(Kind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind = kind;
    }

    private static boolean caveSupport(BlockState state, boolean requireDripstone) {
        // Previously stalagmites required a preexisting dripstone floor.
        // Grand cavern halls expose stone/calcite/tuff instead, so the
        // feature was effectively inert in newly carved mountain caves.
        if (requireDripstone) return state.is(Blocks.DRIPSTONE_BLOCK)
                || state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.CALCITE) || state.is(Blocks.TUFF);
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.TUFF) || state.is(Blocks.DRIPSTONE_BLOCK)
                || state.is(Blocks.MOSS_BLOCK);
    }

    private static boolean dryCaveFloor(WorldGenLevel w, BlockPos pos, boolean requireDripstone) {
        // Preserve the original short-circuit order, while reusing the one
        // above position for the air and fluid probes.
        if (!w.getBlockState(pos).isAir()) return false;
        final BlockPos above = pos.above();
        if (!w.getBlockState(above).isAir()) return false;
        return w.getFluidState(pos).isEmpty()
                && w.getFluidState(above).isEmpty()
                && caveSupport(w.getBlockState(pos.below()), requireDripstone);
    }

    /**
     * Random height-range placement normally hits stone rather than floor air.
     * Search only a bounded local vertical interval for a real cave pocket,
     * never accessing chunks far away or replacing the solid cave wall.
     */
    private static BlockPos nearbyCaveFloor(WorldGenLevel w, BlockPos origin, boolean dripstone) {
        int min = Math.max(w.getMinBuildHeight() + 3,
                GrandOverworldCaveNetwork.MIN_Y);
        // Terrain is up to 2096 blocks tall; do NOT clip cave decorations
        // at old vanilla-level Y45. But a real overhead roof is required:
        // exposed slopes, beaches and hilltops must not get cave patches.
        int surface = w.getHeight(Heightmap.Types.WORLD_SURFACE_WG,
                origin.getX(), origin.getZ()) - 1;
        int max = Math.min(Math.min(w.getMaxBuildHeight() - 3,
                GrandOverworldCaveNetwork.MAX_Y), surface - MIN_ROOF_DEPTH);
        if (max < min) return null;
        int search = origin.getY() > 60
                ? HIGHLAND_SCAN_RADIUS_Y : CAVE_SCAN_RADIUS_Y;
        // Keep the exact nearest-floor traversal and all depth/roof guards.
        // An air-pocket rejection can occur dozens of times in solid rock:
        // one locally scoped mutable position avoids repeated BlockPos
        // allocation and never escapes synchronous predicate invocation.
        final int x = origin.getX(), z = origin.getZ();
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        int resultY = HabitatFloorSearch.nearest(origin.getY(), min, max,
                search, y -> dryCaveFloor(w, probe.set(x, y, z), dripstone));
        return resultY == HabitatFloorSearch.NOT_FOUND
                ? null : new BlockPos(origin.getX(), resultY, origin.getZ());
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        // Limit global NeoForge biome-modifier decorations to RealScale worlds.
        if (!TVAWorldGuard.hasRealScaleGenerator(context)) return false;
        WorldGenLevel w = context.level();
        BlockPos start = context.origin();
        boolean cave = kind == Kind.CAVE_MOSS || kind == Kind.CAVE_DRIPSTONE;
        // Marine experiments must not affect vanilla oceans in stable RealScale
        // or any other mod's world preset, even if a data pack re-adds features.
        if (!cave && !(context.chunkGenerator().getBiomeSource() instanceof SkyOceanBiomeSource))
            return false;
        if (cave) {
            if (start.getY() > GrandOverworldCaveNetwork.MAX_Y
                    || start.getY() < w.getMinBuildHeight() + 3)
                return false;
            start = nearbyCaveFloor(w, start, kind == Kind.CAVE_DRIPSTONE);
            if (start == null) return false;
        } else if (start.getY() >= context.chunkGenerator().getSeaLevel() - 3)
            return false; // Reject beaches, low tide, riverbanks, exposed seabed.

        int changed = 0;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (dx * dx + dz * dz > 5 || context.random().nextInt(4) == 0) continue;
            BlockPos p = start.offset(dx, 0, dz);
            if (cave) {
                // Permit a one-block rise or dip on uneven natural cave floors.
                BlockPos target = null;
                boolean dripstone = kind == Kind.CAVE_DRIPSTONE;
                for (int step : FLOOR_OFFSETS) {
                    BlockPos trial = p.above(step);
                    if (dryCaveFloor(w, trial, dripstone)) {
                        target = trial;
                        break;
                    }
                }
                if (target == null) continue;
                BlockState plant = dripstone
                        ? Blocks.POINTED_DRIPSTONE.defaultBlockState()
                        : Blocks.MOSS_CARPET.defaultBlockState();
                if (!plant.canSurvive(w, target)) continue;
                w.setBlock(target, plant, 2);
                changed++;
            } else {
                // Require a submerged column above natural sediment. This stops
                // decoration from replacing air, plants, coral, shipwrecks or fish.
                if (!w.getFluidState(p).is(FluidTags.WATER)) continue;
                BlockPos above = p.above();
                if (!w.getFluidState(above).is(FluidTags.WATER)) continue;
                // Reuse the seabed coordinate for both validation and
                // placement. Keep validation before the RNG rock choice.
                BlockPos below = p.below();
                BlockState floor = w.getBlockState(below);
                if (!(floor.is(Blocks.SAND) || floor.is(Blocks.GRAVEL)
                        || floor.is(Blocks.CLAY) || floor.is(Blocks.DIRT))) continue;
                if (kind == Kind.OCEAN_SEAGRASS) {
                    if (!w.getBlockState(p).is(Blocks.WATER)) continue;
                    BlockState seagrass = Blocks.SEAGRASS.defaultBlockState();
                    if (!seagrass.canSurvive(w, p)) continue;
                    w.setBlock(p, seagrass, 2);
                } else {
                    // Alter only the original plain sediment, not coral blocks,
                    // seagrass, sea pickles, shipwrecks or underwater structures.
                    BlockState rock = context.random().nextBoolean()
                            ? Blocks.TUFF.defaultBlockState()
                            : Blocks.ANDESITE.defaultBlockState();
                    w.setBlock(below, rock, 2);
                }
                changed++;
            }
        }
        return changed > 0;
    }
}

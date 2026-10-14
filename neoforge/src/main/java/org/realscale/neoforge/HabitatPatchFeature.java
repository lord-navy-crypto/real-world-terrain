package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
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
    public HabitatPatchFeature(Kind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind = kind;
    }

    private static boolean caveSupport(BlockState state, boolean requireDripstone) {
        if (requireDripstone) return state.is(Blocks.DRIPSTONE_BLOCK);
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.TUFF) || state.is(Blocks.DRIPSTONE_BLOCK)
                || state.is(Blocks.MOSS_BLOCK);
    }

    private static boolean dryCaveFloor(WorldGenLevel w, BlockPos pos, boolean requireDripstone) {
        return w.getBlockState(pos).isAir()
                && w.getBlockState(pos.above()).isAir()
                && w.getFluidState(pos).isEmpty()
                && w.getFluidState(pos.above()).isEmpty()
                && caveSupport(w.getBlockState(pos.below()), requireDripstone);
    }

    /**
     * Random height-range placement normally hits stone rather than floor air.
     * Search only a bounded local vertical interval for a real cave pocket,
     * never accessing chunks far away or replacing the solid cave wall.
     */
    private static BlockPos nearbyCaveFloor(WorldGenLevel w, BlockPos origin, boolean dripstone) {
        int min = Math.max(w.getMinBuildHeight() + 3, -58);
        int max = Math.min(45, w.getMaxBuildHeight() - 3);
        int resultY = HabitatFloorSearch.nearest(origin.getY(), min, max,
                CAVE_SCAN_RADIUS_Y, y -> dryCaveFloor(w,
                    new BlockPos(origin.getX(), y, origin.getZ()), dripstone));
        return resultY == HabitatFloorSearch.NOT_FOUND
                ? null : new BlockPos(origin.getX(), resultY, origin.getZ());
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel w = context.level();
        BlockPos start = context.origin();
        boolean cave = kind == Kind.CAVE_MOSS || kind == Kind.CAVE_DRIPSTONE;
        // Marine experiments must not affect vanilla oceans in stable RealScale
        // or any other mod's world preset, even if a data pack re-adds features.
        if (!cave && !(context.chunkGenerator().getBiomeSource() instanceof SkyOceanBiomeSource))
            return false;
        if (cave) {
            if (start.getY() > 46 || start.getY() < w.getMinBuildHeight() + 3)
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
                for (int step : new int[]{0, 1, -1}) {
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
                if (!w.getFluidState(p).is(FluidTags.WATER)
                        || !w.getFluidState(p.above()).is(FluidTags.WATER)) continue;
                BlockState floor = w.getBlockState(p.below());
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
                    w.setBlock(p.below(), rock, 2);
                }
                changed++;
            }
        }
        return changed > 0;
    }
}

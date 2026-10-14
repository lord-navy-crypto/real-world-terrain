package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Small cave and seafloor habitat patches. Never carve, place fluids, or edit ores. */
public final class HabitatPatchFeature extends Feature<NoneFeatureConfiguration> {
    public enum Kind { CAVE_MOSS, CAVE_DRIPSTONE, OCEAN_SEAGRASS, OCEAN_STONE }
    private final Kind kind;
    public HabitatPatchFeature(Kind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind = kind;
    }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel w = context.level();
        BlockPos start = context.origin();
        if (kind == Kind.CAVE_MOSS || kind == Kind.CAVE_DRIPSTONE) {
            if (start.getY() > 45 || start.getY() < w.getMinBuildHeight() + 3)
                return false;
        } else if (start.getY() >= context.chunkGenerator().getSeaLevel() - 3)
            return false; // Avoid shores, rivers, surface shipwrecks, and beaches.

        int changed = 0;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (dx * dx + dz * dz > 5 || context.random().nextInt(4) == 0) continue;
            BlockPos p = start.offset(dx, 0, dz);
            if (kind == Kind.CAVE_MOSS || kind == Kind.CAVE_DRIPSTONE) {
                if (!w.getBlockState(p).isAir() || !w.getFluidState(p).isEmpty()) continue;
                BlockState floor = w.getBlockState(p.below());
                if (!(floor.is(Blocks.STONE) || floor.is(Blocks.DEEPSLATE)
                        || floor.is(Blocks.TUFF) || floor.is(Blocks.DRIPSTONE_BLOCK)))
                    continue;
                BlockState top = kind == Kind.CAVE_MOSS
                        ? Blocks.MOSS_CARPET.defaultBlockState()
                        : Blocks.POINTED_DRIPSTONE.defaultBlockState();
                // Stalagmites are supported by locally generated dripstone stone.
                if (kind == Kind.CAVE_DRIPSTONE && !floor.is(Blocks.DRIPSTONE_BLOCK))
                    continue;
                if (!top.canSurvive(w, p)) continue;
                w.setBlock(p, top, 2);
                changed++;
            } else {
                if (!w.getFluidState(p).is(FluidTags.WATER)
                        || !w.getFluidState(p.above()).is(FluidTags.WATER)) continue;
                BlockState floor = w.getBlockState(p.below());
                if (!(floor.is(Blocks.SAND) || floor.is(Blocks.GRAVEL)
                        || floor.is(Blocks.CLAY) || floor.is(Blocks.DIRT)))
                    continue;
                if (kind == Kind.OCEAN_SEAGRASS) {
                    BlockState grass = Blocks.SEAGRASS.defaultBlockState();
                    if (!grass.canSurvive(w, p)) continue;
                    w.setBlock(p, grass, 2);
                } else {
                    BlockState rock = context.random().nextBoolean()
                            ? Blocks.TUFF.defaultBlockState()
                            : Blocks.ANDESITE.defaultBlockState();
                    // Replaces only ordinary sediment; keeps the entire water
                    // column intact. Never displace fish, coral or seagrass.
                    w.setBlock(p.below(), rock, 2);
                }
                changed++;
            }
        }
        return changed > 0;
    }
}

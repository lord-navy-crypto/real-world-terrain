package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Rare, compact, ground-supported ecological structures for existing vanilla
 * biomes. These decorations NEVER dig, alter fluids or modify river channels.
 *
 * <p>The entire footprint is validated before any block is placed. Invalid
 * sites leave the world unchanged, including when crossing a chunk border.</p>
 */
public final class GroundMicrostructureFeature extends Feature<NoneFeatureConfiguration> {
    public enum Kind { OAK_LOG, SPRUCE_LOG, FOREST_HUMMOCK, UPLAND_SCREE, COASTAL_DRIFTWOOD }
    private final Kind kind;

    public GroundMicrostructureFeature(Kind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind = kind;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        // Limit global NeoForge biome-modifier decorations to RealScale worlds.
        if (!TVAWorldGuard.hasRealScaleGenerator(context)) return false;
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        if (origin.getY() <= level.getMinBuildHeight() + 1
                || origin.getY() >= level.getMaxBuildHeight() - 2) return false;
        return switch (kind) {
            case OAK_LOG -> fallenLog(level, origin, random, Blocks.OAK_LOG.defaultBlockState());
            case SPRUCE_LOG -> fallenLog(level, origin, random, Blocks.SPRUCE_LOG.defaultBlockState());
            case FOREST_HUMMOCK -> smallHummock(level, origin, random);
            case UPLAND_SCREE -> smallScree(level, origin, random);
            case COASTAL_DRIFTWOOD -> coastalDriftwood(level, origin, random);
        };
    }

    private static boolean solidDrySupport(WorldGenLevel level, BlockPos ground) {
        BlockState state = level.getBlockState(ground);
        return state.isCollisionShapeFullBlock(level, ground)
                && level.getFluidState(ground).isEmpty()
                && (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.STONE) || state.is(Blocks.GRAVEL));
    }

    private static boolean emptyDrySpace(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        // Replace only air or common undergrowth. Never overwrite flowers, roots,
        // water, leaf canopy, logs, structures, or block entities.
        boolean free = state.isAir() || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN);
        return free && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos.above()).isAir();
    }

    /**
     * An occasional shoreline log fully supported by sand, red sand, gravel
     * or rock. Entire structure is validated before writing a single block.
     * Never replaces ocean water, river water, leaves or existing structures.
     */
    private static boolean coastalDriftwood(WorldGenLevel level, BlockPos origin, RandomSource random) {
        boolean alongX = random.nextBoolean();
        int length = 2 + random.nextInt(3);
        int offset = length / 2;
        BlockState wood = (random.nextBoolean() ? Blocks.OAK_LOG : Blocks.SPRUCE_LOG)
                .defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS,
                        alongX ? Direction.Axis.X : Direction.Axis.Z);
        for (int i = 0; i < length; i++) {
            BlockPos p = origin.offset(alongX ? i - offset : 0, 0,
                    alongX ? 0 : i - offset);
            BlockPos ground = p.below();
            BlockState s = level.getBlockState(ground);
            if (!(s.is(Blocks.SAND) || s.is(Blocks.RED_SAND)
                    || s.is(Blocks.GRAVEL) || s.is(Blocks.STONE))
                    || !s.isCollisionShapeFullBlock(level, ground)
                    || !level.getFluidState(ground).isEmpty()
                    || !level.getBlockState(p).isAir()
                    || !level.getFluidState(p).isEmpty()
                    || !level.getBlockState(p.above()).isAir()) return false;
        }
        for (int i = 0; i < length; i++) {
            BlockPos p = origin.offset(alongX ? i - offset : 0, 0,
                    alongX ? 0 : i - offset);
            level.setBlock(p, wood, 2);
        }
        return true;
    }

    /** Complete horizontal log supported on every segment; no hovering or partial logs. */
    private static boolean fallenLog(WorldGenLevel level, BlockPos origin,
                                     RandomSource random, BlockState wood) {
        boolean alongX = random.nextBoolean();
        int length = 3 + random.nextInt(4); // 3..6, short enough for local generation
        int left = length / 2;
        BlockState axisWood = wood.setValue(RotatedPillarBlock.AXIS,
                alongX ? Direction.Axis.X : Direction.Axis.Z);
        for (int i = 0; i < length; ++i) {
            BlockPos p = origin.offset(alongX ? i-left : 0, 0, alongX ? 0 : i-left);
            if (!solidDrySupport(level, p.below()) || !emptyDrySpace(level, p))
                return false;
        }
        for (int i = 0; i < length; ++i) {
            BlockPos p = origin.offset(alongX ? i-left : 0, 0, alongX ? 0 : i-left);
            level.setBlock(p, axisWood, 2);
        }
        return true;
    }

    /** Non-carving root-like raised patch; preserves underlying soil and caves. */
    private static boolean smallHummock(WorldGenLevel level, BlockPos origin, RandomSource random) {
        // Only two blocks: center and a random cardinal neighboring block.
        BlockPos adjacent = switch (random.nextInt(4)) {
            case 0 -> origin.north();
            case 1 -> origin.south();
            case 2 -> origin.east();
            default -> origin.west();
        };
        for (BlockPos p : new BlockPos[]{origin, adjacent}) {
            BlockState under = level.getBlockState(p.below());
            if (!(under.is(Blocks.GRASS_BLOCK) || under.is(Blocks.DIRT)
                    || under.is(Blocks.PODZOL)) || !solidDrySupport(level, p.below())
                    || !emptyDrySpace(level, p)) return false;
        }
        level.setBlock(origin, Blocks.ROOTED_DIRT.defaultBlockState(), 2);
        level.setBlock(adjacent, Blocks.MOSS_BLOCK.defaultBlockState(), 2);
        return true;
    }

    /** Small, entirely supported, dry stone fragments. No basin or water edits. */
    private static boolean smallScree(WorldGenLevel level, BlockPos origin, RandomSource random) {
        BlockPos b = origin.offset(random.nextBoolean() ? 1 : -1, 0, 0);
        BlockPos c = origin.offset(0, 0, random.nextBoolean() ? 1 : -1);
        for (BlockPos p : new BlockPos[]{origin, b, c}) {
            if (!solidDrySupport(level, p.below()) || !emptyDrySpace(level, p))
                return false;
        }
        level.setBlock(origin, Blocks.COBBLESTONE.defaultBlockState(), 2);
        level.setBlock(b, Blocks.GRAVEL.defaultBlockState(), 2);
        level.setBlock(c, Blocks.STONE.defaultBlockState(), 2);
        return true;
    }
}

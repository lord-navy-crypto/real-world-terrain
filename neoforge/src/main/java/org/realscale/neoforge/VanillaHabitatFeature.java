package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * OPT-IN ecological structures assembled ONLY from vanilla block states.
 * Not a biome, block or mob registry; enabled by a separate test datapack.
 * Validates every target before writing, never changes fluids, rock density,
 * structures, established chunks or the world preset/biome source.
 */
public final class VanillaHabitatFeature extends Feature<NoneFeatureConfiguration> {
    public enum Habitat { FALLEN_TIMBER, RIPARIAN_REEDS }

    private final Habitat habitat;

    public VanillaHabitatFeature(Habitat habitat) {
        super(NoneFeatureConfiguration.CODEC);
        this.habitat = habitat;
    }

    private static boolean inside(ChunkPos chunk, BlockPos p) {
        return p.getX() >= chunk.getMinBlockX() && p.getX() <= chunk.getMaxBlockX()
            && p.getZ() >= chunk.getMinBlockZ() && p.getZ() <= chunk.getMaxBlockZ();
    }

    private static boolean soil(BlockState ground) {
        return ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.PODZOL)
            || ground.is(Blocks.DIRT) || ground.is(Blocks.COARSE_DIRT)
            || ground.is(Blocks.MOSS_BLOCK);
    }

    private static boolean bank(BlockState ground) {
        return soil(ground) || ground.is(Blocks.MUD)
            || ground.is(Blocks.SAND) || ground.is(Blocks.CLAY);
    }

    private static boolean waterside(WorldGenLevel level, BlockPos air) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = air.relative(d);
            BlockState upper = level.getBlockState(n);
            BlockState lower = level.getBlockState(n.below());
            if (upper.is(Blocks.WATER) || upper.is(Blocks.ICE)
                    || lower.is(Blocks.WATER) || lower.is(Blocks.ICE))
                return true;
        }
        return false;
    }

    private static boolean fallenTimber(WorldGenLevel level, ChunkPos chunk,
            BlockPos origin, RandomSource random) {
        Direction.Axis axis = random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
        int length = 3 + random.nextInt(3);
        int dx = axis == Direction.Axis.X ? 1 : 0;
        int dz = axis == Direction.Axis.Z ? 1 : 0;
        BlockPos[] logs = new BlockPos[length];
        int commonGround = Integer.MIN_VALUE;

        for (int i = 0; i < length; i++) {
            int x = origin.getX() + i * dx;
            int z = origin.getZ() + i * dz;
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos surface = new BlockPos(x, surfaceY, z);
            BlockPos log = surface.above();
            if (!inside(chunk, log) || log.getY() + 2 >= level.getMaxBuildHeight()
                    || !soil(level.getBlockState(surface))
                    || !level.getBlockState(log).isAir()
                    || !level.getBlockState(log.above()).isAir())
                return false;
            if (commonGround == Integer.MIN_VALUE) commonGround = surfaceY;
            if (surfaceY != commonGround) return false;
            logs[i] = log;
        }

        BlockState wood = Blocks.SPRUCE_LOG.defaultBlockState()
            .setValue(RotatedPillarBlock.AXIS, axis);
        for (BlockPos log : logs) level.setBlock(log, wood, 2);
        // Moss uses the vanilla survival rule and cannot overwrite vegetation.
        for (BlockPos log : logs) {
            BlockPos top = log.above();
            BlockState carpet = Blocks.MOSS_CARPET.defaultBlockState();
            if (random.nextInt(3) == 0 && level.getBlockState(top).isAir()
                    && carpet.canSurvive(level, top))
                level.setBlock(top, carpet, 2);
        }
        return true;
    }

    private static boolean riparianReeds(WorldGenLevel level, ChunkPos chunk,
            BlockPos origin, RandomSource random) {
        int placed = 0;
        // Small, bounded bank assemblages. Do not touch water sources, ice,
        // existing trees, structures or the river bed.
        for (int i = 0; i < 12 && placed < 4; i++) {
            int x = origin.getX() + random.nextInt(5) - 2;
            int z = origin.getZ() + random.nextInt(5) - 2;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos ground = new BlockPos(x, y, z);
            BlockPos plant = ground.above();
            if (!inside(chunk, plant) || plant.getY() + 2 >= level.getMaxBuildHeight()
                    || !bank(level.getBlockState(ground))
                    || !level.getBlockState(plant).isAir()
                    || !waterside(level, plant))
                continue;
            BlockState candidate = random.nextInt(3) == 0
                ? Blocks.SUGAR_CANE.defaultBlockState()
                : Blocks.FERN.defaultBlockState();
            if (!candidate.canSurvive(level, plant))
                candidate = Blocks.SHORT_GRASS.defaultBlockState();
            if (!candidate.canSurvive(level, plant)) continue;
            level.setBlock(plant, candidate, 2);
            placed++;
        }
        return placed > 0;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        ChunkGenerator generator = context.chunkGenerator();
        if (!(generator instanceof NoiseBasedChunkGenerator noise)) return false;
        var key = noise.generatorSettings().unwrapKey();
        if (key.isEmpty()) return false;
        ResourceLocation id = key.get().location();
        // Biome modifiers are global; never decorate vanilla/foreign worlds.
        if (!id.getNamespace().equals("realscale")) return false;
        WorldGenLevel level = context.level();
        ChunkPos chunk = new ChunkPos(context.origin());
        return habitat == Habitat.FALLEN_TIMBER
            ? fallenTimber(level, chunk, context.origin(), context.random())
            : riparianReeds(level, chunk, context.origin(), context.random());
    }
}

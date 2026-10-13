package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Conservative above-sea-level stream restore for vanilla River biomes
 * in RealScale procedural presets.
 *
 * No cross-chunk writes, no forced sea-level changes, no use of minecraft:lake
 * (known to be unsafe for 1.21.x water generation).
 *
 * This is a shallow stream, NOT surveyed basin-scale river hydrology.
 */
public final class RiverChannelFeature extends Feature<NoneFeatureConfiguration> {
    public RiverChannelFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    private static boolean natural(BlockState state) {
        return state.is(Blocks.STONE)
            || state.is(Blocks.GRASS_BLOCK)
            || state.is(Blocks.DIRT)
            || state.is(Blocks.COARSE_DIRT)
            || state.is(Blocks.PODZOL)
            || state.is(Blocks.GRAVEL)
            || state.is(Blocks.SAND)
            || state.is(Blocks.RED_SAND)
            || state.is(Blocks.CLAY)
            || state.is(Blocks.MUD)
            || state.is(Blocks.TERRACOTTA)
            || state.is(Blocks.ANDESITE)
            || state.is(Blocks.GRANITE)
            || state.is(Blocks.DIORITE)
            || state.is(Blocks.DEEPSLATE)
            || state.is(Blocks.SNOW_BLOCK);
    }

    /**
     * Surface-heightmaps include thin snow and low vegetation. Treating their
     * blocks as the rock surface made snow-covered highland streams dry.
     * Only one non-structural cover block is removable; logs, leaves and
     * player-made surfaces must not be stripped.
     */
    private static boolean removableCover(BlockState state) {
        return state.is(Blocks.SNOW)
            || state.is(Blocks.SHORT_GRASS)
            || state.is(Blocks.FERN)
            || state.is(Blocks.DEAD_BUSH);
    }

    private static boolean solidBank(BlockState state) {
        return state.blocksMotion() && !state.is(Blocks.LAVA)
            && !state.is(Blocks.WATER);
    }

    /**
     * A high-altitude source is unsafe without banks: water may flow into
     * lower neighboring terrain and spread well beyond the river biome.
     * Avoid carving into uncontained slopes and downhill river steps.
     * Only READ adjacent blocks and keep all writes in the current chunk.
     */
    private static boolean enclosedByBanks(WorldGenLevel level, BlockPos top, int inset, int maxGrade) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = top.relative(side);
            BlockState atWaterSurface = level.getBlockState(neighbor.below(inset));
            BlockState atWaterBase = level.getBlockState(neighbor.below(inset + 1));
            boolean existingRiverWater =
                (atWaterSurface.is(Blocks.WATER) || atWaterSurface.is(Blocks.ICE))
                && atWaterBase.is(Blocks.WATER);
            if (!existingRiverWater
                && (!solidBank(atWaterSurface) || !solidBank(atWaterBase)))
                return false;
            boolean adjacentRiver = level.getBiome(neighbor).is(Biomes.RIVER)
                || level.getBiome(neighbor).is(Biomes.FROZEN_RIVER);
            if (adjacentRiver) {
                int nextTop = level.getHeight(
                    Heightmap.Types.WORLD_SURFACE_WG,
                    neighbor.getX(), neighbor.getZ()) - 1;
                // Riverbeds naturally descend. Requiring EXACTLY equal
                // neighboring surface heights eliminated every river in the
                // mountain-spawn QA (180 river columns, 0 water).
                // A gentle, enclosed drop allows small stream cascades;
                // a cliff-like step still cannot become a source.
                // Non-river banks remain solid at both water levels.
                boolean alreadyFilled = existingRiverWater;
                if (Math.abs(nextTop - top.getY()) > maxGrade && !alreadyFilled)
                    return false;
            }
        }
        return true;
    }

    /**
     * Lab world types embed their persistent river-grade setting in the
     * immutable registry ID. No client-only or mutable global config is read
     * during generation; existing saved worlds retain their original rules.
     */
    private static int riverGradeForWorld(String id) {
        if (!id.startsWith("lab_")) return 6;
        for (int grade : new int[] {0, 3, 6, 9}) {
            if (id.endsWith("_g" + grade)) return grade;
        }
        return -1; // Unsupported / malformed Lab id: never place fluids
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        ChunkGenerator base = context.chunkGenerator();
        if (!(base instanceof NoiseBasedChunkGenerator noise)) return false;
        // NeoForge biome modifiers are global: never touch vanilla or
        // foreign-mod worlds. Legacy USGS DEM has high river biomes far
        // above sea level; allow the same bank-guarded shallow channels as
        // other RealScale presets. The underlying DEM density and sample
        // elevations remain unchanged; only safe river surface columns in
        // newly generated chunks may receive the two-layer stream.
        var settingsKey = noise.generatorSettings().unwrapKey();
        if (settingsKey.isEmpty()) return false;
        ResourceLocation id = settingsKey.get().location();
        if (!id.getNamespace().equals("realscale"))
            return false;
        int maxGrade = riverGradeForWorld(id.getPath());
        if (maxGrade <= 0) return false;

        WorldGenLevel level = context.level();
        ChunkPos chunk = new ChunkPos(context.origin());
        int floor = Math.max(level.getMinBuildHeight() + 8, noise.getSeaLevel() + 6);
        boolean made = false;

        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                BlockPos coverPos = new BlockPos(x, surfaceY, z);
                boolean covered = removableCover(level.getBlockState(coverPos));
                int y = covered ? surfaceY - 1 : surfaceY;
                if (y <= floor || y > level.getMaxBuildHeight() - 6) continue;
                BlockPos top = new BlockPos(x, y, z);
                boolean frozen = level.getBiome(top).is(Biomes.FROZEN_RIVER);
                if (!frozen && !level.getBiome(top).is(Biomes.RIVER)) continue;

                // Prefer natural ground, and refuse to carve through player-like
                // structures, fluids, dangling snow, cave roofs or vegetation.
                BlockState surface = level.getBlockState(top);
                BlockState below = level.getBlockState(top.below());
                BlockState bed = level.getBlockState(top.below(2));
                BlockState foundation = level.getBlockState(top.below(3));
                BlockState deepFoundation = level.getBlockState(top.below(4));
                if (!natural(surface) || !natural(below) || !natural(bed)
                    || !natural(foundation) || !deepFoundation.blocksMotion()
                    || (!level.getBlockState(top.above()).isAir()
                        && !(covered && removableCover(level.getBlockState(top.above())))))
                    continue;

                // A shallow one-block inset works on gentle valley floors.
                // On slopes with a lower neighboring bank, lower the water
                // line another block INSTEAD of exposing the source above it.
                // Never lower farther than two blocks: avoid artificial wells.
                int inset = enclosedByBanks(level, top, 1, maxGrade) ? 1 : 0;
                if (inset == 0 && natural(deepFoundation)
                    && level.getBlockState(top.below(5)).blocksMotion()
                    && enclosedByBanks(level, top, 2, maxGrade))
                    inset = 2;
                if (inset == 0) continue;

                // Do not leave the one-block snow/fern cap floating above
                // the stream. All bank and deep-foundation checks have passed.
                if (covered) {
                    level.setBlock(coverPos, Blocks.AIR.defaultBlockState(), 2);
                }
                // Two water layers and a gravel riverbed. Four-side bank
                // checks precede both depth choices. Never alter a column
                // outside the 16x16 chunk being populated.
                level.setBlock(top.below(inset + 2), Blocks.GRAVEL.defaultBlockState(), 2);
                level.setBlock(top.below(inset + 1), Blocks.WATER.defaultBlockState(), 2);
                level.setBlock(top.below(inset), frozen
                    ? Blocks.ICE.defaultBlockState()
                    : Blocks.WATER.defaultBlockState(), 2);
                for (int airDepth = 0; airDepth < inset; airDepth++) {
                    level.setBlock(top.below(airDepth), Blocks.AIR.defaultBlockState(), 2);
                }
                made = true;
            }
        }
        return made;
    }
}

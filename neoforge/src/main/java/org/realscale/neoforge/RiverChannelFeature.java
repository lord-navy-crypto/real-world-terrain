package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
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
     * A high-altitude source is unsafe without banks: water may flow into
     * lower neighboring terrain and spread well beyond the river biome.
     * Avoid carving into uncontained slopes and downhill river steps.
     * Only READ adjacent blocks and keep all writes in the current chunk.
     */
    private static boolean enclosedByBanks(WorldGenLevel level, BlockPos top) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = top.relative(side);
            BlockState atWaterSurface = level.getBlockState(neighbor.below());
            BlockState atWaterBase = level.getBlockState(neighbor.below(2));
            if (atWaterSurface.isAir() || atWaterBase.isAir())
                return false;
            if (atWaterSurface.is(Blocks.LAVA) || atWaterBase.is(Blocks.LAVA))
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
                boolean alreadyFilled = (atWaterSurface.is(Blocks.WATER)
                    || atWaterSurface.is(Blocks.ICE))
                    && atWaterBase.is(Blocks.WATER);
                if (Math.abs(nextTop - top.getY()) > 3 && !alreadyFilled)
                    return false;
            }
        }
        return true;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        ChunkGenerator base = context.chunkGenerator();
        if (!(base instanceof NoiseBasedChunkGenerator noise)) return false;
        // NeoForge biome modifiers are global. Absolutely do not touch vanilla,
        // foreign-mod worlds, or the measured DEM pilot.
        var settingsKey = noise.generatorSettings().unwrapKey();
        if (settingsKey.isEmpty()) return false;
        ResourceLocation id = settingsKey.get().location();
        if (!id.getNamespace().equals("realscale") || id.getPath().equals("usgs_big_sur"))
            return false;

        WorldGenLevel level = context.level();
        ChunkPos chunk = new ChunkPos(context.origin());
        int floor = Math.max(level.getMinBuildHeight() + 8, noise.getSeaLevel() + 6);
        boolean made = false;

        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                if (y <= floor || y > level.getMaxBuildHeight() - 5) continue;
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
                    || !level.getBlockState(top.above()).isAir()
                    || !enclosedByBanks(level,top))
                    continue;

                // Surface y: atmosphere; y-1: water/ice surface; y-2: liquid
                // water even after vanilla's cold-biome top-layer freezing;
                // y-3: stable gravel riverbed. Ground lies beneath that.
                // Shallow, bounded water with no cross-chunk writes.
                // All writes stay inside the current 16x16 chunk.
                level.setBlock(top.below(3), Blocks.GRAVEL.defaultBlockState(), 2);
                level.setBlock(top.below(2), Blocks.WATER.defaultBlockState(), 2);
                level.setBlock(top.below(), frozen
                    ? Blocks.ICE.defaultBlockState()
                    : Blocks.WATER.defaultBlockState(), 2);
                level.setBlock(top, Blocks.AIR.defaultBlockState(), 2);
                made = true;
            }
        }
        return made;
    }
}

package org.realscale.neoforge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.ServerLevelData;
import org.realscale.neoforge.SkyOceanBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces Minecraft's 11x11 FULL-chunk spawn-safety spiral for RealScale
 * worlds. Vanilla's scan can generate up to 121 complete tall chunks before
 * the normal "Preparing start region" phase begins.
 *
 * Keep vanilla behavior for debug worlds, bonus-chest worlds, and every
 * non-RealScale biome source. For RealScale, preserve the climate-selected
 * spawn chunk and sample a small fixed set of columns directly from the
 * generator; getBaseHeight does not request or generate chunks.
 */
@Mixin(value = MinecraftServer.class, remap = false)
public abstract class FastInitialSpawnMixin {
    @Unique private static final int[][] REALSCALE$SPAWN_OFFSETS = {
            {8, 8}, {4, 4}, {12, 4}, {4, 12}, {12, 12},
            {8, 4}, {8, 12}, {4, 8}, {12, 8}
    };

    @Inject(method = "setInitialSpawn", at = @At("HEAD"),
            cancellable = true, remap = false)
    private static void realscale$fastInitialSpawn(ServerLevel level,
            ServerLevelData levelData, boolean bonusChest, boolean debugWorld,
            CallbackInfo ci) {
        if (debugWorld || bonusChest || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        ServerChunkCache chunks = level.getChunkSource();
        ChunkGenerator generator = chunks.getGenerator();
        if (!(generator.getBiomeSource() instanceof SkyOceanBiomeSource)) {
            return;
        }

        RandomState randomState = chunks.randomState();
        long climateStart = System.nanoTime();
        BlockPos climateTarget = randomState.sampler().findSpawnPosition();
        long climateNanos = System.nanoTime() - climateStart;
        ChunkPos spawnChunk = new ChunkPos(climateTarget);
        long heightStart = System.nanoTime();
        BlockPos spawn = realscale$sampleSpawnColumn(
                level, generator, randomState, spawnChunk);
        long heightNanos = System.nanoTime() - heightStart;

        levelData.setSpawn(spawn, 0.0F);
        System.err.println("REALSCALE_FAST_INITIAL_SPAWN x=" + spawn.getX()
                + " y=" + spawn.getY() + " z=" + spawn.getZ()
                + " climateChunk=" + spawnChunk.x + "," + spawnChunk.z
                + " sampledColumns=" + REALSCALE$SPAWN_OFFSETS.length
                + " climate_ms=" + (climateNanos / 1_000_000.0)
                + " height_ms=" + (heightNanos / 1_000_000.0)
                + " total_ms=" + ((climateNanos + heightNanos) / 1_000_000.0));
        ci.cancel();
    }

    @Unique
    private static BlockPos realscale$sampleSpawnColumn(ServerLevel level,
            ChunkGenerator generator, RandomState randomState,
            ChunkPos chunk) {
        BlockPos bestDry = null;
        int bestDryFloor = Integer.MIN_VALUE;
        BlockPos bestFallback = null;
        int bestFallbackFloor = Integer.MIN_VALUE;
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        for (int[] offset : REALSCALE$SPAWN_OFFSETS) {
            int x = chunk.getMinBlockX() + offset[0];
            int z = chunk.getMinBlockZ() + offset[1];
            int motion = generator.getBaseHeight(x, z,
                    Heightmap.Types.MOTION_BLOCKING, level, randomState);
            if (motion <= minY + 1 || motion >= maxY - 1) {
                continue;
            }

            int floor = generator.getBaseHeight(x, z,
                    Heightmap.Types.OCEAN_FLOOR, level, randomState);
            BlockPos candidate = new BlockPos(x, motion, z);
            if (floor > bestFallbackFloor) {
                bestFallbackFloor = floor;
                bestFallback = candidate;
            }

            // Match the vanilla rejection for a water-covered/obstructed
            // surface without loading a FULL chunk to inspect block states.
            if (motion > floor) {
                int surface = generator.getBaseHeight(x, z,
                        Heightmap.Types.WORLD_SURFACE, level, randomState);
                if (surface <= motion && surface > floor) {
                    continue;
                }
            }

            if (floor > bestDryFloor) {
                bestDryFloor = floor;
                bestDry = candidate;
            }
        }

        if (bestDry != null) {
            return bestDry;
        }
        if (bestFallback != null) {
            // The climate sampler normally selects land. If all nine sampled
            // columns are water/foliage, retain the best local terrain column
            // rather than generating up to 120 additional chunks.
            return bestFallback;
        }

        int x = chunk.getMinBlockX() + 8;
        int z = chunk.getMinBlockZ() + 8;
        int y = Math.max(minY + 1, Math.min(maxY - 2, level.getSeaLevel() + 1));
        return new BlockPos(x, y, z);
    }
}

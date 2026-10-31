package org.realscale.neoforge;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.slf4j.Logger;

/**
 * Selects the first spawn for RealScale Overworlds from a bounded set of
 * deterministic terrain samples. Sampling uses generator base terrain APIs
 * and never asks the server to load or force-load chunks.
 */
public final class RealScaleSpawnPosition {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long SAMPLE_SALT = 0x52534C535041574EL;
    private static final int SLOPE_SAMPLE_STEP = 8;
    private static final int MAX_CANDIDATES = 40;

    private record Candidate(int x, int z, int surfaceY, int relief, int distanceSquared) {}
    private record SpawnChoice(BlockPos position, int targetY, int relief) {}

    private RealScaleSpawnPosition() {}

    public static void onCreateSpawnPosition(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!Level.OVERWORLD.equals(level.dimension())) return;
        if (!(level.getChunkSource().getGenerator()
                instanceof NoiseBasedChunkGenerator generator)) return;

        var settingsHolder = generator.generatorSettings();
        ResourceLocation settingsId = settingsHolder.unwrapKey()
                .map(key -> key.location())
                .orElseGet(() -> level.registryAccess()
                        .registryOrThrow(Registries.NOISE_SETTINGS)
                        .getResourceKey(settingsHolder.value())
                        .map(key -> key.location())
                        .orElse(null));
        if (settingsId == null) {
            LOGGER.warn("RealScale spawn selection skipped: noise-settings registry key was unavailable");
            return;
        }
        if (!"realscale".equals(settingsId.getNamespace())) return;

        RealScaleSpawnMath.Profile profile =
                RealScaleSpawnMath.profileFor(settingsId.getPath());
        RandomState randomState = level.getChunkSource().randomState();
        SpawnChoice best = findBestSpawn(level, generator, randomState, profile);
        if (best == null) {
            LOGGER.warn("RealScale found no safe initial spawn candidate within {} blocks; keeping vanilla selection",
                    profile.searchRadius());
            return;
        }

        event.getSettings().setSpawn(best.position(), 0.0F);
        event.setCanceled(true);
        LOGGER.info("RealScale selected initial spawn x={} y={} z={} targetY={} relief={} settings={}",
                best.position().getX(), best.position().getY(), best.position().getZ(),
                best.targetY(), best.relief(), settingsId);
    }

    private static SpawnChoice findBestSpawn(
            ServerLevel level,
            NoiseBasedChunkGenerator generator,
            RandomState randomState,
            RealScaleSpawnMath.Profile profile
    ) {
        RandomSource random = RandomSource.create(
                RealScaleSpawnMath.mix64(level.getSeed() ^ SAMPLE_SALT));
        Map<Long, Integer> heights = new HashMap<>();
        List<Candidate> candidates = new ArrayList<>(profile.candidateCount());
        int minimumY = generator.getMinY();
        int maximumY = minimumY + generator.getGenDepth();

        for (int i = 0; i < Math.min(profile.candidateCount(), MAX_CANDIDATES); i++) {
            int x;
            int z;
            if (i == 0) {
                x = 0;
                z = 0;
            } else {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double radius = Math.sqrt(random.nextDouble()) * profile.searchRadius();
                x = (int) Math.round(Math.cos(angle) * radius);
                z = (int) Math.round(Math.sin(angle) * radius);
            }

            int surfaceY = heightAt(generator, level, randomState, x, z, heights);
            if (!RealScaleSpawnMath.isHeightAllowed(surfaceY, minimumY, maximumY)) continue;
            if (!isSafeSurface(generator, level, randomState, x, z, surfaceY)) continue;

            int west = heightAt(generator, level, randomState,
                    x - SLOPE_SAMPLE_STEP, z, heights);
            int east = heightAt(generator, level, randomState,
                    x + SLOPE_SAMPLE_STEP, z, heights);
            int north = heightAt(generator, level, randomState,
                    x, z - SLOPE_SAMPLE_STEP, heights);
            int south = heightAt(generator, level, randomState,
                    x, z + SLOPE_SAMPLE_STEP, heights);
            int minimum = Math.min(surfaceY, Math.min(Math.min(west, east), Math.min(north, south)));
            int maximum = Math.max(surfaceY, Math.max(Math.max(west, east), Math.max(north, south)));
            int relief = maximum - minimum;
            int distanceSquared = x * x + z * z;
            candidates.add(new Candidate(x, z, surfaceY, relief, distanceSquared));
        }

        if (candidates.isEmpty()) return null;
        int[] sampledHeights = candidates.stream().mapToInt(Candidate::surfaceY).toArray();
        int targetY = RealScaleSpawnMath.targetHeight(level.getSeed(), profile, sampledHeights);

        Candidate best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Candidate candidate : candidates) {
            double score = RealScaleSpawnMath.score(
                    candidate.surfaceY(), targetY, candidate.relief(), candidate.distanceSquared());
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best == null) return null;
        BlockPos position = BlockPos.containing(
                best.x() + 0.5D, best.surfaceY(), best.z() + 0.5D);
        return new SpawnChoice(position, targetY, best.relief());
    }

    private static int heightAt(
            ChunkGenerator generator,
            ServerLevel level,
            RandomState randomState,
            int x,
            int z,
            Map<Long, Integer> cache
    ) {
        long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        Integer cached = cache.get(key);
        if (cached != null) return cached;
        int height = generator.getBaseHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
        cache.put(key, height);
        return height;
    }

    private static boolean isSafeSurface(
            NoiseBasedChunkGenerator generator,
            ServerLevel level,
            RandomState randomState,
            int x,
            int z,
            int surfaceY
    ) {
        NoiseColumn column = generator.getBaseColumn(x, z, level, randomState);
        BlockState ground = column.getBlock(surfaceY - 1);
        BlockState feet = column.getBlock(surfaceY);
        BlockState head = column.getBlock(surfaceY + 1);
        if (!ground.blocksMotion() || !ground.getFluidState().isEmpty()) return false;
        if (!feet.isAir() || !head.isAir()) return false;
        if (!feet.getFluidState().isEmpty() || !head.getFluidState().isEmpty()) return false;

        Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(
                Math.floorDiv(x, 4),
                Math.floorDiv(surfaceY - 1, 4),
                Math.floorDiv(z, 4),
                randomState.sampler());
        String biomePath = biome.unwrapKey()
                .map(key -> key.location().getPath())
                .orElse("");
        return !biomePath.endsWith("ocean") && !biomePath.endsWith("river");
    }
}

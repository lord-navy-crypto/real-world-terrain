package org.realscale.neoforge;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.realscale.neoforge.client.clipmap.GeometryClipmapLayout;
import org.realscale.neoforge.client.clipmap.SurfaceFeatureProxyProvider;
import org.realscale.neoforge.client.clipmap.SurfaceFeatureProxyRegistry;
import org.realscale.neoforge.client.clipmap.VisibleSurfaceProxy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds low-detail proxies from deterministic worldgen inputs, without
 * constructing distant chunks. Built-ins approximate tree canopies and
 * reproduce the existing sky-island site lattice; other features can register
 * exterior-only providers through SurfaceFeatureProxyRegistry.
 */
public final class VisibleSurfaceProxyGenerator {
    private static final int MAX_PROXIES = 4096;
    private static final ResourceKey<Biome> SKY_BLOSSOM_BIOME =
            ResourceKey.create(Registries.BIOME,
                    ResourceLocation.fromNamespaceAndPath(
                            "realscale", "sky_blossom_isles"));

    private VisibleSurfaceProxyGenerator() {}

    public static List<VisibleSurfaceProxy> collect(
            NoiseBasedChunkGenerator generator,
            RandomState randomState,
            GeometryClipmapLayout.Layout layout,
            long worldSeed) {
        return collect(generator, randomState, layout, worldSeed, () -> true);
    }

    public static List<VisibleSurfaceProxy> collect(NoiseBasedChunkGenerator generator,
            RandomState randomState, GeometryClipmapLayout.Layout layout, long worldSeed,
            java.util.function.BooleanSupplier current) {
        checkCurrent(current);
        DensityFunction terrainDensity = randomState.router().depth();
        BiomeSource biomeSource = generator.getBiomeSource();
        SurfaceFeatureProxyProvider.Context context =
                new SurfaceFeatureProxyProvider.Context(worldSeed, layout,
                        terrainDensity, biomeSource, randomState.sampler(), current);
        List<VisibleSurfaceProxy> output = new ArrayList<>();
        boolean debug = Boolean.getBoolean("realscale.experimental.geometryclipmap.debug");
        long started = System.nanoTime();
        collectCanopyProxies(context, output, current);
        reportStage(debug, "canopies", started, output.size());
        started = System.nanoTime();
        collectSkyIslandProxies(context, biomeSource, output, current);
        reportStage(debug, "sky islands", started, output.size());
        checkCurrent(current);
        started = System.nanoTime();
        SurfaceFeatureProxyRegistry.collect(context, output);
        checkCurrent(current);
        reportStage(debug, "registered exterior providers", started, output.size());
        if (output.size() > MAX_PROXIES) {
            return List.copyOf(output.subList(0, MAX_PROXIES));
        }
        return List.copyOf(output);
    }

    private static void collectCanopyProxies(
            SurfaceFeatureProxyProvider.Context context,
            List<VisibleSurfaceProxy> output, java.util.function.BooleanSupplier current) {
        for (GeometryClipmapLayout.Level level : context.layout().levels()) {
            long step = Math.max(64L, Math.multiplyExact(
                    level.cellSpacingBlocks(), 8L));
            GeometryClipmapLayout.Bounds outer = level.outerBounds();
            GeometryClipmapLayout.Bounds inner = level.innerBounds();
            for (long x = outer.minX(); x < outer.maxX(); x += step) {
                for (long z = outer.minZ(); z < outer.maxZ(); z += step) {
                    checkCurrent(current);
                    if (inner.containsStrict(x, z)) continue;
                    int blockX = Math.toIntExact(x + step / 2L);
                    int blockZ = Math.toIntExact(z + step / 2L);
                    float surface = context.surfaceHeight(blockX, blockZ);
                    if (surface < 65.0f) continue;
                    int surfaceY = Math.round(surface);
                    String biome = context.biomePathAt(blockX, surfaceY, blockZ);
                    float chance = treeChance(biome);
                    if (chance <= 0.0f) continue;
                    long hash = mix(context.worldSeed()
                            ^ ((long) blockX * 0x9E3779B97F4A7C15L)
                            ^ ((long) blockZ * 0xC2B2AE3D27D4EB4FL));
                    double roll = (hash >>> 11) * 0x1.0p-53;
                    if (roll > chance) continue;

                    float radius = 3.0f + (float) Math.floorMod(hash >>> 7, 4L);
                    float height = 8.0f + (float) Math.floorMod(hash >>> 17, 8L);
                    output.add(new VisibleSurfaceProxy(blockX, surfaceY, blockZ,
                            radius, height, treeColor(biome),
                            VisibleSurfaceProxy.TREE_CANOPY));
                    if (output.size() >= MAX_PROXIES) return;
                }
            }
        }
    }

    private static void collectSkyIslandProxies(
            SurfaceFeatureProxyProvider.Context context,
            BiomeSource biomeSource,
            List<VisibleSurfaceProxy> output, java.util.function.BooleanSupplier current) {
        if (!(biomeSource instanceof SkyOceanBiomeSource)) return;
        GeometryClipmapLayout.Bounds bounds =
                context.layout().levels().get(context.layout().levels().size() - 1)
                        .outerBounds();
        int grid = SkyIslandLayout.GRID;
        long minCellX = Math.floorDiv(bounds.minX(), grid) - 1L;
        long maxCellX = Math.floorDiv(bounds.maxX(), grid) + 1L;
        long minCellZ = Math.floorDiv(bounds.minZ(), grid) - 1L;
        long maxCellZ = Math.floorDiv(bounds.maxZ(), grid) + 1L;
        Set<Long> emitted = new HashSet<>();

        for (long gx = minCellX; gx <= maxCellX; gx++) {
            for (long gz = minCellZ; gz <= maxCellZ; gz++) {
                checkCurrent(current);
                int referenceX = Math.toIntExact(gx * grid);
                int referenceZ = Math.toIntExact(gz * grid);
                SkyIslandLayout.Site base = SkyIslandLayout.at(referenceX, referenceZ);
                for (int slot = 0; slot < SkyIslandLayout.SLOTS; slot++) {
                    checkCurrent(current);
                    SkyIslandLayout.Site site = SkyIslandLayout.satellite(base, slot);
                    if (site.x() + site.radius() < bounds.minX()
                            || site.x() - site.radius() >= bounds.maxX()
                            || site.z() + site.radius() < bounds.minZ()
                            || site.z() - site.radius() >= bounds.maxZ()) {
                        continue;
                    }
                    long key = (((long) site.x()) << 32) ^ (site.z() & 0xffffffffL);
                    if (!emitted.add(key)) continue;
                    checkCurrent(current);
                    Holder<Biome> biome = context.biomeAt(
                            site.x(), site.topY(), site.z());
                    if (!biome.is(SKY_BLOSSOM_BIOME)) continue;
                    output.add(new VisibleSurfaceProxy(site.x(),
                            site.topY() - 16, site.z(),
                            site.radius() * 1.8f, 24.0f,
                            0x777D78, VisibleSurfaceProxy.FLOATING_ISLAND));
                    if (output.size() >= MAX_PROXIES) return;
                }
            }
        }
    }

    private static void reportStage(boolean debug, String stage, long started, int count) {
        if (debug) System.out.printf(java.util.Locale.ROOT,
                "[RealScale Geometry Clipmap] proxy stage=%s cumulative=%d elapsed=%.1fms%n",
                stage, count, (System.nanoTime() - started) / 1_000_000.0);
    }

    private static void checkCurrent(java.util.function.BooleanSupplier current) {
        if (!current.getAsBoolean()) throw new java.util.concurrent.CancellationException();
    }

    private static float treeChance(String biome) {
        if (biome.contains("jungle")) return 0.68f;
        if (biome.contains("old_growth")) return 0.56f;
        if (biome.contains("forest") || biome.contains("taiga")
                || biome.contains("grove")) return 0.40f;
        if (biome.contains("swamp") || biome.contains("mangrove")) return 0.30f;
        if (biome.contains("cherry")) return 0.28f;
        return 0.0f;
    }

    private static int treeColor(String biome) {
        if (biome.contains("cherry")) return 0xD9A0C4;
        if (biome.contains("jungle")) return 0x235A2A;
        if (biome.contains("taiga") || biome.contains("grove")) return 0x315C42;
        if (biome.contains("swamp") || biome.contains("mangrove")) return 0x416447;
        return 0x3F7135;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}

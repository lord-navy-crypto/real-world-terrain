package org.realscale.neoforge.client.clipmap;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.realscale.neoforge.client.clipmap.GeometryClipmapLayout;

import java.util.List;

/**
 * Extension point for features that can provide compact exterior proxies.
 * Providers must not enumerate hidden block interiors or request far chunks.
 */
@FunctionalInterface
public interface SurfaceFeatureProxyProvider {
    void collect(Context context, List<VisibleSurfaceProxy> output);

    record Context(long worldSeed,
            GeometryClipmapLayout.Layout layout,
            DensityFunction terrainDensity,
            BiomeSource biomeSource,
            Climate.Sampler climateSampler,
            java.util.function.BooleanSupplier stillCurrent) {
        public Context(long worldSeed, GeometryClipmapLayout.Layout layout,
                DensityFunction terrainDensity, BiomeSource biomeSource,
                Climate.Sampler climateSampler) {
            this(worldSeed, layout, terrainDensity, biomeSource, climateSampler, () -> true);
        }

        public void checkCurrent() {
            if (!stillCurrent.getAsBoolean()) throw new java.util.concurrent.CancellationException();
        }
        public float surfaceHeight(int blockX, int blockZ) {
            checkCurrent();
            double signal = terrainDensity.compute(
                    new DensityFunction.SinglePointContext(blockX, 63, blockZ));
            return (float) Math.max(-64.0, Math.min(2032.0,
                    NaturalAutomateHeightMapping.surfaceY(signal)));
        }

        public Holder<Biome> biomeAt(int blockX, int blockY, int blockZ) {
            checkCurrent();
            return biomeSource.getNoiseBiome(
                    QuartPos.fromBlock(blockX),
                    QuartPos.fromBlock(blockY),
                    QuartPos.fromBlock(blockZ),
                    climateSampler);
        }

        public String biomePathAt(int blockX, int blockY, int blockZ) {
            return biomeAt(blockX, blockY, blockZ).unwrapKey()
                    .map(ResourceKey::location)
                    .map(location -> location.getPath())
                    .orElse("");
        }
    }
}

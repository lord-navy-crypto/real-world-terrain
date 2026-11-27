package org.realscale.neoforge;

import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.biome.BiomeSource;


/** Restrict global NeoForge biome-modifier decorations to RealScale worlds.
 * Never changes vanilla/foreign biome selection or requests adjacent chunks.
 */
public final class TVAWorldGuard {
    private TVAWorldGuard() {}

    public static boolean hasRealScaleGenerator(RealScaleFeatureContext context) {
        if (!(context.chunkGenerator() instanceof NoiseBasedChunkGenerator generator))
            return false;
        var settingsKey = generator.generatorSettings().unwrapKey();
        return settingsKey.isPresent()
                && "realscale".equals(settingsKey.orElseThrow().identifier().getNamespace());
    }
    /** Opt-in wonder experiments, never enabled on the sacred timeline. */
    public static boolean hasExperimentalWonders(RealScaleFeatureContext context) {
        if (!hasRealScaleGenerator(context)) return false;
        BiomeSource source=context.chunkGenerator().getBiomeSource();
        return source instanceof GeoWondersBiomeSource
            || source instanceof SkyOceanBiomeSource sky && sky.containsGeoWonders();
    }

}

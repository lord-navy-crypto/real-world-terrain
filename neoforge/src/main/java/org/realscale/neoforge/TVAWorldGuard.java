package org.realscale.neoforge;

import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/** Restrict global NeoForge biome-modifier decorations to RealScale worlds.
 * Never changes vanilla/foreign biome selection or requests adjacent chunks.
 */
public final class TVAWorldGuard {
    private TVAWorldGuard() {}

    public static boolean hasRealScaleGenerator(FeaturePlaceContext<?> context) {
        if (!(context.chunkGenerator() instanceof NoiseBasedChunkGenerator generator))
            return false;
        return generator.generatorSettings().unwrapKey()
                .map(key -> "realscale".equals(key.location().getNamespace()))
                .orElse(false);
    }
    /** Opt-in wonder experiments, never enabled on the sacred timeline. */
    public static boolean hasExperimentalWonders(FeaturePlaceContext<?> context) {
        if (!hasRealScaleGenerator(context)) return false;
        BiomeSource source=context.chunkGenerator().getBiomeSource();
        return source instanceof GeoWondersBiomeSource
            || source instanceof SkyOceanBiomeSource sky && sky.containsGeoWonders()
            || source instanceof CrossRealmShowcaseBiomeSource showcase
               && showcase.containsGeoWonders();
    }

    /** Opt-in cross-realm invasion, not an implicit effect of any RealScale preset. */
    public static boolean hasCrossRealmInvasion(FeaturePlaceContext<?> context) {
        if (!hasRealScaleGenerator(context)) return false;
        BiomeSource source=context.chunkGenerator().getBiomeSource();
        return source instanceof CrossRealmBiomeSource
            || source instanceof CrossRealmShowcaseBiomeSource
            || source instanceof EndAccentBiomeSource end && end.containsCrossRealm()
            || source instanceof SkyOceanBiomeSource sky && sky.containsCrossRealm();
    }
}

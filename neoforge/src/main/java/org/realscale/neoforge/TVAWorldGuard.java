package org.realscale.neoforge;

import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
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
}

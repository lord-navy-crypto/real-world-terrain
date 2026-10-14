package org.realscale.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Opt-in sky island decorator layered on top of the existing RealScale ecotone
 * source. The land/ocean biome below Y~170 is never modified. A potential sky
 * biome is selected only when ALL radial climate probes remain oceanic and the
 * center is in deep ocean. This avoids loading neighboring chunks in worldgen.
 */
public final class SkyOceanBiomeSource extends BiomeSource {
    public static final MapCodec<SkyOceanBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i ->
            i.group(BiomeSource.CODEC.fieldOf("source").forGetter(SkyOceanBiomeSource::source),
                    RegistryOps.retrieveGetter(Registries.BIOME))
                    .apply(i, SkyOceanBiomeSource::new));
    private final BiomeSource source;
    private final Holder<Biome> blossomIsle;
    private final ThreadLocal<EligibilityCache> cache = ThreadLocal.withInitial(EligibilityCache::new);

    private static final class EligibilityCache {
        Climate.Sampler sampler;
        final LinkedHashMap<Long, Boolean> result = new LinkedHashMap<>(256, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, Boolean> first) {
                return size() > 256;
            }
        };
    }

    private SkyOceanBiomeSource(BiomeSource source, HolderGetter<Biome> registry) {
        this.source = source;
        blossomIsle = registry.getOrThrow(ResourceKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath("realscale", "sky_blossom_isles")));
    }
    private BiomeSource source() { return source; }

    @Override protected MapCodec<? extends BiomeSource> codec() { return CODEC; }
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(source.possibleBiomes().stream(), Stream.of(blossomIsle)).distinct();
    }

    private String underlying(int x, int z, Climate.Sampler sampler) {
        return source.getNoiseBiome(Math.floorDiv(x,4), 16, Math.floorDiv(z,4), sampler)
                .unwrapKey().map(k -> {
                    var loc = k.location();
                    return "minecraft".equals(loc.getNamespace()) ? loc.getPath() : "";
                }).orElse("");
    }
    private boolean deepOceanCenter(SkyIslandLayout.Site s, Climate.Sampler sampler) {
        String atCenter = underlying(s.x(), s.z(), sampler);
        if (!atCenter.startsWith("deep_") || !atCenter.endsWith("ocean"))
            return false;
        // Ocean-only rings; no nearby coast, river, island or mainland samples.
        // This is a conservative 24-directional-point approximation to the
        // nearest land distance, not an exact continent distance field.
        int[] distances = {96, 224, SkyIslandLayout.RING_RADIUS};
        final double[] angles = {0, Math.PI / 4, Math.PI / 2, 3 * Math.PI / 4,
                Math.PI, 5 * Math.PI / 4, 3 * Math.PI / 2, 7 * Math.PI / 4};
        for (int d : distances) for (double angle : angles) {
            int px = s.x() + (int) Math.round(d * Math.cos(angle));
            int pz = s.z() + (int) Math.round(d * Math.sin(angle));
            if (!underlying(px, pz, sampler).contains("ocean"))
                return false;
        }
        return true;
    }
    private boolean eligible(SkyIslandLayout.Site s, Climate.Sampler sampler) {
        EligibilityCache c = cache.get();
        if (c.sampler != sampler) {
            c.sampler = sampler;
            c.result.clear();
        }
        long key = ((long) s.x() << 32) | (s.z() & 0xffffffffL);
        Boolean stored = c.result.get(key);
        if (stored != null) return stored;
        boolean checked = deepOceanCenter(s, sampler);
        c.result.put(key, checked);
        return checked;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        // Expensive ocean qualification never happens for sea floor, normal
        // terrain or caves. Surface and ocean climate remain byte-for-byte
        // controlled by the wrapped multi-noise/ecotone source.
        int y = quartY * 4;
        if (y < 168 || y > 215)
            return source.getNoiseBiome(quartX, quartY, quartZ, sampler);
        int x = quartX * 4, z = quartZ * 4;
        SkyIslandLayout.Site site = SkyIslandLayout.at(x, z);
        if (SkyIslandLayout.insideBiome(site, x, y, z) && eligible(site, sampler))
            return blossomIsle;
        return source.getNoiseBiome(quartX, quartY, quartZ, sampler);
    }
}

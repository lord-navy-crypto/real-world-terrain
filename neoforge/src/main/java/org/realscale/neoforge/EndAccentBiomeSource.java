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
import java.util.stream.Stream;

/**
 * Small, additive Grand End biome islands. The inner BiomeSource is always
 * delegated first: foreign-mod biomes, Nether/Overworld invasion biomes and
 * every non-End ID are returned byte-for-byte unchanged. This is NOT a
 * wholesale End generator replacement. Dragon/main islands remain vanilla.
 *
 * Opt-in through the new unified world presets only; historical worlds retain
 * their saved original biome-source codec and will not be rewritten.
 */
public final class EndAccentBiomeSource extends BiomeSource {
    public static final MapCodec<EndAccentBiomeSource> CODEC =
        RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("source").forGetter(EndAccentBiomeSource::source),
            RegistryOps.retrieveGetter(Registries.BIOME)
        ).apply(i, EndAccentBiomeSource::new));

    private static final int CELL = 512;
    private static final long DRAGON_ISLAND_EXCLUSION = 1024L * 1024L;
    private final BiomeSource source;
    private final Holder<Biome> chorus, crystal, shattered;

    private EndAccentBiomeSource(BiomeSource source, HolderGetter<Biome> registry) {
        this.source = source;
        chorus = biome(registry, "end_chorus_gardens");
        crystal = biome(registry, "end_crystal_barrens");
        shattered = biome(registry, "end_shattered_glades");
    }

    private static Holder<Biome> biome(HolderGetter<Biome> registry, String id) {
        return registry.getOrThrow(ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("realscale", id)));
    }

    private BiomeSource source() { return source; }

    /** Used only by our opt-in decoration guard; foreign biomes remain intact. */
    public boolean containsCrossRealm() {
        return source instanceof CrossRealmBiomeSource
            || source instanceof CrossRealmShowcaseBiomeSource;
    }

    @Override protected MapCodec<? extends BiomeSource> codec() { return CODEC; }

    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(source.possibleBiomes().stream(),
            Stream.of(chorus, crystal, shattered)).distinct();
    }

    private static long mix(long x) {
        x ^= x >>> 30; x *= 0xbf58476d1ce4e5b9L;
        x ^= x >>> 27; x *= 0x94d049bb133111ebL;
        return x ^ (x >>> 31);
    }

    /** Vanilla End biomes only. Foreign mods' biome IDs always win. */
    public static boolean eligibleBaseBiome(String id) {
        return id.equals("minecraft:end_highlands")
            || id.equals("minecraft:end_midlands")
            || id.equals("minecraft:end_barrens")
            || id.equals("minecraft:small_end_islands");
    }

    @Override public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ,
                                                  Climate.Sampler sampler) {
        Holder<Biome> original = source.getNoiseBiome(quartX, quartY, quartZ, sampler);
        String id = original.unwrapKey().map(k -> k.location().toString()).orElse("");
        if (!eligibleBaseBiome(id)) return original;

        int y = quartY * 4;
        if (y < 32 || y > 384) return original;

        long x = (long)quartX * 4, z = (long)quartZ * 4;
        if (x * x + z * z < DRAGON_ISLAND_EXCLUSION) return original;

        long gx = Math.floorDiv(x, CELL), gz = Math.floorDiv(z, CELL);
        long hash = mix(gx * 0x9e3779b97f4a7c15L
                      ^ gz * 0xc2b2ae3d27d4eb4fL ^ 0x71a6e31dL);
        // On average one accent island per five 512-block cells, not a
        // dimension-wide biome replacement.
        if (Math.floorMod(hash, 5L) != 0) return original;

        long cx = gx * CELL + 256L + Math.floorMod(hash >>> 12, 129L) - 64L;
        long cz = gz * CELL + 256L + Math.floorMod(hash >>> 27, 129L) - 64L;
        int radius = 88 + (int)Math.floorMod(hash >>> 42, 49L);
        long dx = x - cx, dz = z - cz;
        if (dx * dx + dz * dz > (long)radius * radius) return original;

        return switch ((int)Math.floorMod(hash >>> 54, 3L)) {
            case 0 -> chorus;
            case 1 -> crystal;
            default -> shattered;
        };
    }
}

package org.realscale.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A vanilla-compatible biome-source decorator that picks RealScale ecotones
 * within a narrow band of real multi-noise biome boundaries. Coordinates are
 * quart positions, never generated chunk/block state or chunk boundaries.
 *
 * <p>Opt-in only: a RealScale world preset must explicitly use this codec.
 * The underlying vanilla biome source is unchanged everywhere except the
 * surface transition strip. Rivers and oceans are never replaced.</p>
 */
public final class NaturalEcotoneBiomeSource extends BiomeSource {
    public static final MapCodec<NaturalEcotoneBiomeSource> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("source").forGetter(NaturalEcotoneBiomeSource::source),
                    RegistryOps.retrieveGetter(Registries.BIOME)
            ).apply(instance, NaturalEcotoneBiomeSource::new));

    private record Rule(String target, int widthBlocks, Set<String> left, Set<String> right) {
        boolean matches(String first, String second) {
            return (left.contains(first) && right.contains(second))
                    || (right.contains(first) && left.contains(second));
        }
    }

    private static Rule rule(String id, int width, String left, String right) {
        return new Rule(id, width, Set.copyOf(Arrays.asList(left.split(","))),
                Set.copyOf(Arrays.asList(right.split(","))));
    }

    private static final String FOREST = "forest,birch_forest,old_growth_birch_forest,dark_forest";
    private static final String TAIGA = "taiga,old_growth_pine_taiga,old_growth_spruce_taiga,snowy_taiga";
    private static final String PLAINS = "plains,sunflower_plains";
    private static final String SAVANNA = "savanna,savanna_plateau,windswept_savanna";
    private static final String BADLANDS = "badlands,wooded_badlands,eroded_badlands";
    private static final String PEAKS = "stony_peaks,jagged_peaks,frozen_peaks,windswept_hills,windswept_gravelly_hills";
    private static final String SNOW = "snowy_plains,snowy_slopes,snowy_taiga,ice_spikes";
    private static final String RIVER = "river,frozen_river";

    /** Declared in priority order for three-way border corners. */
    private static final List<Rule> RULES = List.of(
            rule("woodland_meadow",32,FOREST,"meadow,"+PLAINS),
            rule("forest_steppe",40,FOREST,SAVANNA),
            rule("marshy_woodland",24,"forest,birch_forest,old_growth_birch_forest","swamp,mangrove_swamp"),
            rule("semi_arid_scrubland",48,"desert",SAVANNA),
            rule("subalpine_woodland",32,TAIGA,PEAKS),
            rule("rocky_alpine_meadow",24,"meadow",PEAKS),
            rule("snowy_treeline",24,TAIGA,"snowy_slopes,snowy_taiga"),
            rule("floodplain_margin",16,PLAINS,RIVER),
            rule("birch_conifer_transition",32,"birch_forest,old_growth_birch_forest",TAIGA),
            rule("wet_meadow",24,PLAINS,"swamp"),
            rule("riverine_woodland",16,FOREST,RIVER),
            rule("heathland",32,PLAINS,"windswept_hills,windswept_gravelly_hills"),
            rule("shrub_prairie",32,PLAINS,SAVANNA),
            rule("oasis_fringe",16,"desert",RIVER),
            rule("badlands_fringe",32,BADLANDS,PLAINS),
            rule("scree_transition",32,"stony_peaks","windswept_gravelly_hills"),
            rule("reed_marsh_edge",24,"swamp",RIVER),
            rule("lake_woodland_margin",24,FOREST,"beach"),
            rule("dune_scrub",32,"beach",SAVANNA),
            rule("coastal_heath",32,"beach",PLAINS),
            rule("temperate_to_birch",32,"forest","birch_forest,old_growth_birch_forest"),
            rule("oak_to_dark_forest",32,"forest","dark_forest"),
            rule("taiga_to_tundra",32,TAIGA,"snowy_plains,ice_spikes"),
            rule("snowfield_margin",24,"meadow",SNOW),
            rule("mountain_wetland",24,"meadow","grove"),
            rule("gravel_shore_fringe",24,"stony_shore","taiga,old_growth_pine_taiga,old_growth_spruce_taiga"),
            rule("mangrove_transition",24,"swamp","mangrove_swamp"),
            rule("tropical_forest_edge",32,"jungle,sparse_jungle",PLAINS),
            rule("bamboo_jungle_edge",24,"bamboo_jungle","jungle,sparse_jungle"),
            rule("savanna_badlands",32,SAVANNA,BADLANDS),
            rule("cold_desert_scrub",24,"desert","snowy_plains"),
            rule("warm_river_margin",16,SAVANNA,RIVER),
            rule("pine_oak_mosaic",32,"forest",TAIGA),
            rule("rocky_plateau_grass",32,"windswept_hills","stony_peaks"),
            rule("frost_shrub_tundra",32,"snowy_plains","grove"),
            rule("lichen_rockland",32,"stony_peaks","snowy_slopes"),
            rule("seasonal_floodforest",16,"dark_forest","swamp"),
            rule("meadow_bog_margin",24,"meadow","swamp"),
            rule("coastal_dune_meadow",32,"beach","meadow"),
            rule("terracotta_scrub",32,BADLANDS,"desert")
    );

    private static final Set<String> CANDIDATE_IDS = RULES.stream()
            .flatMap(rule -> Stream.concat(rule.left().stream(), rule.right().stream()))
            .collect(Collectors.toUnmodifiableSet());
    private static final int MAX_SEARCH_QUARTS = RULES.stream()
            .mapToInt(rule -> rule.widthBlocks() / 8).max().orElse(0);

    /** Bounded per-thread climate cache, invalidated when the climate sampler changes. */
    private static final class SamplingCache {
        private static final int LIMIT = 8192;
        Climate.Sampler sampler;
        final LinkedHashMap<Long, Holder<Biome>> positions =
                new LinkedHashMap<>(LIMIT, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<Long, Holder<Biome>> eldest) {
                        return size() > LIMIT;
                    }
                };
    }

    private final BiomeSource source;
    private final ThreadLocal<SamplingCache> cached = ThreadLocal.withInitial(SamplingCache::new);
    private final Map<String, Holder<Biome>> transitionBiomes;

    private NaturalEcotoneBiomeSource(BiomeSource source, HolderGetter<Biome> biomes) {
        this.source = source;
        this.transitionBiomes = new HashMap<>();
        for (Rule rule : RULES) {
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath("realscale", rule.target());
            transitionBiomes.put(rule.target(), biomes.getOrThrow(ResourceKey.create(Registries.BIOME, location)));
        }
    }

    private Holder<Biome> sampleBase(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        SamplingCache cache = cached.get();
        if (cache.sampler != sampler) {
            cache.sampler = sampler;
            cache.positions.clear();
        }
        long key = BlockPos.asLong(quartX, quartY, quartZ);
        Holder<Biome> existing = cache.positions.get(key);
        if (existing != null) return existing;
        Holder<Biome> found = source.getNoiseBiome(quartX, quartY, quartZ, sampler);
        cache.positions.put(key, found);
        return found;
    }

    private BiomeSource source() {
        return source;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(source.possibleBiomes().stream(), transitionBiomes.values().stream()).distinct();
    }

    private static String vanillaId(Holder<Biome> holder) {
        return holder.unwrapKey().map(key -> {
            ResourceLocation location = key.location();
            return "minecraft".equals(location.getNamespace()) ? location.getPath() : "";
        }).orElse("");
    }

    private static boolean protectedWater(String id) {
        return id.contains("ocean") || id.equals("river") || id.equals("frozen_river");
    }

    private static Rule findPair(String first, String second) {
        for (Rule rule : RULES) {
            if (rule.matches(first, second)) return rule;
        }
        return null;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        Holder<Biome> original = sampleBase(quartX, quartY, quartZ, sampler);
        // Surface ecotones must not be painted across caves or deep aquifers.
        if (quartY < 12) return original;
        String current = vanillaId(original);
        if (current.isEmpty() || protectedWater(current) || !CANDIDATE_IDS.contains(current)) return original;

        // Check multiple quart positions outward, stopping at the first
        // underlying biome boundary in each direction. With a maximum half
        // width of 32 blocks, these samples never exceed 8 quart cells.
        Rule nearest = null;
        int closest = Integer.MAX_VALUE;
        final int[][] directions = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] direction : directions) {
            for (int step = 1; step <= MAX_SEARCH_QUARTS; step++) {
                String next = vanillaId(sampleBase(quartX + direction[0] * step,
                        quartY, quartZ + direction[1] * step, sampler));
                if (current.equals(next)) continue;
                Rule rule = findPair(current, next);
                if (rule != null && step * 4 <= rule.widthBlocks() / 2
                        && step < closest) {
                    nearest = rule;
                    closest = step;
                }
                // Do not skip across an incompatible third biome.
                break;
            }
        }
        return nearest == null ? original : transitionBiomes.get(nearest.target());
    }
}

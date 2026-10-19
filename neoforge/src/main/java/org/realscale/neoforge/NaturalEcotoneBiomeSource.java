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
            ).apply(instance, (source, biomes) -> new NaturalEcotoneBiomeSource(source, biomes, false)));

    /** Separate persisted codec: old saved worlds remain on the old 60-rule map. */
    public static final MapCodec<NaturalEcotoneBiomeSource> DIVERSE_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("source").forGetter(NaturalEcotoneBiomeSource::source),
                    RegistryOps.retrieveGetter(Registries.BIOME)
            ).apply(instance, (source, biomes) -> new NaturalEcotoneBiomeSource(source, biomes, true)));

    /** Persisted v3 codec: earlier saved worldgen maps decode unchanged. */
    public static final MapCodec<NaturalEcotoneBiomeSource> DIVERSE_V3_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("source").forGetter(NaturalEcotoneBiomeSource::source),
                    RegistryOps.retrieveGetter(Registries.BIOME)
            ).apply(instance, (source, biomes) ->
                    new NaturalEcotoneBiomeSource(source, biomes, true, true)));

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
            // Narrow, geographically distinct v0.6 climate boundaries.
            rule("cherry_oak_woodland",32,"cherry_grove","forest"),
            rule("cherry_meadow_fringe",24,"cherry_grove","meadow"),
            rule("birch_snowline_scrub",24,"birch_forest","snowy_slopes"),
            rule("spruce_snowdrift_edge",24,"grove","snowy_slopes"),
            rule("mangrove_freshwater_margin",16,"mangrove_swamp","river"),
            rule("coastal_marsh_woodland",24,"swamp","beach"),
            rule("dune_pine_woodland",24,"taiga","beach"),
            rule("montane_oak_foothills",32,"forest","windswept_forest"),
            rule("rocky_savanna_foothills",32,"savanna","stony_peaks"),
            rule("badlands_peak_scree",24,"badlands","stony_peaks"),
            rule("bamboo_meadow_fringe",24,"bamboo_jungle","meadow"),
            rule("snowy_spruce_riverbank",16,"snowy_taiga","frozen_river"),
            // Real geographic micro-habitats are matched before broad forest/plains
            // ecotones so narrow birch glades are not painted as generic woodland.
            rule("birch_parkland",24,"birch_forest,old_growth_birch_forest",PLAINS),
            rule("boreal_parkland",32,"taiga,old_growth_pine_taiga,old_growth_spruce_taiga",PLAINS),
            rule("peat_bog_taiga",24,TAIGA,"swamp"),
            rule("seasonal_savanna_wetland",24,SAVANNA,"swamp"),
            rule("tropical_littoral_forest",24,"jungle,sparse_jungle","beach"),
            rule("frost_riparian_banks",16,"snowy_plains","frozen_river"),
            rule("montane_spruce_meadow",24,TAIGA,"meadow"),
            rule("dry_grass_dunes",32,"desert",PLAINS),
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

    /**
     * Opt-in v2 habitats. Pairs deliberately do not overlap the v1 rule map.
     * Only true biome interfaces are assigned; vanilla rivers and oceans
     * remain protected and all samples stay world-coordinate based.
     */
    private static final List<Rule> DIVERSE_RULES = List.of(
            rule("tropical_dry_woodland",32,"sparse_jungle","savanna,savanna_plateau"),
            rule("cool_temperate_rainforest",32,"dark_forest",
                    "old_growth_spruce_taiga,old_growth_pine_taiga"),
            rule("alpine_sedge_fen",16,"meadow","river,frozen_river"),
            rule("polar_fellfield",24,"snowy_plains","stony_peaks"),
            rule("arid_riparian_woodland",16,"badlands,wooded_badlands,eroded_badlands","river"),
            rule("windy_coastal_headland",24,"stony_shore","meadow,plains")
    );
    private static final List<Rule> ALL_V2_RULES =
            Stream.concat(DIVERSE_RULES.stream(), RULES.stream()).toList();

    /** Version 3: eight non-overlapping natural-climate ecotone pairs. */
    private static final List<Rule> V3_RULES = List.of(
            rule("mediterranean_chaparral",32,"savanna,savanna_plateau","windswept_forest"),
            rule("temperate_tallgrass_prairie",32,"plains,sunflower_plains","meadow"),
            rule("monsoon_woodland",32,"jungle","savanna,savanna_plateau"),
            rule("montane_cloud_scrub",24,"grove","windswept_forest"),
            rule("brackish_tidal_marsh",24,"mangrove_swamp","beach"),
            rule("coastal_pine_barrens",24,"stony_shore","forest"),
            rule("frost_heath_moor",24,"snowy_plains","windswept_gravelly_hills"),
            rule("arid_rocksteppe",32,"desert","stony_peaks")
    );
    private static final List<Rule> ALL_V3_RULES =
            Stream.concat(V3_RULES.stream(), ALL_V2_RULES.stream()).toList();

    private static final Set<String> CANDIDATE_IDS = RULES.stream()
            .flatMap(rule -> Stream.concat(rule.left().stream(), rule.right().stream()))
            .collect(Collectors.toUnmodifiableSet());
    /**
     * Ordered pair lookup: builds all compatible vanilla biome combinations
     * exactly once instead of scanning 60 rules for every quart boundary.
     * putIfAbsent retains the original RULES priority at overlapping pairs.
     */
    private static final Map<String, Map<String, Rule>> RULE_BY_BIOME = indexRules(RULES);
    private static final Map<String, Map<String, Rule>> V2_RULE_BY_BIOME = indexRules(ALL_V2_RULES);
    private static final Map<String, Map<String, Rule>> V3_RULE_BY_BIOME = indexRules(ALL_V3_RULES);
    private static final Set<String> V2_CANDIDATE_IDS = ALL_V2_RULES.stream()
            .flatMap(rule -> Stream.concat(rule.left().stream(), rule.right().stream()))
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> V3_CANDIDATE_IDS = ALL_V3_RULES.stream()
            .flatMap(rule -> Stream.concat(rule.left().stream(), rule.right().stream()))
            .collect(Collectors.toUnmodifiableSet());

    private static Map<String, Map<String, Rule>> indexRules(List<Rule> rules) {
        Map<String, Map<String, Rule>> mutable = new HashMap<>();
        for (Rule rule : rules) {
            for (String a : rule.left()) for (String b : rule.right()) {
                mutable.computeIfAbsent(a, key -> new HashMap<>()).putIfAbsent(b, rule);
                mutable.computeIfAbsent(b, key -> new HashMap<>()).putIfAbsent(a, rule);
            }
        }
        Map<String, Map<String, Rule>> immutable = new HashMap<>();
        mutable.forEach((id, partners) -> immutable.put(id, Map.copyOf(partners)));
        return Map.copyOf(immutable);
    }
    // Limit ray traversal to the largest boundary width this particular
    // vanilla biome can actually participate in. A global 48-block radius
    // needlessly samples remote climate for narrow river/coast transitions.
    private static final Map<String, Integer> MAX_WIDTH_BY_BIOME = buildWidths(RULES);
    private static final Map<String, Integer> V2_MAX_WIDTH_BY_BIOME = buildWidths(ALL_V2_RULES);
    private static final Map<String, Integer> V3_MAX_WIDTH_BY_BIOME = buildWidths(ALL_V3_RULES);

    private static Map<String, Integer> buildWidths(List<Rule> rules) {
        Map<String, Integer> widths = new HashMap<>();
        for (Rule rule : rules) {
            for (String id : rule.left()) widths.merge(id, rule.widthBlocks(), Math::max);
            for (String id : rule.right()) widths.merge(id, rule.widthBlocks(), Math::max);
        }
        return Map.copyOf(widths);
    }
    private static final int MAX_SEARCH_QUARTS = RULES.stream()
            .mapToInt(rule -> rule.widthBlocks() / 8).max().orElse(0);
    // Cardinal and diagonal rays: the same Euclidean radius in all headings.
    // Cardinals first preserve deterministic precedence for equal distances.
    private static final int[][] DIRECTIONS = {
            {1,0},{-1,0},{0,1},{0,-1},
            {1,1},{1,-1},{-1,1},{-1,-1}
    };

    /** Bounded per-thread climate cache, invalidated when the climate sampler changes. */
    private static final class SamplingCache {
        Climate.Sampler sampler;
        final QuartBiomeResultCache<Holder<Biome>> resolved = new QuartBiomeResultCache<>();
        // Two-way primitive-key cache avoids Long boxing and linked-map
        // node allocations for high-altitude worldgen's frequent quart rays.
        final QuartBiomeBaseCache<Holder<Biome>> positions = new QuartBiomeBaseCache<>();

        void prepare(Climate.Sampler nextSampler) {
            if (sampler == nextSampler) return;
            sampler = nextSampler;
            positions.clear();
            // A new sampler may resolve the same quart coordinate differently.
            resolved.clear();
        }
    }

    private final BiomeSource source;
    private final boolean diverse;
    private final boolean expandedV3;
    private final ThreadLocal<SamplingCache> cached = ThreadLocal.withInitial(SamplingCache::new);
    private final Map<String, Holder<Biome>> transitionBiomes;

    private NaturalEcotoneBiomeSource(BiomeSource source, HolderGetter<Biome> biomes, boolean diverse) {
        this(source, biomes, diverse, false);
    }

    private NaturalEcotoneBiomeSource(
            BiomeSource source, HolderGetter<Biome> biomes, boolean diverse, boolean expandedV3) {
        this.source = source;
        this.diverse = diverse;
        this.expandedV3 = expandedV3;
        this.transitionBiomes = new HashMap<>();
        // Original v1 and v2 registration order and selection are untouched.
        for (Rule rule : diverse ? ALL_V2_RULES : RULES) {
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath("realscale", rule.target());
            transitionBiomes.put(rule.target(), biomes.getOrThrow(ResourceKey.create(Registries.BIOME, location)));
        }
        if (expandedV3) for (Rule rule : V3_RULES) {
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath("realscale", rule.target());
            transitionBiomes.put(rule.target(), biomes.getOrThrow(ResourceKey.create(Registries.BIOME, location)));
        }
    }

    private Holder<Biome> sampleBase(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        SamplingCache cache = cached.get();
        cache.prepare(sampler);
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
        if (expandedV3) return DIVERSE_V3_CODEC;
        return diverse ? DIVERSE_CODEC : CODEC;
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

    private Rule findPair(String first, String second) {
        Map<String, Rule> partners = (expandedV3 ? V3_RULE_BY_BIOME
                : diverse ? V2_RULE_BY_BIOME : RULE_BY_BIOME).get(first);
        return partners == null ? null : partners.get(second);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        if(!DensityPerfSampler.ENABLED)
            return getNoiseBiomeUnprofiled(quartX,quartY,quartZ,sampler);
        long start=DensityPerfSampler.begin(DensityPerfSampler.Stage.ECOTONE_BIOME);
        try{return getNoiseBiomeUnprofiled(quartX,quartY,quartZ,sampler);}
        finally{DensityPerfSampler.finish(DensityPerfSampler.Stage.ECOTONE_BIOME,start);}
    }

    private Holder<Biome> getNoiseBiomeUnprofiled(
            int quartX,int quartY,int quartZ,Climate.Sampler sampler) {
        SamplingCache cache = cached.get();
        cache.prepare(sampler);
        long key = BlockPos.asLong(quartX, quartY, quartZ);
        Holder<Biome> previous = cache.resolved.get(key);
        if (previous != null) return previous;

        Holder<Biome> result = resolveUncached(quartX, quartY, quartZ, sampler);
        cache.resolved.put(key, result);
        return result;
    }

    /**
     * Original ecotone algorithm: unchanged priority, boundary search, water
     * preservation and final holder resolution. Only callers memoize output.
     */
    private Holder<Biome> resolveUncached(
            int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        Holder<Biome> original = sampleBase(quartX, quartY, quartZ, sampler);
        // Surface ecotones must not be painted across caves or deep aquifers.
        if (quartY < 12) return original;
        String current = vanillaId(original);
        if (current.isEmpty() || protectedWater(current)
                || !(expandedV3 ? V3_CANDIDATE_IDS
                     : diverse ? V2_CANDIDATE_IDS : CANDIDATE_IDS).contains(current))
            return original;

        // Check multiple quart positions outward, stopping at the first
        // underlying biome boundary in each direction. With a maximum half
        // width of 32 blocks, these samples never exceed 8 quart cells.
        Rule nearest = null;
        int closestDistanceSquared = Integer.MAX_VALUE;
        int maxLocalQuarts = ((expandedV3 ? V3_MAX_WIDTH_BY_BIOME
                : diverse ? V2_MAX_WIDTH_BY_BIOME : MAX_WIDTH_BY_BIOME)
                .getOrDefault(current, 0) + 7) / 8;
        for (int[] direction : DIRECTIONS) {
            for (int step = 1; step <= maxLocalQuarts; step++) {
                int distanceSquared = EcotoneMath.quartDistanceSquared(
                        direction[0], direction[1], step);
                // No rule can be wider than the configured global search span.
                if (distanceSquared > 16 * maxLocalQuarts * maxLocalQuarts) break;
                // Rays are visited from closest to farthest. We can safely
                // stop once a heading cannot beat the best existing border.
                if (distanceSquared >= closestDistanceSquared) break;
                String next = vanillaId(sampleBase(quartX + direction[0] * step,
                        quartY, quartZ + direction[1] * step, sampler));
                if (current.equals(next)) continue;
                Rule rule = findPair(current, next);
                if (rule != null && EcotoneMath.withinRadialBand(
                        direction[0], direction[1], step, rule.widthBlocks())) {
                    nearest = rule;
                    closestDistanceSquared = distanceSquared;
                }
                // The very first adjacent nonmatching biome is the physical
                // interface; never search through an unrelated third biome.
                break;
            }
        }
        return nearest == null ? original : transitionBiomes.get(nearest.target());
    }
}

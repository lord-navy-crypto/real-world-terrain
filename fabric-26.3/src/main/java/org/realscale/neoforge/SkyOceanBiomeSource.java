package org.realscale.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import java.util.stream.Stream;

/** Opt-in offshore archipelago on top of the unchanged vanilla/ecotone
 * biome source. Everything below Y160 is delegated without modification.
 */
public final class SkyOceanBiomeSource extends BiomeSource {
    public static final MapCodec<SkyOceanBiomeSource> CODEC=RecordCodecBuilder.mapCodec(i->
        i.group(BiomeSource.CODEC.fieldOf("source").forGetter(SkyOceanBiomeSource::source),
                RegistryOps.retrieveGetter(Registries.BIOME))
          .apply(i,SkyOceanBiomeSource::new));
    private final BiomeSource source;
    // Offshore radius probes only classify vanilla oceans. Bypass expensive
    // 8-direction ecotone ray searches, which cannot change ocean predicates.
    // Final sky biome delegation ALWAYS uses the original decorated source.
    private final BiomeSource oceanClassificationSource;
    private final Holder<Biome> blossomIsle;
    private final ThreadLocal<EligibilityCache> cache=ThreadLocal.withInitial(EligibilityCache::new);

    private static final class EligibilityCache {
        Climate.Sampler sampler;
        final SkyIslandSiteCache sites = new SkyIslandSiteCache();
        void prepare(Climate.Sampler next) {
            if(sampler==next)return;
            sampler=next;
            // Every site carries its ocean tier. A new climate sampler can
            // return different oceans for the exact same lattice cell.
            sites.clear();
        }
    }
    private static BiomeSource unwrapOceanClassificationSource(BiomeSource candidate) {
        // New canonical presets compose identity/decorator wrappers around the
        // vanilla source. Unwrap them for offshore-ocean probes so those probes
        // do not accidentally run the expensive ecotone boundary-ray searches.
        if(candidate instanceof NaturalEcotoneBiomeSource eco)
            return unwrapOceanClassificationSource(eco.offshoreOceanClassificationSource());
        if(candidate instanceof GeoWondersBiomeSource wonders)
            return unwrapOceanClassificationSource(wonders.wrappedSource());
        if(candidate instanceof CaveDepthBiomeSource cave)
            return unwrapOceanClassificationSource(cave.wrappedSource());
        return candidate;
    }
    private SkyOceanBiomeSource(BiomeSource source,HolderGetter<Biome> biomes) {
        this.source=source;
        this.oceanClassificationSource=unwrapOceanClassificationSource(source);
        blossomIsle=biomes.getOrThrow(ResourceKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath("realscale","sky_blossom_isles")));
    }
    private BiomeSource source(){return source;}
    /** Preserve sky island placement for nested optional Geo Wonders presets. */
    public boolean containsGeoWonders() {
        return source instanceof GeoWondersBiomeSource;
    }
    @Override protected MapCodec<? extends BiomeSource> codec(){return CODEC;}
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(source.possibleBiomes().stream(),Stream.of(blossomIsle)).distinct();
    }
    private String underlying(int x,int z,BiomeResolver oceanResolver) {
        return oceanResolver.getNoiseBiome(Math.floorDiv(x,4),16,Math.floorDiv(z,4))
                .unwrapKey().map(k->{
                    var id=k.identifier();
                    return "minecraft".equals(id.getNamespace())?id.getPath():"";
                }).orElse("");
    }
    private int isolationTier(SkyIslandSiteCache.Entry site,BiomeResolver oceanResolver) {
        if(site.tier>=0)return site.tier;
        int value=SkyIslandLayout.oceanTier(site.base(),
                (x,z)->{
                    String id=underlying(x,z,oceanResolver);
                    return id.startsWith("deep_")&&id.endsWith("ocean");
                },
                (x,z)->underlying(x,z,oceanResolver).endsWith("ocean"));
        site.tier=value;
        return value;
    }
    @Override public BiomeResolver createResolver(Climate.Sampler sampler) {
        BiomeResolver sourceResolver = source.createResolver(sampler);
        BiomeResolver oceanResolver = oceanClassificationSource.createResolver(sampler);
        return (quartX, quartY, quartZ) -> {
            if (!DensityPerfSampler.ENABLED)
                return getNoiseBiomeUnprofiled(quartX, quartY, quartZ, sampler, sourceResolver, oceanResolver);
            long start = DensityPerfSampler.begin(DensityPerfSampler.Stage.SKY_BIOME);
            try {
                return getNoiseBiomeUnprofiled(quartX, quartY, quartZ, sampler, sourceResolver, oceanResolver);
            } finally {
                DensityPerfSampler.finish(DensityPerfSampler.Stage.SKY_BIOME, start);
            }
        };
    }

    private Holder<Biome> getNoiseBiomeUnprofiled(
            int quartX,int quartY,int quartZ,Climate.Sampler sampler,
            BiomeResolver sourceResolver,BiomeResolver oceanResolver) {
        // Explicit identity delegation for ocean, seabed, ocean structures and
        // even tall surface mountain biomes that do not contain an island.
        int y=quartY*4;
        // The widest possible island (including +/-3 satellite height
        // offsets and the -13/+8 biome interval) lies within 167..214.
        // Outside this proven envelope, preserve underlying biome directly.
        if(y<SkyIslandLayout.MIN_BIOME_Y||y>SkyIslandLayout.MAX_BIOME_Y)
            return sourceResolver.getNoiseBiome(quartX,quartY,quartZ);
        int x=quartX*4,z=quartZ*4;
        EligibilityCache state=cache.get();
        state.prepare(sampler);
        SkyIslandSiteCache.Entry site=state.sites.at(x,z);
        SkyIslandLayout.Site base=site.base();
        // Every island is within +/-94 X/Z of its deterministic parent.
        // Reject the overwhelming majority of distant quart queries before
        // hashing/allocating the five candidate island Site records.
        if(!SkyIslandLayout.mayIntersectIslands(base,x,z))
            return sourceResolver.getNoiseBiome(quartX,quartY,quartZ);
        // Reject most positions without computing a single remote-climate ring.
        // Only the central island needs a remote-ocean eligibility scan
        // before satellite checks. Once resolved, a tier 1/2 site skips
        // distant slots entirely. Preserve exactly the same biome result.
        int tier=-1;
        int eligibleSlots=SkyIslandLayout.SLOTS;
        for(int slot=0;slot<eligibleSlots;slot++){
            SkyIslandLayout.Site island=site.satellite(slot);
            if(!SkyIslandLayout.insideBiome(island,x,y,z))continue;
            if(tier<0){
                tier=isolationTier(site,oceanResolver);
                eligibleSlots=SkyIslandLayout.countForTier(tier);
            }
            if(slot<eligibleSlots)return blossomIsle;
        }
        return sourceResolver.getNoiseBiome(quartX,quartY,quartZ);
    }
}

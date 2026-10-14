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

/** Opt-in offshore archipelago on top of the unchanged vanilla/ecotone
 * biome source. Everything below Y160 is delegated without modification.
 */
public final class SkyOceanBiomeSource extends BiomeSource {
    public static final MapCodec<SkyOceanBiomeSource> CODEC=RecordCodecBuilder.mapCodec(i->
        i.group(BiomeSource.CODEC.fieldOf("source").forGetter(SkyOceanBiomeSource::source),
                RegistryOps.retrieveGetter(Registries.BIOME))
          .apply(i,SkyOceanBiomeSource::new));
    private final BiomeSource source;
    private final Holder<Biome> blossomIsle;
    private final ThreadLocal<EligibilityCache> cache=ThreadLocal.withInitial(EligibilityCache::new);

    private static final class EligibilityCache {
        Climate.Sampler sampler;
        final LinkedHashMap<Long,Integer> tiers=new LinkedHashMap<>(256,.75f,true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long,Integer> eldest) {
                return size()>256;
            }
        };
    }
    private SkyOceanBiomeSource(BiomeSource source,HolderGetter<Biome> biomes) {
        this.source=source;
        blossomIsle=biomes.getOrThrow(ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("realscale","sky_blossom_isles")));
    }
    private BiomeSource source(){return source;}
    @Override protected MapCodec<? extends BiomeSource> codec(){return CODEC;}
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(source.possibleBiomes().stream(),Stream.of(blossomIsle)).distinct();
    }
    private String underlying(int x,int z,Climate.Sampler sampler) {
        return source.getNoiseBiome(Math.floorDiv(x,4),16,Math.floorDiv(z,4),sampler)
                .unwrapKey().map(k->{
                    var id=k.location();
                    return "minecraft".equals(id.getNamespace())?id.getPath():"";
                }).orElse("");
    }
    private int isolationTier(SkyIslandLayout.Site base,Climate.Sampler sampler) {
        EligibilityCache c=cache.get();
        if(c.sampler!=sampler){c.sampler=sampler;c.tiers.clear();}
        long key=((long)base.x()<<32)|(base.z()&0xffffffffL);
        Integer previous=c.tiers.get(key);
        if(previous!=null)return previous;
        int value=SkyIslandLayout.oceanTier(base,
                (x,z)->{
                    String id=underlying(x,z,sampler);
                    return id.startsWith("deep_")&&id.endsWith("ocean");
                },
                (x,z)->underlying(x,z,sampler).endsWith("ocean"));
        c.tiers.put(key,value);
        return value;
    }
    @Override public Holder<Biome> getNoiseBiome(int quartX,int quartY,int quartZ,
                                                  Climate.Sampler sampler) {
        // Explicit identity delegation for ocean, seabed, ocean structures and
        // even tall surface mountain biomes that do not contain an island.
        int y=quartY*4;
        if(y<160||y>224)return source.getNoiseBiome(quartX,quartY,quartZ,sampler);
        int x=quartX*4,z=quartZ*4;
        SkyIslandLayout.Site base=SkyIslandLayout.at(x,z);
        // Reject most positions without computing a single remote-climate ring.
        for(int slot=0;slot<SkyIslandLayout.SLOTS;slot++){
            SkyIslandLayout.Site island=SkyIslandLayout.satellite(base,slot);
            if(!SkyIslandLayout.insideBiome(island,x,y,z))continue;
            if(slot<SkyIslandLayout.countForTier(isolationTier(base,sampler)))
                return blossomIsle;
        }
        return source.getNoiseBiome(quartX,quartY,quartZ,sampler);
    }
}

package org.realscale.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * TEST-only original-biome decorator. The regular four RealScale presets
 * remain unchanged; only explicitly selected cave-ecology test preset
 * constructs this source. Ocean/river/Deep Dark biome identities stay vanilla.
 *
 * Source uses quart coordinates and original vanilla cave biome climate, not
 * carved-block or fluid reads (those aren't available at biome-source stage).
 * All six custom biome JSON files inherit ORIGINAL 1.21.1 cave ore, lake,
 * dungeon, carver and spawn lists to prevent disappearing vanilla generation.
 */
public final class CaveDepthBiomeSource extends BiomeSource {
    public static final MapCodec<CaveDepthBiomeSource> CODEC=
            RecordCodecBuilder.mapCodec(i->i.group(
                    BiomeSource.CODEC.fieldOf("source").forGetter(CaveDepthBiomeSource::source),
                    RegistryOps.retrieveGetter(Registries.BIOME))
                    .apply(i,CaveDepthBiomeSource::new));
    private final BiomeSource source;
    private final Map<CaveBiomeRegionMath.Region,Holder<Biome>> regions;
    private CaveDepthBiomeSource(BiomeSource base,HolderGetter<Biome> biomes) {
        this.source=base;
        EnumMap<CaveBiomeRegionMath.Region,Holder<Biome>> targets=
                new EnumMap<>(CaveBiomeRegionMath.Region.class);
        for(CaveBiomeRegionMath.Region region:CaveBiomeRegionMath.Region.values()){
            if(region==CaveBiomeRegionMath.Region.NONE)continue;
            String path=switch(region){
                case DAMP_LIMESTONE->"damp_limestone_caverns";
                case DEEP_FUNGAL->"deep_fungal_caverns";
                case DRY_KARST->"dry_karst_caves";
                case UNDERGROUND_SPRINGS->"underground_springs";
                case VOLCANIC->"volcanic_caverns";
                case GLACIAL->"glacial_subsurface";
                case GLOW_GROTTO->"glowing_moss_grottos";
                case FOSSIL_CAVES->"fossil_limestone_caves";
                default->throw new IllegalStateException("NONE is never target");
            };
            targets.put(region,biomes.getOrThrow(ResourceKey.create(Registries.BIOME,
                    ResourceLocation.fromNamespaceAndPath("realscale",path))));
        }
        this.regions=Map.copyOf(targets);
    }
    private BiomeSource source(){return source;}
    @Override protected MapCodec<? extends BiomeSource> codec(){return CODEC;}
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(source.possibleBiomes().stream(),regions.values().stream()).distinct();
    }
    @Override public Holder<Biome> getNoiseBiome(int x,int y,int z,Climate.Sampler sampler){
        Holder<Biome> original=source.getNoiseBiome(x,y,z,sampler);
        String id=original.unwrapKey().map(key->
                "minecraft".equals(key.location().getNamespace())
                        ?key.location().getPath():"").orElse("");
        CaveBiomeRegionMath.Region choice=CaveBiomeRegionMath.select(x,y,z,id);
        return choice==CaveBiomeRegionMath.Region.NONE
                ?original:regions.get(choice);
    }
}

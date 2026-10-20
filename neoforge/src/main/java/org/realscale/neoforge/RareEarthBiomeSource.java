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

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Opt-in, versioned rare-Earth biome wrapper; existing presets stay untouched.
 * Sparse 256-block land districts, restricted by source biome and depth.
 * This is a climate proxy, not georeferenced hydrological/geothermal modelling.
 */
public final class RareEarthBiomeSource extends BiomeSource {
    public static final MapCodec<RareEarthBiomeSource> CODEC =
        RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("source").forGetter(v -> v.source),
            RegistryOps.retrieveGetter(Registries.BIOME)
        ).apply(instance, RareEarthBiomeSource::new));

    private static final String[] IDS = {
        "geothermal_sulfur_basin","socotra_dragonblood_woodland",
        "white_dune_lagoon","sundarbans_tidal_mangrove",
        "congo_peat_swamp_forest","steppe_saline_lake",
        "acidic_geothermal_mudflat","montane_mist_forest",
        "permafrost_fen","limestone_island_scrub",
        "namib_fog_desert","serpentine_barrens","socotra_endemic_scrub"
    };
    private final BiomeSource source;
    private final Map<String,Holder<Biome>> rare = new HashMap<>();
    private RareEarthBiomeSource(BiomeSource source, HolderGetter<Biome> biomes) {
        this.source=source;
        for(String id: IDS)
            rare.put(id,biomes.getOrThrow(ResourceKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath("realscale",id))));
    }
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(source.possibleBiomes().stream(),rare.values().stream()).distinct();
    }
    private static long mix(long z){
        z=(z^(z>>>30))*0xbf58476d1ce4e5b9L;
        z=(z^(z>>>27))*0x94d049bb133111ebL;
        return z^(z>>>31);
    }
    @Override public Holder<Biome> getNoiseBiome(int qx,int qy,int qz,Climate.Sampler sampler) {
        Holder<Biome> original=source.getNoiseBiome(qx,qy,qz,sampler);
        if(qy<16)return original;
        String id=original.unwrapKey().map(k -> k.location().getNamespace().equals("minecraft")
                ? k.location().getPath():"").orElse("");
        if(id.isEmpty()||id.contains("ocean")||id.contains("river")
                ||id.contains("cave")||id.equals("deep_dark"))return original;
        // 256-block landscapes; only 1 of 8 districts changes, never small
        // checkerboard patches. The source biome still controls eligibility.
        long h=mix((long)Math.floorDiv(qx,64)*0x9e3779b97f4a7c15L
                ^ (long)Math.floorDiv(qz,64)*0x632be59bd9b4e019L
                ^ 0x193a4ef1L);
        if(Math.floorMod(h,8)!=0)return original;
        // Low three hash bits are zero for admitted districts; use *higher*
        // bits for variants or half the rare ecosystems become unreachable.
        int variant=(int)((h>>>3)&3L);
        String target=switch(id){
            case "badlands","eroded_badlands" -> (variant&1)==0
                    ?"geothermal_sulfur_basin":"acidic_geothermal_mudflat";
            case "desert" -> variant==0?"white_dune_lagoon":
                    (h&1)==0?"namib_fog_desert":"limestone_island_scrub";
            case "savanna","savanna_plateau" -> (h&1)==0
                    ?"socotra_dragonblood_woodland":"socotra_endemic_scrub";
            case "mangrove_swamp" -> "sundarbans_tidal_mangrove";
            case "swamp" -> "congo_peat_swamp_forest";
            case "plains","sunflower_plains" -> "steppe_saline_lake";
            case "windswept_forest","dark_forest" -> "montane_mist_forest";
            case "snowy_plains","snowy_taiga" -> "permafrost_fen";
            case "stony_peaks","windswept_gravelly_hills" -> "serpentine_barrens";
            default -> null;
        };
        return target==null?original:rare.get(target);
    }
    @Override protected MapCodec<? extends BiomeSource> codec(){return CODEC;}
}

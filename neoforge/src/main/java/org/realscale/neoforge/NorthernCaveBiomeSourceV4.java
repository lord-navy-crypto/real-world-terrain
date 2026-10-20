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
import java.util.List;
import java.util.stream.Stream;

/** V4-only 3D cave biome overlay; unchanged above Y=36 and over waters. */
public final class NorthernCaveBiomeSourceV4 extends BiomeSource {
    public static final MapCodec<NorthernCaveBiomeSourceV4> CODEC=
        RecordCodecBuilder.mapCodec(i->i.group(
            BiomeSource.CODEC.fieldOf("source").forGetter(NorthernCaveBiomeSourceV4::source),
            RegistryOps.retrieveGetter(Registries.BIOME))
        .apply(i,NorthernCaveBiomeSourceV4::new));
    private static final List<String> NAMES=List.of(
        "underground_springs","deep_fungal_caverns","glacial_subsurface",
        "damp_limestone_caverns","dry_karst_caves","volcanic_caverns");
    private final BiomeSource source;
    private final List<Holder<Biome>> regions;
    private NorthernCaveBiomeSourceV4(BiomeSource source,HolderGetter<Biome> biomes) {
        this.source=source;
        regions=NAMES.stream().map(id->biomes.getOrThrow(ResourceKey.create(
            Registries.BIOME,ResourceLocation.fromNamespaceAndPath("realscale",id)))).toList();
    }
    private BiomeSource source(){return source;}
    @Override protected MapCodec<? extends BiomeSource> codec(){return CODEC;}
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(source.possibleBiomes().stream(),regions.stream()).distinct();
    }
    private static String name(Holder<Biome> b) {
        return b.unwrapKey().map(k->k.location().toString()).orElse("");
    }
    @Override public Holder<Biome> getNoiseBiome(int qx,int qy,int qz,Climate.Sampler sampler) {
        Holder<Biome> original=source.getNoiseBiome(qx,qy,qz,sampler);
        if(qy>9||qy< -12)return original;
        String chosen=NorthernCaveBiomeMathV4.select(name(original),
            name(source.getNoiseBiome(qx,16,qz,sampler)),qy,
            NorthernCaveBiomeMathV4.regionHash(qx,qz));
        if(chosen==null)return original;
        int idx=NAMES.indexOf(chosen.substring("realscale:".length()));
        return idx<0?original:regions.get(idx);
    }
}

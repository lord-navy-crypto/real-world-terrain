package org.realscale.neoforge;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/** Identity BiomeSource wrapper that opts into experimental geological wonders.
 * Preserves the original climate/biome selection byte-for-byte.
 */
public final class GeoWondersBiomeSource extends BiomeSource {
    public static final MapCodec<GeoWondersBiomeSource> CODEC =
        RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("source").forGetter(GeoWondersBiomeSource::source)
        ).apply(i,GeoWondersBiomeSource::new));
    private final BiomeSource source;
    private GeoWondersBiomeSource(BiomeSource source){this.source=source;}
    private BiomeSource source(){return source;}
    BiomeSource wrappedSource(){return source;}
    @Override protected MapCodec<? extends BiomeSource> codec(){return CODEC;}
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes(){
        return source.possibleBiomes().stream();
    }
    @Override public Holder<Biome> getNoiseBiome(int x,int y,int z,Climate.Sampler sampler){
        return source.getNoiseBiome(x,y,z,sampler);
    }
}

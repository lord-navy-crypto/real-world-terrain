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
 * Test-only biome invasion gallery near world origin (0,0).
 * Reuses REAL Minecraft biome holders, rather than decorative stand-ins.
 * Biome assignment alone does NOT transplant ground blocks or structures.
 * Never installed by production presets.
 */
public final class CrossRealmShowcaseBiomeSource extends BiomeSource {
    public static final MapCodec<CrossRealmShowcaseBiomeSource> CODEC =
        RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("source").forGetter(CrossRealmShowcaseBiomeSource::source),
            RegistryOps.retrieveGetter(Registries.BIOME))
            .apply(i,CrossRealmShowcaseBiomeSource::new));

    private final BiomeSource source;
    private final Holder<Biome> nether, end, overworld;
    private CrossRealmShowcaseBiomeSource(BiomeSource source,HolderGetter<Biome> biomes) {
        this.source=source;
        nether=lookup(biomes,"nether_wastes");
        end=lookup(biomes,"end_highlands");
        overworld=lookup(biomes,"forest");
    }
    private static Holder<Biome> lookup(HolderGetter<Biome> biomes,String name) {
        return biomes.getOrThrow(ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("minecraft",name)));
    }
    private BiomeSource source(){return source;}
    /** The outer gallery must not conceal opt-in geology from worldgen. */
    boolean containsGeoWonders() {
        return source instanceof GeoWondersBiomeSource
                || source instanceof SkyOceanBiomeSource sky
                   && sky.containsGeoWonders()
                || source instanceof RareEarthBiomeSource rare
                   && rare.containsGeoWonders();
    }
    @Override protected MapCodec<? extends BiomeSource> codec(){return CODEC;}
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(source.possibleBiomes().stream(),
            Stream.of(nether,end,overworld)).distinct();
    }
    private static boolean inside(long x,long z,int centerX,int radius) {
        long dx=x-centerX;
        return dx*dx+z*z<=(long)radius*radius;
    }
    @Override public Holder<Biome> getNoiseBiome(int qx,int qy,int qz,Climate.Sampler sampler) {
        Holder<Biome> normal=source.getNoiseBiome(qx,qy,qz,sampler);
        String id=normal.unwrapKey().map(k->k.location().toString()).orElse("");
        // Unified invasion spawn is locked to one of our three own End
        // habitats. The gallery never changes that original spawn into
        // minecraft:nether_wastes or minecraft:end_highlands.
        if (id.equals("realscale:end_chorus_gardens")
                || id.equals("realscale:end_crystal_barrens")
                || id.equals("realscale:end_shattered_glades")) return normal;
        boolean n=id.equals("minecraft:nether_wastes")||id.equals("minecraft:crimson_forest")
            ||id.equals("minecraft:warped_forest")||id.equals("minecraft:soul_sand_valley")
            ||id.equals("minecraft:basalt_deltas");
        boolean e=id.equals("minecraft:the_end")||id.equals("minecraft:end_highlands")
            ||id.equals("minecraft:end_midlands")||id.equals("minecraft:end_barrens")
            ||id.equals("minecraft:small_end_islands");
        long x=(long)qx*4,z=(long)qz*4;
        int y=qy*4;
        if(n) {
            if(y>=36&&y<=112) {
                if(inside(x,z,0,76))return overworld;  // Overworld -> Nether
                if(inside(x,z,176,72))return end;      // End -> Nether
            }
            return normal;
        }
        if(e) {
            if(y>=48&&y<=160) {
                if(inside(x,z,0,76))return overworld;  // Overworld -> End
                if(inside(x,z,176,72))return nether;   // Nether -> End
            }
            return normal;
        }
        // Test overworld: at origin is Nether, +176 blocks is End.
        // Safeguard below-ground biomes and prevent ordinary world changes.
        if(id.startsWith("minecraft:")&&y>=48&&y<=192) {
            if(inside(x,z,0,76))return nether;        // Nether -> Overworld
            if(inside(x,z,176,72))return end;         // End -> Overworld
        }
        return normal;
    }
}

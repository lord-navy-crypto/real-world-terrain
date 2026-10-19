package org.realscale.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
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
 * Opt-in rare authentic overworld biome IDs inside grand Nether/End.
 * Reuses vanilla biome holders (not imitation palettes); underlying terrain,
 * dimensions and default world presets remain unchanged. Deterministic patches
 * cannot spread over time or write neighboring chunks.
 */
public final class CrossRealmBiomeSource extends BiomeSource {
    public static final MapCodec<CrossRealmBiomeSource> CODEC =
        RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("source").forGetter(CrossRealmBiomeSource::source),
            Codec.STRING.optionalFieldOf("target","auto").forGetter(CrossRealmBiomeSource::target),
            // Missing field preserves the original Nether/vanilla End spawn
            // in any old saved generator codec; NEW profiles set it true.
            Codec.BOOL.optionalFieldOf("spawn_end_habitats",false)
                .forGetter(CrossRealmBiomeSource::spawnEndHabitats),
            RegistryOps.retrieveGetter(Registries.BIOME)
        ).apply(i, CrossRealmBiomeSource::new));

    private static final int CELL=512;
    // A 192-block radius covers the origin spawn platform and vanilla
    // randomized 1st-spawn offsets. It deliberately does NOT turn the whole
    // world into a Nether/End biome.
    private static final long SPAWN_INVASION_RADIUS_SQUARED=192L*192L;
    public static boolean isSpawnInvasionZone(int quartX,int quartZ) {
        long x=(long)quartX*4L,z=(long)quartZ*4L;
        return x*x+z*z<=SPAWN_INVASION_RADIUS_SQUARED;
    }
    private final BiomeSource source;
    private final String target;
    private final boolean spawnEndHabitats;
    private final Holder<Biome> forest, plains, meadow, cherry, taiga, netherWastes, endHighlands;
    private final Holder<Biome> chorusGardens, crystalBarrens, shatteredGlades;

    private CrossRealmBiomeSource(BiomeSource source,String target,
                                 boolean spawnEndHabitats,HolderGetter<Biome> registry) {
        this.source=source;
        this.target=target;
        this.spawnEndHabitats=spawnEndHabitats;
        forest=get(registry,"forest");
        plains=get(registry,"plains");
        meadow=get(registry,"meadow");
        cherry=get(registry,"cherry_grove");
        taiga=get(registry,"taiga");
        netherWastes=get(registry,"nether_wastes");
        endHighlands=get(registry,"end_highlands");
        chorusGardens=getAccent(registry,"end_chorus_gardens");
        crystalBarrens=getAccent(registry,"end_crystal_barrens");
        shatteredGlades=getAccent(registry,"end_shattered_glades");
    }
    private static Holder<Biome> get(HolderGetter<Biome> registry,String id) {
        return registry.getOrThrow(ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("minecraft",id)));
    }
    private static Holder<Biome> getAccent(HolderGetter<Biome> registry,String id) {
        return registry.getOrThrow(ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("realscale",id)));
    }
    private BiomeSource source(){return source;}
    private String target(){return target;}
    private boolean spawnEndHabitats(){return spawnEndHabitats;}
    @Override protected MapCodec<? extends BiomeSource> codec(){return CODEC;}
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(source.possibleBiomes().stream(),
            Stream.of(forest,plains,meadow,cherry,taiga,netherWastes,endHighlands,
                      chorusGardens,crystalBarrens,shatteredGlades)).distinct();
    }
    private static long mix(long x) {
        x^=x>>>30;x*=0xbf58476d1ce4e5b9L;
        x^=x>>>27;x*=0x94d049bb133111ebL;
        return x^(x>>>31);
    }

    @Override public Holder<Biome> getNoiseBiome(int quartX,int quartY,int quartZ,
                                                  Climate.Sampler sampler) {
        Holder<Biome> original=source.getNoiseBiome(quartX,quartY,quartZ,sampler);
        // Only extend an actual Nether or End biome; never overwrite ocean,
        // rivers or overworld climate, even if somebody wraps the wrong source.
        String biome=original.unwrapKey().map(k->k.location().toString()).orElse("");
        if("overworld".equals(target) && isSpawnInvasionZone(quartX, quartZ)) {
            // Locked rule: the NEW WORLD's starting biome is EXACTLY one of
            // RealScale's three original End habitats, never a vanilla Nether
            // biome or a vanilla End biome. Determine the choice from a fixed
            // seed-dependent source sample, not local X/Y: the whole starting
            // column, even above Measured mountains, stays ONE biome.
            if (!spawnEndHabitats) {
                // Do not silently change the old immutable cross-realm world
                // generators already written to players' level.dat files.
                return (biome.hashCode() & 1)==0 ? netherWastes : endHighlands;
            }
            String anchor = source.getNoiseBiome(0,20,0,sampler).unwrapKey()
                    .map(k->k.location().toString()).orElse("minecraft:plains");
            int choice=(int)Math.floorMod(mix(anchor.hashCode() ^ 0x7346b127L),3L);
            return switch(choice) {
                case 0 -> chorusGardens;
                case 1 -> crystalBarrens;
                default -> shatteredGlades;
            };
        }
        if("overworld".equals(target)) {
            // A different site population from the Nether/End imported
            // overworld habitats. Strictly land-only and opt-in.
            int y=quartY*4;
            if(y<56||y>196||!biome.startsWith("minecraft:")
                    ||biome.endsWith("ocean")||biome.endsWith("river")
                    ||biome.contains("beach")||biome.contains("swamp"))
                return original;
            long x=(long)quartX*4,z=(long)quartZ*4;
            long gx=Math.floorDiv(x+CELL/2,CELL);
            long gz=Math.floorDiv(z+CELL/2,CELL);
            long hash=mix(gx*0x9e3779b97f4a7c15L
                          ^gz*0xc2b2ae3d27d4eb4fL ^0x1257b85L);
            if(Math.floorMod(hash,28L)!=0)return original;
            long cx=gx*CELL+(Math.floorMod(hash>>>10,97L)-48);
            long cz=gz*CELL+(Math.floorMod(hash>>>24,97L)-48);
            long dx=x-cx,dz=z-cz;
            int radius=36+(int)Math.floorMod(hash>>>42,28L);
            if(dx*dx+dz*dz>(long)radius*radius)return original;
            return (hash&16L)==0?netherWastes:endHighlands;
        }
        boolean nether=biome.equals("minecraft:nether_wastes")
                || biome.equals("minecraft:crimson_forest")
                || biome.equals("minecraft:warped_forest")
                || biome.equals("minecraft:soul_sand_valley")
                || biome.equals("minecraft:basalt_deltas");
        boolean end=biome.equals("minecraft:end_highlands")
                || biome.equals("minecraft:end_midlands")
                || biome.equals("minecraft:end_barrens")
                || biome.equals("minecraft:the_end")
                || biome.equals("minecraft:small_end_islands");
        if(!nether&&!end)return original;
        int y=quartY*4;
        if(nether&&(y<36||y>112))return original;
        if(end&&(y<48||y>160))return original;
        long x=(long)quartX*4,z=(long)quartZ*4;
        long gx=Math.floorDiv(x,CELL),gz=Math.floorDiv(z,CELL);
        long hash=mix(gx*0x9e3779b97f4a7c15L ^ gz*0xc2b2ae3d27d4eb4fL
                     ^ (nether?0x421a09L:0x7ad163L));
        // One eligible patch per 24 large cells: rare yet discoverable.
        if(Math.floorMod(hash,24L)!=0)return original;
        long cx=gx*CELL+CELL/2+(Math.floorMod(hash>>>12,129L)-64);
        long cz=gz*CELL+CELL/2+(Math.floorMod(hash>>>26,129L)-64);
        int radius=32+(int)Math.floorMod(hash>>>42,29L);
        long dx=x-cx,dz=z-cz;
        if(dx*dx+dz*dz>(long)radius*radius)return original;
        return switch((int)Math.floorMod(hash>>>52,5L)) {
            case 0 -> forest;
            case 1 -> plains;
            case 2 -> meadow;
            case 3 -> cherry;
            default -> taiga;
        };
    }
}

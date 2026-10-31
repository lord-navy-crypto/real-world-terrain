package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Optional;
import java.util.Random;

/**
 * Selects a first-world spawn from generated terrain samples, without forcing
 * chunks to load. Only RealScale overworld noise settings are affected.
 */
public final class SpawnAltitudeSelector {
    private static final Logger LOGGER=LogUtils.getLogger();
    private static final int[] RINGS={32,64,96,128,160,192,224,256};

    private SpawnAltitudeSelector() {}

    public static void onCreateSpawnPosition(LevelEvent.CreateSpawnPosition event) {
        if(!(event.getLevel() instanceof ServerLevel level))return;
        if(!Level.OVERWORLD.equals(level.dimension()))return;

        ServerLevelData settings=event.getSettings();
        if(settings.isInitialized())return;

        if(!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator))
            return;
        Optional<ResourceKey<NoiseGeneratorSettings>> key=
                generator.generatorSettings().unwrapKey();
        if(key.isEmpty() || !"realscale".equals(key.get().location().getNamespace()))
            return;

        Optional<Selection> selected=select(level,generator,key.get().location().getPath());
        if(selected.isEmpty())return;

        Selection result=selected.get();
        level.setDefaultSpawnPos(result.position(),result.yaw());
        event.setCanceled(true);
        LOGGER.info("RealScale selected first-world spawn {} at Y={} (target Y={}, preset={}, sampled {} candidates)",
                result.position(),result.position().getY(),result.targetY(),
                key.get().location(),result.samples());
    }

    static Optional<Selection> select(ServerLevel level,NoiseBasedChunkGenerator generator,
                                      String preset) {
        final int seaLevel=generator.getSeaLevel();
        final int targetY=SpawnAltitudeMath.targetAltitude(preset,seaLevel,level.getSeed());
        final RandomState randomState=level.getChunkSource().randomState();
        final long seed=SpawnAltitudeMath.mix64(level.getSeed() ^ preset.hashCode());
        final Random random=new Random(seed);
        final double rotation=random.nextDouble()*Math.PI*2.0;

        Candidate best=null;
        int samples=0;
        // Always evaluate the origin, then eight directions on each bounded ring.
        best=evaluate(level,generator,randomState,0,0,0,targetY,seaLevel,best);
        samples++;

        for(int ring=0;ring<RINGS.length;ring++) {
            int baseRadius=RINGS[ring];
            for(int sector=0;sector<8;sector++) {
                double angle=rotation+(sector+random.nextDouble()*0.35)*(Math.PI/4.0);
                int radius=Math.max(16,baseRadius+random.nextInt(17)-8);
                int x=(int)Math.round(Math.cos(angle)*radius);
                int z=(int)Math.round(Math.sin(angle)*radius);
                best=evaluate(level,generator,randomState,x,z,radius,
                        targetY,seaLevel,best);
                samples++;
            }
        }
        if(best==null)return Optional.empty();
        return Optional.of(new Selection(new BlockPos(best.x,best.surfaceY,best.z),
                targetY,best.score,samples,(float)(random.nextDouble()*360.0)));
    }

    private static Candidate evaluate(ServerLevel level,NoiseBasedChunkGenerator generator,
                                       RandomState randomState,int x,int z,int distance,
                                       int targetY,int seaLevel,Candidate best) {
        // getFirstFreeHeight evaluates the noise column directly; it does not
        // synchronously generate or load a chunk at each candidate coordinate.
        int surfaceY=generator.getFirstFreeHeight(x,z,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,level,randomState);
        if(surfaceY<=seaLevel+8 || surfaceY<=level.getMinBuildHeight()+1
                || surfaceY>=level.getMaxBuildHeight()-32)
            return best;

        BlockPos biomePos=new BlockPos(x,Math.max(level.getMinBuildHeight(),surfaceY-1),z);
        Holder<Biome> biome=level.getBiome(biomePos);
        if(isWetSpawnBiome(biome))return best;

        double score=SpawnAltitudeMath.candidateScore(surfaceY,targetY,seaLevel,
                distance,level.getMinBuildHeight(),level.getMaxBuildHeight());
        if(best==null || score<best.score)
            return new Candidate(x,z,surfaceY,score);
        return best;
    }

    private static boolean isWetSpawnBiome(Holder<Biome> biome) {
        if(biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER))return true;
        return biome.unwrapKey().map(key -> {
            String path=key.location().getPath();
            return path.contains("ocean") || path.contains("river")
                    || path.contains("marsh") || path.contains("wetland")
                    || path.contains("bog") || path.contains("floodplain");
        }).orElse(false);
    }

    record Selection(BlockPos position,int targetY,double score,int samples,float yaw) {}
    private record Candidate(int x,int z,int surfaceY,double score) {}
}

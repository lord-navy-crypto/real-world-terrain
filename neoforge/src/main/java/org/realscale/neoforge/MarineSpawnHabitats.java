package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Conservative vanilla sea-life habitat filter. NO entity addition and NO
 * biome-wide spawn modifier: all non-RealScale worlds remain vanilla.
 * Only narrows natural spawning, preserving mob caps, groups, spawn placement,
 * buckets, commands, structures and vanilla population mechanics.
 */
public final class MarineSpawnHabitats {
    private static final ResourceLocation ENABLED =
            ResourceLocation.fromNamespaceAndPath("realscale","usgs_global_hybrid_marine");
    private MarineSpawnHabitats() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MarineSpawnHabitats::onSpawn);
    }

    private static void onSpawn(MobSpawnEvent.PositionCheck event) {
        if(event.getSpawnType()!=MobSpawnType.NATURAL)return;
        Mob mob=event.getEntity();
        EntityType<?> type=mob.getType();
        if(type!=EntityType.COD && type!=EntityType.SALMON
                && type!=EntityType.TROPICAL_FISH && type!=EntityType.PUFFERFISH
                && type!=EntityType.SQUID && type!=EntityType.DOLPHIN)return;
        if(!(event.getLevel().getLevel() instanceof ServerLevel level))return;
        if(!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator noise))return;
        if(!noise.generatorSettings().unwrapKey()
                .map(k->k.location().equals(ENABLED)).orElse(false))return;
        BlockPos pos=mob.blockPosition();
        String biome=level.getBiome(pos).unwrapKey()
                .map(k->k.location().toString()).orElse("");
        if(!MarineEcologyZonationMath.ocean(biome))return;
        if(!level.getBlockState(pos).is(Blocks.WATER))return;
        int sea=level.getSeaLevel();
        int floor=level.getHeight(Heightmap.Types.OCEAN_FLOOR,pos.getX(),pos.getZ());
        int column=Math.max(0,sea-floor);
        int above=Math.max(0,pos.getY()-floor);
        boolean hard=MarineEcologyFeature.isHardSeafloor(level.getBlockState(
                new BlockPos(pos.getX(),floor-1,pos.getZ())));
        double patch=MarineEcologyZonationMath.patch(pos.getX(),pos.getZ(),level.getSeed());
        MarineEcologyZonationMath.Habitat habitat=
                MarineEcologyZonationMath.classify(biome,column,hard,patch);
        // Sampling rejection is deterministic over coarse cells and mob types,
        // not per tick. No further entities are created, so caps stay vanilla.
        double chance=MarineMarineLifeMath.suitability(type,biome,habitat,column,above);
        long seed=level.getSeed() ^ ((long)pos.getX()/8*0x9e3779b97f4a7c15L)
                ^ ((long)pos.getZ()/8*0x632be59bd9b4e019L)
                ^ ((long)type.hashCode()*0x94d049bb133111ebL);
        if(MarineEcologyZonationMath.unit(seed)>chance)
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
    }
}

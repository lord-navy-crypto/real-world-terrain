package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Ensures every NEW unified invasion world initially spawns inside an
 * authentic Nether/End biome, not merely "near" a rare invasion patch.
 *
 * CreateSpawnPosition only fires when initializing a new world. Existing
 * saved worlds, manual spawn edits and vanilla worlds are left untouched.
 */
@EventBusSubscriber(modid="realscale")
public final class InvasionInitialSpawn {
    private InvasionInitialSpawn() {}

    @SubscribeEvent
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD))
            return;
        BiomeSource source=level.getChunkSource().getGenerator().getBiomeSource();
        if (!(source instanceof SkyOceanBiomeSource sky && sky.containsCrossRealm())
                && !(source instanceof CrossRealmBiomeSource)
                && !(source instanceof CrossRealmShowcaseBiomeSource))
            return;

        // Bounded, one-column chunk generation rather than a radius-wide
        // biome search. A deliberately small stable 3x3 standing area is
        // installed only when the save is first created.
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,0,0);
        y=Math.max(80,y);
        y=Math.max(level.getMinBuildHeight()+5,
                   Math.min(level.getMaxBuildHeight()-5,y));
        BlockPos spawn=new BlockPos(0,y,0);
        for (int dx=-1;dx<=1;dx++)
            for (int dz=-1;dz<=1;dz++) {
                BlockPos floor=spawn.offset(dx,-1,dz);
                level.setBlockAndUpdate(floor,
                    (Math.abs(dx)==1 && Math.abs(dz)==1)
                        ? Blocks.GLOWSTONE.defaultBlockState()
                        : Blocks.BLACKSTONE.defaultBlockState());
                for (int dy=0;dy<3;dy++)
                    level.setBlockAndUpdate(spawn.offset(dx,dy,dz),
                                            Blocks.AIR.defaultBlockState());
            }
        event.getSettings().setSpawn(spawn,0.0F);
        event.setCanceled(true);
    }
}

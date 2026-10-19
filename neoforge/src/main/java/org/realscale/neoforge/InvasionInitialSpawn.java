package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * NEW-world-only survival arrival point. The saved unified Overworld biome
 * source guarantees one of RealScale's three End habitats at 0/0.
 *
 * This listener NEVER resets a world spawn on reload, edits far chunks, or
 * enables invasion in a pre-existing canonical/foreign world.
 */
@EventBusSubscriber(modid="realscale")
public final class InvasionInitialSpawn {
    private InvasionInitialSpawn() {}

    private static boolean isOurEndBiome(String id) {
        return id.equals("realscale:end_chorus_gardens")
            || id.equals("realscale:end_crystal_barrens")
            || id.equals("realscale:end_shattered_glades");
    }

    @SubscribeEvent
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)) return;
        BiomeSource source=level.getChunkSource().getGenerator().getBiomeSource();
        if (!(source instanceof SkyOceanBiomeSource sky && sky.containsCrossRealm())
                && !(source instanceof CrossRealmBiomeSource)
                && !(source instanceof CrossRealmShowcaseBiomeSource)) return;

        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,0,0);
        // Works with Measured's taller landscape and lowland/ocean spawns.
        y=Math.max(80,y);
        y=Math.max(level.getMinBuildHeight()+6,
                   Math.min(level.getMaxBuildHeight()-7,y));
        BlockPos spawn=new BlockPos(0,y,0);

        // Never modify the world if another preset or codec intercepted
        // the origin; the three custom biome IDs are a required invariant.
        String biome=level.getBiome(spawn).unwrapKey()
                .map(k->k.location().toString()).orElse("");
        if(!isOurEndBiome(biome)) return;

        boolean chorus=biome.equals("realscale:end_chorus_gardens");
        boolean crystal=biome.equals("realscale:end_crystal_barrens");
        BlockState base=(chorus?Blocks.END_STONE_BRICKS:
                crystal?Blocks.CALCITE:Blocks.PURPUR_BLOCK).defaultBlockState();
        BlockState inlay=(chorus?Blocks.PURPUR_BLOCK:
                crystal?Blocks.AMETHYST_BLOCK:Blocks.END_STONE_BRICKS).defaultBlockState();

        // Compact 9x9 lit survival pad with a continuous low guardrail.
        // No opening over a high mountain cliff or sea. A player can jump
        // or break the railing to leave when ready.
        // Small and bounded: 81 columns, never a world-scale spawn rewrite.
        for(int dx=-4;dx<=4;dx++) for(int dz=-4;dz<=4;dz++) {
            int ax=Math.abs(dx),az=Math.abs(dz);
            boolean perimeter=ax==4||az==4;
            BlockPos floor=spawn.offset(dx,-1,dz);
            BlockState floorState=(ax==0 || az==0 || (ax==3&&az==3))
                    ? inlay : base;
            level.setBlockAndUpdate(floor,floorState);
            for(int dy=0;dy<4;dy++) {
                BlockPos head=spawn.offset(dx,dy,dz);
                level.setBlockAndUpdate(head,Blocks.AIR.defaultBlockState());
            }
            if(perimeter) {
                // The four light posts make the rim visible without using
                // lava or dangerous environmental effects.
                if(ax==4 && az==4) {
                    level.setBlockAndUpdate(spawn.offset(dx,0,dz),
                        Blocks.END_STONE_BRICKS.defaultBlockState());
                    level.setBlockAndUpdate(spawn.offset(dx,1,dz),
                        Blocks.SEA_LANTERN.defaultBlockState());
                } else {
                    level.setBlockAndUpdate(spawn.offset(dx,0,dz),
                        Blocks.END_STONE_BRICK_WALL.defaultBlockState());
                }
            }
        }
        // The original respawn direction may still be randomized by gamerule
        // spawnRadius; this core location and its surrounding biome are fixed.
        event.getSettings().setSpawn(spawn,0.0F);
        event.setCanceled(true);
    }
}

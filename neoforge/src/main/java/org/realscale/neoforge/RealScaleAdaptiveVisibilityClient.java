package org.realscale.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * CLIENT-ONLY opt-in observation of surface/cave exposure for RealScale.
 *
 * Nothing in this class edits visibility, render distance, render Sections,
 * world state, server chunks or Sodium's private scheduling queues.
 * Sampling defaults OFF; when enabled it uses only nearby loaded chunks,
 * at most five sky probes once per twenty game ticks, and one surface
 * heightmap lookup. Unloaded/uncertain areas revert to TRANSITION.
 *
 * Enable diagnostic logs with -Drealscale.client.visibility.profile=true .
 * This is a safe first half of adaptive budgets, not claimed FPS optimization.
 */
@EventBusSubscriber(modid = "realscale", value = Dist.CLIENT)
public final class RealScaleAdaptiveVisibilityClient {
    private RealScaleAdaptiveVisibilityClient() {}

    private static final boolean PROFILE =
            Boolean.getBoolean("realscale.client.visibility.profile");
    private static final int SAMPLE_INTERVAL_TICKS = 20;
    private static final int PRINT_INTERVAL_SAMPLES = 15;
    private static final AdaptiveVisibilityBudget.Tracker TRACKER =
            new AdaptiveVisibilityBudget.Tracker();
    private static final int[][] PROBES = {
        {0,0},{8,0},{-8,0},{0,8},{0,-8}
    };

    private static ClientLevel rememberedLevel;
    private static BlockPos previous;
    private static int ticks;
    private static int sinceLog;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!PROFILE) return; // zero sampling/query cost without explicit opt-in
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level != rememberedLevel) {
            TRACKER.reset();
            rememberedLevel = level;
            previous = null;
            ticks = 0;
            sinceLog = 0;
        }
        if (level == null || player == null) return;
        if (++ticks < SAMPLE_INTERVAL_TICKS) return;
        ticks = 0;

        BlockPos eye = BlockPos.containing(player.getEyePosition());
        boolean movedFar = previous != null &&
                (Math.abs((long)eye.getX()-previous.getX()) > 24 ||
                 Math.abs((long)eye.getY()-previous.getY()) > 24 ||
                 Math.abs((long)eye.getZ()-previous.getZ()) > 24);
        previous = eye;
        AdaptiveVisibilityBudget.Observation sample = observe(level,player,eye,movedFar);
        AdaptiveVisibilityBudget.Mode before = TRACKER.mode();
        AdaptiveVisibilityBudget.Mode now = TRACKER.observe(sample);

        if (before != now) {
            System.err.println("[RealScale adaptive visibility] " + before +
                    " -> " + now + " at " + eye.toShortString() +
                    " (advisory only; no renderer modifications)");
        }
        if (++sinceLog >= PRINT_INTERVAL_SAMPLES) {
            sinceLog = 0;
            System.err.println("[RealScale adaptive visibility] mode=" + now +
                    " samples=" + TRACKER.samples() +
                    " transitions=" + TRACKER.transitions() +
                    " skyAtCamera=" + sample.skyAtCamera() +
                    " nearbySky=" + sample.nearbySkyAccess() +
                    " evidenceKnown=" + sample.known() +
                    " (client-only probe; not section visibility or GPU work)");
        }
    }

    private static AdaptiveVisibilityBudget.Observation observe(
            ClientLevel level, LocalPlayer player, BlockPos eye, boolean movedFar) {
        // The dimensional sky model is different in Nether/End. Keep this
        // experimental high-mountain detector confined to the Overworld.
        if (!level.dimension().equals(Level.OVERWORLD) ||
                player.isUnderWater() || movedFar)
            return unknown();

        int exposed = 0;
        boolean centerSky = false;
        for (int i=0; i<PROBES.length; i++) {
            BlockPos pos = eye.offset(PROBES[i][0],0,PROBES[i][1]);
            // Never access a remote Section just to decide exposure.
            if (pos.getY() < level.getMinBuildHeight() ||
                    pos.getY() >= level.getMaxBuildHeight() ||
                    !level.hasChunk(Math.floorDiv(pos.getX(),16),
                                    Math.floorDiv(pos.getZ(),16)))
                return unknown();
            boolean seesSky = level.canSeeSky(pos);
            if (i==0) centerSky = seesSky;
            if (seesSky) exposed++;
        }
        boolean nearbySky = exposed>0;
        // Partially exposed probes are likely cave mouths, sinkholes,
        // cliff faces or thin roofs; do not give one side special treatment.
        boolean ambiguousOpening = exposed>0 && exposed<PROBES.length;
        int surfaceY = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                eye.getX(),eye.getZ());
        boolean substantialCover = surfaceY > eye.getY()+12;
        return new AdaptiveVisibilityBudget.Observation(
                true,centerSky,nearbySky,substantialCover,ambiguousOpening,false);
    }

    private static AdaptiveVisibilityBudget.Observation unknown() {
        return new AdaptiveVisibilityBudget.Observation(
                false,false,false,false,true,false);
    }
}

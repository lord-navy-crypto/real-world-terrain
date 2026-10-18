package org.realscale.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Optional, physical-client-only FPS and frame interval measurements.
 *
 * Uses NeoForge's public RenderFrameEvent.Post, once per displayed frame.
 * It NEVER changes worldgen, Section visibility, render distance, render
 * queue, VSync, shader state, or Sodium.
 *
 * JVM options:
 * -Drealscale.client.fps.profile=true
 * -Drealscale.client.fps.scene=surface_alpine
 *
 * Logged frame deltas are Post-to-Post wall time including VSync and idle
 * pacing; not a GPU/CPU render-duration or a proof of optimization benefit.
 * For comparable results disable FPS caps/VSync in BOTH runs, retain identical
 * settings, pre-generate chunks, and keep the game window foreground.
 */
@EventBusSubscriber(modid="realscale", value=Dist.CLIENT)
public final class RealScaleClientFrameProfiler {
    private RealScaleClientFrameProfiler(){}

    private static final boolean ENABLED =
            Boolean.getBoolean("realscale.client.fps.profile");
    private static final long WARMUP_NANOS=10_000_000_000L;
    private static final long REPORT_NANOS=30_000_000_000L;
    private static final String SCENE = System.getProperty(
            "realscale.client.fps.scene","unspecified")
            .replaceAll("[^A-Za-z0-9_-]","_").substring(0,
                    Math.min(48,System.getProperty(
                            "realscale.client.fps.scene","unspecified").length()));

    private static ClientLevel lastLevel;
    private static long warmupUntil,previousFrameNanos,windowStarted;
    private static int reportIndex;
    private static ClientFrameIntervalStats window = new ClientFrameIntervalStats();

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Post event) {
        if (!ENABLED) return; // No per-frame nanotime calls when OFF.
        Minecraft client=Minecraft.getInstance();
        ClientLevel world=client.level;
        long now=System.nanoTime();
        // Do not include menu/loading-screen intervals in active-world FPS.
        if(world==null || client.player==null || client.screen!=null) {
            reset(world,now);
            return;
        }
        if(world!=lastLevel){
            reset(world,now);
            return;
        }
        if(previousFrameNanos==0) {
            previousFrameNanos=now;
            return;
        }
        long delta=now-previousFrameNanos;
        previousFrameNanos=now;
        // Alt-tab, OS suspend, debugger stalls etc. must not masquerade
        // as ordinary chunk rendering; start fresh after a >2s gap.
        if(delta<=0 || delta>2_000_000_000L){
            reset(world,now);
            return;
        }
        if(now<warmupUntil)return;
        window.add(delta);
        if(now-windowStarted>=REPORT_NANOS && window.frames()>=30) {
            String mode=RealScaleAdaptiveVisibilityClient.currentMode().name();
            System.err.println("[RealScale fps profile] interval="+(++reportIndex)+
                    " "+window.report(SCENE,mode));
            window=new ClientFrameIntervalStats();
            windowStarted=now;
        }
    }

    private static void reset(ClientLevel world,long now) {
        lastLevel=world;
        warmupUntil=now+WARMUP_NANOS;
        previousFrameNanos=0L;
        windowStarted=warmupUntil;
        window=new ClientFrameIntervalStats();
    }
}

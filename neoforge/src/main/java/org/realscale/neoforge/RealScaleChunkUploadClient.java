package org.realscale.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Actual opt-in client upload pacing and diagnostic telemetry.
 *
 * Pre/Post bracket one rendered frame. Menus, loading, disconnects and
 * uploads outside that bracket use vanilla's unmodified upload loop.
 * All queued tasks remain queued; this only time-slices completion.
 * Does not limit the player's configured render distance.
 */
@EventBusSubscriber(modid="realscale",value=Dist.CLIENT)
public final class RealScaleChunkUploadClient {
    private RealScaleChunkUploadClient(){}

    public static final boolean ENABLED=Boolean.parseBoolean(System.getProperty(
            "realscale.experimental.client.uploadbudget","false"));
    // Both properties are required. Mode-aware scheduling never activates
    // the experimental upload Mixin by itself.
    public static final boolean MODE_AWARE=ENABLED &&
            Boolean.parseBoolean(System.getProperty(
                    "realscale.experimental.client.uploadbudget.modeaware","false"));
    public static final boolean ADAPTIVE=MODE_AWARE &&
            Boolean.parseBoolean(System.getProperty(
                    "realscale.experimental.client.uploadbudget.adaptive","false"));
    private static final ClientUploadFrameGovernor GOVERNOR=
            new ClientUploadFrameGovernor();
    private static long frameBeginNanos;
    private static final boolean PROFILE=ENABLED ||
            Boolean.getBoolean("realscale.client.chunkqueue.profile");
    public static final ClientChunkUploadBudget BUDGET =
            ClientChunkUploadBudget.fromProperties();

    private static ClientLevel lastWorld;
    private static long lastReportNanos;
    private static long frames,framesWithBacklog,totalQueued,totalUploading;
    private static int peakQueued,peakUploading,minFreeBuffers=Integer.MAX_VALUE;
    private static long budgetFrames,uploaded;
    private static long lastReportedDeferred;

    @SubscribeEvent public static void onPre(RenderFrameEvent.Pre event){
        if(!PROFILE)return;
        Minecraft mc=Minecraft.getInstance();
        ClientLevel world=mc.level;
        boolean playing=world!=null && mc.player!=null && mc.screen==null;
        if(world!=lastWorld){
            reset(world);
        }
        if(ENABLED) {
            // Measure only with the explicit third opt-in. A Pre-to-Post
            // interval includes frame pacing and is not GPU-only render time.
            frameBeginNanos=ADAPTIVE&&playing?System.nanoTime():0L;
            if(MODE_AWARE) {
                SectionRenderDispatcher dispatcher=playing
                        ?mc.levelRenderer.getSectionRenderDispatcher():null;
                AdaptiveVisibilityBudget.Mode state=playing&&dispatcher!=null
                        ?RealScaleAdaptiveVisibilityClient.currentMode()
                        :AdaptiveVisibilityBudget.Mode.TRANSITION;
                ClientChunkUploadBudget.VisibilityMode uploadMode=switch(state) {
                    case OPEN_SURFACE -> ClientChunkUploadBudget.VisibilityMode.OPEN_SURFACE;
                    case SEALED_CAVE -> ClientChunkUploadBudget.VisibilityMode.SEALED_CAVE;
                    case TRANSITION -> ClientChunkUploadBudget.VisibilityMode.TRANSITION;
                };
                BUDGET.beginModeAwareFrame(playing,uploadMode,
                        dispatcher==null?0:Math.max(0,dispatcher.getToUpload()),
                        ADAPTIVE&&GOVERNOR.constrained());
            } else BUDGET.beginFrame(playing);
        }
    }

    @SubscribeEvent public static void onPost(RenderFrameEvent.Post event){
        if(!PROFILE)return;
        Minecraft mc=Minecraft.getInstance();
        if(ENABLED){
            if(ADAPTIVE && frameBeginNanos!=0L){
                GOVERNOR.observe(System.nanoTime()-frameBeginNanos);
                frameBeginNanos=0L;
            }
            if(BUDGET.active()){
                uploaded+=BUDGET.tasksThisFrame();
                if(BUDGET.spentNanosThisFrame()>=BUDGET.frameMaxNanos()
                        ||BUDGET.tasksThisFrame()>=BUDGET.frameMaxTasks())
                    budgetFrames++;
            }
            BUDGET.endFrame();
        }
        if(mc.level==null || mc.player==null || mc.screen!=null)return;
        SectionRenderDispatcher dispatcher=mc.levelRenderer.getSectionRenderDispatcher();
        if(dispatcher==null)return;
        int queued=Math.max(0,dispatcher.getToBatchCount());
        int uploading=Math.max(0,dispatcher.getToUpload());
        int free=Math.max(0,dispatcher.getFreeBufferCount());
        frames++;
        if(queued>0||uploading>0)framesWithBacklog++;
        totalQueued+=queued;
        totalUploading+=uploading;
        peakQueued=Math.max(peakQueued,queued);
        peakUploading=Math.max(peakUploading,uploading);
        minFreeBuffers=Math.min(minFreeBuffers,free);
        long now=System.nanoTime();
        if(lastReportNanos==0)lastReportNanos=now;
        if(now-lastReportNanos<30_000_000_000L)return;
        if(frames>0){
            double denom=(double)frames;
            long currentDeferred=BUDGET.deferredCalls();
            System.err.println("REALSCALE_CLIENT_CHUNK_QUEUE "+
                    "mode="+(MODE_AWARE?"mode_aware_upload_budget":
                            ENABLED?"upload_budget":"vanilla_probe")+
                    " visibility="+(MODE_AWARE?
                            RealScaleAdaptiveVisibilityClient.currentMode():"not_sampled")+
                    " transition_bypass_frames="+BUDGET.transitionBypasses()+
                    " backlog_rescue_frames="+BUDGET.backlogRescueFrames()+
                    " adaptive="+ADAPTIVE+
                    " adaptive_constrained="+GOVERNOR.constrained()+
                    " adaptive_slow_frames="+GOVERNOR.slowFrames()+
                    " adaptive_reduced_frames="+BUDGET.adaptiveReducedFrames()+
                    " frames="+frames+
                    " backlog_frames="+framesWithBacklog+
                    " avg_compile_pending="+String.format(java.util.Locale.ROOT,"%.2f",totalQueued/denom)+
                    " peak_compile_pending="+peakQueued+
                    " avg_upload_pending="+String.format(java.util.Locale.ROOT,"%.2f",totalUploading/denom)+
                    " peak_upload_pending="+peakUploading+
                    " min_free_mesh_buffers="+minFreeBuffers+
                    " uploaded_by_budget="+uploaded+
                    " budget_frames="+budgetFrames+
                    " deferred_calls="+(currentDeferred-lastReportedDeferred)+
                    " no_render_distance_or_worldgen_change=true"+
                    " no_section_reordering=true");
            lastReportedDeferred=currentDeferred;
        }
        frames=framesWithBacklog=totalQueued=totalUploading=budgetFrames=uploaded=0;
        peakQueued=peakUploading=0;
        minFreeBuffers=Integer.MAX_VALUE;
        lastReportNanos=now;
    }

    private static void reset(ClientLevel world){
        lastWorld=world;
        frames=framesWithBacklog=totalQueued=totalUploading=budgetFrames=uploaded=0;
        peakQueued=peakUploading=0;
        minFreeBuffers=Integer.MAX_VALUE;
        lastReportNanos=0L;
        lastReportedDeferred=BUDGET.deferredCalls();
        if(ENABLED)BUDGET.endFrame();
        frameBeginNanos=0L;
        if(ADAPTIVE)GOVERNOR.reset();
    }
}

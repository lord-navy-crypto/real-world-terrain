package org.realscale.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import java.lang.reflect.Method;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;

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

    public static final boolean ENABLED=
            RealScalePerformanceProfile.clientUploadBudgetEnabled();
    // Both properties are required. Mode-aware scheduling never activates
    // the experimental upload Mixin by itself.
    public static final boolean MODE_AWARE=
            RealScalePerformanceProfile.modeAwareUploadEnabled();
    public static final boolean ADAPTIVE=
            RealScalePerformanceProfile.adaptiveUploadEnabled();
    public static final boolean PREDICTIVE=
            RealScalePerformanceProfile.predictiveUploadEnabled();
    private static final ClientUploadFrameGovernor GOVERNOR=
            new ClientUploadFrameGovernor();
    private static long frameBeginNanos;
    private static final boolean PROFILE=ENABLED
            || RealScalePerformanceProfile.diagnosticsEnabled()
            || Boolean.getBoolean("realscale.client.chunkqueue.profile");
    public static final ClientChunkUploadBudget BUDGET =
            ClientChunkUploadBudget.fromProperties();

    private static ClientLevel lastWorld;
    private static Method dispatcherAccessor;
    private static Method queuedAccessor;
    private static Method uploadingAccessor;
    private static Method freeBuffersAccessor;
    private static boolean dispatcherLookupAttempted;
    private static long lastReportNanos;
    private static long frames,framesWithBacklog,totalQueued,totalUploading;
    private static int peakQueued,peakUploading,minFreeBuffers=Integer.MAX_VALUE;
    private static long budgetFrames,uploaded;
    private static long lastReportedDeferred;
    private static long heapSamples,totalHeapUsedBytes,peakHeapUsedBytes,maxHeapBytes;

    @SubscribeEvent public static void onPre(TickEvent.RenderTickEvent event){
        if (event.phase != TickEvent.Phase.START) return;
        if(!PROFILE)return;
        Minecraft mc=Minecraft.getInstance();
        ClientLevel world=mc.level;
        boolean playing=world!=null && mc.player!=null && mc.screen==null;
        if(world!=lastWorld){
            reset(world);
        }
        // A menu or disconnect cannot carry an old slow-frame streak into
        // the next playable scene, even when the level object is unchanged.
        if(ADAPTIVE && !playing)GOVERNOR.reset();
        if(PREDICTIVE && !playing)BUDGET.resetPrediction();
        if(ENABLED) {
            // Measure only with the explicit third opt-in. A Pre-to-Post
            // interval includes frame pacing and is not GPU-only render time.
            frameBeginNanos=ADAPTIVE&&playing?System.nanoTime():0L;
            if(MODE_AWARE) {
                Object dispatcher=playing ? getSectionDispatcher(mc) : null;
                RealScaleDomainManager.Domain domain=playing&&dispatcher!=null
                        ?RealScaleAdaptiveVisibilityClient.currentDomainFor(world,mc.player)
                        :RealScaleDomainManager.Domain.TRANSITION;
                ClientChunkUploadBudget.VisibilityMode uploadMode=switch(domain) {
                    case SURFACE -> ClientChunkUploadBudget.VisibilityMode.OPEN_SURFACE;
                    case UNDERGROUND -> ClientChunkUploadBudget.VisibilityMode.SEALED_CAVE;
                    case TRANSITION -> ClientChunkUploadBudget.VisibilityMode.TRANSITION;
                };
                BUDGET.beginModeAwareFrame(playing,uploadMode,
                        dispatcher==null?0:readDispatcherCount(dispatcher,uploadingAccessor),
                        ADAPTIVE&&GOVERNOR.constrained(),PREDICTIVE);
            } else BUDGET.beginFrame(playing);
        }
    }

    @SubscribeEvent public static void onPost(TickEvent.RenderTickEvent event){
        if (event.phase != TickEvent.Phase.END) return;
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
        Object dispatcher=getSectionDispatcher(mc);
        if(dispatcher==null)return;
        int queued=readDispatcherCount(dispatcher,queuedAccessor);
        int uploading=readDispatcherCount(dispatcher,uploadingAccessor);
        int free=readDispatcherCount(dispatcher,freeBuffersAccessor);
        Runtime runtime=Runtime.getRuntime();
        long heapUsed=Math.max(0L,runtime.totalMemory()-runtime.freeMemory());
        long heapMax=Math.max(1L,runtime.maxMemory());
        heapSamples++;
        totalHeapUsedBytes=Long.MAX_VALUE-totalHeapUsedBytes<heapUsed
                ?Long.MAX_VALUE:totalHeapUsedBytes+heapUsed;
        peakHeapUsedBytes=Math.max(peakHeapUsedBytes,heapUsed);
        maxHeapBytes=Math.max(maxHeapBytes,heapMax);
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
                            RealScaleAdaptiveVisibilityClient.currentModeFor(mc.level,mc.player):"not_sampled")+
                    " transition_bypass_frames="+BUDGET.transitionBypasses()+
                    " backlog_rescue_frames="+BUDGET.backlogRescueFrames()+
                    " adaptive="+ADAPTIVE+
                    " adaptive_constrained="+GOVERNOR.constrained()+
                    " adaptive_slow_frames="+GOVERNOR.slowFrames()+
                    " adaptive_reduced_frames="+BUDGET.adaptiveReducedFrames()+
                    " predictive="+PREDICTIVE+
                    " estimated_task_us="+(BUDGET.estimatedTaskNanos()/1000L)+
                    " predictive_stops="+BUDGET.predictiveStops()+
                    " frames="+frames+
                    " backlog_frames="+framesWithBacklog+
                    " avg_compile_pending="+String.format(java.util.Locale.ROOT,"%.2f",totalQueued/denom)+
                    " peak_compile_pending="+peakQueued+
                    " avg_upload_pending="+String.format(java.util.Locale.ROOT,"%.2f",totalUploading/denom)+
                    " peak_upload_pending="+peakUploading+
                    " min_free_mesh_buffers="+minFreeBuffers+
                    " avg_heap_used_mib="+(heapSamples==0?0:totalHeapUsedBytes/heapSamples/1_048_576L)+
                    " peak_heap_used_mib="+(peakHeapUsedBytes/1_048_576L)+
                    " max_heap_mib="+(maxHeapBytes/1_048_576L)+
                    " peak_heap_used_pct="+(maxHeapBytes<=0?0:Math.round(100.0*peakHeapUsedBytes/maxHeapBytes))+
                    " heap_metric=JVM_heap_not_GPU_memory"+
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
        heapSamples=totalHeapUsedBytes=peakHeapUsedBytes=maxHeapBytes=0L;
        lastReportNanos=now;
    }

    public record QueueSnapshot(boolean available,boolean budgetEnabled,
            boolean modeAware,boolean adaptive,boolean predictive,
            int compilePending,int uploadPending,int freeMeshBuffers,
            long sampleFrames,double averageCompilePending,double averageUploadPending,
            int peakCompilePending,int peakUploadPending,long heapUsedBytes,
            long heapMaxBytes,long deferredCalls,long uploadedTasks,
            RealScaleDomainManager.Domain activeDomain,int uploadMultiplier) {}

    public static QueueSnapshot snapshot() {
        Minecraft mc=Minecraft.getInstance();
        int queued=0,uploading=0,free=0;
        boolean available=mc.level!=null && mc.player!=null
                && mc.levelRenderer!=null;
        if(available) {
            Object dispatcher=getSectionDispatcher(mc);
            if(dispatcher==null) available=false;
            else {
                queued=readDispatcherCount(dispatcher,queuedAccessor);
                uploading=readDispatcherCount(dispatcher,uploadingAccessor);
                free=readDispatcherCount(dispatcher,freeBuffersAccessor);
            }
        }
        Runtime runtime=Runtime.getRuntime();
        long heapUsed=Math.max(0L,runtime.totalMemory()-runtime.freeMemory());
        long heapMax=Math.max(1L,runtime.maxMemory());
        double denominator=Math.max(1L,frames);
        return new QueueSnapshot(available,ENABLED,MODE_AWARE,ADAPTIVE,PREDICTIVE,
                queued,uploading,free,frames,totalQueued/denominator,
                totalUploading/denominator,peakQueued,peakUploading,
                heapUsed,heapMax,BUDGET.deferredCalls(),BUDGET.tasksThisFrame(),
                BUDGET.activeDomain(),BUDGET.activeUploadMultiplier());
    }

    /** Resolve the chunk renderer without a version-specific compile-time type. */
    private static Object getSectionDispatcher(Minecraft minecraft) {
        if (minecraft.levelRenderer == null) return null;
        if (!dispatcherLookupAttempted) {
            dispatcherLookupAttempted = true;
            Class<?> rendererType = minecraft.levelRenderer.getClass();
            dispatcherAccessor = findNoArg(rendererType, "getSectionRenderDispatcher");
            if (dispatcherAccessor == null) dispatcherAccessor = findNoArg(rendererType, "getChunkRenderDispatcher");
        }
        if (dispatcherAccessor == null) return null;
        try {
            Object dispatcher = dispatcherAccessor.invoke(minecraft.levelRenderer);
            if (dispatcher != null && queuedAccessor == null && uploadingAccessor == null && freeBuffersAccessor == null) {
                Class<?> type = dispatcher.getClass();
                queuedAccessor = findNoArg(type, "getToBatchCount");
                uploadingAccessor = findNoArg(type, "getToUpload");
                freeBuffersAccessor = findNoArg(type, "getFreeBufferCount");
            }
            return dispatcher;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private static Method findNoArg(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Method method = current.getDeclaredMethod(name); method.setAccessible(true); return method; }
            catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }

    private static int readDispatcherCount(Object dispatcher, Method accessor) {
        if (dispatcher == null || accessor == null) return 0;
        try {
            Object value = accessor.invoke(dispatcher);
            return value instanceof Number number ? Math.max(0, number.intValue()) : 0;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return 0; }
    }
    private static void reset(ClientLevel world){
        lastWorld=world;
        frames=framesWithBacklog=totalQueued=totalUploading=budgetFrames=uploaded=0;
        peakQueued=peakUploading=0;
        minFreeBuffers=Integer.MAX_VALUE;
        heapSamples=totalHeapUsedBytes=peakHeapUsedBytes=maxHeapBytes=0L;
        lastReportNanos=0L;
        lastReportedDeferred=BUDGET.deferredCalls();
        if(ENABLED)BUDGET.endFrame();
        frameBeginNanos=0L;
        if(ADAPTIVE)GOVERNOR.reset();
        if(PREDICTIVE)BUDGET.resetPrediction();
    }
}

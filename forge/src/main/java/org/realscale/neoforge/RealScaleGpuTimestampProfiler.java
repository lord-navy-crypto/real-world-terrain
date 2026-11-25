package org.realscale.neoforge;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;

/**
 * Optional physical-client GPU timestamp span profiler.
 *
 * Uses asynchronous GL_TIMESTAMP markers instead of GL_TIME_ELAPSED, avoiding
 * interference with another mod's active elapsed-time query. Results are read
 * only when available; the render thread is never blocked waiting for the GPU.
 * A timestamp span is not GPU utilization and can include idle gaps between
 * submitted commands. Unsupported drivers leave rendering unchanged.
 */
@EventBusSubscriber(modid="realscale",value=Dist.CLIENT)
public final class RealScaleGpuTimestampProfiler {
    private static final boolean ENABLED=
            RealScalePerformanceProfile.diagnosticsEnabled()
                    ||Boolean.getBoolean("realscale.client.gpu.profile");
    private static final int SLOTS=16;
    private static final int[] START=new int[SLOTS],END=new int[SLOTS];
    private static final boolean[] PENDING=new boolean[SLOTS];
    private static final int GL_TIMESTAMP=0x8E28;
    private static final int GL_QUERY_RESULT=0x8866;
    private static final int GL_QUERY_RESULT_AVAILABLE=0x8867;
    private static boolean initialized,supported,disabled,frameActive;
    private static int cursor,activeSlot;
    private static long skippedBusySlots,lastReportNanos;
    private static GpuTimestampStats window=new GpuTimestampStats();

    private RealScaleGpuTimestampProfiler(){}

    private static void initialize() {
        if(initialized)return;
        initialized=true;
        try {
            var caps=GL.getCapabilities();
            if(!caps.OpenGL33&&!caps.GL_ARB_timer_query) {
                disabled=true;
                System.err.println("[RealScale GPU timestamp] timer queries unsupported; disabled");
                return;
            }
            for(int i=0;i<SLOTS;i++) {
                START[i]=GL15C.glGenQueries();
                END[i]=GL15C.glGenQueries();
            }
            supported=true;
        } catch(RuntimeException ex) {
            disabled=true;
            System.err.println("[RealScale GPU timestamp] initialization failed: "
                    +ex.getClass().getSimpleName());
        }
    }

    @SubscribeEvent
    public static void onFramePre(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if(!ENABLED||disabled)return;
        initialize();
        if(!supported)return;
        try {
            int slot=cursor;
            if(PENDING[slot]) {
                if(GL15C.glGetQueryObjecti(END[slot],GL_QUERY_RESULT_AVAILABLE)==0) {
                    skippedBusySlots++;
                    cursor=(cursor+1)%SLOTS;
                    return;
                }
                long start=GL33C.glGetQueryObjecti64(START[slot],GL_QUERY_RESULT);
                long end=GL33C.glGetQueryObjecti64(END[slot],GL_QUERY_RESULT);
                window.add(end-start);
                PENDING[slot]=false;
                reportIfReady();
            }
            GL33C.glQueryCounter(START[slot],GL_TIMESTAMP);
            activeSlot=slot;
            frameActive=true;
            cursor=(cursor+1)%SLOTS;
        } catch(RuntimeException ex) {
            disable("sampling failed: "+ex.getClass().getSimpleName());
        }
    }

    @SubscribeEvent
    public static void onFramePost(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if(!ENABLED||disabled||!frameActive)return;
        try {
            GL33C.glQueryCounter(END[activeSlot],GL_TIMESTAMP);
            PENDING[activeSlot]=true;
            frameActive=false;
        } catch(RuntimeException ex) {
            frameActive=false;
            disable("completion failed: "+ex.getClass().getSimpleName());
        }
    }

    private static void reportIfReady() {
        long now=System.nanoTime();
        if(lastReportNanos==0L)lastReportNanos=now;
        if(now-lastReportNanos<30_000_000_000L||window.samples()<30)return;
        System.err.println("[RealScale GPU timestamp] samples="+window.samples()
                +" mean_ms="+fmt(window.meanMs())
                +" p50_ms="+fmt(window.percentileUpperMs(.50))
                +" p95_ms="+fmt(window.percentileUpperMs(.95))
                +" p99_ms="+fmt(window.percentileUpperMs(.99))
                +" max_ms="+fmt(window.maxMs())
                +" over_50ms="+window.over50ms()
                +" skipped_busy_slots="+skippedBusySlots
                +" method=GL_TIMESTAMP_span_not_GPU_utilization");
        window=new GpuTimestampStats();
        lastReportNanos=now;
        skippedBusySlots=0L;
    }

    private static String fmt(double value) {
        return Double.isNaN(value)?"256+":String.format(
                java.util.Locale.ROOT,"%.2f",value);
    }
    private static void disable(String message) {
        if(disabled)return;
        disabled=true;
        frameActive=false;
        System.err.println("[RealScale GPU timestamp] "+message
                +"; rendering remains unchanged");
    }

    public record Snapshot(boolean enabled,boolean supported,long samples,
            double meanMs,double p50Ms,double p95Ms,double p99Ms,
            double maxMs,long over50ms,long skippedBusySlots) {}
    public static Snapshot snapshot() {
        return new Snapshot(ENABLED&&!disabled,supported,window.samples(),
                window.meanMs(),window.percentileUpperMs(.50),
                window.percentileUpperMs(.95),window.percentileUpperMs(.99),
                window.maxMs(),window.over50ms(),skippedBusySlots);
    }
}

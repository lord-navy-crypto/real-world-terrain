package org.realscale.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Live, read-only performance telemetry plus one route to the shared settings page. */
public final class RealScalePerformanceStudioScreen extends Screen {
    private final Screen parent;

    public RealScalePerformanceStudioScreen(Screen parent) {
        super(Component.literal("RealScale Performance Studio"));
        this.parent=parent;
    }

    @Override
    protected void init() {
        int center=width/2;
        int y=height-30;
        addRenderableWidget(Button.builder(Component.literal("Performance Settings"),
                button -> Minecraft.getInstance().setScreen(
                        RealScaleTerrainLabClient.createScreen(this)))
                .bounds(center-154,y,148,20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"),
                button -> onClose())
                .bounds(center+6,y,100,20).build());
    }

    @Override
    public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        renderBackground(graphics);
        super.render(graphics,mouseX,mouseY,partialTick);
        graphics.drawCenteredString(font,title,width/2,12,0xffffffff);

        int left=Math.max(12,width/2-220);
        int right=Math.min(width-12,width/2+18);
        int y=34;
        var profile=RealScalePerformanceProfile.activeProfile();
        line(graphics,left,y,"Active profile",profile.name().toLowerCase(Locale.ROOT)); y+=16;
        long estimate=RealScalePerformanceProfile.estimatedCacheArrayBytesOneSet();
        line(graphics,left,y,"Estimated RealScale cache arrays / one cache set",
                String.format(Locale.ROOT,"%.2f MiB",estimate/1048576.0)); y+=17;
        line(graphics,left,y,"Minecraft-reported FPS",
                Integer.toString(Minecraft.getInstance().getFps())); y+=18;

        graphics.drawString(font,Component.literal("FRAME INTERVAL TELEMETRY"),
                left,y,0xff80c8ff); y+=15;
        RealScaleClientFrameProfiler.FrameSnapshot frame=
                RealScaleClientFrameProfiler.snapshot();
        line(graphics,left,y,"Profiler enabled",frame.enabled()?"yes":"no"); y+=15;
        if(frame.frames()>0) {
            line(graphics,left,y,"Samples / mean FPS",
                    frame.frames()+" / "+String.format(Locale.ROOT,"%.1f",frame.meanFps())); y+=15;
            line(graphics,left,y,"P50 / P95 / P99 interval",
                    ms(frame.p50IntervalMs())+" / "+ms(frame.p95IntervalMs())+" / "+ms(frame.p99IntervalMs())); y+=15;
            line(graphics,left,y,"Frames over 50 ms / maximum",
                    frame.slowFramesOver50ms()+" / "+ms(frame.maxIntervalMs())); y+=15;
        } else {
            line(graphics,left,y,"Samples","No active-world interval window yet"); y+=15;
        }

        y+=5;
        graphics.drawString(font,Component.literal("GPU TIMESTAMP SPAN"),left,y,0xff80c8ff); y+=15;
        RealScaleGpuTimestampProfiler.Snapshot gpu=
                RealScaleGpuTimestampProfiler.snapshot();
        line(graphics,left,y,"Timer queries",
                gpu.enabled()?(gpu.supported()?"available":"initializing"):"off / unsupported"); y+=15;
        line(graphics,left,y,"Samples / mean GPU span",
                gpu.samples()+" / "+ms(gpu.meanMs())); y+=15;
        line(graphics,left,y,"P50 / P95 / P99 GPU span",
                ms(gpu.p50Ms())+" / "+ms(gpu.p95Ms())+" / "+ms(gpu.p99Ms())); y+=15;

        y+=5;
        graphics.drawString(font,Component.literal("CHUNK QUEUE & MEMORY"),
                left,y,0xff80c8ff); y+=15;
        RealScaleChunkUploadClient.QueueSnapshot queue=
                RealScaleChunkUploadClient.snapshot();
        line(graphics,left,y,"Live queue available",queue.available()?"yes":"no active world"); y+=15;
        if(queue.available()) {
            line(graphics,left,y,"Compile / upload pending",
                    queue.compilePending()+" / "+queue.uploadPending()); y+=15;
            line(graphics,left,y,"Free mesh buffers",Integer.toString(queue.freeMeshBuffers())); y+=15;
            line(graphics,left,y,"Average compile / upload pending",
                    String.format(Locale.ROOT,"%.1f / %.1f",
                            queue.averageCompilePending(),queue.averageUploadPending())); y+=15;
            line(graphics,left,y,"Peak compile / upload pending",
                    queue.peakCompilePending()+" / "+queue.peakUploadPending()); y+=15;
        }
        double heapPercent=queue.heapMaxBytes()<=0?0.0:
                Math.min(1.0,queue.heapUsedBytes()/(double)queue.heapMaxBytes());
        line(graphics,left,y,"JVM heap used / max",
                mib(queue.heapUsedBytes())+" / "+mib(queue.heapMaxBytes())); y+=13;
        int barWidth=Math.min(320,width-2*left);
        graphics.fill(left,y,left+barWidth,y+8,0xff303840);
        graphics.fill(left,y,left+(int)(barWidth*heapPercent),y+8,0xff4f9bca);
        y+=17;
        line(graphics,left,y,"Upload budget / adaptive / predictive",
                queue.budgetEnabled()+" / "+queue.adaptive()+" / "+queue.predictive()); y+=15;
        line(graphics,left,y,"Deferred calls / tasks this frame",
                queue.deferredCalls()+" / "+queue.uploadedTasks()); y+=15;

        int noteY=Math.min(height-55,y+5);
        graphics.drawCenteredString(font,
                Component.literal("Frame intervals include VSync; GPU timestamp spans are not GPU utilization and may include idle gaps."),
                width/2,noteY,0xffb5c0c8);
        graphics.drawCenteredString(font,
                Component.literal("Heap is JVM heap, not VRAM; timestamp span is not GPU busy time. Upload pacing may delay terrain appearance."),
                width/2,noteY+13,0xff9eabb5);
        if(!frame.enabled())
            graphics.drawCenteredString(font,
                    Component.literal("Enable Live Performance Diagnostics in Performance Settings, then restart."),
                    width/2,noteY+26,0xffd7bb83);
    }

    private void line(GuiGraphics graphics,int x,int y,String label,String value) {
        graphics.drawString(font,Component.literal(label),x,y,0xffb7c9d6);
        int valueX=Math.min(width-12,x+250);
        graphics.drawString(font,Component.literal(value),valueX,y,0xffffffff);
    }

    private static String ms(double value) {
        return Double.isNaN(value)?"256+ ms":
                String.format(Locale.ROOT,"%.1f ms",value);
    }

    private static String mib(long bytes) {
        return String.format(Locale.ROOT,"%.0f MiB",bytes/1048576.0);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}

package org.realscale.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Read-only persisted-chunk atlas. It NEVER opens the world/server or creates
 * a chunk, and never estimates "generated" from a noisy height preview.
 *
 * Chunky integration in v0.1 is guidance/clipboard only: Chunky owns all
 * generation, resume and task status when the player enters the actual world.
 */
public final class WorldAtlasMapScreen extends Screen {
    private static final int[] RADII={512,1024,2048,4096};
    private final Screen parent;
    private final Path save;
    private int radiusIndex=1;
    private int centerX,centerZ;
    private WorldAtlasRegionIndex.Snapshot current;
    private CompletableFuture<WorldAtlasRegionIndex.Snapshot> pending;
    private String message="";
    private boolean chunkyLoaded;
    private Button pauseDrawButton;

    public WorldAtlasMapScreen(Screen parent,Path save) {
        super(Component.translatable("realscale.atlas.title"));
        this.parent=parent;
        this.save=save;
    }

    private void requestScan() {
        current=null;
        message="";
        final int x=centerX,z=centerZ,radius=RADII[radiusIndex];
        // Async, bounded, headers-only I/O; never block game render thread
        // with a long scan, and never force a server to start while offline.
        pending=CompletableFuture.supplyAsync(() -> {
            try { return WorldAtlasRegionIndex.scan(save,x,z,radius); }
            catch (java.io.IOException ex) {
                throw new CompletionException(ex);
            }
        });
    }

    @Override protected void init() {
        chunkyLoaded=ModList.get().isLoaded("chunky");
        int mid=width/2;
        int drawingY=height-106;
        addRenderableWidget(Button.builder(
                Component.translatable("realscale.atlas.draw_start"),
                b -> startDrawing())
                .bounds(mid-126,drawingY,92,20).build());
        pauseDrawButton=Button.builder(
                Component.translatable("realscale.atlas.draw_pause"),
                b -> togglePause())
                .bounds(mid-29,drawingY,80,20).build();
        addRenderableWidget(pauseDrawButton);
        addRenderableWidget(Button.builder(
                Component.translatable("realscale.atlas.draw_cancel"),
                b -> {if(!WorldAtlasBackgroundPainter.cancel())
                    message=Component.translatable("realscale.atlas.draw_idle").getString();})
                .bounds(mid+56,drawingY,72,20).build());
        int y=height-79;
        addRenderableWidget(Button.builder(Component.literal("X -"),
                b -> {centerX-=512;requestScan();})
                .bounds(mid-112,y,51,20).build());
        addRenderableWidget(Button.builder(Component.literal("X +"),
                b -> {centerX+=512;requestScan();})
                .bounds(mid-57,y,51,20).build());
        addRenderableWidget(Button.builder(Component.literal("Z -"),
                b -> {centerZ-=512;requestScan();})
                .bounds(mid+6,y,51,20).build());
        addRenderableWidget(Button.builder(Component.literal("Z +"),
                b -> {centerZ+=512;requestScan();})
                .bounds(mid+61,y,51,20).build());
        y=height-54;
        addRenderableWidget(Button.builder(Component.literal("Radius -"),
                b -> {radiusIndex=Math.max(0,radiusIndex-1);requestScan();})
                .bounds(mid-134,y,85,20).build());
        addRenderableWidget(Button.builder(Component.literal("Radius +"),
                b -> {radiusIndex=Math.min(RADII.length-1,radiusIndex+1);
                      requestScan();})
                .bounds(mid-43,y,85,20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("realscale.atlas.copy_chunky"),
                b -> copyChunkyGuide()).bounds(mid+48,y,120,20).build());
        y=height-28;
        addRenderableWidget(Button.builder(
                Component.translatable("realscale.atlas.back"),
                b -> onClose()).bounds(8,y,91,20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("realscale.atlas.refresh"),
                b -> requestScan()).bounds(width-102,y,94,20).build());
        if(pending==null)requestScan();
    }

    private void copyChunkyGuide() {
        // Each of these is one **separate in-world chat command**.
        // No offline world mutation, hidden server process or fake progress.
        String commands="/chunky center "+centerX+" "+centerZ
                +"\n/chunky radius "+RADII[radiusIndex]
                +"\n/chunky shape square"
                +"\n/chunky start";
        Minecraft.getInstance().keyboardHandler.setClipboard(commands);
        message=Component.translatable("realscale.atlas.copied").getString();
    }

    private void startDrawing() {
        try {
            boolean started=WorldAtlasBackgroundPainter.start(save,
                    centerX,centerZ,RADII[radiusIndex]);
            message=Component.translatable(started
                    ?"realscale.atlas.draw_started"
                    :"realscale.atlas.draw_busy").getString();
        } catch(IllegalArgumentException ex) {
            // Repeated panning near the world border must not crash the GUI.
            message=ex.getMessage()==null?"Map center is out of bounds":
                    ex.getMessage();
        }
    }

    private void togglePause() {
        var job=WorldAtlasBackgroundPainter.current();
        if(job!=null && "PAUSED".equals(job.state()))
            WorldAtlasBackgroundPainter.resume();
        else WorldAtlasBackgroundPainter.pause();
    }

    private WorldAtlasBackgroundPainter.View visiblePainting() {
        var job=WorldAtlasBackgroundPainter.current();
        return job!=null && job.matches(save,centerX,centerZ,
                RADII[radiusIndex])?job:null;
    }

    @Override public void tick() {
        super.tick();
        if(pauseDrawButton!=null) {
            var job=WorldAtlasBackgroundPainter.current();
            boolean paused=job!=null && "PAUSED".equals(job.state());
            pauseDrawButton.setMessage(Component.translatable(paused
                    ?"realscale.atlas.draw_resume"
                    :"realscale.atlas.draw_pause"));
        }
        if(pending==null || !pending.isDone())return;
        try {
            current=pending.join();
        } catch(CompletionException ex) {
            Throwable root=ex.getCause()==null?ex:ex.getCause();
            message=root.getMessage()==null?"Region scan failed":root.getMessage();
            current=null;
        } catch(RuntimeException ex) {
            message=ex.getMessage()==null?"Region scan failed":ex.getMessage();
            current=null;
        } finally {
            pending=null;
        }
    }

    @Override public void render(GuiGraphics gfx,int mouseX,int mouseY,float partialTick) {
        super.render(gfx,mouseX,mouseY,partialTick);
        gfx.drawCenteredString(font,title,width/2,10,0xffffffff);
        String world=save.getFileName().toString();
        gfx.drawCenteredString(font,Component.literal(
                font.plainSubstrByWidth(world,Math.max(130,width-28))),
                width/2,24,0xffdddddd);
        gfx.drawCenteredString(font,Component.literal(
                "XZ: "+centerX+","+centerZ+" | +/-"+RADII[radiusIndex]+" blocks"),
                width/2,36,0xffaaaacc);

        int grid=WorldAtlasRegionIndex.MAP_CELLS;
        int step=Math.max(1,Math.min(5,
                Math.min((width-26)/grid,(height-172)/grid)));
        int size=grid*step,left=(width-size)/2,top=49;
        gfx.fill(left-2,top-2,left+size+2,top+size+2,0xff19252a);
        var drawing=visiblePainting();
        if(current!=null || drawing!=null) {
            for(int z=0;z<grid;z++)for(int x=0;x<grid;x++) {
                int coverage=current==null?0:current.cellCoverage(x,z);
                int color=(coverage==0)?0xff47545c:
                        (coverage==255?0xff67ad87:0xffb5a46a);
                if(drawing!=null) {
                    int sampled=drawing.color(x,z);
                    if(sampled!=0)color=sampled; // Real persisted heightmaps only
                }
                int px=left+x*step,pz=top+z*step;
                gfx.fill(px,pz,px+step,pz+step,color);
            }
        }else{
            gfx.drawCenteredString(font,
                    Component.translatable("realscale.atlas.scanning"),
                    width/2,top+size/2,0xffffffff);
        }
        // One bounded status row stays ABOVE the first footer button at
        // height-79. Do not overpaint navigation on 240px GUI-scaled windows.
        int statusY=Math.min(top+size+5,height-120);
        if(drawing!=null) {
            String progress=drawing.state()+" | "
                    +drawing.finished()+"/"+WorldAtlasBackgroundPainter.PIXELS
                    +" cells | "+drawing.available()+" height samples"
                    +" | "+String.format(java.util.Locale.ROOT,
                        "%.1f",drawing.percent())+"%"
                    +(drawing.errors()>0?" | "+drawing.errors()+" unreadable":"");
            gfx.drawCenteredString(font,Component.literal(
                    font.plainSubstrByWidth(progress,Math.max(130,width-16))),
                    width/2,statusY,0xffc5e6d0);
        } else if(!message.isEmpty()) {
            gfx.drawCenteredString(font,Component.literal(
                    font.plainSubstrByWidth(message,Math.max(130,width-16))),
                    width/2,statusY,0xffffff99);
        } else if(current!=null) {
            String stats=current.savedChunks()+"/"+current.targetChunks()
                    +" saved ("+String.format(java.util.Locale.ROOT,
                        "%.1f",current.percentSaved())+"%)"
                    +(chunkyLoaded?" | Chunky available":" | Chunky optional");
            gfx.drawCenteredString(font,Component.literal(
                    font.plainSubstrByWidth(stats,Math.max(130,width-16))),
                    width/2,statusY,0xffc5e6d0);
        }
        // Clip the legend into the LEFT margin of the map; no overlap
        // with the coverage pixels or bottom navigation at GUI scale 2/3.
        String legend=Component.translatable(drawing!=null
                ?"realscale.atlas.draw_real_legend"
                :"realscale.atlas.coverage_only").getString();
        gfx.drawString(font,Component.literal(
                font.plainSubstrByWidth(legend,Math.max(35,left-16))),
                8,49,0xffbcbcbc);
    }

    @Override public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}

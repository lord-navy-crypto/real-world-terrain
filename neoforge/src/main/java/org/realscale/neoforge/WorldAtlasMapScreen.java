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

    @Override public void tick() {
        super.tick();
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
        int step=Math.max(2,Math.min(5,
                Math.min((width-26)/grid,(height-140)/grid)));
        int size=grid*step,left=(width-size)/2,top=49;
        gfx.fill(left-2,top-2,left+size+2,top+size+2,0xff19252a);
        if(current!=null) {
            for(int z=0;z<grid;z++)for(int x=0;x<grid;x++) {
                int coverage=current.cellCoverage(x,z);
                int color=(coverage==0)?0xff47545c:
                        (coverage==255?0xff67ad87:0xffb5a46a);
                int px=left+x*step,pz=top+z*step;
                gfx.fill(px,pz,px+step,pz+step,color);
            }
        }else{
            gfx.drawCenteredString(font,
                    Component.translatable("realscale.atlas.scanning"),
                    width/2,top+size/2,0xffffffff);
        }
        int below=top+size+6;
        if(current!=null) {
            String stats=current.savedChunks()+" / "+current.targetChunks()
                    +" saved slots ("+String.format(java.util.Locale.ROOT,
                        "%.1f",current.percentSaved())+"%)";
            gfx.drawCenteredString(font,Component.literal(stats),
                    width/2,below,0xffc5e6d0);
        }
        gfx.drawCenteredString(font,
                Component.translatable("realscale.atlas.coverage_only"),
                width/2,below+11,0xffbcbcbc);
        if(!message.isEmpty())
            gfx.drawCenteredString(font,Component.literal(
                    font.plainSubstrByWidth(message,Math.max(160,width-20))),
                    width/2,height-100,0xffffff99);
        gfx.drawCenteredString(font,
                Component.translatable(chunkyLoaded?
                    "realscale.atlas.chunky_found":"realscale.atlas.chunky_missing"),
                width/2,height-92,0xffaaaacc);
    }

    @Override public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}

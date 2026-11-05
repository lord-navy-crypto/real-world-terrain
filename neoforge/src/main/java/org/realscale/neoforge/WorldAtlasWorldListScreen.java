package org.realscale.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Offline chooser: read-only access to the local saves directory. */
public final class WorldAtlasWorldListScreen extends Screen {
    private final Screen parent;
    private List<Path> worlds=List.of();
    private String error;
    private int page;

    public WorldAtlasWorldListScreen(Screen parent) {
        super(Component.translatable("realscale.atlas.title"));
        this.parent=parent;
    }

    @Override protected void init() {
        try {
            worlds=WorldAtlasRegionIndex.findLocalWorlds(
                    Minecraft.getInstance().gameDirectory.toPath().resolve("saves"));
            error=null;
        } catch(IOException ex) {
            worlds=List.of();
            error=ex.getMessage();
        }
        int perPage=Math.max(1,Math.min(8,(height-114)/24));
        page=Math.max(0,Math.min(page,Math.max(0,(worlds.size()-1)/perPage)));
        int start=page*perPage;
        int widthButton=Math.max(130,Math.min(290,width-28));
        for(int i=0;i<perPage && start+i<worlds.size();i++) {
            Path world=worlds.get(start+i);
            String folder=world.getFileName().toString();
            addRenderableWidget(Button.builder(
                    Component.literal(font.plainSubstrByWidth(folder,widthButton-18)),
                    b -> Minecraft.getInstance().setScreen(
                            new WorldAtlasMapScreen(this,world)))
                    .bounds((width-widthButton)/2,41+i*24,widthButton,20).build());
        }
        int bottom=height-29;
        addRenderableWidget(Button.builder(
                Component.translatable("realscale.atlas.back"),
                b -> onClose()).bounds(9,bottom,90,20).build());
        Button previous=Button.builder(Component.literal("<"),
                b -> {page--;rebuildWidgets();})
                .bounds(width/2-48,bottom,42,20).build();
        previous.active=page>0;
        addRenderableWidget(previous);
        Button next=Button.builder(Component.literal(">"),
                b -> {page++;rebuildWidgets();})
                .bounds(width/2+6,bottom,42,20).build();
        next.active=start+perPage<worlds.size();
        addRenderableWidget(next);
    }

    @Override public void render(GuiGraphics gfx,int mouseX,int mouseY,float partialTick) {
        super.render(gfx,mouseX,mouseY,partialTick);
        gfx.drawCenteredString(font,title,width/2,17,0xffffffff);
        if(worlds.isEmpty()) {
            Component msg=error==null
                    ?Component.translatable("realscale.atlas.empty")
                    :Component.literal(error);
            gfx.drawCenteredString(font,msg,width/2,height/2,0xffff9999);
        }
        if(!worlds.isEmpty())
            gfx.drawString(font,Component.literal(worlds.size()+" saves"),
                    10,17,0xffaaaaaa);
    }

    @Override public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}

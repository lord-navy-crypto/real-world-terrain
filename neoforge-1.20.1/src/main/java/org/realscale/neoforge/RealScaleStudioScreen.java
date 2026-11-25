package org.realscale.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Unified navigation surface; specialized tools remain pages under one entry point. */
public final class RealScaleStudioScreen extends Screen {
    private final Screen parent;

    public RealScaleStudioScreen(Screen parent) {
        super(Component.literal("RealScale Studio"));
        this.parent=parent;
    }

    @Override
    protected void init() {
        int center=width/2;
        int buttonWidth=240;
        int x=center-buttonWidth/2;
        int y=Math.max(48,height/2-62);
        addRenderableWidget(Button.builder(
                Component.literal("Terrain Lab — Natural Automate"),
                button -> Minecraft.getInstance().setScreen(
                        RealScaleTerrainLabClient.createScreen(this)))
                .bounds(x,y,buttonWidth,22).build());
        addRenderableWidget(Button.builder(
                Component.literal("Performance Studio"),
                button -> Minecraft.getInstance().setScreen(
                        new RealScalePerformanceStudioScreen(this)))
                .bounds(x,y+30,buttonWidth,22).build());
        addRenderableWidget(Button.builder(
                Component.literal("Geographic Climate Inspector"),
                button -> Minecraft.getInstance().setScreen(
                        new GeographicClimateInspectorScreen(this)))
                .bounds(x,y+60,buttonWidth,22).build());
        addRenderableWidget(Button.builder(Component.literal("Back"),
                button -> onClose())
                .bounds(center-60,y+102,120,20).build());
    }

    @Override
    public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        renderBackground(graphics);
        super.render(graphics,mouseX,mouseY,partialTick);
        graphics.drawCenteredString(font,title,width/2,20,0xffffffff);
        graphics.drawCenteredString(font,
                Component.literal("One workspace for world profiles, climate and performance."),
                width/2,36,0xffb9c8d4);
        graphics.drawCenteredString(font,
                Component.literal("Natural Automate is the recommended experimental terrain mode."),
                width/2,Math.min(height-18,height/2+80),0xff9eabb5);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}

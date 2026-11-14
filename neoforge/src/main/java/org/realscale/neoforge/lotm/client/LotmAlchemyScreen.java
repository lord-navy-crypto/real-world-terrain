package org.realscale.neoforge.lotm.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.realscale.neoforge.lotm.LotmAlchemyMenu;
public final class LotmAlchemyScreen extends AbstractContainerScreen<LotmAlchemyMenu> {
    public LotmAlchemyScreen(LotmAlchemyMenu menu, Inventory inv, Component title) {
        super(menu,inv,title); imageWidth=176; imageHeight=198;
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY) {
        int x=leftPos,y=topPos;
        g.fill(x,y,x+imageWidth,y+imageHeight,0xFF171421);
        g.fill(x+3,y+3,x+imageWidth-3,y+99,0xFF292238);
        g.fill(x+7,y+7,x+imageWidth-7,y+95,0xFF211B30);
        g.fill(x+88,y+78,x+137,y+86,0xFF100E17);
        int max=menu.getMaxProgress();
        if(max>0) { int w=Math.max(0,Math.min(47,47*menu.getProgress()/max)); g.fill(x+89,y+79,x+89+w,y+85,0xFF9C76D0); }
        g.fill(x+7,y+101,x+imageWidth-7,y+imageHeight-5,0xFF211B30);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my) {
        g.drawString(font,title,8,6,0xFFE9DDF6,false);
        g.drawString(font,Component.translatable("gui.realscale.lotm_alchemy.grid"),8,23,0xFFCBBBDD,false);
        g.drawString(font,Component.translatable("gui.realscale.lotm_alchemy.fuel"),88,23,0xFFCBBBDD,false);
        g.drawString(font,Component.translatable("gui.realscale.lotm_alchemy.output"),139,23,0xFFCBBBDD,false);
        if(menu.getMaxProgress()>0) g.drawString(font,Component.translatable("gui.realscale.lotm_alchemy.processing"),88,66,0xFFD8C8EA,false);
        Component status = Component.translatable("gui.realscale.lotm_alchemy.status." + menu.getMachineStatus());
        g.drawString(font, font.plainSubstrByWidth(status.getString(), 82), 8, 84, 0xFFD8C8EA, false);
        g.drawString(font,Component.translatable("container.inventory"),8,101,0xFFE9DDF6,false);
    }
}

package org.realscale.neoforge.lotm.client;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.realscale.neoforge.lotm.LotmAlchemyContent;
@EventBusSubscriber(modid="realscale",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class LotmClientEvents {
    private LotmClientEvents() {}
    @SubscribeEvent public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(LotmAlchemyContent.ALCHEMY_MENU.get(),LotmAlchemyScreen::new);
    }

    @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(LotmAlchemyContent.ALCHEMY_BLOCK_ENTITY.get(), LotmAlchemyRenderer::new);
    }
}

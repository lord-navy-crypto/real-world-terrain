package org.realscale.neoforge.lotm.client;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.realscale.neoforge.lotm.LotmAlchemyContent;
import org.realscale.neoforge.lotm.LotmCreatureEntities;
import org.realscale.neoforge.lotm.LotmCreatureKind;
import org.realscale.neoforge.lotm.LotmStatusMenu;
import org.realscale.neoforge.lotm.client.LotmStatusScreen;
@EventBusSubscriber(modid="realscale",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class LotmClientEvents {
    private LotmClientEvents() {}
    @SubscribeEvent public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(LotmAlchemyContent.ALCHEMY_MENU.get(),LotmAlchemyScreen::new);
        event.register(LotmAlchemyContent.STATUS_MENU.get(), LotmStatusScreen::new);
    }

    @SubscribeEvent public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(LotmClientKeys.OPEN_PANEL);
    }

    @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(LotmAlchemyContent.ALCHEMY_BLOCK_ENTITY.get(), LotmAlchemyRenderer::new);
        for (LotmCreatureKind kind : LotmCreatureKind.values()) {
            event.registerEntityRenderer(LotmCreatureEntities.type(kind).get(), LotmCreatureRenderer::new);
        }
    }
}

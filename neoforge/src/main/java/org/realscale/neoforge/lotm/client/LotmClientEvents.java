package org.realscale.neoforge.lotm.client;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.realscale.neoforge.lotm.LotmAlchemyContent;
@Mod.EventBusSubscriber(modid="realscale",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class LotmClientEvents {
    private LotmClientEvents() {}
    @SubscribeEvent public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(LotmAlchemyContent.ALCHEMY_MENU.get(),LotmAlchemyScreen::new);
    }
}

package org.realscale.neoforge.lotm.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.realscale.neoforge.lotm.LotmSkillPayload;

@EventBusSubscriber(modid = "realscale", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class LotmClientGameEvents {
    private LotmClientGameEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) return;
        while (LotmKeyMappings.PRIMARY.consumeClick()) {
            PacketDistributor.sendToServer(new LotmSkillPayload(1));
        }
        while (LotmKeyMappings.SECONDARY.consumeClick()) {
            PacketDistributor.sendToServer(new LotmSkillPayload(2));
        }
    }
}

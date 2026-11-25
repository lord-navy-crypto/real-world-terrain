package org.realscale.neoforge;

import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;

/**
 * Reuse the vanilla oak-boat renderer for the first playable prototype.
 * Avoids a custom geometry/texture requirement during physics validation.
 */
@EventBusSubscriber(modid = "realscale", bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class RealScaleHovercraftClient {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RealScaleVehicles.HOVERCRAFT.get(),
                ctx -> new BoatRenderer(ctx, false));
    }

    private RealScaleHovercraftClient() {}
}

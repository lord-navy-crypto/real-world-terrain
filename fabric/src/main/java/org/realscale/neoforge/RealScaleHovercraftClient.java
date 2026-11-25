package org.realscale.neoforge;

import net.minecraft.client.renderer.entity.BoatRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * Reuse the vanilla oak-boat renderer for the first playable prototype.
 * Avoids a custom geometry/texture requirement during physics validation.
 */
public final class RealScaleHovercraftClient {
    public static void registerRenderers() {
        EntityRendererRegistry.register(RealScaleVehicles.HOVERCRAFT.get(),
                ctx -> new BoatRenderer(ctx, false));
    }

    private RealScaleHovercraftClient() {}
}

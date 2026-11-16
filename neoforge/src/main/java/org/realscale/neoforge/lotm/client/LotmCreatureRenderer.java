package org.realscale.neoforge.lotm.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.realscale.neoforge.lotm.LotmCreature;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class LotmCreatureRenderer extends GeoEntityRenderer<LotmCreature> {
    public LotmCreatureRenderer(EntityRendererProvider.Context context) {
        super(context, new LotmCreatureModel());
        this.shadowRadius = 0.55F;
    }
}

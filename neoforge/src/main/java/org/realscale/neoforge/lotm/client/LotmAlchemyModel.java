package org.realscale.neoforge.lotm.client;

import net.minecraft.resources.ResourceLocation;
import org.realscale.neoforge.lotm.LotmAlchemyBlockEntity;
import software.bernie.geckolib.model.GeoModel;

/** GeckoLib model contract for the alchemy apparatus. */
public final class LotmAlchemyModel extends GeoModel<LotmAlchemyBlockEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("realscale", "geo/lotm_alchemy_apparatus.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("realscale", "textures/block/lotm_alchemy_apparatus.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath("realscale", "animations/lotm_alchemy_apparatus.animation.json");

    @Override public ResourceLocation getModelResource(LotmAlchemyBlockEntity animatable) { return MODEL; }
    @Override public ResourceLocation getTextureResource(LotmAlchemyBlockEntity animatable) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(LotmAlchemyBlockEntity animatable) { return ANIMATION; }
}

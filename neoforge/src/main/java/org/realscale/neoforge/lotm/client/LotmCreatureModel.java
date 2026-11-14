package org.realscale.neoforge.lotm.client;

import net.minecraft.resources.ResourceLocation;
import org.realscale.neoforge.lotm.LotmCreature;
import software.bernie.geckolib.model.GeoModel;

public final class LotmCreatureModel extends GeoModel<LotmCreature> {
    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath("realscale", path);
    }

    @Override
    public ResourceLocation getModelResource(LotmCreature creature) {
        return resource("geo/lotm_" + creature.kind().id() + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(LotmCreature creature) {
        return resource("textures/entity/lotm_" + creature.kind().id() + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(LotmCreature creature) {
        return resource("animations/lotm_" + creature.kind().id() + ".animation.json");
    }
}

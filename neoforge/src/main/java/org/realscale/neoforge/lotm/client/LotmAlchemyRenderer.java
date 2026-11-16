package org.realscale.neoforge.lotm.client;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.realscale.neoforge.lotm.LotmAlchemyBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public final class LotmAlchemyRenderer extends GeoBlockRenderer<LotmAlchemyBlockEntity> {
    public LotmAlchemyRenderer(BlockEntityRendererProvider.Context context) { super(new LotmAlchemyModel()); }
}

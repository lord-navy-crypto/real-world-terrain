package org.realscale.neoforge.lotm;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Codec bridge for data-driven LOTM alchemy recipes. */
public final class LotmAlchemyRecipeSerializer implements RecipeSerializer<LotmAlchemyRecipe> {
    @Override
    public MapCodec<LotmAlchemyRecipe> codec() {
        return LotmAlchemyRecipe.CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, LotmAlchemyRecipe> streamCodec() {
        return LotmAlchemyRecipe.STREAM_CODEC;
    }
}

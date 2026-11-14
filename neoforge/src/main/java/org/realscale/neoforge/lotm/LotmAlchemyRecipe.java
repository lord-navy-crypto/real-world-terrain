package org.realscale.neoforge.lotm;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import java.util.List;

/** Shapeless 2x2 recipe used only by the LOTM alchemy apparatus. */
public final class LotmAlchemyRecipe implements Recipe<CraftingInput> {
    public static final MapCodec<LotmAlchemyRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Ingredient.CODEC.listOf().fieldOf("ingredients").forGetter(recipe -> recipe.ingredients),
                    ItemStack.CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
            ).apply(instance, LotmAlchemyRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LotmAlchemyRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()),
                    recipe -> recipe.ingredients,
                    ItemStack.STREAM_CODEC,
                    recipe -> recipe.result,
                    LotmAlchemyRecipe::new
            );

    private final List<Ingredient> ingredients;
    private final ItemStack result;

    public LotmAlchemyRecipe(List<Ingredient> ingredients, ItemStack result) {
        if (ingredients.isEmpty() || ingredients.size() > 4) {
            throw new IllegalArgumentException("LOTM alchemy recipes require one to four ingredients");
        }
        this.ingredients = List.copyOf(ingredients);
        this.result = result.copy();
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.size() != 4) return false;
        java.util.List<ItemStack> occupied = new java.util.ArrayList<>(4);
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (!stack.isEmpty()) occupied.add(stack);
        }
        if (occupied.size() != ingredients.size()) return false;

        // A broad tag can overlap a specific item. Greedy slot assignment would
        // reject an otherwise valid shapeless recipe depending on slot order.
        boolean[][] compatible = new boolean[occupied.size()][ingredients.size()];
        for (int slot = 0; slot < occupied.size(); slot++) {
            for (int index = 0; index < ingredients.size(); index++) {
                compatible[slot][index] = ingredients.get(index).test(occupied.get(slot));
            }
        }
        return LotmIngredientMatching.canMatch(compatible);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= ingredients.size();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.addAll(ingredients);
        return list;
    }

    @Override
    public RecipeType<?> getType() {
        return LotmAlchemyContent.ALCHEMY_RECIPE_TYPE.get();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return LotmAlchemyContent.ALCHEMY_RECIPE_SERIALIZER.get();
    }
}

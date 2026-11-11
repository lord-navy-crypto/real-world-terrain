package org.realscale.neoforge.lotm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import java.util.List;
import java.util.Optional;

/** 2x2 recipe grid + spirit-ember fuel + output. Processing is server-authoritative. */
public final class LotmAlchemyBlockEntity extends BlockEntity implements MenuProvider {
    private static final int GRID_SIZE = 4, FUEL_SLOT = 4, OUTPUT_SLOT = 5;
    private int progress, maxProgress;
    private ResourceLocation activeRecipe;
    private final ItemStackHandler inventory = new ItemStackHandler(6) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == FUEL_SLOT) return stack.is(LotmContent.SPIRIT_EMBER.get());
            return slot >= 0 && slot < GRID_SIZE;
        }
        @Override protected void onContentsChanged(int slot) {
            if (slot < FUEL_SLOT) { progress = 0; maxProgress = 0; activeRecipe = null; }
            setChanged();
        }
    };
    private final ContainerData data = new ContainerData() {
        @Override public int get(int i) { return i == 0 ? progress : maxProgress; }
        @Override public void set(int i, int v) { if (i == 0) progress = v; else if (i == 1) maxProgress = v; }
        @Override public int getCount() { return 2; }
    };
    public LotmAlchemyBlockEntity(BlockPos pos, BlockState state) { super(LotmAlchemyContent.ALCHEMY_BLOCK_ENTITY.get(), pos, state); }
    public ItemStackHandler getItemHandler() { return inventory; }
    @Override public Component getDisplayName() { return Component.translatable("container.realscale.lotm_alchemy_apparatus"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        ContainerLevelAccess access = level == null ? ContainerLevelAccess.NULL : ContainerLevelAccess.create(level, worldPosition);
        return new LotmAlchemyMenu(id, inv, inventory, access, data);
    }
    public static void tick(Level level, BlockPos pos, BlockState state, LotmAlchemyBlockEntity machine) {
        if (!(level instanceof ServerLevel server)) return;
        CraftingInput input = CraftingInput.of(2, 2, List.of(
            machine.inventory.getStackInSlot(0), machine.inventory.getStackInSlot(1),
            machine.inventory.getStackInSlot(2), machine.inventory.getStackInSlot(3)));
        Optional<RecipeHolder<CraftingRecipe>> match = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, server);
        if (match.isEmpty()) { machine.resetProgress(); return; }
        RecipeHolder<CraftingRecipe> holder = match.get();
        ItemStack result = holder.value().assemble(input, server.registryAccess());
        if (result.isEmpty() || !(result.getItem() instanceof LotmPotionItem potion)) { machine.resetProgress(); return; }
        if (machine.inventory.getStackInSlot(FUEL_SLOT).isEmpty() || !machine.canAcceptOutput(result)) return;
        if (!holder.id().equals(machine.activeRecipe)) {
            machine.activeRecipe = holder.id();
            machine.progress = 0;
            machine.maxProgress = LotmBalance.alchemyTicks(potion.sequence(),
                LotmConfig.ALCHEMY_SEQUENCE9_TICKS.get(), LotmConfig.ALCHEMY_SEQUENCE8_TICKS.get(),
                LotmConfig.ALCHEMY_SEQUENCE7_TICKS.get());
        }
        machine.progress++;
        if (machine.progress < machine.maxProgress) return;
        for (int i = 0; i < GRID_SIZE; i++) if (!machine.inventory.getStackInSlot(i).isEmpty()) machine.inventory.extractItem(i, 1, false);
        machine.inventory.extractItem(FUEL_SLOT, 1, false);
        ItemStack output = machine.inventory.getStackInSlot(OUTPUT_SLOT);
        if (output.isEmpty()) machine.inventory.setStackInSlot(OUTPUT_SLOT, result.copy());
        else { output.grow(result.getCount()); machine.inventory.setStackInSlot(OUTPUT_SLOT, output); }
        machine.progress = 0; machine.maxProgress = 0; machine.activeRecipe = null; machine.setChanged();
    }
    private boolean canAcceptOutput(ItemStack result) {
        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        return output.isEmpty() || (ItemStack.isSameItemSameComponents(output, result)
            && output.getCount() + result.getCount() <= Math.min(output.getMaxStackSize(), result.getMaxStackSize()));
    }
    private void resetProgress() {
        if (progress == 0 && maxProgress == 0 && activeRecipe == null) return;
        progress = 0; maxProgress = 0; activeRecipe = null; setChanged();
    }
    @Override public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putInt("Progress", progress); tag.putInt("MaxProgress", maxProgress);
        if (activeRecipe != null) tag.putString("ActiveRecipe", activeRecipe.toString());
    }
    @Override public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        progress = Math.max(0, tag.getInt("Progress")); maxProgress = Math.max(0, tag.getInt("MaxProgress"));
        activeRecipe = tag.contains("ActiveRecipe") ? ResourceLocation.tryParse(tag.getString("ActiveRecipe")) : null;
    }
}

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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import java.util.List;
import java.util.Optional;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** 2x2 recipe grid + Spirit Ember fuel + output. Server-authoritative and save-safe. */
public final class LotmAlchemyBlockEntity extends BlockEntity implements MenuProvider, GeoBlockEntity {
    private static final int GRID_SIZE = 4, FUEL_SLOT = 4, OUTPUT_SLOT = 5;
    private static final RawAnimation IDLE_ANIMATION =
            RawAnimation.begin().thenLoop("animation.realscale.lotm_alchemy_apparatus.idle");
    private static final RawAnimation BREWING_ANIMATION =
            RawAnimation.begin().thenLoop("animation.realscale.lotm_alchemy_apparatus.brew");
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int progress, maxProgress;
    private ResourceLocation activeRecipe;

    /** Output can only be written internally; automation and players cannot insert into it. */
    private final class MachineInventory extends ItemStackHandler {
        private boolean internalOutputWrite;
        private MachineInventory() { super(6); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == FUEL_SLOT) return stack.is(LotmContent.SPIRIT_EMBER.get());
            if (slot == OUTPUT_SLOT) return internalOutputWrite && stack.getItem() instanceof LotmPotionItem;
            return slot >= 0 && slot < GRID_SIZE;
        }
        void setOutput(ItemStack stack) {
            internalOutputWrite = true;
            try { super.setStackInSlot(OUTPUT_SLOT, stack); }
            finally { internalOutputWrite = false; }
        }
        @Override protected void onContentsChanged(int slot) {
            if (slot < FUEL_SLOT) resetProgress();
            setChanged();
        }
    }

    private final MachineInventory inventory = new MachineInventory();
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return index == 0 ? progress : maxProgress; }
        @Override public void set(int index, int value) {
            if (index == 0) progress = value;
            else if (index == 1) maxProgress = value;
        }
        @Override public int getCount() { return 2; }
    };

    public LotmAlchemyBlockEntity(BlockPos pos, BlockState state) {
        super(LotmAlchemyContent.ALCHEMY_BLOCK_ENTITY.get(), pos, state);
    }
    public ItemStackHandler getItemHandler() { return inventory; }

    public boolean isBrewing() {
        BlockState state = getBlockState();
        return state.hasProperty(LotmAlchemyBlock.BREWING) && state.getValue(LotmAlchemyBlock.BREWING);
    }

    private void setBrewingState(boolean brewing) {
        if (level == null || level.isClientSide) return;
        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() instanceof LotmAlchemyBlock
                && state.getValue(LotmAlchemyBlock.BREWING) != brewing) {
            level.setBlock(worldPosition, state.setValue(LotmAlchemyBlock.BREWING, brewing), 3);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "alchemy", 0,
                state -> state.setAndContinue(isBrewing() ? BREWING_ANIMATION : IDLE_ANIMATION)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
    @Override public Component getDisplayName() {
        return Component.translatable("container.realscale.lotm_alchemy_apparatus");
    }
    @Override public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        ContainerLevelAccess access = level == null ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(level, worldPosition);
        return new LotmAlchemyMenu(id, playerInventory, inventory, access, data);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, LotmAlchemyBlockEntity machine) {
        if (!(level instanceof ServerLevel server) || !LotmConfig.ENABLED.get()) return;
        CraftingInput input = CraftingInput.of(2, 2, List.of(
                machine.inventory.getStackInSlot(0), machine.inventory.getStackInSlot(1),
                machine.inventory.getStackInSlot(2), machine.inventory.getStackInSlot(3)));
        Optional<RecipeHolder<LotmAlchemyRecipe>> match =
                server.getRecipeManager().getRecipeFor(LotmAlchemyContent.ALCHEMY_RECIPE_TYPE.get(), input, server);
        if (match.isEmpty()) { machine.resetProgress(); return; }
        RecipeHolder<CraftingRecipe> holder = match.get();
        ItemStack result = holder.value().assemble(input, server.registryAccess());
        if (result.isEmpty() || !(result.getItem() instanceof LotmPotionItem potion)
                || potion.sequence() < 8 || potion.sequence() > 9) {
            machine.resetProgress();
            return;
        }
        int configuredTicks = LotmBalance.alchemyTicks(potion.sequence(),
                LotmConfig.ALCHEMY_SEQUENCE9_TICKS.get(), LotmConfig.ALCHEMY_SEQUENCE8_TICKS.get());
        if (configuredTicks <= 0) { machine.resetProgress(); return; }
        if (machine.inventory.getStackInSlot(FUEL_SLOT).isEmpty()
                || !machine.canAcceptOutput(result)) {
            machine.setBrewingState(false);
            return;
        }
        if (!holder.id().equals(machine.activeRecipe) || machine.maxProgress <= 0) {
            machine.activeRecipe = holder.id();
            machine.progress = 0;
            machine.maxProgress = configuredTicks;
        }
        machine.progress++;
        machine.setBrewingState(true);
        machine.setChanged();
        if (machine.progress < machine.maxProgress) return;
        for (int slot = 0; slot < GRID_SIZE; slot++) {
            if (!machine.inventory.getStackInSlot(slot).isEmpty())
                machine.inventory.extractItem(slot, 1, false);
        }
        machine.inventory.extractItem(FUEL_SLOT, 1, false);
        ItemStack output = machine.inventory.getStackInSlot(OUTPUT_SLOT);
        if (output.isEmpty()) machine.inventory.setOutput(result.copy());
        else {
            ItemStack combined = output.copy();
            combined.grow(result.getCount());
            machine.inventory.setOutput(combined);
        }
        machine.progress = 0;
        machine.maxProgress = 0;
        machine.activeRecipe = null;
        machine.setChanged();
    }

    private boolean canAcceptOutput(ItemStack result) {
        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        return output.isEmpty() || (ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount()
                <= Math.min(output.getMaxStackSize(), result.getMaxStackSize()));
    }
    private void resetProgress() {
        if (progress == 0 && maxProgress == 0 && activeRecipe == null) return;
        progress = 0;
        maxProgress = 0;
        activeRecipe = null;
        setBrewingState(false);
        setChanged();
    }
    @Override public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
        if (activeRecipe != null) tag.putString("ActiveRecipe", activeRecipe.toString());
    }
    @Override public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        maxProgress = Math.max(0, Math.min(4800, tag.getInt("MaxProgress")));
        progress = Math.max(0, Math.min(maxProgress, tag.getInt("Progress")));
        activeRecipe = tag.contains("ActiveRecipe")
                ? ResourceLocation.tryParse(tag.getString("ActiveRecipe")) : null;
        if (maxProgress == 0 || activeRecipe == null) {
            progress = 0; maxProgress = 0; activeRecipe = null;
        }
    }
}

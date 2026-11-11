package org.realscale.neoforge.lotm;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class LotmAlchemyBlock extends Block implements EntityBlock {
    public LotmAlchemyBlock(Properties properties) { super(properties); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LotmAlchemyBlockEntity(pos, state);
    }
    @SuppressWarnings("unchecked")
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != LotmAlchemyContent.ALCHEMY_BLOCK_ENTITY.get()) return null;
        return (BlockEntityTicker<T>) (world, blockPos, blockState, blockEntity) ->
                LotmAlchemyBlockEntity.tick(world, blockPos, blockState, (LotmAlchemyBlockEntity) blockEntity);
    }
    @Override public InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!LotmConfig.ENABLED.get()) return InteractionResult.FAIL;
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof LotmAlchemyBlockEntity machine) {
            serverPlayer.openMenu(machine);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos,
            BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof LotmAlchemyBlockEntity machine) {
            for (int slot = 0; slot < 6; slot++) {
                ItemStack stack = machine.getItemHandler().getStackInSlot(slot);
                if (!stack.isEmpty()) Block.popResource(level, pos, stack.copy());
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}

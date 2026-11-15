package org.realscale.neoforge.lotm;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** In-game-only altar for the Fallen Mother's separate boon progression. */
public final class LotmBoonAltarBlock extends Block {
    public LotmBoonAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!LotmConfig.ENABLED.get()) return InteractionResult.FAIL;
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            String result = LotmBoonState.useAtAltar(serverPlayer);
            serverPlayer.displayClientMessage(
                    Component.translatable("message.realscale.lotm.boon.altar." + result), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

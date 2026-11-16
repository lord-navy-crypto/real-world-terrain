package org.realscale.neoforge.lotm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One-use ruin find: only active Sequence 9/8 characteristics are awarded. */
public final class LotmRelicCacheBlock extends Block {
    public LotmRelicCacheBlock(Properties properties) { super(properties); }
    @Override public void playerDestroy(Level level, Player player, BlockPos pos,
            BlockState state, BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (level.isClientSide() || player.isCreative() || !LotmConfig.ENABLED.get()) return;
        int sequence = level.random.nextInt(4) == 0 ? 8 : 9; // 75% S9, 25% S8.
        LotmPath[] paths = LotmPath.values();
        LotmPath pathway = paths[level.random.nextInt(paths.length)];
        Block.popResource(level, pos, new ItemStack(LotmContent.characteristic(pathway, sequence).get()));
    }
}

package org.realscale.neoforge.lotm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One-use ruin find: a Sequence 9/8/7 characteristic, never a reusable dispenser. */
public final class LotmRelicCacheBlock extends Block {
    public LotmRelicCacheBlock(Properties properties) { super(properties); }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos,
            BlockState state, BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (level.isClientSide() || !LotmConfig.ENABLED.get()) return;
        int roll = level.random.nextInt(100);
        int seq = roll < 8 ? 7 : roll < 33 ? 8 : 9;
        LotmPath[] paths = LotmPath.values();
        LotmPath pathway = paths[level.random.nextInt(paths.length)];
        Block.popResource(level, pos, new ItemStack(LotmContent.characteristic(pathway, seq).get()));
    }
}

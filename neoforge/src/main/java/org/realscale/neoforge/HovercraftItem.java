package org.realscale.neoforge;

import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Place a rideable hovercraft by right-clicking water or a free surface. */
public final class HovercraftItem extends Item {
    public HovercraftItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (hit.getType() == HitResult.Type.MISS)
            return InteractionResultHolder.pass(stack);
        if (hit.getType() != HitResult.Type.BLOCK)
            return InteractionResultHolder.fail(stack);

        Vec3 position = hit.getLocation();
        HovercraftEntity craft = RealScaleVehicles.HOVERCRAFT.get().create(level);
        if (craft == null) return InteractionResultHolder.fail(stack);
        craft.setPos(position.x, position.y, position.z);
        craft.setYRot(player.getYRot());
        if (!level.noCollision(craft, craft.getBoundingBox()))
            return InteractionResultHolder.fail(stack);

        if (!level.isClientSide) {
            level.addFreshEntity(craft);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, position);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}

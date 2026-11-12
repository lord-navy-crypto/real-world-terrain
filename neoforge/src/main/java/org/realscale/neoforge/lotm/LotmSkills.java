package org.realscale.neoforge.lotm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import java.util.List;

/** Server-side Sequence 9/8 active skills. The client only requests a slot number. */
public final class LotmSkills {
    private LotmSkills() {}

    /** Called only by a server-authoritative Player Abilities ActiveAbility. */
    public static boolean executeFromAbility(ServerPlayer player, LotmPath expectedPath, int slot, int sequence) {
        if (!LotmConfig.ENABLED.get()) {
            player.displayClientMessage(Component.translatable("message.realscale.lotm.disabled"), true);
            return false;
        }
        if (LotmPath.of(player) != expectedPath || LotmState.sequence(player) != sequence
                || sequence < 8 || sequence > 9 || slot < 1 || slot > 2) {
            player.displayClientMessage(Component.translatable("message.realscale.lotm.ability.wrong_sequence"), true);
            return false;
        }

        int cost = LotmBalance.skillCost(expectedPath.id(), slot, sequence);
        if (!LotmState.consume(player, cost)) {
            player.displayClientMessage(Component.translatable("message.realscale.lotm.low_spirit"), true);
            return false;
        }

        int detected = slot == 1 ? activatePrimary(player, expectedPath, sequence)
                : activateSecondary(player, expectedPath, sequence);
        LotmActing.recordSkillUse(player, 2 + (9 - sequence));
        if (detected > 0) LotmActing.recordResearch(player, Math.min(4, detected));

        if (detected > 0) {
            player.displayClientMessage(Component.translatable(
                    "message.realscale.lotm.skill.result", detected, LotmState.spirit(player)), true);
        } else {
            player.displayClientMessage(Component.translatable("message.realscale.lotm.skill.none"), true);
        }
        return true;
    }

    private static int activatePrimary(Player player, LotmPath path, int sequence) {
        Level level = player.level();
        int range = sequence == 8 ? 18 : 14;
        AABB area = new AABB(player.blockPosition()).inflate(range);
        int detected = 0;

        switch (path) {
            case SEER -> {
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                        entity -> entity != player && (entity instanceof LotmCreature
                                || entity.getPersistentData().contains(LotmEvents.VARIANT)))) {
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 140, 0, true, false));
                    detected++;
                }
                if (sequence == 8) player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 180, 0, true, false));
            }
            case MYSTERY_PRYER -> {
                detected = revealOccultBlocks(player, sequence == 8 ? 9 : 7, 5, 8);
                if (sequence == 8) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0, true, false));
            }
            case SLEEPLESS -> {
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                        entity -> entity != player && entity instanceof Monster)) {
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, true, false));
                    if (sequence == 8) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0, true, false));
                    detected++;
                }
                if (!level.isDay()) player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 240, 0, true, false));
                if (sequence == 8) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0, true, false));
            }
            case CORPSE_COLLECTOR -> {
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                        LotmSkills::isUndead)) {
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 150, 0, true, false));
                    if (sequence == 8) target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0, true, false));
                    detected++;
                }
                player.setTicksFrozen(0);
                if (sequence == 8) player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, false));
            }
        }
        return detected;
    }

    private static int activateSecondary(Player player, LotmPath path, int sequence) {
        Level level = player.level();
        int detected = 0;
        switch (path) {
            case SEER -> {
                BlockPos origin = player.blockPosition();
                BlockPos nearest = null;
                double nearestDistance = Double.MAX_VALUE;
                int radius = sequence == 8 ? 12 : 10;
                for (BlockPos pos : BlockPos.betweenClosed(
                        origin.offset(-radius, -6, -radius), origin.offset(radius, 6, radius))) {
                    if (!level.hasChunkAt(pos) || !isOccultBlock(level, pos)) continue;
                    double distance = origin.distSqr(pos);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = pos.immutable();
                    }
                }
                if (nearest == null) {
                    player.displayClientMessage(Component.translatable("message.realscale.lotm.unclear_omen"), true);
                } else {
                    String direction = LotmBalance.bearing(nearest.getX() - origin.getX(),
                            nearest.getZ() - origin.getZ(), nearest.getY() - origin.getY());
                    player.displayClientMessage(Component.translatable("message.realscale.lotm.omen",
                            Component.translatable("message.realscale.lotm.bearing." + direction)), true);
                    detected = 1;
                }
            }
            case MYSTERY_PRYER -> {
                detected = revealOccultBlocks(player, sequence == 8 ? 10 : 8, 6, 10);
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, sequence == 8 ? 180 : 120, 0, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, sequence == 8 ? 120 : 80, 0, true, false));
            }
            case SLEEPLESS -> {
                int duration = !level.isDay() ? (sequence == 8 ? 360 : 280) : (sequence == 8 ? 220 : 160);
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, duration, 0, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, sequence == 8 ? 180 : 120, 0, true, false));
                detected = 1;
            }
            case CORPSE_COLLECTOR -> {
                player.setTicksFrozen(0);
                int radius = sequence == 8 ? 22 : 18;
                AABB area = new AABB(player.blockPosition()).inflate(radius);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                        LotmSkills::isUndead)) {
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 180, 0, true, false));
                    detected++;
                }
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, sequence == 8 ? 160 : 100, 0, true, false));
                if (detected == 0) detected = 1; // The self-protection effect is still a successful activation.
            }
        }
        return detected;
    }

    private static int revealOccultBlocks(Player player, int radius, int vertical, int particleLimit) {
        Level level = player.level();
        BlockPos origin = player.blockPosition();
        int detected = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-radius, -vertical, -radius), origin.offset(radius, vertical, radius))) {
            if (!level.hasChunkAt(pos) || !isOccultBlock(level, pos)) continue;
            detected++;
            if (detected <= particleLimit && level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.8,
                        pos.getZ() + 0.5, 6, 0.18, 0.25, 0.18, 0.02);
            }
        }
        return detected;
    }

    private static boolean isOccultBlock(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.is(LotmContent.STAR_ORE.get()) || state.is(LotmContent.CURSED_ORE.get())
                || state.is(LotmContent.GRAVE_ORE.get()) || state.is(LotmContent.MIDNIGHT_BLOOM.get())
                || state.is(LotmContent.FACE_ROSE.get()) || state.is(LotmContent.SOUL_BELLFLOWER.get())
                || state.is(LotmContent.BLOOD_ROSE.get()) || state.is(LotmContent.RELIC_CACHE.get());
    }

    private static boolean isUndead(LivingEntity entity) {
        return entity instanceof Zombie || entity instanceof Skeleton || entity instanceof Phantom;
    }

}

package org.realscale.neoforge.lotm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
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

        int cost = LotmBalance.skillCost(expectedPath.id(), slot, sequence,
                LotmCurios.hasDivinationPendulum(player), LotmState.instability(player));
        if (!LotmState.consume(player, cost)) {
            player.displayClientMessage(Component.translatable("message.realscale.lotm.low_spirit"), true);
            return false;
        }

        int detected = slot == 1 ? activatePrimary(player, expectedPath, sequence)
                : activateSecondary(player, expectedPath, sequence);
        LotmActing.recordSkillUse(player, 2 + (9 - sequence));

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
                    LotmActing.recordResearchDiscovery(player, entityClue(target), 2);
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
                    LotmActing.recordResearchDiscovery(player, entityClue(target), 2);
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
                    LotmActing.recordResearchDiscovery(player, entityClue(target), 2);
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
                int radius = LotmBalance.divinationRadius(sequence,
                        LotmCurios.hasDivinationPendulum(player));
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
                    LotmActing.recordResearchDiscovery(player, blockClue(level, nearest), 2);
                    detected = 1;
                }
            }
            case MYSTERY_PRYER -> {
                // The primary ability surveys occult geology. The secondary
                // interprets living supernatural signatures rather than scanning the same blocks.
                int radius = sequence == 8 ? 18 : 14;
                AABB area = new AABB(player.blockPosition()).inflate(radius);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                        entity -> entity != player && (entity instanceof LotmCreature
                                || entity.getPersistentData().contains(LotmEvents.VARIANT)))) {
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, true, false));
                    LotmActing.recordResearchDiscovery(player, entityClue(target), 2);
                    detected++;
                }
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                        sequence == 8 ? 200 : 140, 0, true, false));
            }
            case SLEEPLESS -> {
                int duration = !level.isDay() ? (sequence == 8 ? 360 : 280) : (sequence == 8 ? 220 : 160);
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, duration, 0, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, sequence == 8 ? 180 : 120, 0, true, false));
                detected = 1;
            }
            case CORPSE_COLLECTOR -> {
                // The primary ability locates and weakens undead. This separate
                // shroud skill grants a bounded defensive window without another scan.
                player.setTicksFrozen(0);
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                        sequence == 8 ? 160 : 100, 0, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                        sequence == 8 ? 120 : 80, 0, true, false));
                detected = 1;
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
            LotmActing.recordResearchDiscovery(player, blockClue(level, pos), 2);
            detected++;
            if (detected <= particleLimit && level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.8,
                        pos.getZ() + 0.5, 6, 0.18, 0.25, 0.18, 0.02);
            }
        }
        return detected;
    }

    private static String entityClue(LivingEntity entity) {
        return "entity:" + BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
    }

    private static String blockClue(Level level, BlockPos pos) {
        return "block:" + BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
    }

    private static boolean isOccultBlock(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.is(LotmContent.STAR_ORE.get()) || state.is(LotmContent.CURSED_ORE.get())
                || state.is(LotmContent.GRAVE_ORE.get()) || state.is(LotmContent.LUNAR_MICA_ORE.get())
                || state.is(LotmContent.ECHO_QUARTZ_ORE.get()) || state.is(LotmContent.ABYSSAL_SALT_ORE.get())
                || state.is(LotmContent.MIDNIGHT_BLOOM.get())
                || state.is(LotmContent.FACE_ROSE.get()) || state.is(LotmContent.SOUL_BELLFLOWER.get())
                || state.is(LotmContent.BLOOD_ROSE.get()) || state.is(LotmContent.RELIC_CACHE.get());
    }

    static boolean isUndeadType(EntityType<?> type) {
        return type != null && type.is(EntityTypeTags.UNDEAD);
    }

    private static boolean isUndead(LivingEntity entity) {
        return entity != null && isUndeadType(entity.getType());
    }

}

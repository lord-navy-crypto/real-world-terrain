package org.realscale.neoforge.lotm;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.Objects;

/** Shared server-authoritative body for the ten distinct LOTM EntityTypes. */
public final class LotmCreature extends PathfinderMob implements GeoEntity {
    private final LotmCreatureKind kind;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private boolean kindGoalsRegistered;
    private int nextDiveTick;
    private int lastDiveTick = -1000;

    public LotmCreature(EntityType<? extends PathfinderMob> type, Level level, LotmCreatureKind kind) {
        super(type, level);
        this.kind = Objects.requireNonNull(kind, "kind");
        configureMovement();
        registerKindGoals();
    }

    public LotmCreatureKind kind() {
        return kind;
    }

    private void configureMovement() {
        if (kind.isFlying()) {
            navigation = new FlyingPathNavigation(this, level());
            moveControl = new FlyingMoveControl(this, 20, true);
            setNoGravity(true);
        } else if (kind.isAquatic()) {
            navigation = new WaterBoundPathNavigation(this, level());
            moveControl = new SmoothSwimmingMoveControl(this, 85, 10,
                    (float) kind.movementSpeed(), 0.08F, true);
        } else if (kind.isAmphibious()) {
            navigation = new AmphibiousPathNavigation(this, level());
            navigation.setCanFloat(true);
        }
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        if (kind != null) registerKindGoals();
    }

    private void registerKindGoals() {
        if (kindGoalsRegistered || kind == null) return;
        kindGoalsRegistered = true;
        goalSelector.addGoal(0, new FloatGoal(this));
        if (kind.isNocturnal()) goalSelector.addGoal(4, new NocturnalRestGoal(this));

        if (kind.isAquatic() || kind.isAmphibious()) {
            goalSelector.addGoal(1, new RandomSwimmingGoal(this, 0.9D, 40));
        }
        if (kind.isHostile() || kind.retaliates()) {
            goalSelector.addGoal(2, new MeleeAttackGoal(
                    this, kind.movementSpeed() * (kind.isHostile() ? 1.10D : 0.90D), true));
            targetSelector.addGoal(1, new HurtByTargetGoal(this));
            if (kind.isHostile()) {
                targetSelector.addGoal(2, kind.isNocturnal()
                        ? new NocturnalPlayerTargetGoal(this)
                        : new NearestAttackableTargetGoal<>(this, Player.class, true));
            }
        } else {
            goalSelector.addGoal(2, new PanicGoal(this, 1.20D));
            if (kind.isNocturnal() || kind.isAquatic()) {
                goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, 8.0F, 1.0D, 1.2D));
            }
        }

        if (kind.isFlying()) {
            goalSelector.addGoal(5, new RandomStrollGoal(this, 1.0D));
        } else if (!kind.isAquatic()) {
            goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, kind.movementSpeed()));
        }
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() || kind != LotmCreatureKind.DEATH_CROW) return;
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;
        double verticalDifference = getY() - target.getY();
        double horizontalDistanceSqr = distanceToSqr(target.getX(), getY(), target.getZ());
        if (!LotmCreatureBehavior.shouldDive(tickCount, nextDiveTick,
                verticalDifference, horizontalDistanceSqr)) return;
        Vec3 toward = target.position().subtract(position());
        Vec3 dive = new Vec3(toward.x, Math.min(-0.5D, toward.y), toward.z).normalize();
        push(dive.x * 0.18D, -0.22D, dive.z * 0.18D);
        triggerAnim("creature_actions", "special");
        lastDiveTick = tickCount;
        nextDiveTick = tickCount + 45;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean accepted = super.hurt(source, amount);
        if (!accepted || level().isClientSide() || !isAlive()) return accepted;

        switch (LotmCreatureBehavior.forKind(kind)) {
            case INK_CLOAK -> {
                if (getRandom().nextFloat() < 0.35F) {
                    addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, true, false));
                    triggerAnim("creature_actions", "special");
                    setTarget(null);
                    navigation.stop();
                    if (level() instanceof ServerLevel server) {
                        server.sendParticles(ParticleTypes.SQUID_INK,
                                getX(), getY() + getBbHeight() * 0.5D, getZ(),
                                18, 0.35D, 0.25D, 0.35D, 0.02D);
                    }
                }
            }
            case NOCTURNAL_EVASION -> {
                if (source.getEntity() instanceof LivingEntity attacker) {
                    Vec3 away = position().subtract(attacker.position());
                    if (away.lengthSqr() < 1.0E-6D) away = getLookAngle().reverse();
                    away = away.normalize();
                    push(away.x * 0.34D, 0.18D, away.z * 0.34D);
                    triggerAnim("creature_actions", "special");
                }
                addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, true, false));
            }
            case SPRING_ESCAPE -> {
                if (source.getEntity() instanceof LivingEntity attacker) {
                    Vec3 away = position().subtract(attacker.position());
                    if (away.lengthSqr() < 1.0E-6D) away = getLookAngle().reverse();
                    away = away.normalize();
                    push(away.x * 0.30D, 0.42D, away.z * 0.30D);
                    triggerAnim("creature_actions", "special");
                }
                addEffect(new MobEffectInstance(MobEffects.JUMP, 60, 1, true, false));
                addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, true, false));
            }
            case HIGHLAND_COUNTERCHARGE -> {
                if (source.getEntity() instanceof LivingEntity attacker) {
                    setTarget(attacker);
                    Vec3 toward = attacker.position().subtract(position());
                    if (toward.lengthSqr() > 1.0E-6D) {
                        toward = toward.normalize();
                        push(toward.x * 0.20D, 0.06D, toward.z * 0.20D);
                        triggerAnim("creature_actions", "special");
                    }
                    addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 0, true, false));
                }
            }
            case FOUR_EYED_GUARD -> {
                if (getHealth() <= getMaxHealth() * 0.5F) {
                    addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 0, true, false));
                    triggerAnim("creature_actions", "special");
                }
            }
            case DISORIENTING_GAZE, MIST_GRIP, ARMORED_BRUTE, PACK_HOWL, DIVE_HUNTER -> {
                // These profiles express their signature behavior when attacking.
            }
        }
        return accepted;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (!hit) return false;
        triggerAnim("creature_actions", "attack");
        if (level().isClientSide() || !(target instanceof LivingEntity victim)) return true;

        switch (LotmCreatureBehavior.forKind(kind)) {
            case DISORIENTING_GAZE, MIST_GRIP, HIGHLAND_COUNTERCHARGE, ARMORED_BRUTE, PACK_HOWL ->
                    triggerAnim("creature_actions", "special");
            case DIVE_HUNTER -> {
                if (lastDiveTick >= 0 && tickCount - lastDiveTick <= 12) {
                    triggerAnim("creature_actions", "special");
                }
            }
            default -> { }
        }

        switch (LotmCreatureBehavior.forKind(kind)) {
            case DISORIENTING_GAZE -> {
                victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, true, false));
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 35, 0, true, false));
            }
            case MIST_GRIP -> victim.addEffect(
                    new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 90, 1, true, false));
            case HIGHLAND_COUNTERCHARGE -> victim.addEffect(
                    new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 0, true, false));
            case ARMORED_BRUTE -> victim.addEffect(
                    new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 35, 0, true, false));
            case PACK_HOWL -> {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 35, 0, true, false));
                for (LotmCreature ally : level().getEntitiesOfClass(
                        LotmCreature.class, getBoundingBox().inflate(12.0D),
                        candidate -> candidate != this && candidate.isAlive() && candidate.kind() == kind)) {
                    ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0, true, false));
                }
                addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0, true, false));
            }
            case DIVE_HUNTER -> {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 45, 0, true, false));
                if (lastDiveTick >= 0 && tickCount - lastDiveTick <= 12) {
                    victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, true, false));
                    victim.push(0.0D, 0.18D, 0.0D);
                    lastDiveTick = -1000;
                }
            }
            case INK_CLOAK, NOCTURNAL_EVASION, SPRING_ESCAPE, FOUR_EYED_GUARD -> {
                // These creatures specialize in evasion or defense, not attack debuffs.
            }
        }
        return true;
    }

    @Override
    public void animateHurt(float yaw) {
        super.animateHurt(yaw);
        if (!level().isClientSide()) triggerAnim("creature_actions", "hurt");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        String prefix = "animation." + kind.id() + ".";
        controllers.add(new AnimationController<>(this, "creature_locomotion", 5, state -> {
            if (isDeadOrDying()) return state.setAndContinue(RawAnimation.begin().thenPlay(prefix + "death"));
            if (state.isMoving()) return state.setAndContinue(RawAnimation.begin().thenLoop(prefix + "walk"));
            return state.setAndContinue(RawAnimation.begin().thenLoop(prefix + "idle"));
        }));
        controllers.add(new AnimationController<>(this, "creature_actions", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", RawAnimation.begin().thenPlay(prefix + "attack"))
                .triggerableAnim("special", RawAnimation.begin().thenPlay(prefix + "special"))
                .triggerableAnim("hurt", RawAnimation.begin().thenPlay(prefix + "hurt")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    /**
     * Keeps nocturnal creatures still during daylight without suppressing
     * higher-priority panic, avoidance, retaliation, or attack goals.
     */
    private static final class NocturnalRestGoal extends Goal {
        private final LotmCreature creature;

        private NocturnalRestGoal(LotmCreature creature) {
            this.creature = creature;
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        private boolean shouldRest() {
            return !LotmCreatureSpawnRules.isNocturnalActive(creature.level().getDayTime());
        }

        @Override
        public boolean canUse() {
            return shouldRest();
        }

        @Override
        public boolean canContinueToUse() {
            return shouldRest();
        }

        @Override
        public void start() {
            creature.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            creature.getNavigation().stop();
            creature.setDeltaMovement(0.0D, 0.0D, 0.0D);
        }
    }

    private static final class NocturnalPlayerTargetGoal extends NearestAttackableTargetGoal<Player> {
        private final LotmCreature creature;
        private NocturnalPlayerTargetGoal(LotmCreature creature) {
            super(creature, Player.class, true);
            this.creature = creature;
        }

        private boolean isNight() {
            return LotmCreatureSpawnRules.isNocturnalActive(creature.level().getDayTime());
        }

        @Override
        public boolean canUse() {
            return isNight() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return isNight() && super.canContinueToUse();
        }
    }
}

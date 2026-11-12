package org.realscale.neoforge.lotm;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
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
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Objects;

/** Shared server-authoritative body for the ten distinct LOTM EntityTypes. */
public final class LotmCreature extends PathfinderMob implements GeoEntity {
    private final LotmCreatureKind kind;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private boolean kindGoalsRegistered;

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
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) triggerAnim("creature_actions", "attack");
        return hit;
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
                .triggerableAnim("hurt", RawAnimation.begin().thenPlay(prefix + "hurt")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    private static final class NocturnalPlayerTargetGoal extends NearestAttackableTargetGoal<Player> {
        private final LotmCreature creature;
        private NocturnalPlayerTargetGoal(LotmCreature creature) {
            super(creature, Player.class, true);
            this.creature = creature;
        }
        @Override
        public boolean canUse() {
            return !creature.level().isDay() && super.canUse();
        }
    }
}

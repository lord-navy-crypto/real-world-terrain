package org.realscale.neoforge.lotm;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
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
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Shared runtime for the ten distinct LOTM EntityTypes.
 * EntityType identity and LotmCreatureKind metadata remain separate and stable.
 */
public final class LotmCreature extends PathfinderMob implements GeoEntity {
    private static final RawAnimation IDLE =
            RawAnimation.begin().thenLoop("animation.lotm_creature.idle");
    private static final RawAnimation WALK =
            RawAnimation.begin().thenLoop("animation.lotm_creature.walk");
    private static final RawAnimation ATTACK =
            RawAnimation.begin().thenPlay("animation.lotm_creature.attack");

    private final LotmCreatureKind kind;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private boolean kindGoalsRegistered;

    public LotmCreature(EntityType<? extends PathfinderMob> type, Level level, LotmCreatureKind kind) {
        super(type, level);
        this.kind = java.util.Objects.requireNonNull(kind, "kind");

        // PathfinderMob supplies the common lifecycle; movement controls are
        // specialized after construction so each kind gets the right navigator.
        if (kind.isFlying()) {
            this.navigation = new FlyingPathNavigation(this, level);
            this.moveControl = new FlyingMoveControl(this, 10, true);
            setNoGravity(true);
        } else if (kind.isAquatic()) {
            this.navigation = new WaterBoundPathNavigation(this, level);
            this.moveControl = new SmoothSwimmingMoveControl(
                    this, 85, 10, (float) kind.movementSpeed(), 0.08F, true);
        }
        registerKindGoals();
    }

    public LotmCreatureKind kind() {
        return kind;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // Some Minecraft constructors invoke this before subclass fields are
        // assigned. The constructor calls registerKindGoals again afterwards.
        if (kind != null) registerKindGoals();
    }

    private void registerKindGoals() {
        if (kindGoalsRegistered || kind == null) return;
        kindGoalsRegistered = true;

        goalSelector.addGoal(0, new FloatGoal(this));
        if (kind.isAquatic() || kind.isAmphibious()) {
            goalSelector.addGoal(1, new RandomSwimmingGoal(this, 1.0D, 40));
        }

        if (kind.isHostile() || kind.retaliates()) {
            goalSelector.addGoal(2, new MeleeAttackGoal(
                    this, kind.movementSpeed() * (kind.isHostile() ? 1.10D : 0.90D), true));
            targetSelector.addGoal(1, new HurtByTargetGoal(this));
            if (kind.isHostile()) {
                targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
            }
        } else {
            goalSelector.addGoal(2, new PanicGoal(this, 1.20D));
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
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "creature_movement", 5, state -> {
            if (getTarget() != null && (kind.isHostile() || kind.retaliates())) {
                return state.setAndContinue(ATTACK);
            }
            if (getDeltaMovement().lengthSqr() > 0.0004D) {
                return state.setAndContinue(WALK);
            }
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}

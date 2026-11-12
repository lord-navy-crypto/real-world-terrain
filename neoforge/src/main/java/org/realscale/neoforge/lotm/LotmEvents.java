package org.realscale.neoforge.lotm;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.function.Supplier;

/** Server-side LOTM event hooks, no terrain noise / chunk hooks. */
public final class LotmEvents {
    public static final String VARIANT = "realscale_lotm_variant";
    private static final String CHECKED = "realscale_lotm_variant_checked";

    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !LotmConfig.ENABLED.get() || !LotmConfig.CREATURE_VARIANTS.get()) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living)) return;
        if (entity.getPersistentData().getBoolean(CHECKED)) return;
        entity.getPersistentData().putBoolean(CHECKED, true);
        Holder<Biome> biome = entity.level().getBiome(entity.blockPosition());
        String biomeId = biome.unwrapKey().map(key -> key.location().toString()).orElse("");
        String type = living instanceof Squid ? "squid"
                : living instanceof Spider ? "spider"
                : living instanceof Bat ? "bat"
                : living instanceof Frog ? "frog"
                : living instanceof Goat ? "goat"
                : living instanceof Husk ? "husk"
                : living instanceof Turtle ? "turtle"
                : living instanceof Wolf ? "wolf"
                : living instanceof Parrot ? "parrot"
                : living instanceof Ocelot ? "ocelot"
                : living instanceof Cow ? "cow"
                : living instanceof Phantom ? "phantom"
                : living instanceof EnderMan ? "enderman"
                : living instanceof Cat ? "cat"
                : living instanceof Zombie ? "zombie" : "";
        String variant = type.isEmpty() ? null : LotmEcologyRules.pickVariant(
                type, biomeId, entity.getRandom().nextInt(1_000_000),
                LotmConfig.VARIANT_RARITY_PERCENT.get());
        if (variant != null) {
            entity.getPersistentData().putString(VARIANT, variant);
            entity.setCustomName(Component.translatable("entity.realscale.lotm." + variant));
            entity.setCustomNameVisible(true);
        }
    }

    public static void onDrops(LivingDropsEvent event) {
        if (!LotmConfig.ENABLED.get() || !LotmConfig.CREATURE_VARIANTS.get()) return;
        LivingEntity entity = event.getEntity();
        String id = LotmEcologyRules.DROPS.get(entity.getPersistentData().getString(VARIANT));
        if (id == null) return;
        Supplier<Item> material = LotmContent.materialItem(id);
        if (material != null) event.getDrops().add(new ItemEntity(entity.level(),
                entity.getX(), entity.getY(), entity.getZ(), new ItemStack(material.get())));
    }

    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        LotmPlayerAbilitySync.sanitizeUnauthorizedGrants(player);
        LotmPlayerAbilitySync.showPendingTomeMessage(player);
        if (!LotmConfig.ENABLED.get()) {
            if (player.tickCount % 20 == 0) LotmPlayerAbilitySync.sync(player);
            return;
        }
        if (player.tickCount % 40 != 0) return;
        LotmPath path = LotmPath.of(player);
        if (path == null) return;
        int sequence = LotmState.sequence(player);
        if (sequence < 8 || sequence > 9) return;
        LotmActing.onPlayerTick(player);
        // Regenerate discreetly; night-rested Sleepless users regain spirituality faster.
        int recovery = LotmConfig.SPIRIT_RECOVERY.get();
        if (path == LotmPath.SLEEPLESS && !player.level().isDay()) recovery += 1;
        LotmState.regenerate(player, recovery);
        if (player.tickCount % 80 != 0) return;
        if (path == LotmPath.SEER && sequence == 8) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 0, true, false));
        }
        if (path == LotmPath.MYSTERY_PRYER && sequence == 8) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 0, true, false));
        }
        if (path == LotmPath.SLEEPLESS && !player.level().isDay()) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 0, true, false));
        }
        if (path == LotmPath.CORPSE_COLLECTOR && player.getTicksFrozen() > 0) {
            // Temperature hardiness is represented conservatively without changing combat damage.
            player.setTicksFrozen(0);
        }
    }

    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        LotmState.copy(event.getOriginal(), event.getEntity());
    }

    private LotmEvents() {}
}

package org.realscale.neoforge.lotm;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.FluidTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registry and server bootstrap for the ten active independent LOTM creatures. */
public final class LotmCreatureEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, "realscale");
    private static final Map<LotmCreatureKind, Supplier<EntityType<LotmCreature>>> TYPES =
            new EnumMap<>(LotmCreatureKind.class);

    static {
        for (LotmCreatureKind kind : LotmCreatureKind.values()) {
            TYPES.put(kind, ENTITY_TYPES.register("lotm_" + kind.id(), () ->
                    EntityType.Builder.<LotmCreature>of(
                                    (type, level) -> new LotmCreature(type, level, kind),
                                    kind.spawnCategory())
                            .sized(kind.width(), kind.height())
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build(registryId(kind).toString())));
        }
    }

    private LotmCreatureEntities() {}

    public static ResourceLocation registryId(LotmCreatureKind kind) {
        Objects.requireNonNull(kind, "kind");
        return ResourceLocation.fromNamespaceAndPath("realscale", "lotm_" + kind.id());
    }

    public static Set<LotmCreatureKind> registeredKinds() {
        return EnumSet.copyOf(TYPES.keySet());
    }

    public static Supplier<EntityType<LotmCreature>> type(LotmCreatureKind kind) {
        Supplier<EntityType<LotmCreature>> supplier = TYPES.get(Objects.requireNonNull(kind, "kind"));
        if (supplier == null) throw new IllegalArgumentException("Unknown LOTM creature kind");
        return supplier;
    }

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        TYPES.forEach((kind, supplier) -> event.put(supplier.get(), attributes(kind)));
    }

    private static AttributeSupplier attributes(LotmCreatureKind kind) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, kind.maxHealth())
                .add(Attributes.MOVEMENT_SPEED, kind.movementSpeed())
                .add(Attributes.ATTACK_DAMAGE, kind.attackDamage())
                .add(Attributes.ARMOR, kind.armor())
                .add(Attributes.FOLLOW_RANGE, kind.isHostile() ? 24.0D : 12.0D)
                .build();
    }

    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        TYPES.forEach((kind, supplier) -> event.register(
                supplier.get(),
                kind.isAquatic() ? SpawnPlacementTypes.IN_WATER : SpawnPlacementTypes.ON_GROUND,
                kind.isAquatic() ? Heightmap.Types.OCEAN_FLOOR : Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, spawnReason, pos, random) -> {
                    if (!LotmConfig.ENABLED.get() || !LotmConfig.CREATURE_ENTITIES.get()) return false;
                    String biomeId = level.getBiome(pos).unwrapKey()
                            .map(key -> key.location().toString()).orElse("");
                    if (!LotmCreatureSpawnRules.canSpawnInBiome(kind, biomeId)) return false;
                    if (kind.isAquatic() && !level.getFluidState(pos).is(FluidTags.WATER)) return false;
                    return Mob.checkMobSpawnRules(type, level, spawnReason, pos, random);
                },
                RegisterSpawnPlacementsEvent.Operation.REPLACE));
    }
}

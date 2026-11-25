package org.realscale.neoforge;

import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import java.util.function.Supplier;

/** Optional gameplay vehicle, separate from density functions and presets. */
public final class RealScaleVehicles {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, "realscale");
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, "realscale");

    public static final Supplier<EntityType<HovercraftEntity>> HOVERCRAFT =
            ENTITIES.register("hovercraft", () ->
                    EntityType.Builder.<HovercraftEntity>of(
                            HovercraftEntity::new, MobCategory.MISC)
                    .sized(1.375F, 0.5625F)
                    .clientTrackingRange(10)
                    .build("realscale:hovercraft"));

    public static final Supplier<Item> HOVERCRAFT_ITEM =
            ITEMS.register("hovercraft", () ->
                    new HovercraftItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        ITEMS.register(bus);
    }

    private RealScaleVehicles() {}
}

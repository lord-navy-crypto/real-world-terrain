package org.realscale.neoforge;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import java.util.function.Supplier;
public final class RealScaleVehicles {
    private static EntityType<HovercraftEntity> entity;
    private static Item item;
    public static final Supplier<EntityType<HovercraftEntity>> HOVERCRAFT = () -> entity;
    public static final Supplier<Item> HOVERCRAFT_ITEM = () -> item;
    public static void register() {
        entity = Registry.register(BuiltInRegistries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("realscale", "hovercraft"),
            EntityType.Builder.<HovercraftEntity>of(HovercraftEntity::new, MobCategory.MISC).sized(1.375F, 0.5625F).clientTrackingRange(10).build("realscale:hovercraft"));
        item = Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath("realscale", "hovercraft"),
            new HovercraftItem(new Item.Properties().stacksTo(1)));
    }
    private RealScaleVehicles() {}
}

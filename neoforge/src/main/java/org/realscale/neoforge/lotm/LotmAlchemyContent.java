package org.realscale.neoforge.lotm;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;
public final class LotmAlchemyContent {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "realscale");
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, "realscale");
    public static final Supplier<BlockEntityType<LotmAlchemyBlockEntity>> ALCHEMY_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("lotm_alchemy_apparatus", () -> BlockEntityType.Builder.of(
            LotmAlchemyBlockEntity::new, LotmContent.ALCHEMY_APPARATUS.get()).build(null));
    public static final Supplier<MenuType<LotmAlchemyMenu>> ALCHEMY_MENU =
        MENUS.register("lotm_alchemy", () -> new MenuType<>(LotmAlchemyMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static void register(IEventBus bus) { BLOCK_ENTITIES.register(bus); MENUS.register(bus); }
    private LotmAlchemyContent() {}
}

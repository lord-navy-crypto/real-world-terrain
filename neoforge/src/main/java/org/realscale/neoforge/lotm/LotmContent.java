package org.realscale.neoforge.lotm;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

/** Gameplay content is registered alongside, not instead of, RealScale worldgen. */
public final class LotmContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("realscale");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("realscale");

    public static final Supplier<Block> STAR_ORE = BLOCKS.register("lotm_star_crystal_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE).strength(3.5F)));
    public static final Supplier<Block> CURSED_ORE = BLOCKS.register("lotm_cursed_crystal_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE).strength(3.5F)));
    public static final Supplier<Block> GRAVE_ORE = BLOCKS.register("lotm_grave_crystal_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).strength(2.5F)));
    public static final Supplier<Block> MIDNIGHT_BLOOM = BLOCKS.register("lotm_midnight_bloom",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.BLUE_ORCHID)
                    .noCollission().noOcclusion().strength(0.1F).sound(SoundType.GRASS)));

    public static final Supplier<? extends Item> STAR_ORE_ITEM = ITEMS.registerSimpleBlockItem("lotm_star_crystal_ore", STAR_ORE);
    public static final Supplier<? extends Item> CURSED_ORE_ITEM = ITEMS.registerSimpleBlockItem("lotm_cursed_crystal_ore", CURSED_ORE);
    public static final Supplier<? extends Item> GRAVE_ORE_ITEM = ITEMS.registerSimpleBlockItem("lotm_grave_crystal_ore", GRAVE_ORE);
    public static final Supplier<? extends Item> BLOOM_ITEM = ITEMS.registerSimpleBlockItem("lotm_midnight_bloom", MIDNIGHT_BLOOM);
    public static final Supplier<Item> STAR_CRYSTAL = ITEMS.registerSimpleItem("lotm_star_crystal", new Item.Properties());
    public static final Supplier<Item> CURSED_CRYSTAL = ITEMS.registerSimpleItem("lotm_cursed_crystal", new Item.Properties());
    public static final Supplier<Item> GRAVE_CRYSTAL = ITEMS.registerSimpleItem("lotm_grave_crystal", new Item.Properties());
    public static final Supplier<Item> LAVAS_SQUID_ESSENCE = ITEMS.registerSimpleItem("lotm_lavas_squid_essence", new Item.Properties());
    public static final Supplier<Item> NIGHTMARE_EYE_SHARD = ITEMS.registerSimpleItem("lotm_nightmare_eye_shard", new Item.Properties());
    public static final Supplier<Item> NIGHT_OWL_TOKEN = ITEMS.registerSimpleItem("lotm_night_owl_token", new Item.Properties());
    public static final Supplier<Item> BLACK_SPOTTED_FROG_TOKEN = ITEMS.registerSimpleItem("lotm_black_spotted_frog_token", new Item.Properties());
    public static final Supplier<Item> SHROUDMAN_CRYSTAL = ITEMS.registerSimpleItem("lotm_shroudman_crystal", new Item.Properties());
    public static final Supplier<? extends Item> DIVINATION_PENDULUM = ITEMS.registerItem("lotm_divination_pendulum",
            LotmDivinationItem::new, new Item.Properties().stacksTo(1));
    public static final Supplier<Item> EMPTY_VIAL = ITEMS.registerSimpleItem("lotm_empty_vial", new Item.Properties());
    public static final Supplier<? extends Item> SPIRIT_FOCUS = ITEMS.registerItem("lotm_spirit_focus",
            LotmFocusItem::new, new Item.Properties().stacksTo(1));

    public static final Supplier<? extends Item> SEER_POTION = ITEMS.registerItem("lotm_seer_potion",
            p -> new LotmPotionItem(LotmPath.SEER, p), new Item.Properties().stacksTo(1));
    public static final Supplier<? extends Item> PRYER_POTION = ITEMS.registerItem("lotm_mystery_pryer_potion",
            p -> new LotmPotionItem(LotmPath.MYSTERY_PRYER, p), new Item.Properties().stacksTo(1));
    public static final Supplier<? extends Item> SLEEPLESS_POTION = ITEMS.registerItem("lotm_sleepless_potion",
            p -> new LotmPotionItem(LotmPath.SLEEPLESS, p), new Item.Properties().stacksTo(1));
    public static final Supplier<? extends Item> CORPSE_POTION = ITEMS.registerItem("lotm_corpse_collector_potion",
            p -> new LotmPotionItem(LotmPath.CORPSE_COLLECTOR, p), new Item.Properties().stacksTo(1));

    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); }
    private LotmContent() {}
}

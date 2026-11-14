package org.realscale.neoforge.lotm;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;
import java.util.Map;
import java.util.LinkedHashMap;

/** Gameplay content is registered alongside, not instead of, RealScale worldgen. */
public final class LotmContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("realscale");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("realscale");

    public static final Supplier<Block> STAR_ORE = BLOCKS.register("lotm_star_crystal_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE).strength(3.5F).requiresCorrectToolForDrops()));
    public static final Supplier<Block> CURSED_ORE = BLOCKS.register("lotm_cursed_crystal_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE).strength(3.5F).requiresCorrectToolForDrops()));
    public static final Supplier<Block> GRAVE_ORE = BLOCKS.register("lotm_grave_crystal_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).strength(2.5F).requiresCorrectToolForDrops()));
    public static final Supplier<Block> LUNAR_MICA_ORE = BLOCKS.register("lotm_lunar_mica_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE)
                    .strength(3.0F).requiresCorrectToolForDrops()));
    public static final Supplier<Block> ECHO_QUARTZ_ORE = BLOCKS.register("lotm_echo_quartz_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE)
                    .strength(3.0F).requiresCorrectToolForDrops()));
    public static final Supplier<Block> ABYSSAL_SALT_ORE = BLOCKS.register("lotm_abyssal_salt_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE)
                    .strength(3.0F).requiresCorrectToolForDrops()));
    public static final Supplier<Block> MIDNIGHT_BLOOM = BLOCKS.register("lotm_midnight_bloom",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.BLUE_ORCHID)
                    .noCollission().noOcclusion().strength(0.1F).sound(SoundType.GRASS)));

    // Sequence 8 scenery plus retained Sequence 7 archive assets: three flowers, quartz ore and a ruin cache.
    public static final Supplier<Block> FACE_ROSE = BLOCKS.register("lotm_face_rose",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.BLUE_ORCHID)
                    .noCollission().noOcclusion().strength(0.1F).sound(SoundType.GRASS)));
    public static final Supplier<Block> SOUL_BELLFLOWER = BLOCKS.register("lotm_soul_bellflower",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.BLUE_ORCHID)
                    .noCollission().noOcclusion().strength(0.1F).sound(SoundType.GRASS)));
    public static final Supplier<Block> BLOOD_ROSE = BLOCKS.register("lotm_blood_rose",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.BLUE_ORCHID)
                    .noCollission().noOcclusion().strength(0.1F).sound(SoundType.GRASS)));
    public static final Supplier<Block> SPIRIT_QUARTZ_ORE = BLOCKS.register("lotm_spirit_quartz_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE).strength(3.0F)));
    public static final Supplier<Block> RELIC_CACHE = BLOCKS.register("lotm_relic_cache",
            () -> new LotmRelicCacheBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.MOSSY_STONE_BRICKS)
                    .strength(2.5F)));
    public static final Supplier<Block> ALCHEMY_APPARATUS = BLOCKS.register("lotm_alchemy_apparatus",
            () -> new LotmAlchemyBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_BRICKS)
                    .strength(4.0F).requiresCorrectToolForDrops()));

    public static final Supplier<? extends Item> STAR_ORE_ITEM = ITEMS.registerSimpleBlockItem("lotm_star_crystal_ore", STAR_ORE);
    public static final Supplier<? extends Item> CURSED_ORE_ITEM = ITEMS.registerSimpleBlockItem("lotm_cursed_crystal_ore", CURSED_ORE);
    public static final Supplier<? extends Item> GRAVE_ORE_ITEM = ITEMS.registerSimpleBlockItem("lotm_grave_crystal_ore", GRAVE_ORE);
    public static final Supplier<? extends Item> LUNAR_MICA_ORE_ITEM = ITEMS.registerSimpleBlockItem("lotm_lunar_mica_ore", LUNAR_MICA_ORE);
    public static final Supplier<? extends Item> ECHO_QUARTZ_ORE_ITEM = ITEMS.registerSimpleBlockItem("lotm_echo_quartz_ore", ECHO_QUARTZ_ORE);
    public static final Supplier<? extends Item> ABYSSAL_SALT_ORE_ITEM = ITEMS.registerSimpleBlockItem("lotm_abyssal_salt_ore", ABYSSAL_SALT_ORE);
    public static final Supplier<? extends Item> BLOOM_ITEM = ITEMS.registerSimpleBlockItem("lotm_midnight_bloom", MIDNIGHT_BLOOM);
    public static final Supplier<? extends Item> FACE_ROSE_ITEM = ITEMS.registerSimpleBlockItem("lotm_face_rose", FACE_ROSE);
    public static final Supplier<? extends Item> SOUL_BELLFLOWER_ITEM = ITEMS.registerSimpleBlockItem("lotm_soul_bellflower", SOUL_BELLFLOWER);
    public static final Supplier<? extends Item> BLOOD_ROSE_ITEM = ITEMS.registerSimpleBlockItem("lotm_blood_rose", BLOOD_ROSE);
    public static final Supplier<? extends Item> SPIRIT_QUARTZ_ORE_ITEM = ITEMS.registerSimpleBlockItem("lotm_spirit_quartz_ore", SPIRIT_QUARTZ_ORE);
    public static final Supplier<? extends Item> RELIC_CACHE_ITEM = ITEMS.registerSimpleBlockItem("lotm_relic_cache", RELIC_CACHE);
    public static final Supplier<? extends Item> ALCHEMY_APPARATUS_ITEM =
            ITEMS.registerSimpleBlockItem("lotm_alchemy_apparatus", ALCHEMY_APPARATUS);
    public static final Supplier<Item> STAR_CRYSTAL = ITEMS.registerSimpleItem("lotm_star_crystal", new Item.Properties());
    public static final Supplier<Item> CURSED_CRYSTAL = ITEMS.registerSimpleItem("lotm_cursed_crystal", new Item.Properties());
    public static final Supplier<Item> GRAVE_CRYSTAL = ITEMS.registerSimpleItem("lotm_grave_crystal", new Item.Properties());
    public static final Supplier<Item> LUNAR_MICA = ITEMS.registerSimpleItem("lotm_lunar_mica", new Item.Properties());
    public static final Supplier<Item> ECHO_QUARTZ = ITEMS.registerSimpleItem("lotm_echo_quartz", new Item.Properties());
    public static final Supplier<Item> ABYSSAL_SALT = ITEMS.registerSimpleItem("lotm_abyssal_salt", new Item.Properties());
    public static final Supplier<Item> LAVAS_SQUID_ESSENCE = ITEMS.registerSimpleItem("lotm_lavas_squid_essence", new Item.Properties());
    public static final Supplier<Item> NIGHTMARE_EYE_SHARD = ITEMS.registerSimpleItem("lotm_nightmare_eye_shard", new Item.Properties());
    public static final Supplier<Item> NIGHT_OWL_TOKEN = ITEMS.registerSimpleItem("lotm_night_owl_token", new Item.Properties());
    public static final Supplier<Item> BLACK_SPOTTED_FROG_TOKEN = ITEMS.registerSimpleItem("lotm_black_spotted_frog_token", new Item.Properties());
    public static final Supplier<Item> SHROUDMAN_CRYSTAL = ITEMS.registerSimpleItem("lotm_shroudman_crystal", new Item.Properties());
    public static final Supplier<? extends Item> DIVINATION_PENDULUM = ITEMS.registerItem("lotm_divination_pendulum",
            LotmDivinationItem::new, new Item.Properties().stacksTo(1));
    public static final Supplier<Item> EMPTY_VIAL = ITEMS.registerSimpleItem("lotm_empty_vial", new Item.Properties());
    public static final Supplier<Item> SPIRIT_EMBER = ITEMS.registerSimpleItem("lotm_spirit_ember", new Item.Properties());
    public static final Supplier<? extends Item> SPIRIT_FOCUS = ITEMS.registerItem("lotm_spirit_focus",
            LotmFocusItem::new, new Item.Properties().stacksTo(1));
    public static final Supplier<? extends Item> STABILITY_SIGIL = ITEMS.registerItem(
            "lotm_stability_sigil", LotmStabilitySigilItem::new, new Item.Properties().stacksTo(16));

    public static final Supplier<? extends Item> SEER_POTION = ITEMS.registerItem("lotm_seer_potion",
            p -> new LotmPotionItem(LotmPath.SEER, p), new Item.Properties().stacksTo(1));
    public static final Supplier<? extends Item> PRYER_POTION = ITEMS.registerItem("lotm_mystery_pryer_potion",
            p -> new LotmPotionItem(LotmPath.MYSTERY_PRYER, p), new Item.Properties().stacksTo(1));
    public static final Supplier<? extends Item> SLEEPLESS_POTION = ITEMS.registerItem("lotm_sleepless_potion",
            p -> new LotmPotionItem(LotmPath.SLEEPLESS, p), new Item.Properties().stacksTo(1));
    public static final Supplier<? extends Item> CORPSE_POTION = ITEMS.registerItem("lotm_corpse_collector_potion",
            p -> new LotmPotionItem(LotmPath.CORPSE_COLLECTOR, p), new Item.Properties().stacksTo(1));


    /** Item IDs are stable, including the existing four Sequence 9 potions. */
    private static final Map<String, Supplier<Item>> MATERIALS = new LinkedHashMap<>();
    private static final Map<String, Supplier<? extends Item>> CHARACTERISTICS = new LinkedHashMap<>();
    private static final Map<String, Supplier<? extends Item>> PROMOTIONS = new LinkedHashMap<>();
    private static final Map<String, Supplier<? extends Item>> SKILL_BOOKS = new LinkedHashMap<>();

    private static String skillBookKey(LotmPath path, int slot) {
        return path.id() + ":" + slot;
    }

    /** A study manual improves a pathway skill but never grants the external ability itself. */
    public static Supplier<? extends Item> skillBook(LotmPath path, int slot) {
        return path == null || slot < 1 || slot > 2 ? null : SKILL_BOOKS.get(skillBookKey(path, slot));
    }

    private static void material(String suffix) {
        MATERIALS.put(suffix, ITEMS.registerSimpleItem("lotm_" + suffix, new Item.Properties()));
    }

    private static String key(LotmPath pathway, int sequence) {
        return pathway.id() + ":" + sequence;
    }

    /** Unknown or archived drop IDs fail closed instead of crashing the server drop event. */
    public static Supplier<Item> materialItem(String suffix) {
        return suffix == null ? null : MATERIALS.get(suffix);
    }

    /** Count registered LOTM materials in the player's complete inventory, including offhand and armor. */
    public static int countMaterials(Player player) {
        if (player == null) return 0;
        int total = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            for (Supplier<Item> material : MATERIALS.values()) {
                if (stack.is(material.get())) {
                    total += stack.getCount();
                    break;
                }
            }
        }
        return total;
    }

    /** Active potions are also listed in the LOTM creative inventory category. */
    public static Supplier<? extends Item> potion(LotmPath path, int sequence) {
        if (path == null) return null;
        if (sequence == 9) {
            return switch (path) {
                case SEER -> SEER_POTION;
                case MYSTERY_PRYER -> PRYER_POTION;
                case SLEEPLESS -> SLEEPLESS_POTION;
                case CORPSE_COLLECTOR -> CORPSE_POTION;
            };
        }
        return PROMOTIONS.get(key(path, sequence));
    }

    public static Supplier<? extends Item> characteristic(LotmPath path, int sequence) {
        Supplier<? extends Item> found = CHARACTERISTICS.get(key(path, sequence));
        if (found == null) throw new IllegalArgumentException("Unregistered LOTM characteristic");
        return found;
    }

    static {
        MATERIALS.put("lavas_squid_essence", LAVAS_SQUID_ESSENCE);
        MATERIALS.put("nightmare_eye_shard", NIGHTMARE_EYE_SHARD);
        MATERIALS.put("night_owl_token", NIGHT_OWL_TOKEN);
        MATERIALS.put("black_spotted_frog_token", BLACK_SPOTTED_FROG_TOKEN);
        MATERIALS.put("shroudman_crystal", SHROUDMAN_CRYSTAL);
        MATERIALS.put("lunar_mica", LUNAR_MICA);
        MATERIALS.put("echo_quartz", ECHO_QUARTZ);
        MATERIALS.put("abyssal_salt", ABYSSAL_SALT);
        for (String material : new String[] {
            "hornacis_goat_horn", "ironarm_spine", "four_eyed_turtle_heart",
            "crimson_howler_voice", "death_crow_eye", "mist_treant_root",
            "evil_pattern_panther_essence", "stag_beast_antler",
            "grey_grandmother_eye", "dream_crow_heart", "nightmare_shadow_core",
            "spectral_cat_paw", "spirit_quartz", "occult_catalyst"
        }) material(material);
        // Keep S7 registry IDs so older pilot worlds/items do not become unknown IDs.
        // S7 recipes, drops and advancement are disabled until that tier is reopened.
        for (LotmPath path : LotmPath.values()) {
            for (int slot = 1; slot <= 2; slot++) {
                final int skillSlot = slot;
                String manualId = "lotm_" + path.id() + "_skill_manual_" + slot;
                SKILL_BOOKS.put(skillBookKey(path, slot), ITEMS.registerItem(manualId,
                        properties -> new LotmSkillBookItem(path, skillSlot, properties),
                        new Item.Properties().stacksTo(1)));
            }
            for (int rank = 9; rank >= 7; rank--) {
                final int sequence = rank;
                String characteristicId = "lotm_" + path.id() + "_s" + sequence + "_characteristic";
                CHARACTERISTICS.put(key(path, sequence), ITEMS.registerItem(characteristicId,
                        properties -> new LotmPotionItem(path, sequence, true, properties),
                        new Item.Properties().stacksTo(1)));
                if (sequence == 9) continue; // preserve legacy Sequence 9 potion IDs
                String potionId = "lotm_" + path.id() + "_s" + sequence + "_potion";
                PROMOTIONS.put(key(path, sequence), ITEMS.registerItem(potionId,
                        properties -> new LotmPotionItem(path, sequence, false, properties),
                        new Item.Properties().stacksTo(1)));
            }
        }
    }

    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); }
    private LotmContent() {}
}

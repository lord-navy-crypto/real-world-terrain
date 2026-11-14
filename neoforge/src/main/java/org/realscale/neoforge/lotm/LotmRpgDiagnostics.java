package org.realscale.neoforge.lotm;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Read-only runtime acceptance checks for the active low-sequence LOTM content.
 *
 * Run with /lotm diagnose in a loaded world. This checks actual registries and
 * the server recipe manager; it does not grant items, edit player data, or
 * change world blocks. Visual appearance and natural world generation still
 * require a real play session.
 */
public final class LotmRpgDiagnostics {
    private static final String[] CREATURE_MATERIALS = {
        "lavas_squid_essence", "nightmare_eye_shard", "night_owl_token",
        "black_spotted_frog_token", "shroudman_crystal", "hornacis_goat_horn",
        "ironarm_spine", "four_eyed_turtle_heart", "crimson_howler_voice", "death_crow_eye"
    };

    private LotmRpgDiagnostics() {}

    public static int run(ServerPlayer player) {
        int passed = 0;
        int total = 0;
        player.displayClientMessage(Component.translatable(
                "message.realscale.lotm.diagnose.header"), false);

        List<Supplier<Block>> oreBlocks = List.of(
                LotmContent.STAR_ORE, LotmContent.CURSED_ORE, LotmContent.GRAVE_ORE,
                LotmContent.LUNAR_MICA_ORE, LotmContent.ECHO_QUARTZ_ORE,
                LotmContent.ABYSSAL_SALT_ORE);
        List<Supplier<? extends Item>> oreItems = List.of(
                LotmContent.STAR_ORE_ITEM, LotmContent.CURSED_ORE_ITEM, LotmContent.GRAVE_ORE_ITEM,
                LotmContent.LUNAR_MICA_ORE_ITEM, LotmContent.ECHO_QUARTZ_ORE_ITEM,
                LotmContent.ABYSSAL_SALT_ORE_ITEM);
        int ores = countBlocks(oreBlocks) + countItems(oreItems);
        passed += report(player, "ores", ores, 12);
        total += 12;

        List<Supplier<? extends Item>> minerals = List.of(
                LotmContent.STAR_CRYSTAL, LotmContent.CURSED_CRYSTAL, LotmContent.GRAVE_CRYSTAL,
                LotmContent.LUNAR_MICA, LotmContent.ECHO_QUARTZ, LotmContent.ABYSSAL_SALT);
        passed += report(player, "minerals", countItems(minerals), 6);
        total += 6;

        List<Supplier<Block>> flowers = List.of(
                LotmContent.MIDNIGHT_BLOOM, LotmContent.FACE_ROSE,
                LotmContent.SOUL_BELLFLOWER, LotmContent.BLOOD_ROSE);
        List<Supplier<? extends Item>> flowerItems = List.of(
                LotmContent.BLOOM_ITEM, LotmContent.FACE_ROSE_ITEM,
                LotmContent.SOUL_BELLFLOWER_ITEM, LotmContent.BLOOD_ROSE_ITEM);
        passed += report(player, "flora",
                countBlocks(flowers) + countItems(flowerItems), 8);
        total += 8;

        int creatureMaterials = 0;
        for (String material : CREATURE_MATERIALS) {
            if (registeredItem(LotmContent.materialItem(material))) creatureMaterials++;
        }
        passed += report(player, "creature_materials", creatureMaterials, CREATURE_MATERIALS.length);
        total += CREATURE_MATERIALS.length;

        int manuals = 0;
        for (LotmPath path : LotmPath.values()) {
            for (int slot = 1; slot <= 2; slot++) {
                if (registeredItem(LotmContent.skillBook(path, slot))) manuals++;
            }
        }
        passed += report(player, "skill_manuals", manuals, LotmPath.values().length * 2);
        total += LotmPath.values().length * 2;

        int apparatus = 0;
        if (registeredBlock(LotmContent.ALCHEMY_APPARATUS)) apparatus++;
        if (registeredItem(LotmContent.ALCHEMY_APPARATUS_ITEM)) apparatus++;
        if (LotmAlchemyContent.ALCHEMY_BLOCK_ENTITY.get() != null) apparatus++;
        if (LotmAlchemyContent.ALCHEMY_MENU.get() != null) apparatus++;
        if (LotmAlchemyContent.STATUS_MENU.get() != null) apparatus++;
        if (LotmAlchemyContent.ALCHEMY_RECIPE_TYPE.get() != null) apparatus++;
        if (LotmAlchemyContent.ALCHEMY_RECIPE_SERIALIZER.get() != null) apparatus++;
        passed += report(player, "alchemy", apparatus, 7);
        total += 7;

        int manualRecipes = 0;
        try {
            manualRecipes = (int) player.serverLevel().getRecipeManager()
                    .getAllRecipesFor(LotmAlchemyContent.ALCHEMY_RECIPE_TYPE.get()).stream()
                    .filter(holder -> holder.value().getResultItem(player.serverLevel().registryAccess())
                            .getItem() instanceof LotmSkillBookItem)
                    .count();
        } catch (RuntimeException ignored) {
            // Report this as a failed recipe check; do not interrupt the player's session.
        }
        passed += report(player, "manual_recipes", Math.min(16, manualRecipes), 16);
        total += 16;

        int configChecks = 0;
        if (LotmConfig.SKILL_MASTERY_CAP.get() >= 1 && LotmConfig.SKILL_MASTERY_CAP.get() <= 2) configChecks++;
        if (LotmConfig.SKILL_COST_PERCENT.get() >= 25 && LotmConfig.SKILL_COST_PERCENT.get() <= 200) configChecks++;
        if (LotmConfig.ALCHEMY_SKILL_MANUAL_TICKS.get() >= 20
                && LotmConfig.ALCHEMY_SKILL_MANUAL_TICKS.get() <= 2400) configChecks++;
        if (LotmConfig.HUD_REFRESH_TICKS.get() >= 20 && LotmConfig.HUD_REFRESH_TICKS.get() <= 200) configChecks++;
        if (LotmConfig.APPEARANCE_INTERVAL_TICKS.get() >= 10
                && LotmConfig.APPEARANCE_INTERVAL_TICKS.get() <= 200) configChecks++;
        if (LotmConfig.MATERIAL_DROP_CHANCE_PERCENT.get() >= 0
                && LotmConfig.MATERIAL_DROP_CHANCE_PERCENT.get() <= 100) configChecks++;
        passed += report(player, "config", configChecks, 6);
        total += 6;

        LotmPath path = LotmPath.of(player);
        if (path == null) {
            player.displayClientMessage(Component.translatable(
                    "message.realscale.lotm.diagnose.no_path"), false);
        } else {
            int sequence = LotmState.sequence(player);
            int masteryCap = sequence == 9 ? 1 : LotmConfig.SKILL_MASTERY_CAP.get();
            int playerChecks = 0;
            if (sequence == 8 || sequence == 9) playerChecks++;
            int firstMastery = LotmState.skillMastery(player, path, 1);
            int secondMastery = LotmState.skillMastery(player, path, 2);
            if (firstMastery >= 0 && firstMastery <= masteryCap
                    && secondMastery >= 0 && secondMastery <= masteryCap) playerChecks++;
            int firstCost = LotmBalance.skillCostWithMastery(
                    LotmBalance.skillCost(path.id(), 1, sequence,
                            LotmCurios.hasDivinationPendulum(player), LotmState.instability(player)),
                    firstMastery, LotmConfig.SKILL_COST_PERCENT.get());
            int secondCost = LotmBalance.skillCostWithMastery(
                    LotmBalance.skillCost(path.id(), 2, sequence,
                            LotmCurios.hasDivinationPendulum(player), LotmState.instability(player)),
                    secondMastery, LotmConfig.SKILL_COST_PERCENT.get());
            if (firstCost > 0 && firstCost < LotmBalance.MAX_SPIRIT
                    && secondCost > 0 && secondCost < LotmBalance.MAX_SPIRIT) playerChecks++;
            if (LotmState.spirit(player) >= 0 && LotmState.spirit(player) <= LotmBalance.MAX_SPIRIT) playerChecks++;
            passed += report(player, "player", playerChecks, 4);
            total += 4;
            player.displayClientMessage(Component.translatable(
                    "message.realscale.lotm.diagnose.player_detail",
                    Component.translatable("lotm.realscale.path." + path.id()), sequence,
                    firstMastery, masteryCap, secondMastery, masteryCap, firstCost, secondCost), false);
        }

        int failures = total - passed;
        player.displayClientMessage(Component.translatable(
                "message.realscale.lotm.diagnose.summary", passed, total, failures), false);
        player.displayClientMessage(Component.translatable(
                "message.realscale.lotm.diagnose.scope"), false);
        return failures == 0 ? 1 : 0;
    }

    private static int report(ServerPlayer player, String section, int actual, int expected) {
        player.displayClientMessage(Component.translatable(
                "message.realscale.lotm.diagnose.section",
                Component.translatable("message.realscale.lotm.diagnose." + section),
                Math.min(actual, expected), expected), false);
        return Math.min(actual, expected);
    }

    private static int countBlocks(List<Supplier<Block>> suppliers) {
        int count = 0;
        for (Supplier<Block> supplier : suppliers) if (registeredBlock(supplier)) count++;
        return count;
    }

    private static int countItems(List<? extends Supplier<? extends Item>> suppliers) {
        int count = 0;
        for (Supplier<? extends Item> supplier : suppliers) if (registeredItem(supplier)) count++;
        return count;
    }

    private static boolean registeredBlock(Supplier<Block> supplier) {
        try {
            Block block = supplier == null ? null : supplier.get();
            if (block == null || block == Blocks.AIR) return false;
            var key = BuiltInRegistries.BLOCK.getKey(block);
            return key != null && "realscale".equals(key.getNamespace());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean registeredItem(Supplier<? extends Item> supplier) {
        try {
            Item item = supplier == null ? null : supplier.get();
            if (item == null || item == Items.AIR) return false;
            var key = BuiltInRegistries.ITEM.getKey(item);
            return key != null && "realscale".equals(key.getNamespace());
        } catch (RuntimeException exception) {
            return false;
        }
    }
}

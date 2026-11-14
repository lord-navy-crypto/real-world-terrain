"""LOTM RPG botany, discovery and anti-farming source/resource acceptance checks.

These are static contracts, not a replacement for an actual client save-world playtest.
"""
from __future__ import annotations

import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "neoforge/src/main/resources/data/realscale/recipe"
ASSETS = ROOT / "neoforge/src/main/resources/assets/realscale/lang"
JAVA = ROOT / "neoforge/src/main/java/org/realscale/neoforge"
PLANT_RECIPES = {
    "lotm_spirit_ember_midnight_bloom": ("lotm_midnight_bloom", "lotm_spirit_ember", 3),
    "lotm_occult_catalyst_face_rose": ("lotm_face_rose", "lotm_occult_catalyst", 3),
    "lotm_occult_catalyst_soul_bellflower": ("lotm_soul_bellflower", "lotm_occult_catalyst", 3),
    "lotm_spirit_ember_blood_rose": ("lotm_blood_rose", "lotm_spirit_ember", 4),
}
GUIDE_SECTIONS = ("creatures", "flora", "minerals", "alchemy", "progression")


class LotmRpgExpansionTests(unittest.TestCase):
    def test_four_existing_supernatural_flowers_supply_distinct_optional_material_routes(self):
        for name, (flower, output, quantity) in PLANT_RECIPES.items():
            with self.subTest(recipe=name):
                recipe = json.loads((DATA / (name + ".json")).read_text(encoding="utf-8"))
                self.assertEqual("minecraft:crafting_shapeless", recipe["type"])
                self.assertEqual("misc", recipe["category"])
                self.assertIn("realscale:" + flower,
                              {entry["item"] for entry in recipe["ingredients"]})
                self.assertGreaterEqual(len(recipe["ingredients"]), 3)
                self.assertEqual("realscale:" + output, recipe["result"]["id"])
                self.assertEqual(quantity, recipe["result"]["count"])
                self.assertNotIn("s7", json.dumps(recipe).lower())
        # The normal material routes are additive, never deleted/replaced.
        for name in ("lotm_occult_catalyst", "lotm_spirit_ember",
                     "lotm_spirit_focus_lunar", "lotm_spirit_ember_abyssal",
                     "lotm_occult_catalyst_echo"):
            self.assertTrue((DATA / (name + ".json")).exists(), name)

    def test_fieldwork_uses_persistent_world_provenance_for_all_six_active_ores(self):
        acting = (JAVA / "lotm/LotmActing.java").read_text(encoding="utf-8")
        provenance = (JAVA / "lotm/LotmResourceProvenance.java").read_text(encoding="utf-8")
        mod = (JAVA / "RealScaleMod.java").read_text(encoding="utf-8")
        self.assertIn("LotmActing::onBlockPlace", mod)
        self.assertIn("LotmActing::onBlockBreak", mod)
        self.assertIn("SavedData", provenance)
        self.assertIn("computeIfAbsent", provenance)
        self.assertIn("setDirty()", provenance)
        self.assertIn("LotmResourceProvenance.get(level).markPlaced", acting)
        self.assertIn("LotmResourceProvenance.get(level).consumePlacement", acting)
        for ore in ("STAR_ORE", "CURSED_ORE", "GRAVE_ORE",
                    "LUNAR_MICA_ORE", "ECHO_QUARTZ_ORE", "ABYSSAL_SALT_ORE"):
            self.assertIn("LotmContent." + ore + ".get()", acting)

    def test_explosions_prune_placed_resource_provenance(self):
        acting = (JAVA / "lotm/LotmActing.java").read_text(encoding="utf-8")
        provenance = (JAVA / "lotm/LotmResourceProvenance.java").read_text(encoding="utf-8")
        mod = (JAVA / "RealScaleMod.java").read_text(encoding="utf-8")
        self.assertIn("ExplosionEvent.Detonate", acting)
        self.assertIn("getAffectedBlocks()", acting)
        self.assertIn("consumePlacements", acting)
        self.assertIn("public int consumePlacements(Iterable<BlockPos> positions)", provenance)
        self.assertIn("LotmActing::onExplosionDetonate", mod)

    def test_research_has_novel_clues_per_pathway_and_sequence_and_uses_server_game_clock(self):
        acting = (JAVA / "lotm/LotmActing.java").read_text(encoding="utf-8")
        skills = (JAVA / "lotm/LotmSkills.java").read_text(encoding="utf-8")
        commands = (JAVA / "lotm/LotmCommands.java").read_text(encoding="utf-8")
        self.assertIn("RESEARCH_PREFIX", acting)
        self.assertIn("StringTag.valueOf(sourceId)", acting)
        self.assertIn("if (seen.contains(id)) return 0;", acting)
        self.assertIn("RESEARCH_PREFIX + suffix", acting)
        self.assertIn("overworld().getGameTime()", acting)
        self.assertIn("recordResearchDiscovery(player, entityClue(target), 2)", skills)
        self.assertIn("recordResearchDiscovery(player, blockClue(level, pos), 2)", skills)
        self.assertNotIn("recordResearch(player, Math.min(4, detected))", skills)
        self.assertIn('Commands.literal("journal")', commands)
        for section in GUIDE_SECTIONS:
            self.assertIn('Commands.literal("' + section + '")', commands)

    def test_curios_focus_items_have_exclusive_real_gameplay_effects_and_bilingual_tooltips(self):
        curios = (JAVA / "lotm/LotmCurios.java").read_text(encoding="utf-8")
        balance = (JAVA / "lotm/LotmBalance.java").read_text(encoding="utf-8")
        events = (JAVA / "lotm/LotmEvents.java").read_text(encoding="utf-8")
        skills = (JAVA / "lotm/LotmSkills.java").read_text(encoding="utf-8")
        focus = (JAVA / "lotm/LotmFocusItem.java").read_text(encoding="utf-8")
        pendulum = (JAVA / "lotm/LotmDivinationItem.java").read_text(encoding="utf-8")
        slot = json.loads((ROOT / "neoforge/src/main/resources/data/realscale/curios/slots/occult_focus.json").read_text(encoding="utf-8"))
        item_tag = json.loads((ROOT / "neoforge/src/main/resources/data/curios/tags/item/occult_focus.json").read_text(encoding="utf-8"))
        self.assertEqual(1, slot["size"])
        self.assertEqual({"realscale:lotm_spirit_focus", "realscale:lotm_divination_pendulum"}, set(item_tag["values"]))
        self.assertIn("CuriosApi.getCuriosInventory", curios)
        self.assertIn('getCurios().get("occult_focus")', curios)
        self.assertIn("getStacks()", curios)
        self.assertIn("LotmCurios.hasSpiritFocus(player)", events)
        self.assertIn("LotmCurios.hasDivinationPendulum(player)", skills)
        commands = (JAVA / "lotm/LotmCommands.java").read_text(encoding="utf-8")
        self.assertIn("message.realscale.lotm.status.accessory", commands)
        self.assertIn("LotmBalance.instabilitySurcharge(LotmState.instability(player))", commands)
        self.assertIn("spiritRecovery(int configuredAmount, boolean hasSpiritFocus)", balance)
        self.assertIn("divinationRadius(int sequence, boolean hasPendulum)", balance)
        self.assertIn("appendHoverText", focus)
        self.assertIn("appendHoverText", pendulum)
        for locale in ("en_us", "zh_cn"):
            lang = json.loads((ASSETS / (locale + ".json")).read_text(encoding="utf-8"))
            for key in ("spirit_focus.effect", "divination_pendulum.effect", "curio_exclusive_slot"):
                self.assertTrue(lang["tooltip.realscale.lotm." + key])


    def test_direct_characteristic_instability_is_reversible_and_balanced(self):
        state = (JAVA / "lotm/LotmState.java").read_text(encoding="utf-8")
        balance = (JAVA / "lotm/LotmBalance.java").read_text(encoding="utf-8")
        skills = (JAVA / "lotm/LotmSkills.java").read_text(encoding="utf-8")
        sigil = (JAVA / "lotm/LotmStabilitySigilItem.java").read_text(encoding="utf-8")
        content = (JAVA / "lotm/LotmContent.java").read_text(encoding="utf-8")
        recipe = json.loads((DATA / "lotm_stability_sigil.json").read_text(encoding="utf-8"))
        model = json.loads((ROOT / "neoforge/src/main/resources/assets/realscale/models/item/lotm_stability_sigil.json").read_text(encoding="utf-8"))
        generator = (ROOT / "scripts/generate_lotm_visuals.py").read_text(encoding="utf-8")
        self.assertIn("if (direct) player.getPersistentData().putInt(INSTABILITY_KEY, 1)", state)
        self.assertIn("reduceInstability(Player player, int amount)", state)
        self.assertIn("instabilitySurcharge(int instability)", balance)
        self.assertIn("LotmState.instability(player)", skills)
        self.assertIn("LotmState.reduceInstability(player, 1)", sigil)
        self.assertIn("STABILITY_SIGIL", content)
        self.assertEqual("minecraft:crafting_shapeless", recipe["type"])
        self.assertEqual("realscale:lotm_stability_sigil", recipe["result"]["id"])
        self.assertEqual(1, recipe["result"]["count"])
        self.assertEqual("realscale:item/lotm_stability_sigil", model["textures"]["layer0"])
        self.assertIn('"lotm_stability_sigil"', generator)
        for locale in ("en_us", "zh_cn"):
            lang = json.loads((ASSETS / (locale + ".json")).read_text(encoding="utf-8"))
            self.assertTrue(lang["item.realscale.lotm_stability_sigil"])
            self.assertTrue(lang["tooltip.realscale.lotm.stability_sigil.effect"])
            self.assertTrue(lang["message.realscale.lotm.stability_sigil.restored"])


    def test_potion_tooltips_explain_pathway_and_both_advancement_requirements(self):
        potion = (JAVA / "lotm/LotmPotionItem.java").read_text(encoding="utf-8")
        self.assertIn("appendHoverText", potion)
        self.assertIn("POTION_PRACTICE.get()", potion)
        self.assertIn("CHARACTERISTIC_PRACTICE.get()", potion)
        for locale in ("en_us", "zh_cn"):
            lang = json.loads((ASSETS / (locale + ".json")).read_text(encoding="utf-8"))
            for key in ("path", "rank", "characteristic", "direct_risk",
                        "characteristic_requirement", "first_potion", "prepared",
                        "prepared_requirement", "first_characteristic"):
                self.assertTrue(lang["tooltip.realscale.lotm.potion." + key])


    def test_sequence8_pathway_passives_are_distinct_and_documented(self):
        events = (JAVA / "lotm/LotmEvents.java").read_text(encoding="utf-8")
        self.assertIn("path == LotmPath.SEER && sequence == 8", events)
        self.assertIn("MobEffects.SLOW_FALLING", events)
        self.assertIn("path == LotmPath.MYSTERY_PRYER && sequence == 8", events)
        self.assertIn("LightLayer.BLOCK", events)
        self.assertIn("MobEffects.NIGHT_VISION", events)
        for locale in ("en_us", "zh_cn"):
            lang = json.loads((ASSETS / (locale + ".json")).read_text(encoding="utf-8"))
            seer = lang["message.realscale.lotm.skills.seer.8"].lower()
            pryer = lang["message.realscale.lotm.skills.mystery_pryer.8"].lower()
            self.assertTrue("fall" in seer or "下坠" in seer)
            self.assertTrue("night vision" in pryer or "夜视" in pryer)


    def test_bilingual_discovery_and_field_guide_content_is_complete(self):
        for locale in ("en_us", "zh_cn"):
            lang = json.loads((ASSETS / (locale + ".json")).read_text(encoding="utf-8"))
            for key in ("journal.header", "journal.entry", "journal.empty", "journal.more"):
                self.assertTrue(lang["message.realscale.lotm." + key])
            for section in GUIDE_SECTIONS:
                self.assertTrue(lang["message.realscale.lotm.guide." + section])
            self.assertIn("/lotm journal", lang["message.realscale.lotm.guide"])


    def test_alchemy_output_capacity_rejects_overstacked_recipe_results(self):
        balance = (JAVA / "lotm/LotmBalance.java").read_text(encoding="utf-8")
        machine = (JAVA / "lotm/LotmAlchemyBlockEntity.java").read_text(encoding="utf-8")
        self.assertIn("canFitAlchemyOutput(boolean outputEmpty, boolean sameItemAndComponents", balance)
        self.assertIn("long combined = (long) outputCount + resultCount", balance)
        self.assertIn("LotmBalance.canFitAlchemyOutput(output.isEmpty()", machine)
        self.assertIn("result.getCount(), result.getMaxStackSize()", machine)

    def test_alchemy_tick_revalidates_loaded_fuel_item_identity(self):
        machine = (JAVA / "lotm/LotmAlchemyBlockEntity.java").read_text(encoding="utf-8")
        self.assertIn("isItemValid(int slot, ItemStack stack)", machine)
        self.assertIn("stack.is(LotmContent.SPIRIT_EMBER.get())", machine)
        self.assertIn("!machine.inventory.getStackInSlot(FUEL_SLOT).is(LotmContent.SPIRIT_EMBER.get())", machine)
        self.assertIn("extractItem(FUEL_SLOT, 1, false)", machine)

    def test_alchemy_machine_status_is_server_synced_and_localized(self):
        machine = (JAVA / "lotm/LotmAlchemyBlockEntity.java").read_text(encoding="utf-8")
        menu = (JAVA / "lotm/LotmAlchemyMenu.java").read_text(encoding="utf-8")
        screen = (JAVA / "lotm/client/LotmAlchemyScreen.java").read_text(encoding="utf-8")
        for status in ("STATUS_NO_RECIPE", "STATUS_BREWING", "STATUS_WAITING_FUEL",
                       "STATUS_OUTPUT_BLOCKED", "STATUS_DISABLED", "STATUS_UNAVAILABLE"):
            self.assertIn(status, machine)
        self.assertIn("@Override public int getCount() { return 3; }", machine)
        self.assertIn("new SimpleContainerData(3)", menu)
        self.assertIn("checkContainerDataCount(data, 3)", menu)
        self.assertIn("getMachineStatus()", menu)
        self.assertIn("gui.realscale.lotm_alchemy.status.", screen)
        for locale in ("en_us", "zh_cn"):
            lang = json.loads((ASSETS / (locale + ".json")).read_text(encoding="utf-8"))
            for index in range(6):
                self.assertTrue(lang["gui.realscale.lotm_alchemy.status." + str(index)])

    def test_skills_command_shows_live_effective_cost_and_cooldown(self):
        commands = (JAVA / "lotm/LotmCommands.java").read_text(encoding="utf-8")
        self.assertIn('"message.realscale.lotm.skills.cost"', commands)
        self.assertIn("LotmBalance.skillCooldownTicks(slot, sequence)", commands)
        self.assertIn("LotmCurios.hasDivinationPendulum(player)", commands)
        self.assertIn("LotmState.instability(player)", commands)
        for locale in ("en_us", "zh_cn"):
            lang = json.loads((ASSETS / (locale + ".json")).read_text(encoding="utf-8"))
            self.assertTrue(lang["message.realscale.lotm.skills.cost"])

    def test_unauthorized_ability_audit_is_bounded_but_grant_event_remains_immediate(self):
        sync = (JAVA / "lotm/LotmPlayerAbilitySync.java").read_text(encoding="utf-8")
        events = (JAVA / "lotm/LotmEvents.java").read_text(encoding="utf-8")
        self.assertIn("Math.floorMod(tickCount, 20) == 0", sync)
        self.assertIn("periodicSync(player)", events)
        self.assertIn("public static void periodicSync(ServerPlayer player)", sync)
        self.assertIn("sync(player);", sync[sync.index("public static void periodicSync"):sync.index("public static void sanitizeUnauthorizedGrants")])
        self.assertIn("sanitizeUnauthorizedGrants(player);", sync)
        self.assertIn("public static void onAbilityGranted(AbilityGrantedEvent event)", sync)
        self.assertIn("AbilityAPI.revoke(player, event.getSource(), event.getAbility())", sync)
        self.assertIn("sync(ServerPlayer player)", sync)

    def test_resource_provenance_respects_cancellable_placement_and_explosion_lifecycle(self):
        acting = (JAVA / "lotm/LotmActing.java").read_text(encoding="utf-8")
        start = acting.index("public static void onBlockPlace")
        end = acting.index("public static void onBlockBreak", start)
        place_handler = acting[start:end]
        self.assertIn("event.isCanceled()", place_handler)
        self.assertIn("markPlaced(event.getPos())", place_handler)
        self.assertLess(place_handler.index("event.isCanceled()"), place_handler.index("markPlaced(event.getPos())"))
        explosion_start = acting.index("public static void onExplosionDetonate")
        explosion_end = acting.index("static boolean isFieldworkBlock", explosion_start)
        explosion_handler = acting[explosion_start:explosion_end]
        self.assertIn("ExplosionEvent.Start is the cancellable phase", acting[:explosion_start])
        self.assertIn("consumePlacements(event.getAffectedBlocks())", explosion_handler)
        self.assertNotIn("event.isCanceled()", explosion_handler,
                         "Detonate is not cancellable; Start cancellation prevents it from firing.")
        mod = (JAVA / "RealScaleMod.java").read_text(encoding="utf-8")
        self.assertIn("import net.neoforged.bus.api.EventPriority;", mod)
        self.assertIn("addListener(EventPriority.LOWEST, true, LotmActing::onBlockPlace)", mod)
        self.assertIn("addListener(EventPriority.LOWEST, true, LotmActing::onBlockBreak)", mod)

    def test_dedicated_server_smoke_summons_all_active_creature_types(self):
        smoke = (ROOT / "qa/neoforge/rcon_usgs_center_smoke.py").read_text(encoding="utf-8")
        kinds = (JAVA / "lotm/LotmCreatureKind.java").read_text(encoding="utf-8")
        self.assertIn("def verify_lotm_creature_registry(rcon: Rcon)", smoke)
        self.assertIn("data get entity @e[type={entity_type},sort=nearest,limit=1] Health", smoke)
        self.assertIn("verify_lotm_creature_registry(rcon)", smoke)
        for creature_id in ("starlit_squid", "nightmare_eye", "night_owl",
                            "black_spotted_frog", "shroudman", "hornacis_goat",
                            "ironarm_beast", "four_eyed_turtle", "crimson_howler",
                            "death_crow"):
            self.assertIn('"' + creature_id + '"', smoke)
            self.assertIn('"' + creature_id + '"', kinds)

    def test_actionbar_hud_is_opt_in_server_authoritative_and_bilingual(self):
        hud = (JAVA / "lotm/LotmHud.java").read_text(encoding="utf-8")
        events = (JAVA / "lotm/LotmEvents.java").read_text(encoding="utf-8")
        commands = (JAVA / "lotm/LotmCommands.java").read_text(encoding="utf-8")
        self.assertIn("displayClientMessage(line, true)", hud)
        self.assertIn("LotmHud.tick(player)", events)
        self.assertIn("LotmHud.toggle(player)", commands)
        for locale in ("en_us", "zh_cn"):
            lang = json.loads((ASSETS / (locale + ".json")).read_text(encoding="utf-8"))
            self.assertIn("/lotm hud", lang["message.realscale.lotm.guide"])
        self.assertIn("LotmHud.copy(original, clone)", (JAVA / "lotm/LotmState.java").read_text(encoding="utf-8"))
        for locale in ("en_us", "zh_cn"):
            lang = json.loads((ASSETS / (locale + ".json")).read_text(encoding="utf-8"))
            for key in ("message.realscale.lotm.hud.enabled",
                        "message.realscale.lotm.hud.disabled",
                        "message.realscale.lotm.hud.line"):
                self.assertTrue(lang[key])

    def test_creatures_have_noncombat_schooling_and_pack_regrouping(self):
        behavior = (JAVA / "lotm/LotmCreatureBehavior.java").read_text(encoding="utf-8")
        creature = (JAVA / "lotm/LotmCreature.java").read_text(encoding="utf-8")
        self.assertIn("followsSameKind(kind)", creature)
        self.assertIn("new FollowSameKindGoal(this", creature)
        self.assertIn("other.kind() == creature.kind()", creature)
        self.assertIn("creature.getTarget() != null", creature)
        self.assertIn("LotmCreatureKind.STARLIT_SQUID", behavior)
        self.assertIn("LotmCreatureKind.CRIMSON_HOWLER", behavior)

    def test_unknown_material_drop_ids_fail_closed(self):
        content = (JAVA / "lotm/LotmContent.java").read_text(encoding="utf-8")
        events = (JAVA / "lotm/LotmEvents.java").read_text(encoding="utf-8")
        start = content.index("public static Supplier<Item> materialItem")
        end = content.index("public static Supplier<? extends Item> characteristic", start)
        lookup = content[start:end]
        self.assertIn("return suffix == null ? null : MATERIALS.get(suffix)", lookup)
        self.assertIn("if (material != null)", events)

    def test_lotm_config_command_reports_all_gameplay_controls_bilingually(self):
        commands = (JAVA / "lotm/LotmCommands.java").read_text(encoding="utf-8")
        start = commands.index('Commands.literal("config")')
        end = commands.index('Commands.literal("guide")', start)
        config = commands[start:end]
        for setting in ("ENABLED", "CREATURE_ENTITIES", "CREATURE_VARIANTS", "RELIC_SITES",
                        "POTION_PRACTICE", "CHARACTERISTIC_PRACTICE", "SPIRIT_RECOVERY",
                        "DIGESTION_REWARD_PERCENT", "ALCHEMY_SEQUENCE9_TICKS",
                        "ALCHEMY_SEQUENCE8_TICKS", "VARIANT_RARITY_PERCENT", "RELIC_RARITY"):
            self.assertIn("LotmConfig." + setting + ".get()", config)
        for locale in ("en_us", "zh_cn"):
            lang = json.loads((ASSETS / (locale + ".json")).read_text(encoding="utf-8"))
            for suffix in ("", ".progression", ".alchemy", ".ecology"):
                self.assertTrue(lang["message.realscale.lotm.config" + suffix])

    def test_progression_transition_revalidates_before_mutating_player_state(self):
        state = (JAVA / "lotm/LotmState.java").read_text(encoding="utf-8")
        potion = (JAVA / "lotm/LotmPotionItem.java").read_text(encoding="utf-8")
        preflight_start = state.index("public static String validateAdvance")
        start = state.index("public static String advance", preflight_start)
        end = state.index("public static int sequence", start)
        preflight = state[preflight_start:start]
        transition = state[start:end]
        self.assertIn("LotmProgression.validate", preflight)
        self.assertIn("String validation = validateAdvance(player, path, sequence, direct);", transition)
        self.assertLess(transition.index("validateAdvance(player, path, sequence, direct)"),
                        transition.index("tag.putInt(LotmPath.NBT_SEQUENCE, sequence)"))
        self.assertIn("LotmState.validateAdvance(player, pathway, sequence, characteristic)", potion)
        self.assertIn("LotmState.advance(player, pathway, sequence, characteristic)", potion)
        self.assertNotIn("LotmProgression.validate", potion)
        for locale in ("en_us", "zh_cn"):
            lang = json.loads((ASSETS / (locale + ".json")).read_text(encoding="utf-8"))
            self.assertTrue(lang["message.realscale.lotm.advance.disabled"])

    def test_periodic_ability_sync_reconciles_grants_after_live_config_changes(self):
        sync = (JAVA / "lotm/LotmPlayerAbilitySync.java").read_text(encoding="utf-8")
        events = (JAVA / "lotm/LotmEvents.java").read_text(encoding="utf-8")
        start = sync.index("public static void periodicSync")
        end = sync.index("public static void sanitizeUnauthorizedGrants", start)
        periodic = sync[start:end]
        self.assertIn("shouldAuditUnauthorizedGrants(player.tickCount)", periodic)
        self.assertIn("sync(player)", periodic)
        self.assertIn("LotmPlayerAbilitySync.periodicSync(player)", events)
        self.assertNotIn("if (player.tickCount % 20 == 0) LotmPlayerAbilitySync.sync(player)", events)

    def test_pathway_ability_precheck_uses_the_same_curios_and_instability_cost(self):
        ability = (JAVA / "lotm/LotmPathwayAbility.java").read_text(encoding="utf-8")
        skills = (JAVA / "lotm/LotmSkills.java").read_text(encoding="utf-8")
        expected = "LotmBalance.skillCost(pathway.id(), slot, sequence,"
        self.assertIn(expected, ability)
        self.assertIn("LotmCurios.hasDivinationPendulum(player)", ability)
        self.assertIn("LotmState.instability(player)", ability)
        self.assertIn("LotmBalance.skillCost(expectedPath.id(), slot, sequence,", skills)
        self.assertIn("LotmCurios.hasDivinationPendulum(player)", skills)
        self.assertIn("LotmState.instability(player)", skills)

    def test_spirit_practice_and_daily_acting_arithmetic_are_overflow_safe(self):
        balance = (JAVA / "lotm/LotmBalance.java").read_text(encoding="utf-8")
        acting = (JAVA / "lotm/LotmActing.java").read_text(encoding="utf-8")
        self.assertIn("private static int boundedAdd(int current, int amount, int maximum)", balance)
        self.assertIn("long total = (long) Math.max(0, current) + Math.max(0, amount)", balance)
        self.assertIn("clampDailyUsed(stored, cap)", acting)
        self.assertIn("long scaled = (long) points * percent / 100L", acting)
        self.assertIn("scaledReward(points, LotmConfig.DIGESTION_REWARD_PERCENT.get(), cap - used)", acting)

    def test_instability_is_bounded_and_status_explains_advancement_requirements(self):
        balance = (JAVA / "lotm/LotmBalance.java").read_text(encoding="utf-8")
        state = (JAVA / "lotm/LotmState.java").read_text(encoding="utf-8")
        commands = (JAVA / "lotm/LotmCommands.java").read_text(encoding="utf-8")
        self.assertIn("MAX_INSTABILITY = 100", balance)
        self.assertIn("addInstability(int current, int amount)", balance)
        self.assertIn("clampInstability(instability) * 2", balance)
        self.assertIn("LotmBalance.addInstability(tag.getInt(INSTABILITY_KEY), 1)", state)
        self.assertIn("LotmBalance.clampInstability(stored)", state)
        self.assertIn("status.digestion_guide", commands)
        self.assertIn("status.archived", commands)
        for locale in ("en_us", "zh_cn"):
            lang = json.loads((ASSETS / (locale + ".json")).read_text(encoding="utf-8"))
            self.assertTrue(lang["message.realscale.lotm.status.digestion_guide"])
            self.assertTrue(lang["message.realscale.lotm.status.archived"])



    def test_occult_panel_has_branching_skill_tree_live_journal_and_read_only_settings(self):
        screen = (JAVA / "lotm/client/LotmStatusScreen.java").read_text(encoding="utf-8")
        network = (JAVA / "lotm/LotmPanelNetwork.java").read_text(encoding="utf-8")
        acting = (JAVA / "lotm/LotmActing.java").read_text(encoding="utf-8")
        self.assertIn("skill_tree.node.s9", screen)
        self.assertIn("skill_tree.node.locked", screen)
        self.assertIn("JOURNAL_PAGE_SIZE = 7", screen)
        self.assertIn("LotmPanelNetwork.clientResearchEntries()", screen)
        self.assertIn("journalPageCount()", screen)
        self.assertIn("ResearchLogPayload", network)
        self.assertIn("PacketDistributor.sendToPlayer", network)
        self.assertIn("playToClient(ResearchLogPayload.TYPE", network)
        self.assertIn("MAX_RESEARCH_JOURNAL_ENTRIES = 128", acting)
        self.assertIn("researchJournalKey(path)", acting)
        self.assertIn("loadResearchJournal(player, path)", acting)
        self.assertIn("Collections.reverse(latestFirst)", network)
        self.assertIn("ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(64))", network)
        session = (JAVA / "lotm/client/LotmClientSessionEvents.java").read_text(encoding="utf-8")
        self.assertIn("ClientPlayerNetworkEvent.LoggingIn", session)
        self.assertIn("ClientPlayerNetworkEvent.LoggingOut", session)
        self.assertIn("clearClientResearchLog()", session)
        self.assertIn("LotmPanelNetwork.syncResearchLog(serverPlayer)", acting)
        self.assertIn("settings.server_header", screen)
        self.assertIn("settings.readonly_note", screen)
        self.assertIn("renderJournalEntryTooltip(graphics, mouseX, mouseY)", screen)
        self.assertIn("activeSequence && menu.skillsEnabled()", screen)
        self.assertIn("boolean treeActive = menu.skillsEnabled()", screen)
        self.assertIn("treeActive && menu.sequence() == 8", screen)
        self.assertEqual(2, screen.count("LotmActing.dailyCap(categories[index])"))
        for locale in ("en_us", "zh_cn"):
            lang = json.loads((ASSETS / (locale + ".json")).read_text(encoding="utf-8"))
            for key in ("skill_tree.header", "journal.entry", "journal.empty",
                        "journal.page", "settings.personal_header",
                        "settings.server_header", "settings.readonly_note"):
                self.assertTrue(lang["gui.realscale.lotm_panel." + key])


if __name__ == "__main__":
    unittest.main()

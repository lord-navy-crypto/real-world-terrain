#!/usr/bin/env python3
"""Static contract gates for the experimental navigable-river + hovercraft feature."""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "neoforge/src/main"
SRC = ROOT / "java/org/realscale/neoforge"
RES = ROOT / "resources"


class NavigationFeatureContracts(unittest.TestCase):
    def test_hovercraft_recipe_is_1_boat_plus_4_leather(self):
        r = json.loads((RES / "data/realscale/recipe/hovercraft.json").read_text())
        self.assertEqual(r["type"], "minecraft:crafting_shaped")
        self.assertEqual(r["pattern"], [" L ", "LBL", " L "])
        self.assertEqual(r["key"]["B"], {"tag": "minecraft:boats"})
        self.assertEqual(r["key"]["L"], {"item": "minecraft:leather"})
        self.assertEqual(r["result"]["id"], "realscale:hovercraft")

    def test_navigation_channel_uses_open_water_and_protected_seabed(self):
        source = (SRC / "RiverChannelFeature.java").read_text()
        method = source.split("private static boolean tryOpenNavigableChannel(", 1)[1].split(
            "private static int riverGradeForWorld(", 1)[0]
        self.assertIn("top.below(3), Blocks.GRAVEL", method)
        self.assertIn("top.below(2), Blocks.WATER", method)
        self.assertIn("top.below(), Blocks.WATER", method)
        self.assertIn("top, frozen", method)
        self.assertNotIn("Blocks.DIRT.defaultBlockState()", method)
        self.assertIn("if (top.getY() - 3 <= floor) return false", method)
        self.assertIn("natural(level.getBlockState(top.below(3)))", method)
        self.assertIn("tryOpenNavigableChannel(", source)
        self.assertIn('if (!id.getNamespace().equals("realscale"))', source)
        self.assertIn("noise.getSeaLevel() + 6", source)

    def test_item_and_entity_are_registered_and_client_only_renderer(self):
        mod = (SRC / "RealScaleMod.java").read_text()
        reg = (SRC / "RealScaleVehicles.java").read_text()
        craft = (SRC / "HovercraftEntity.java").read_text()
        client = (SRC / "RealScaleHovercraftClient.java").read_text()
        item = (SRC / "HovercraftItem.java").read_text()
        self.assertIn("RealScaleVehicles.register(bus)", mod)
        self.assertIn('ENTITIES.register("hovercraft"', reg)
        self.assertIn('ITEMS.registerItem("hovercraft"', reg)
        self.assertIn("extends Boat", craft)
        self.assertIn("getFirstPassenger() == null", craft)
        self.assertIn("getFluidState(at.above()).is(FluidTags.WATER)", craft)
        self.assertIn("getPlayerPOVHitResult", item)
        self.assertIn("addFreshEntity", item)
        self.assertIn("Dist.CLIENT", client)
        self.assertIn("registerEntityRenderer", client)

    def test_localizations_and_asset(self):
        for lang in ("en_us", "zh_cn"):
            loc = json.loads((RES / f"assets/realscale/lang/{lang}.json").read_text())
            self.assertIn("item.realscale.hovercraft", loc)
            self.assertIn("entity.realscale.hovercraft", loc)
        model = json.loads((RES / "assets/realscale/models/item/hovercraft.json").read_text())
        self.assertEqual(model["textures"]["layer0"], "minecraft:item/oak_boat")


if __name__ == "__main__":
    unittest.main()

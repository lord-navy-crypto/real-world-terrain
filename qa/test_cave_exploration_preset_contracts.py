"""Keep experimental cave geometry strictly isolated from existing saved worlds."""
import json
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
J=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
D=ROOT/"neoforge/src/main/resources/data/realscale/worldgen"

class CaveExplorationPresetContracts(unittest.TestCase):
    def test_only_the_new_preset_enables_gallery_v2(self):
        noise=json.loads((D/"noise_settings/tva_cave_exploration.json").read_text())
        final=noise["noise_router"]["final_density"]
        self.assertEqual(final["type"],"realscale:grand_overworld_caverns")
        self.assertIs(final["gallery_v2"],True)
        self.assertEqual(noise["noise"]["height"],2096)
        self.assertEqual(noise["noise"]["min_y"],-64)
        for path in (D/"noise_settings").glob("*.json"):
            if path.name=="tva_cave_exploration.json":continue
            data=json.loads(path.read_text())
            node=data.get("noise_router",{}).get("final_density")
            if isinstance(node,dict):
                self.assertNotEqual(node.get("gallery_v2"),True,path.name)

    def test_new_world_keeps_three_realm_ids_and_old_world_stays_unchanged(self):
        world=json.loads((D/"world_preset/cave_exploration_three_realms.json").read_text())
        natural=json.loads((D/"world_preset/natural_three_realms.json").read_text())
        self.assertEqual(set(world["dimensions"]),{
            "minecraft:overworld","minecraft:the_nether","minecraft:the_end"})
        self.assertEqual(world["dimensions"]["minecraft:the_nether"],
                         natural["dimensions"]["minecraft:the_nether"])
        self.assertEqual(world["dimensions"]["minecraft:the_end"],
                         natural["dimensions"]["minecraft:the_end"])
        self.assertEqual(world["dimensions"]["minecraft:overworld"]["generator"]["settings"],
                         "realscale:tva_cave_exploration")
        self.assertEqual(natural["dimensions"]["minecraft:overworld"]["generator"]["settings"],
                         "realscale:tva_natural_smoothed")
        normal=json.loads((ROOT/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json").read_text())
        extended=json.loads((ROOT/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset/extended.json").read_text())
        self.assertNotIn("realscale:cave_exploration_three_realms",normal["values"])
        self.assertNotIn("realscale:cave_exploration_three_realms",extended["values"])

    def test_legacy_codec_defaults_off_and_extra_caves_have_safe_bounds(self):
        codec=(J/"GrandOverworldCaverns.java").read_text()
        geo=(J/"GrandOverworldCaveDesignV2.java").read_text()
        self.assertIn('Codec.BOOL.optionalFieldOf("gallery_v2",false)',codec)
        self.assertIn("galleryV2?computeExpanded(ctx):computeUnprofiled(ctx)",codec)
        self.assertIn("mayTouchColumn(x,y,z)",codec)
        self.assertIn("strength(wx,wy,wz)",codec)
        self.assertIn("MIN_EXTRA_Y=0,MAX_EXTRA_Y=1600",geo)
        self.assertIn("ThreadLocal.withInitial",geo)
        self.assertIn("geometryFitsInsideCell",geo)
        self.assertIn("tunnelStrength(",geo)
        workflow=(ROOT/".github/workflows/cave-exploration-world.yml").read_text()
        self.assertIn("Cave Exploration experimental saved-world generation",workflow)
        self.assertIn("cave_exploration_world_boot.py",workflow)

if __name__=="__main__":unittest.main()

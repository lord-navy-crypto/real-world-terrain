#!/usr/bin/env python3
"""Worldgen river channel registration, sea-level and save safety contracts."""
import json
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/"neoforge/src/main"
RES=ROOT/"resources"
SRC=ROOT/"java/org/realscale/neoforge"
ALL=("realism_max","georeal_ultimate","himalayan","grand_canyon",
     "usgs_big_sur","continental","realistic_mountains","ultimate")
class RiverRegression(unittest.TestCase):
    def test_sea_level_fixed_to_63_and_water_aquifers(self):
        for p in ALL:
            with self.subTest(preset=p):
                j=json.loads((RES/"data/realscale/worldgen/noise_settings"/(p+".json")).read_text())
                self.assertEqual(j["sea_level"],63)
                self.assertEqual(j["default_fluid"]["Name"],"minecraft:water")
                self.assertTrue(j["aquifers_enabled"])

    def test_safe_worldgen_feature_registration(self):
        f=json.loads((RES/"data/realscale/worldgen/configured_feature/river_channel.json").read_text())
        p=json.loads((RES/"data/realscale/worldgen/placed_feature/river_channel.json").read_text())
        b=json.loads((RES/"data/realscale/neoforge/biome_modifier/river_channel.json").read_text())
        self.assertEqual(f["type"],"realscale:river_channel")
        self.assertEqual(p["feature"],"realscale:river_channel")
        self.assertEqual(b["type"],"neoforge:add_features")
        self.assertEqual(set(b["biomes"]),{"minecraft:river","minecraft:frozen_river"})
        self.assertEqual(b["step"],"local_modifications")
        self.assertNotIn("minecraft:lake",json.dumps((f,p,b)))
        java=(SRC/"RiverChannelFeature.java").read_text()
        # A former blanket exclusion left all legacy USGS mountain rivers dry:
        # seed 2 measured 0 wet columns from 448 sampled River columns.
        # Other RealScale worldgen retains the existing bank/foundation guard.
        self.assertNotIn('id.getPath().equals("usgs_big_sur")',java)
        self.assertIn('if (!id.getNamespace().equals("realscale"))',java)
        self.assertIn('return 6;',java)
        self.assertIn('id.getNamespace().equals("realscale")',java)
        self.assertIn('new ChunkPos(context.origin())',java)
        self.assertIn('noise.getSeaLevel() + 6',java)
        self.assertIn('top.below(inset + 2), Blocks.GRAVEL',java)
        self.assertIn('top.below(inset + 1), Blocks.WATER',java)
        self.assertIn('top.below(5)',java)
        self.assertIn('enclosedByBanks(level, top, 1, maxGrade)',java)
        self.assertIn('enclosedByBanks(level, top, 2, maxGrade)',java)
        self.assertIn('Direction.Plane.HORIZONTAL',java)
        self.assertIn('neighbor.below(inset + 1)',java)
        self.assertIn('Math.abs(nextTop - top.getY()) > maxGrade',java)
        self.assertIn('riverGradeForWorld',java)
        self.assertIn('id.endsWith("_g" + grade)',java)
        self.assertIn('if (maxGrade <= 0) return false',java)
        self.assertIn('removableCover',java)
        self.assertIn('state.is(Blocks.SNOW)',java)
        self.assertIn('state.is(Blocks.SHORT_GRASS)',java)
        self.assertIn('covered ? surfaceY - 1 : surfaceY',java)
        # Reject construction materials and other non-natural supports:
        # motion-blocking alone is not proof of geological riverbed rock.
        self.assertIn('!natural(foundation) || !natural(deepFoundation)',java)
        self.assertNotIn('!deepFoundation.blocksMotion()',java)
        self.assertIn('solidBank(atWaterSurface)',java)
        self.assertIn('solidBank(atWaterBase)',java)
        self.assertIn('tryAdjacentSafeRiverPool(level, chunk, top, floor, maxGrade)',java)
        # Mountain overflow is an explicit design choice, NOT relaxed QA.
        # Two natural River-biome columns become one contiguous source reach.
        self.assertIn('tryHighlandOverflowReach(level, chunk, top, floor, maxGrade)',java)
        self.assertIn('private static final int MAX_HIGHLAND_REACHES = 8;',java)
        self.assertIn('private static final int MAX_OPEN_BANK_FACES = 3;',java)
        self.assertIn('highlandReaches < MAX_HIGHLAND_REACHES',java)
        self.assertIn('openFaces > MAX_OPEN_BANK_FACES',java)
        self.assertIn('Math.abs(first.getY() - second.getY()) > Math.min(3, maxGrade)',java)
        self.assertIn('BlockPos[] pair = {first, second}',java)
        self.assertIn('if (!valid) continue;',java)
        self.assertIn('for (int y = cell.getY(); y >= waterY - 2; y--)',java)
        self.assertIn('top.getY() >= Math.max(128, floor + 32)',java)
        self.assertIn('water.below(2), Blocks.GRAVEL',java)
        self.assertIn('water.below(), Blocks.WATER',java)
        self.assertIn('x < chunk.getMinBlockX()',java)
        self.assertIn('z > chunk.getMaxBlockZ()',java)
        self.assertIn('x < chunk.getMinBlockX()',java)
        self.assertIn('z > chunk.getMaxBlockZ()',java)
        self.assertIn('int waterY = Math.min(first.getY(), second.getY()) - 1',java)
        self.assertIn('BlockPos[] cells = {first, second}',java)
        self.assertIn('solidBank(upper)',java)
        self.assertIn('solidBank(lower)',java)
        self.assertIn('if (!enclosed) continue;',java)
        self.assertIn('level.setBlock(coverPos, Blocks.AIR',java)
        self.assertNotIn('state.is(Blocks.OAK_LEAVES)',java)
if __name__=="__main__":unittest.main()

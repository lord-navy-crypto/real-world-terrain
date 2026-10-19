#!/usr/bin/env python3
"""Fail-closed source invariants for native UI-only Chaotianmen placement.

Not a substitute for a real interactive client run. Java compile is tested
separately. This catches dangerous regressions when UI controls are refactored.
"""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
CLIENT=ROOT/"neoforge/src/main/java/org/realscale/neoforge/RealScaleChongqingGisClient.java"
LOCK=ROOT/"neoforge/src/main/java/org/realscale/neoforge/ChongqingPlacementLock.java"
PLAN=ROOT/"neoforge/src/main/java/org/realscale/neoforge/ChongqingGisPlan.java"


class NativeGisSafetyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.client=CLIENT.read_text(encoding="utf-8")
        cls.lock=LOCK.read_text(encoding="utf-8")
        cls.plan=PLAN.read_text(encoding="utf-8")

    def test_no_gameplay_command_calls_or_automatic_start(self):
        code=self.client
        self.assertIn("new GisScreen()",code)
        self.assertIn("Survey nearby terrain (NO writes)",code)
        self.assertIn("I backed up this NEW world: NO",code)
        self.assertIn("Start / Pause 256 blocks per tick",code)
        self.assertIn("onClose()",code)
        self.assertNotIn("performPrefixedCommand(",code)
        self.assertNotIn("performCommand(",code)
        self.assertNotIn("Commands.parse(",code)
        self.assertNotIn("level.setBlock(pos",code[:code.index("void applyTick()")])

    def test_world_identity_and_surface_checked_before_any_write(self):
        code=self.client
        self.assertIn('equals("realscale:special_chongqing_sky")',code)
        self.assertIn("player.level().dimension() != Level.OVERWORLD",code)
        self.assertIn("!player.getAbilities().instabuild",code)
        self.assertIn("getSingleplayerServer()",code)
        self.assertIn("Heightmap.Types.WORLD_SURFACE",code)
        self.assertIn("!level.getChunkSource().hasChunk(",code)
        apply=code[code.index("void applyTick()"):]
        self.assertLess(apply.index("terrainMatches(level,pos,old,target)"),
                        apply.index("ChongqingPlacementLock.claim("))
        self.assertLess(apply.index("ChongqingPlacementLock.claim("),
                        apply.index("level.setBlock(pos,target,3)"))
        self.assertIn("previewGood",code)

    def test_fail_closed_world_journal_and_provenance(self):
        lock=self.lock
        self.assertIn("StandardOpenOption.CREATE_NEW",lock)
        self.assertIn("Files.isSymbolicLink(worldRoot)",lock)
        self.assertIn("plan_sha256",lock)
        self.assertIn("osm_sha256",lock)
        self.assertIn("realscale-chaotianmen-gis-lock.json",lock)
        self.assertIn("MAX_WRITES = 5_000_000L",self.plan)
        self.assertIn("dem.metresAt(x, z)",self.plan)


if __name__=="__main__":
    unittest.main()

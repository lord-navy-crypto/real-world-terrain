#!/usr/bin/env python3
"""Source contract for bounded, seed-stable RealScale initial spawn selection."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"

class RealScaleSpawnProfileTests(unittest.TestCase):
    def test_math_helper_exposes_seeded_preset_quantile_contract(self):
        source=(JAVA/"RealScaleSpawnMath.java").read_text()
        for token in ("record Profile","profileFor(","targetQuantilePermille(",
                      "targetHeight(","sampledHeights","score(","isHeightAllowed("):
            self.assertIn(token,source)
        for token in ("alpine","canyon","glacial","volcanic","karst","dune","usgs"):
            self.assertIn(token,source.lower())

    def test_event_is_overworld_realscale_only_and_preserves_vanilla_fallback(self):
        source=(JAVA/"RealScaleSpawnPosition.java").read_text()
        for token in ("LevelEvent.CreateSpawnPosition","instanceof ServerLevel",
                      "Level.OVERWORLD","settingsHolder.unwrapKey()",
                      "RealScaleSpawnMath.profileFor","event.getSettings().setSpawn",
                      "event.setCanceled(true)"):
            self.assertIn(token,source)
        self.assertIn("if (best == null)",source)
        self.assertIn("keeping vanilla selection",source)

    def test_sampling_uses_base_terrain_without_forcing_chunks(self):
        source=(JAVA/"RealScaleSpawnPosition.java").read_text()
        for token in ("getBaseHeight(","getBaseColumn(","Heightmap.Types.WORLD_SURFACE_WG",
                      "randomState()","getFluidState().isEmpty()","profile.candidateCount()"):
            self.assertIn(token,source)
        self.assertNotRegex(source,r"\.getChunk\s*\(")
        self.assertNotRegex(source,r"\.getHeight\s*\(")
        self.assertRegex(source,r"MAX_CANDIDATES\s*=\s*\d+")

    def test_listener_is_registered_once_on_neoforge_event_bus(self):
        mod=(JAVA/"RealScaleMod.java").read_text()
        self.assertIn("NeoForge.EVENT_BUS.addListener(RealScaleSpawnPosition::onCreateSpawnPosition)",mod)

if __name__=="__main__": unittest.main()

#!/usr/bin/env python3
"""Validate actual saved-biome altitude-band report mathematics."""
import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent/"neoforge"))
from check_realism_continuity import altitude_biome_audit

class AltitudeClimateAuditTests(unittest.TestCase):
    def test_observation_uses_surface_y_not_noise_guess(self):
        heights={(0,0):70,(1,0):120,(2,0):185,(3,0):260}
        biomes={(0,0):"minecraft:plains",(1,0):"minecraft:meadow",
                (2,0):"minecraft:grove",(3,0):"minecraft:snowy_slopes"}
        out=altitude_biome_audit(heights,biomes)["bands"]
        self.assertEqual(out["63-95"]["snowy_or_frozen_count"],0)
        self.assertEqual(out["160-239"]["snowy_or_frozen_count"],1)
        self.assertEqual(out["240-4095"]["snowy_or_frozen_fraction"],1.0)

    def test_missing_biomes_not_falsely_counted_as_dry(self):
        report=altitude_biome_audit({(0,0):230,(1,1):250},{(0,0):None})
        self.assertEqual(report["bands"]["160-239"]["sample_columns"],0)
        self.assertIsNone(report["bands"]["160-239"]["snowy_or_frozen_fraction"])

    def test_hot_at_high_altitude_is_reported_not_silently_reassigned(self):
        out=altitude_biome_audit({(0,0):400},{(0,0):"minecraft:desert"})["bands"]
        self.assertEqual(out["240-4095"]["hot_dry_or_tropical_count"],1)

if __name__=="__main__":unittest.main()

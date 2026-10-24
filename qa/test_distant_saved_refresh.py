"""Distant saved-chunk refresh must be a real disk/loaded-chunk QA test.

GPU-visible Distant Horizons LOD rendering is not provable with headless RCON.
Fail closed on saved block/biome/heightmap drift, fake GPU claims, or skipped
forceload ticket release between virgin and reacquired passes.
"""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
RCON=(ROOT/"qa/neoforge/rcon_worldgen_smoke.py").read_text()
WORKFLOW=ROOT/".github/workflows/far-saved-refresh-qa.yml"


class DistantSavedRefreshContracts(unittest.TestCase):
    def test_all_three_virgin_regions_then_reacquire_all_three(self):
        self.assertIn("FAR_ROUTE_SITES=((1024,1024),(-2048,1536),(4096,-2048))",RCON)
        self.assertIn('elif profile in ("distant_route","distant_refresh"):',RCON)
        self.assertIn("for x,z in FAR_ROUTE_SITES:",RCON)
        self.assertIn("for x,z in reversed(FAR_ROUTE_SITES):",RCON)
        self.assertIn('await_full_chunk(rcon,x,z,timeout_seconds=remaining)',RCON)
        self.assertIn('rcon.send("save-all flush")',RCON)
        self.assertIn('run forceload remove {x} {z}',RCON)
        self.assertIn("strict 180s limit",RCON)
        self.assertIn("reload_seconds>75",RCON)
        self.assertIn('print("QA_FAR_REFRESH_EXISTING_ANVIL_PARITY_PASS:"',RCON)

    def test_real_saved_blocks_compare_and_gpu_disclaimer(self):
        self.assertIn("before=target_chunk_digests(root)",RCON)
        self.assertIn("after=target_chunk_digests(root)",RCON)
        self.assertIn("differences=saved_world_differences(before,after)",RCON)
        self.assertIn('"same_server_anvil_semantic_parity":not differences',RCON)
        self.assertIn('"gpu_dh_lod_rendering_verified":False',RCON)
        self.assertIn('raise AssertionError("Distant reload changed saved Anvil "',RCON)
        self.assertIn('rcon_distant_refresh_timing.json',RCON)

    def test_expensive_test_opt_in_only_and_not_a_fake_success(self):
        src=WORKFLOW.read_text()
        self.assertIn("types: [labeled]",src)
        self.assertIn("run-far-refresh",src)
        self.assertIn("workflow_dispatch:",src)
        self.assertIn("distant_refresh",src)
        self.assertIn("if: always()",src)
        self.assertIn("exit 1",src)
        self.assertIn("gpu_dh_lod_rendering_verified",RCON)
        self.assertIn("timeout-minutes:",src)


if __name__=="__main__":
    unittest.main()

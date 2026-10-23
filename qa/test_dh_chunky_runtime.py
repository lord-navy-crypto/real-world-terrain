import unittest
from pathlib import Path
import sys

sys.path.insert(0,str(Path(__file__).resolve().parent / "neoforge"))
from verify_dh_chunky_guard_runtime import evaluate,EXACT_CRASH,EXACT_PATH,FULL_CHUNK,PATCH_LOADED,PATCH_HOOK,PATCH_DEFERRED,PATCH_READY,PATCH_BOUND,DH_WORLDGEN_QUEUE

class RuntimeVerdictTests(unittest.TestCase):
    def test_client_report_original_upstream_crash_is_expected_unpatched(self):
        result=evaluate(EXACT_CRASH+"\n"+EXACT_PATH, "", "unpatched", 1)
        self.assertEqual(result["verdict"],"reproduced_known_DH_Chunky_crash")
        self.assertTrue(result["pass_gate"])
    def test_patched_world_requires_mixin_hook_and_full_chunk(self):
        self.assertTrue(evaluate(PATCH_LOADED+"\n"+PATCH_HOOK+"\n"+PATCH_BOUND+"\n"+DH_WORLDGEN_QUEUE,FULL_CHUNK,
                                 "patched",0)["pass_gate"])
        self.assertFalse(evaluate(PATCH_LOADED,FULL_CHUNK,
                                  "patched",0)["pass_gate"])
        self.assertFalse(evaluate(PATCH_LOADED+"\n"+PATCH_HOOK,"",
                                  "patched",0)["pass_gate"])
    def test_do_not_count_full_chunk_as_dh_listener_bound(self):
        log="\n".join((PATCH_LOADED,PATCH_HOOK,DH_WORLDGEN_QUEUE))
        self.assertFalse(evaluate(log,FULL_CHUNK,"patched",0)["pass_gate"])

    def test_listener_must_resume_after_initial_defer(self):
        log = "\n".join((PATCH_LOADED,PATCH_HOOK,PATCH_BOUND,PATCH_DEFERRED,DH_WORLDGEN_QUEUE))
        stuck = evaluate(log,FULL_CHUNK,"patched",0)
        self.assertFalse(stuck["pass_gate"])
        self.assertFalse(stuck["listener_not_stuck"])
        restored = evaluate(log+"\n"+PATCH_READY,FULL_CHUNK,"patched",0)
        self.assertTrue(restored["pass_gate"])
        self.assertTrue(restored["listener_not_stuck"])
    def test_dh_generator_queue_must_initialize(self):
        log="\n".join((PATCH_LOADED,PATCH_HOOK))
        self.assertFalse(evaluate(log,FULL_CHUNK,"patched",0)["pass_gate"])

    def test_patched_world_must_not_hide_known_original_exception(self):
        log=PATCH_LOADED+"\n"+PATCH_HOOK+"\n"+PATCH_BOUND+"\n"+DH_WORLDGEN_QUEUE+"\n"+EXACT_CRASH+"\n"+EXACT_PATH
        self.assertFalse(evaluate(log,FULL_CHUNK,"patched",0)["pass_gate"])
    def test_unpatched_can_be_inconclusive_not_fake_reproduced(self):
        r=evaluate("DistantHorizons Chunky initialized",FULL_CHUNK,"unpatched",0)
        self.assertTrue(r["pass_gate"])
        self.assertEqual(r["verdict"],"baseline_started_no_reproduction")
    def test_real_server_writes_noninvasive_lod_storage_report_after_stop(self):
        code=(Path(__file__).resolve().parents[0]/"neoforge"/
              "rcon_worldgen_smoke.py").read_text()
        self.assertIn('from audit_dh_lod_sqlite import scan',code)
        self.assertIn('dh-chunky-lod-storage.json',code)
        self.assertIn('REALSCALE_DH_LOD_PERSISTENCE_AFTER_STOP',code)
        self.assertLess(code.index('proc.wait(timeout=125)'),
                        code.index('storage=scan(scan_root)'))

    def test_other_unpatched_crash_is_failure(self):
        self.assertFalse(evaluate("OutOfMemoryError","", "unpatched",1)["pass_gate"])

if __name__=="__main__":
    unittest.main()

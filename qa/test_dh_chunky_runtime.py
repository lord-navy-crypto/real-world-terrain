import unittest
from pathlib import Path
import sys

sys.path.insert(0,str(Path(__file__).resolve().parent / "neoforge"))
from verify_dh_chunky_guard_runtime import evaluate,EXACT_CRASH,EXACT_PATH,FULL_CHUNK,PATCH_LOADED,PATCH_HOOK,PATCH_DEFERRED,PATCH_READY,DH_WORLDGEN_QUEUE

class RuntimeVerdictTests(unittest.TestCase):
    def test_client_report_original_upstream_crash_is_expected_unpatched(self):
        result=evaluate(EXACT_CRASH+"\n"+EXACT_PATH, "", "unpatched", 1)
        self.assertEqual(result["verdict"],"reproduced_known_DH_Chunky_crash")
        self.assertTrue(result["pass_gate"])
    def test_patched_world_requires_mixin_hook_and_full_chunk(self):
        self.assertTrue(evaluate(PATCH_LOADED+"\n"+PATCH_HOOK+"\n"+DH_WORLDGEN_QUEUE,FULL_CHUNK,
                                 "patched",0)["pass_gate"])
        self.assertFalse(evaluate(PATCH_LOADED,FULL_CHUNK,
                                  "patched",0)["pass_gate"])
        self.assertFalse(evaluate(PATCH_LOADED+"\n"+PATCH_HOOK,"",
                                  "patched",0)["pass_gate"])
    def test_listener_must_resume_after_initial_defer(self):
        log = "\n".join((PATCH_LOADED,PATCH_HOOK,PATCH_DEFERRED,DH_WORLDGEN_QUEUE))
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
        log=PATCH_LOADED+"\n"+PATCH_HOOK+"\n"+DH_WORLDGEN_QUEUE+"\n"+EXACT_CRASH+"\n"+EXACT_PATH
        self.assertFalse(evaluate(log,FULL_CHUNK,"patched",0)["pass_gate"])
    def test_unpatched_can_be_inconclusive_not_fake_reproduced(self):
        r=evaluate("DistantHorizons Chunky initialized",FULL_CHUNK,"unpatched",0)
        self.assertTrue(r["pass_gate"])
        self.assertEqual(r["verdict"],"baseline_started_no_reproduction")
    def test_other_unpatched_crash_is_failure(self):
        self.assertFalse(evaluate("OutOfMemoryError","", "unpatched",1)["pass_gate"])

if __name__=="__main__":
    unittest.main()

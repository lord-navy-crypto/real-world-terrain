#!/usr/bin/env python3
"""Fail-closed code contracts for asynchronous real-saved-heightmap rendering."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
LANG=ROOT/"neoforge/src/main/resources/assets/realscale/lang"

class WorldAtlasBackgroundPainterTests(unittest.TestCase):
    def test_real_saved_height_nbt_is_read_only_and_bounded(self):
        source=(SRC/"WorldAtlasHeightmapReader.java").read_text()
        self.assertIn('name.equals("WORLD_SURFACE")',source)
        self.assertIn('name.equals("OCEAN_FLOOR")',source)
        self.assertIn("GZIPInputStream",source)
        self.assertIn("InflaterInputStream",source)
        self.assertIn("MAX_CHUNK_NBT = 4*1024*1024",source)
        self.assertIn("bitsFor(int count)",source)
        self.assertIn("return 12;",source)
        for forbidden in ("Files.write(", "new FileOutputStream(",
                          "Files.newOutputStream(", "getChunk(", "ServerLevel"):
            self.assertNotIn(forbidden,source)

    def test_background_job_is_cancelable_single_daemon_and_survives_screen_close(self):
        source=(SRC/"WorldAtlasBackgroundPainter.java").read_text()
        self.assertIn("newSingleThreadExecutor",source)
        self.assertIn("t.setDaemon(true)",source)
        self.assertIn("Thread.MIN_PRIORITY",source)
        self.assertIn("AtomicBoolean paused",source)
        self.assertIn("AtomicBoolean cancelled",source)
        self.assertIn('task.publish("DONE"',source)
        self.assertIn('task.publish("CANCELLED"',source)
        self.assertIn("WorldAtlasHeightmapReader.openSampler(task.world)",source)
        self.assertIn("sampler.read(cx,cz)",source)
        reader=(SRC/"WorldAtlasHeightmapReader.java").read_text()
        self.assertIn("MAX_OPEN_REGIONS = 32",reader)
        self.assertIn("implements AutoCloseable",reader)
        self.assertIn("regions.clear()",reader)
        self.assertNotIn("ProcessBuilder(",source)
        self.assertNotIn("new FileOutputStream(",source)
        self.assertNotIn("ServerChunkCache",source)

    def test_gui_has_live_progress_and_controls_but_no_hidden_chunky_copy(self):
        source=(SRC/"WorldAtlasMapScreen.java").read_text()
        for name in ("warmup_start","draw_start","draw_pause","draw_resume",
                     "draw_cancel","draw_busy","draw_real_legend"):
            self.assertIn("realscale.atlas."+name,source)
        self.assertIn("WorldAtlasBackgroundPainter.start(",source)
        self.assertIn("WorldAtlasOfflinePregenerator.startWarmup(",source)
        self.assertIn("if(serverJob!=null)",source)
        self.assertIn("WorldAtlasBackgroundPainter.cancel()",source)
        self.assertIn("WorldAtlasBackgroundPainter.pause()",source)
        self.assertIn("WorldAtlasBackgroundPainter.resume()",source)
        self.assertIn("WorldAtlasBackgroundPainter.current()",source)
        self.assertIn("drawing.available()",source)
        for forbidden in ("ProcessBuilder(","loadLevel(", "runServer(",
                          "createWorldOpenFlows()","getChunk("):
            self.assertNotIn(forbidden,source)

    def test_bilingual_ui(self):
        keys=("warmup_start","warmup_starting","warmup_busy","busy_shared_io",
              "draw_start,"draw_pause","draw_resume","draw_cancel",
              "draw_started","draw_busy","draw_idle","draw_real_legend")
        for filename in ("en_us.json","zh_cn.json"):
            lang=json.loads((LANG/filename).read_text())
            for key in keys:
                self.assertTrue(lang.get("realscale.atlas."+key),
                                filename+" missing "+key)

if __name__=="__main__":
    unittest.main()

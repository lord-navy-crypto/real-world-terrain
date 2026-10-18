#!/usr/bin/env python3
"""Fail-closed checks: offline NeoForge+Chunky must not double-open world."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
LANG=ROOT/"neoforge/src/main/resources/assets/realscale/lang"

class AtlasOfflineTests(unittest.TestCase):
    def test_backend_uses_chunky_instead_of_another_chunk_generator(self):
        src=(SRC/"WorldAtlasOfflinePregenerator.java").read_text()
        self.assertIn('chunky start minecraft:overworld square ',src)
        self.assertIn('chunky pause minecraft:overworld',src)
        self.assertIn('chunky continue minecraft:overworld',src)
        self.assertIn('chunky progress',src)
        self.assertIn("Chunky task saved; flushing world and stopping",src)
        self.assertIn('s.command("save-all flush")',src)
        self.assertIn('s.command("stop")',src)
        self.assertIn("Task stopped for minecraft:overworld",src)
        self.assertIn("No tasks running.",src)
        self.assertIn("GenerationTask.saveTask",src)
        self.assertIn("parseChunkyProgress(String line)",src)
        self.assertIn("WorldAtlasOfflinePregenerator.progress()",(
            SRC/"WorldAtlasMapScreen.java").read_text())
        self.assertIn("Task started in ",src)
        self.assertNotIn("chunky cancel",src)
        self.assertNotIn("forceload add",src)
        self.assertNotIn("getChunkAtAsync",src)
        self.assertNotIn("runServer(",src)
        self.assertNotIn("server.properties",src.split("Files.write(",1)[-1] if "Files.write(" in src else "")

    def test_exclusive_local_single_instance_world_lock_and_no_save_writes(self):
        src=(SRC/"WorldAtlasOfflinePregenerator.java").read_text()
        self.assertIn('world.toRealPath(LinkOption.NOFOLLOW_LINKS)',src)
        self.assertIn('channel.tryLock()',src)
        self.assertIn('OverlappingFileLockException',src)
        self.assertIn('old!=null && !old.status.terminal()',src)
        self.assertIn('old.process.isAlive()',src)
        self.assertIn('"server-ip",""',src)
        self.assertIn('"127.0.0.1"',src)
        self.assertIn('"enable-rcon","false"',src)
        self.assertIn('"eula"',src)
        self.assertIn("ProcessBuilder(\"/bin/bash\"",src)
        self.assertIn('"--universe",saves.toString()',src)
        self.assertIn('"--world",worldName,"--port","0","nogui"',src)
        for forbidden in ("Files.write(", "Files.newOutputStream(", "Files.delete(",
                          "new FileOutputStream(", "ProcessHandle.allProcesses()"):
            self.assertNotIn(forbidden,src)

    def test_atlas_world_list_button_and_task_status(self):
        src=(SRC/"WorldAtlasMapScreen.java").read_text()
        self.assertIn("WorldAtlasOfflinePregenerator.start(",src)
        self.assertIn("WorldAtlasOfflinePregenerator.pause()",src)
        self.assertIn("WorldAtlasOfflinePregenerator.resume()",src)
        self.assertIn("WorldAtlasOfflinePregenerator.stopAndSave()",src)
        self.assertIn("WorldAtlasOfflinePregenerator.pollProgress()",src)
        self.assertIn("WorldAtlasOfflinePregenerator.status()",src)
        self.assertNotIn("new ServerChunkCache",src)

    def test_localizations(self):
        keys=("pregen_start","pregen_pause","pregen_resume",
              "pregen_stop","pregen_starting","pregen_busy")
        for name in ("en_us","zh_cn"):
            src=json.loads((LANG/(name+".json")).read_text())
            for key in keys:
                self.assertTrue(src.get("realscale.atlas."+key),
                                name+" missing "+key)

if __name__=="__main__":
    unittest.main()

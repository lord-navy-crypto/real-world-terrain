#!/usr/bin/env python3
"""Contract checks for the read-only World Atlas and saved-terrain drawing."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
RES=ROOT/"neoforge/src/main/resources/assets/realscale/lang"

class WorldAtlasFeatureTest(unittest.TestCase):
    def test_offline_world_list_button_not_create_world_interception(self):
        source=(SRC/"WorldAtlasMenuClient.java").read_text()
        self.assertIn("ScreenEvent.Init.Post",source)
        self.assertIn("SelectWorldScreen",source)
        self.assertNotIn("CreateWorldScreen",source.split("@SubscribeEvent",1)[-1])
        self.assertIn("event.addListener(Button.builder(",source)

    def test_actual_readonly_mca_coverage_no_chunk_creation(self):
        source=(SRC/"WorldAtlasRegionIndex.java").read_text()
        self.assertIn('input.readNBytes(4096)',source)
        self.assertIn('path=regions.resolve("r."+rx+"."+rz+".mca")',source)
        self.assertIn('Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)',source)
        self.assertIn("saved[index(cx,cz,minX,minZ,width,depth)]++",source)
        self.assertIn("Files.newInputStream(path)",source)
        self.assertNotIn("Files.newOutputStream(",source)
        self.assertNotIn("Files.write(",source)
        self.assertNotIn("ChunkGenerator",source)
        self.assertNotIn("server.execute(",source)

    def test_readonly_atlas_and_manual_chunky_guide_no_hidden_server(self):
        screen=(SRC/"WorldAtlasMapScreen.java").read_text()
        self.assertIn("CompletableFuture.supplyAsync(",screen)
        self.assertIn('ModList.get().isLoaded("chunky")',screen)
        self.assertIn("keyboardHandler.setClipboard(commands)",screen)
        self.assertIn("WorldAtlasRegionIndex.scan(",screen)
        for unsafe in ("ProcessBuilder(","Runtime.getRuntime().exec(",
                       "createWorldOpenFlows()", "loadLevel(",
                       "getChunk(", "ServerChunkCache"):
            self.assertNotIn(unsafe,screen)
        self.assertIn('realscale.atlas.coverage_only',screen)

    def test_english_chinese_atlas_labels(self):
        keys=("entry","title","empty","back","refresh","scanning",
              "coverage_only","chunky_found","chunky_missing",
              "copy_chunky","copied")
        for name in ("en_us","zh_cn"):
            data=json.loads((RES/(name+".json")).read_text())
            for key in keys:
                self.assertTrue(data.get("realscale.atlas."+key),
                                name+" missing "+key)

if __name__=="__main__":
    unittest.main()

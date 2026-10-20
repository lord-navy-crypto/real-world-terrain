"""Opt-in v4 northern cave biomes and sky island geometry verification."""
import json,subprocess,tempfile,shutil,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
J=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
D=ROOT/"neoforge/src/main/resources/data/realscale"
def load(p):return json.loads(p.read_text())
class NorthernV4(unittest.TestCase):
 def test_wiring_and_legacy_isolation(self):
  p=load(D/"worldgen/world_preset/tva_northern_fjord_experiment_v4_three_realms.json")
  s=p["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
  self.assertEqual(s["source"]["source"]["source"]["source"]["type"],"realscale:northern_cave_biomes_v4")
  for tag in ("normal","extended"):
   t=ROOT/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset"
   self.assertEqual(len(load(t/(tag+".json"))["values"]),4)
  self.assertIn("hasNorthernFjordV4", (J/"SkyIslandFeature.java").read_text())
  self.assertIn("hasNorthernFjordV4", (J/"NorthernCaveHabitatFeatureV4.java").read_text())
  m=load(D/"neoforge/biome_modifier/northern_cave_habitats_v4.json")
  self.assertEqual(m["step"],"underground_decoration")
  self.assertEqual(len(m["biomes"]),6)
  self.assertEqual(load(D/"worldgen/placed_feature/northern_cave_habitats_v4.json")["feature"],
    "realscale:northern_cave_habitats_v4")
 def test_production_math_java(self):
  if not shutil.which("javac") or not shutil.which("java"):self.skipTest("JDK unavailable")
  smoke="""package org.realscale.neoforge;
public class NorthernV4Smoke {
 static void ok(boolean v,String s){if(!v)throw new AssertionError(s);}
 public static void main(String[] args) {
  boolean[] seen=new boolean[2];
  for(int x=-240;x<=240;x+=17)for(int z=-240;z<=240;z+=19){
   long h=NorthernCaveBiomeMathV4.regionHash(x,z);
   String cave=NorthernCaveBiomeMathV4.select("minecraft:lush_caves","minecraft:plains",0,h);
   if("realscale:deep_fungal_caverns".equals(cave))seen[0]=true;
   if("realscale:underground_springs".equals(cave))seen[1]=true;
   ok(NorthernCaveBiomeMathV4.select("minecraft:lush_caves","minecraft:plains",10,h)==null,"surface unchanged");
   ok(NorthernCaveBiomeMathV4.select("minecraft:deep_dark","minecraft:plains",0,h)==null,"deep dark");
   ok(NorthernCaveBiomeMathV4.select("minecraft:dripstone_caves","minecraft:deep_ocean",0,h)==null,"sea");
   int dx=x%14,dz=z%14;
   double ratio=(dx*dx+dz*dz)/392.0;
   int top=SkyIslandShapeV4.top(195,dx,dz,ratio);
   int thick=SkyIslandShapeV4.thickness(ratio,dx,dz);
   ok(top>=188&&top<=198&&thick>=3&&thick<=15,"island bounded");
  }
  ok(seen[0]&&seen[1],"multiple biomes exist");
  System.out.println("NORTHERN_CAVE_SKY_V4_PASS");
 }
}
"""
  with tempfile.TemporaryDirectory() as d:
   f=Path(d)/"NorthernV4Smoke.java";f.write_text(smoke)
   subprocess.run(["javac","-d",d,str(J/"NorthernCaveBiomeMathV4.java"),
       str(J/"SkyIslandShapeV4.java"),str(f)],check=True,capture_output=True,timeout=45)
   p=subprocess.run(["java","-cp",d,"org.realscale.neoforge.NorthernV4Smoke"],
       check=True,capture_output=True,text=True,timeout=45)
   self.assertIn("NORTHERN_CAVE_SKY_V4_PASS",p.stdout)
if __name__=="__main__":unittest.main()

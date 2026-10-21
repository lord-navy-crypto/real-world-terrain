"""Actual production Java and JSON contract for versioned USGS fitted terrain.
Never claims synthetic extrapolation is measured truth beyond the DEM tile.
"""
import json,shutil,subprocess,tempfile,unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
J=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
TEST=ROOT/"neoforge/src/test/java/org/realscale/neoforge"
RES=ROOT/"neoforge/src/main/resources"
WG=RES/"data/realscale/worldgen"

def read(p):return json.loads(p.read_text(encoding="utf-8"))

class FittedUSGSQA(unittest.TestCase):
    def test_immutable_previous_usgs_world_and_new_version(self):
        older=read(WG/"world_preset/tva_measured_global_landforms_three_realms.json")
        fitted=read(WG/"world_preset/tva_measured_usgs_fitted_v1_three_realms.json")
        self.assertEqual(older["dimensions"]["minecraft:the_nether"],fitted["dimensions"]["minecraft:the_nether"])
        self.assertEqual(older["dimensions"]["minecraft:the_end"],fitted["dimensions"]["minecraft:the_end"])
        old_gen=older["dimensions"]["minecraft:overworld"]["generator"]
        new_gen=fitted["dimensions"]["minecraft:overworld"]["generator"]
        self.assertEqual(old_gen["biome_source"],new_gen["biome_source"])
        self.assertEqual(old_gen["settings"],"realscale:usgs_global_hybrid_marine")
        self.assertEqual(new_gen["settings"],"realscale:usgs_fitted_continuation_v1")
        for tag in ("normal","extended"):
            modes=read(RES/"data/minecraft/tags/worldgen/world_preset"/(tag+".json"))["values"]
            self.assertEqual(len(modes),4)
            self.assertEqual(modes[1],"realscale:tva_measured_usgs_fitted_v2_three_realms")
            # V1 remains loadable for existing generated worlds but is hidden from new-world selection.
            self.assertNotIn("realscale:tva_measured_usgs_fitted_v1_three_realms",modes)
            self.assertNotIn("realscale:tva_measured_global_landforms_three_realms",modes)
    def test_same_marine_ocean_caves_climate_resources(self):
        old=read(WG/"noise_settings/usgs_global_hybrid_marine.json")
        new=read(WG/"noise_settings/usgs_fitted_continuation_v1.json")
        self.assertEqual(new["sea_level"],old["sea_level"])
        self.assertEqual(new["surface_rule"],old["surface_rule"])
        self.assertEqual(new["noise"],old["noise"])
        self.assertEqual(new["aquifers_enabled"],old["aquifers_enabled"])
        self.assertEqual(new["default_fluid"],old["default_fluid"])
        self.assertEqual(new["ore_veins_enabled"],old["ore_veins_enabled"])
        def convert(v):
            return json.loads(json.dumps(v).replace(
                "realscale:usgs_fitted_continuation_v1/",
                "realscale:usgs_global_hybrid_marine/"))
        self.assertEqual(convert(new),old)
        source=read(WG/"density_function/usgs_fitted_continuation_v1/terrain_density.json")
        self.assertEqual(source["type"],"realscale:dem_fitted_continuation_v1")
        self.assertEqual(source["base"],"realscale:usgs_global_hybrid_marine/terrain_density")
        self.assertEqual(source["continents"],"realscale:realism_max/continental_field")
        self.assertGreater(source["strength"],0.0)
        self.assertLess(source["strength"],1.0)
        self.assertEqual(convert(read(WG/"density_function/usgs_fitted_continuation_v1/cave_density.json")),
                         read(WG/"density_function/usgs_global_hybrid_marine/cave_density.json"))
        mod=(J/"RealScaleMod.java").read_text(encoding="utf-8")
        self.assertIn('"dem_fitted_continuation_v1"), RealDemFittedDensityV1.MAP_CODEC',mod)
    def test_actual_java_continuation_math_32m_dem_and_signed_far_world(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("JDK 21 unavailable")
        java="""package org.realscale.neoforge;
public class RealDemFittedMathV1Smoke {
 static void chk(boolean v,String e){if(!v)throw new AssertionError(e);}
 public static void main(String[] args) {
   RealDemDataSource dem=RealDemDataSource.loadMeasured();
   chk(Double.isFinite(RealDemFittedMathV1.mean()),"DEM mean");
   chk(RealDemFittedMathV1.roughness()>0,"DEM roughness");
   int n=0;
   for(int x=dem.originX();x<=dem.maxX();x+=32)
     for(int z=dem.originZ();z<=dem.maxZ();z+=32){
       chk(Double.doubleToRawLongBits(dem.worldSolidYAt(x,z))==
           Double.doubleToRawLongBits(RealDemFittedMathV1.predictedHeight(x,z)),
           "measured raster was overwritten at "+x+","+z);
       n++;
     }
   chk(n==256,"missing real DEM raster points");
   for(int x=-30000;x<=30000;x+=113){
     for(int z=-30000;z<=30000;z+=271){
       double h=RealDemFittedMathV1.predictedHeight(x,z);
       chk(Double.isFinite(h)&&h>=-48&&h<=2000,"out of bounds");
       chk(Double.doubleToRawLongBits(h)==Double.doubleToRawLongBits(
           RealDemFittedMathV1.predictedHeight(x,z)),"not deterministic");
     }
   }
   // Sample across chunk, DEM, 4096 and 8192 grid boundaries. The
   // adjacent 1m slope must not exhibit abrupt cliffs or NaNs.
   int[] edges={-16384,-8192,-4096,-2048,-512,-16,0,16,512,2048,4096,8192,16384};
   for(int edge:edges)
      for(int other:new int[]{-3071,-999,-191,0,211,1777,5197}) {
         double a=RealDemFittedMathV1.predictedHeight(edge-1,other);
         double b=RealDemFittedMathV1.predictedHeight(edge+1,other);
         double c=RealDemFittedMathV1.predictedHeight(other,edge-1);
         double d=RealDemFittedMathV1.predictedHeight(other,edge+1);
         chk(Math.abs(b-a)<65,"east-west abrupt fitted edge "+edge+","+other);
         chk(Math.abs(d-c)<65,"north-south abrupt fitted edge "+edge+","+other);
      }
   // Crossing the actual data rectangle must be continuous at each side.
   for(int t=0;t<32;t++){
      int x=dem.originX()+t*(dem.maxX()-dem.originX())/31;
      int z=dem.originZ()+t*(dem.maxZ()-dem.originZ())/31;
      for(int v:new int[]{0,1,3,9}) {
        chk(Math.abs(RealDemFittedMathV1.predictedHeight(x,dem.maxZ()+v)
                 -RealDemFittedMathV1.predictedHeight(x,dem.maxZ()))<65,"south seam");
        chk(Math.abs(RealDemFittedMathV1.predictedHeight(dem.originX()-v,z)
                 -RealDemFittedMathV1.predictedHeight(dem.originX(),z))<65,"west seam");
      }
   }
   for(int x:new int[]{-30000000,-1000000,1000000,30000000})
      for(int z:new int[]{-30000000,0,30000000})
          chk(Double.isFinite(RealDemFittedMathV1.predictedHeight(x,z)),"far coordinates");
   System.out.println("USGS_FITTED_V1_JAVA_PASS: measured cells, unsigned/signed far field, all four seams");
 }
}
"""
        with tempfile.TemporaryDirectory(prefix="usgs-fitted-v1-") as temp:
            out=Path(temp)
            smoke=out/"RealDemFittedMathV1Smoke.java"
            smoke.write_text(java,encoding="utf-8")
            sources=[J/name for name in ["RealDemTile.java","RealDemDataSource.java",
                             "RealDemSeamlessMath.java","RealDemFittedMathV1.java"]]
            sources.append(smoke)
            subprocess.run(["javac","-d",str(out),*map(str,sources)],check=True,
                capture_output=True,text=True,timeout=40)
            p=subprocess.run(["java","-cp",str(out)+":"+str(RES),
                "org.realscale.neoforge.RealDemFittedMathV1Smoke"],
                check=True,capture_output=True,text=True,timeout=45)
            self.assertIn("USGS_FITTED_V1_JAVA_PASS",p.stdout)

if __name__=="__main__":unittest.main()

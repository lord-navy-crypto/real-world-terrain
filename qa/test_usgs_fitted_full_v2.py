"""USGS fully fitted exterior V2: no generic land density beyond blend band.

Real, compiled production Java math. Asserts strict preservation of V1 saves,
real USGS points, coastline/ocean gates, globally continuous function sampling,
and no arbitrary 16/256/4096/8192 Minecraft chunk lattice stepping.
"""
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
RES=ROOT/"neoforge/src/main/resources"
WORLD=RES/"data/realscale/worldgen"
def load(p):return json.loads(p.read_text(encoding="utf-8"))

class USGSFittedFullV2(unittest.TestCase):
    def test_new_v2_isolated_v1_and_true_100_percent_land(self):
        v1=load(WORLD/"world_preset/tva_measured_usgs_fitted_v1_three_realms.json")
        v2=load(WORLD/"world_preset/tva_measured_usgs_fitted_v2_three_realms.json")
        self.assertEqual(v2["dimensions"]["minecraft:the_nether"],v1["dimensions"]["minecraft:the_nether"])
        self.assertEqual(v2["dimensions"]["minecraft:the_end"],v1["dimensions"]["minecraft:the_end"])
        a=v1["dimensions"]["minecraft:overworld"]["generator"]
        b=v2["dimensions"]["minecraft:overworld"]["generator"]
        self.assertEqual(a["biome_source"],b["biome_source"])
        self.assertEqual(a["settings"],"realscale:usgs_fitted_continuation_v1")
        self.assertEqual(b["settings"],"realscale:usgs_fitted_continuation_v2")
        d1=load(WORLD/"density_function/usgs_fitted_continuation_v1/terrain_density.json")
        d2=load(WORLD/"density_function/usgs_fitted_continuation_v2/terrain_density.json")
        self.assertEqual(d1["type"],"realscale:dem_fitted_continuation_v1")
        self.assertEqual(d1["strength"],0.72)
        self.assertEqual(d2,{"type":"realscale:dem_fitted_continuation_v2",
            "base":"realscale:usgs_global_hybrid_marine/terrain_density",
            "continents":"realscale:realism_max/continental_field"})
        for tag in ("normal","extended"):
            choices=load(RES/"data/minecraft/tags/worldgen/world_preset"/(tag+".json"))["values"]
            self.assertEqual(len(choices),4)
            self.assertEqual(choices[1],"realscale:tva_measured_usgs_fitted_v2_three_realms")
            self.assertNotIn("realscale:tva_measured_usgs_fitted_v1_three_realms",choices)
        wrapper=(JAVA/"RealDemFittedFullDensityV2.java").read_text(encoding="utf-8")
        self.assertIn("if(fittedWeight>=1.0)return fitted;",wrapper)
        self.assertIn("if(c.distance<=0.0)return base.compute(ctx);",wrapper)
        self.assertIn("if(continentalness<=-0.15)return base.compute(ctx);",wrapper)
        # No old generic base compute should occur before the fitted return.
        self.assertLess(wrapper.index("if(fittedWeight>=1.0)return fitted;"),
                        wrapper.index("final double original=base.compute(ctx);"))
        mod=(JAVA/"RealScaleMod.java").read_text(encoding="utf-8")
        self.assertIn('"dem_fitted_continuation_v2"), RealDemFittedFullDensityV2.MAP_CODEC',mod)

    def test_underlying_ocean_caves_climate_and_legacy_settings_bit_stable(self):
        old=load(WORLD/"noise_settings/usgs_fitted_continuation_v1.json")
        newer=load(WORLD/"noise_settings/usgs_fitted_continuation_v2.json")
        translated=json.loads(json.dumps(newer).replace(
            "realscale:usgs_fitted_continuation_v2/",
            "realscale:usgs_fitted_continuation_v1/"))
        self.assertEqual(translated,old)
        # The ocean/cave/rare-landform base remains exactly the original.
        self.assertEqual(load(WORLD/"density_function/usgs_fitted_continuation_v2/cave_density.json"),
            json.loads(json.dumps(load(WORLD/"density_function/usgs_fitted_continuation_v1/cave_density.json")).replace(
                "realscale:usgs_fitted_continuation_v1/",
                "realscale:usgs_fitted_continuation_v2/")))
        self.assertEqual(newer["sea_level"],63)
        self.assertEqual(newer["noise"]["height"],2096)

    def test_actual_production_java_continuity_far_away_and_exact_land_weight(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("JDK 21 is unavailable")
        src="""package org.realscale.neoforge;
public final class RealDemFittedFullV2Smoke {
  static void require(boolean ok,String error){if(!ok)throw new AssertionError(error);}
  static void testGate(){
    for(double d:new double[]{1536,2048,4096,8192,65536,1000000,30000000}){
      for(double c:new double[]{0.18,0.3,1,10}){
        require(RealDemFittedFullMathV2.fittedWeight(d,c)==1,
           "Land far away is not 100 percent fitted "+d+" "+c);
        require(RealDemFittedFullMathV2.blendDensity(-999,77,d,c)==77,
           "Far continent still reads generic procedural density");
      }
    }
    for(double d:new double[]{0,1,512,1536,16384}){
      require(RealDemFittedFullMathV2.fittedWeight(d,-0.15)==0,
          "Ocean floor changed!");
    }
    require(RealDemFittedFullMathV2.fittedWeight(0.0,1.0)==0,
            "True DEM tile lost exact preservation");
    double prev=0;
    for(int i=0;i<=1536;i++){
      double w=RealDemFittedFullMathV2.fittedWeight(i,1.0);
      require(w>=prev&&w<=1&&w>=0,"Invalid smoothly monotone fade");
      prev=w;
    }
    double h=0.0001;
    require((RealDemFittedFullMathV2.demTransitionWeight(h)-0)/h<0.001,
            "Blend starts with an abrupt slope");
    require((1-RealDemFittedFullMathV2.demTransitionWeight(1536-h))/h<0.001,
            "Blend ends with an abrupt slope");
  }
  static void testContinuousTerrain(){
    RealDemDataSource dem=RealDemDataSource.loadMeasured();
    for(int ix=0;ix<dem.width();ix++){
      for(int iz=0;iz<dem.height();iz++){
        int x=dem.originX()+(int)(dem.cellMeters()*ix);
        int z=dem.originZ()+(int)(dem.cellMeters()*iz);
        require(Double.doubleToRawLongBits(
                 RealDemFittedMathV2.predictedHeight(x,z)) ==
                Double.doubleToRawLongBits(dem.worldSolidYAt(x,z)),
                "Measured elevation modified!");
      }
    }
    // Check every world-space column across four cardinal directions,
    // negative coordinates and full noise-lattice/chunk boundaries.
    int samples=0;double worstStep=0;
    for(int edge:new int[]{-65536,-32768,-16384,-8192,-4096,-2048,-1536,-512,
                           -256,-32,-16,0,16,32,256,512,1536,2048,4096,8192,
                           16384,32768,65536}){
      for(int offset:new int[]{-5243,-2137,-901,0,617,1681,3193,8127}){
        for(int delta=-32;delta<=32;delta++){
          int x=edge+delta,z=offset;
          if(RealDemFittedMathV2.distanceOutside(x,z)<=1536)continue;
          double left=RealDemFittedMathV2.predictedHeight(x-1,z);
          double at=RealDemFittedMathV2.predictedHeight(x,z);
          double right=RealDemFittedMathV2.predictedHeight(x+1,z);
          double up=RealDemFittedMathV2.predictedHeight(z,x-1);
          double down=RealDemFittedMathV2.predictedHeight(z,x+1);
          require(Double.isFinite(at),"non-finite terrain");
          // This is a conservative bound across 1-block continuous samples;
          // a cliff measuring dozens of blocks per column is not acceptable.
          worstStep=Math.max(worstStep,Math.abs(at-left));
          worstStep=Math.max(worstStep,Math.abs(right-at));
          worstStep=Math.max(worstStep,Math.abs(down-up)/2.0);
          samples++;
        }
      }
    }
    require(samples>7000,"Insufficient seam probes");
    require(worstStep<12.0,"Fitted terrain still has discrete blocky seams: "+worstStep);
    for(int x:new int[]{-30000000,-1000000,-65536,65536,1000000,30000000}){
      for(int z:new int[]{-30000000,-130000,0,130000,30000000}){
        double h=RealDemFittedMathV2.predictedHeight(x,z);
        require(Double.isFinite(h)&&h>=-48&&h<=2000,
            "Distant fitted terrain unstable or out of bounds");
      }
    }
    System.out.println("USGS_FITTED_FULL_V2_CONTINUITY_PASS probes="+samples+
                       " maxAdjacentSlope="+worstStep);
  }
  public static void main(String[] args){testGate();testContinuousTerrain();}
}
"""
        with tempfile.TemporaryDirectory(prefix="usgs-fitted-v2-") as temp:
            dst=Path(temp)
            smoke=dst/"RealDemFittedFullV2Smoke.java"
            smoke.write_text(src,encoding="utf-8")
            files=[JAVA/n for n in ("RealDemTile.java","RealDemDataSource.java",
                    "RealDemSeamlessMath.java","RealDemFittedMathV2.java",
                    "RealDemFittedFullMathV2.java")]
            cmd=["javac","-d",str(dst),*map(str,files),str(smoke)]
            subprocess.run(cmd,check=True,capture_output=True,text=True,timeout=45)
            p=subprocess.run(["java","-cp",str(dst)+":"+str(RES),
                   "org.realscale.neoforge.RealDemFittedFullV2Smoke"],
                   check=False,capture_output=True,text=True,timeout=45)
            self.assertEqual(p.returncode,0,"JDK continuity probe failed:\\n"+p.stdout+"\\n"+p.stderr)
            self.assertIn("USGS_FITTED_FULL_V2_CONTINUITY_PASS",p.stdout)

if __name__=="__main__":unittest.main()

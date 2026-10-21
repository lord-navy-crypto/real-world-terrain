"""Versioned Natural land hypsometry: no measured, marine, cave or saved-world mutation."""
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
R=ROOT/"neoforge/src/main/resources"
WORLD=R/"data/realscale/worldgen"
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
NEW="tva_natural_hypsometry_v1"
HIST="tva_cinematic_integrated_v1"
N="tva_natural_hypsometry_v1_three_realms"
OLD="tva_sacred_biodiversity_end3_spawn3_v3"
def read(path):return json.loads(path.read_text(encoding="utf-8"))

class LandHypsometryWorldQA(unittest.TestCase):
    def test_isolated_versioned_natural_world_and_four_menu_choices(self):
        old=read(WORLD/"world_preset"/(OLD+".json"))
        new=read(WORLD/"world_preset"/(N+".json"))
        self.assertEqual(old["dimensions"]["minecraft:the_nether"],new["dimensions"]["minecraft:the_nether"])
        self.assertEqual(old["dimensions"]["minecraft:the_end"],new["dimensions"]["minecraft:the_end"])
        o=old["dimensions"]["minecraft:overworld"]["generator"]
        n=new["dimensions"]["minecraft:overworld"]["generator"]
        self.assertEqual(o["biome_source"],n["biome_source"])
        self.assertEqual(o["settings"],"realscale:"+HIST)
        self.assertEqual(n["settings"],"realscale:"+NEW)
        for tag in ("normal","extended"):
            vals=read(R/"data/minecraft/tags/worldgen/world_preset"/(tag+".json"))["values"]
            self.assertEqual(len(vals),4)
            self.assertEqual(vals[0],"realscale:"+N)
            self.assertNotIn("realscale:"+OLD,vals)

    def test_noise_is_same_except_versioned_three_router_fields(self):
        orig=read(WORLD/"noise_settings"/(HIST+".json"))
        upd=read(WORLD/"noise_settings"/(NEW+".json"))
        self.assertEqual(upd["sea_level"],63)
        self.assertEqual(upd["noise"],orig["noise"])
        self.assertEqual(upd["surface_rule"],orig["surface_rule"])
        self.assertEqual(upd["aquifers_enabled"],orig["aquifers_enabled"])
        self.assertEqual(upd["default_fluid"],orig["default_fluid"])
        self.assertEqual(upd["ore_veins_enabled"],orig["ore_veins_enabled"])
        self.assertEqual(upd["spawn_target"],orig["spawn_target"])
        a=orig["noise_router"]
        b=upd["noise_router"]
        keep=lambda d:{k:v for k,v in d.items() if k not in
                      ("depth","final_density","initial_density_without_jaggedness")}
        self.assertEqual(keep(a),keep(b))
        self.assertEqual(b["depth"],"realscale:"+NEW+"/terrain_density")
        self.assertEqual(b["initial_density_without_jaggedness"],"realscale:"+NEW+"/preliminary_density")
        final=dict(b["final_density"]);final["base"]=a["final_density"]["base"]
        self.assertEqual(final,a["final_density"])
        height=read(WORLD/"density_function"/NEW/"height_signal.json")
        self.assertEqual(height,{"type":"minecraft:cache_2d","argument":{
            "type":"realscale:land_hypsometry_v1",
            "base":"realscale:"+HIST+"/height_signal","strength":0.28}})
        terrain=read(WORLD/"density_function"/NEW/"terrain_density.json")
        before=read(WORLD/"density_function"/HIST/"terrain_density.json")
        terrain["argument1"]=before["argument1"]
        self.assertEqual(terrain,before)
        self.assertEqual(read(WORLD/"density_function"/NEW/"preliminary_density.json")["argument1"],
                         "realscale:"+NEW+"/terrain_density")

    def test_far_measured_and_special_families_are_untouched(self):
        hyps=read(WORLD/"world_preset"/(N+".json"))
        for entry,original in [
            ("tva_measured_global_landforms_three_realms","realscale:usgs_global_hybrid_marine"),
            ("tva_alpine_three_realms","realscale:tva_alpine_smoothed"),
            ("tva_canyon_three_realms","realscale:tva_canyon_smoothed")]:
            p=read(WORLD/"world_preset"/(entry+".json"))
            self.assertEqual(p["dimensions"]["minecraft:overworld"]["generator"]["settings"],original)
        before=read(WORLD/"noise_settings/usgs_global_hybrid_marine.json")
        self.assertEqual(before["sea_level"],63)
        self.assertNotIn("land_hypsometry_v1",json.dumps(before))
        register=(JAVA/"RealScaleMod.java").read_text(encoding="utf-8")
        self.assertIn('"land_hypsometry_v1"), LandHypsometryDensity.MAP_CODEC',register)

    def test_monotone_ocean_preserving_production_java_math(self):
        if not (shutil.which("java") and shutil.which("javac")):
            self.skipTest("JDK 21 not installed")
        runner="""package org.realscale.neoforge;
public final class LandHypsometrySmoke {
    public static void main(String[] args) {
        for(double s:new double[]{0,.06,.28,.45}){
            double last=Double.NEGATIVE_INFINITY;
            for(int i=-32000;i<=140000;i++){
                double x=i/10000.0, y=LandHypsometryMath.adjust(x,s);
                if(!Double.isFinite(y)||y<=last)throw new AssertionError("non-monotonic "+x);
                if(x<=0.0&&Double.doubleToRawLongBits(x)!=Double.doubleToRawLongBits(y))
                    throw new AssertionError("sea or ocean modified");
                last=y;
            }
        }
        if(LandHypsometryMath.adjust(2.8,.28)>=2.8)
           throw new AssertionError("land not biased to low elevations");
        System.out.println("LAND_HYPSOMETRY_PASS");
    }
}"""
        with tempfile.TemporaryDirectory() as directory:
            work=Path(directory)
            smoke=work/"LandHypsometrySmoke.java"
            smoke.write_text(runner,encoding="utf-8")
            subprocess.run(["javac","-d",directory,
                str(JAVA/"LandHypsometryMath.java"),str(smoke)],check=True,
                capture_output=True,text=True,timeout=30)
            p=subprocess.run(["java","-cp",directory,
                "org.realscale.neoforge.LandHypsometrySmoke"],check=True,
                capture_output=True,text=True,timeout=30)
            self.assertIn("LAND_HYPSOMETRY_PASS",p.stdout)

if __name__=="__main__":unittest.main()

"""Real compiler and math-equivalence regression for one-file RealScale terrain.

The production NeoForge file has the Mojang codec and all 10 mathematical
shapes in one source. Strip only the codec-facing wrapper for a standalone
JDK 21 parity check against the original three frozen math implementations.
"""
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
WORLD=ROOT/"neoforge/src/main/resources/data/realscale/worldgen"
SOURCE=JAVA/"RealScaleCinematicIntegrated.java"

PARITY_JAVA=r"""
package org.realscale.neoforge;
public final class IntegratedParitySmoke {
    private static void check(boolean ok,String message) {
        if(!ok)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        int samples=0;
        double[][] climates={
            {0.7,0.7}, {0.7,-0.5}, {-0.8,0.6},
            {-0.8,-0.5}, {0.0,0.0}, {0.35,0.09}
        };
        for(int cx=-9;cx<=9;cx++)for(int cz=-9;cz<=9;cz++) {
            long h=CinematicLandformMathV3.hash(cx,cz);
            check(IntegratedMathOnly.V3.hash(cx,cz)==h,"hash parity");
            check(IntegratedMathOnly.V3.siteX(cx,h)==CinematicLandformMathV3.siteX(cx,h),
                    "site X parity");
            check(IntegratedMathOnly.V3.siteZ(cz,h)==CinematicLandformMathV3.siteZ(cz,h),
                    "site Z parity");
            double radius=CinematicLandformMathV3.radius(h);
            check(IntegratedMathOnly.V3.radius(h)==radius,"radius parity");
            for(double[] c:climates)
                check(IntegratedMathOnly.V3.kind(h,c[0],c[1])
                        ==CinematicLandformMathV3.kind(h,c[0],c[1]),"climate parity");
            for(int k=0;k<=CinematicLandformMathV3.NONE;k++)
                for(int ix=-7;ix<=7;ix++)for(int iz=-7;iz<=7;iz++){
                    double x=radius*ix/7.0,z=radius*iz/7.0;
                    double expected=CinematicLandformMathV3.relief(k,x,z,radius,h);
                    double actual=IntegratedMathOnly.V3.relief(k,x,z,radius,h);
                    check(Double.doubleToLongBits(expected)==Double.doubleToLongBits(actual),
                            "exact 10-kind relief parity at "+cx+","+cz+" shape="+k);
                    check(IntegratedMathOnly.V3.apply(k,2.5,x,z,radius,h,0.7)
                            ==CinematicLandformMathV3.apply(k,2.5,x,z,radius,h,0.7),
                            "height application parity");
                    samples++;
                }
        }
        System.out.println("INTEGRATED_ONE_FILE_PARITY_PASS tested_points="+samples);
    }
}
"""

class RealScaleIntegratedSourceTest(unittest.TestCase):
    def test_one_file_compiles_and_matches_existing_terrain_exactly(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Java 21 toolchain unavailable")
        integrated=SOURCE.read_text(encoding="utf-8")
        begin="// === BEGIN INTEGRATED PURE JAVA MATH ==="
        end="// === END INTEGRATED PURE JAVA MATH ==="
        self.assertIn(begin,integrated)
        self.assertIn(end,integrated)
        maths=integrated.split(begin,1)[1].split(end,1)[0]
        for n in ("V1","V2","V3"):
            self.assertIn("static final class "+n+" {",maths)
        with tempfile.TemporaryDirectory(prefix="real-scale-all-in-one-") as d:
            p=Path(d)
            (p/"IntegratedMathOnly.java").write_text(
                "package org.realscale.neoforge;\n"
                "final class IntegratedMathOnly {\n"+maths+"\n}\n",
                encoding="utf-8")
            (p/"IntegratedParitySmoke.java").write_text(PARITY_JAVA,encoding="utf-8")
            sources=[
                JAVA/"CinematicLandformMath.java",
                JAVA/"CinematicLandformMathV2.java",
                JAVA/"CinematicLandformMathV3.java",
                p/"IntegratedMathOnly.java",
                p/"IntegratedParitySmoke.java"
            ]
            result=subprocess.run(["javac","-d",d,*map(str,sources)],
                                  capture_output=True,text=True,timeout=45)
            self.assertEqual(result.returncode,0,result.stderr[-4000:])
            check=subprocess.run(["java","-cp",d,
                    "org.realscale.neoforge.IntegratedParitySmoke"],
                    capture_output=True,text=True,timeout=45)
            self.assertEqual(check.returncode,0,check.stderr[-4000:])
            self.assertIn("INTEGRATED_ONE_FILE_PARITY_PASS",check.stdout)

    def test_neo_forge_codec_is_self_contained_and_versioned(self):
        s=SOURCE.read_text(encoding="utf-8")
        self.assertIn("public final class RealScaleCinematicIntegrated",s)
        self.assertIn("implements DensityFunction.SimpleFunction",s)
        self.assertIn("static final MapCodec<RealScaleCinematicIntegrated> MAP_CODEC",s)
        self.assertIn("new RealScaleCinematicIntegrated(base.mapAll(visitor)",s)
        self.assertIn("V3.kind(",s)
        self.assertIn("V3.apply(",s)
        self.assertIn("Math.floorDiv(x,V3.CELL)",s)
        for legacy in ("CinematicLandformMath.java","CinematicLandformMathV2.java",
                       "CinematicLandformMathV3.java"):
            self.assertNotIn(legacy,s)
        mod=(JAVA/"RealScaleMod.java").read_text()
        self.assertIn('"cinematic_landforms_integrated_v1"), RealScaleCinematicIntegrated.MAP_CODEC',mod)

    def test_integrated_active_menu_and_immutable_archived_noise(self):
        noise=json.loads((WORLD/"noise_settings/tva_cinematic_integrated_v1.json").read_text())
        old=json.loads((WORLD/"noise_settings/tva_cinematic_landforms_v3.json").read_text())
        self.assertEqual(noise["noise"]["height"],old["noise"]["height"])
        self.assertEqual(noise["noise_router"]["temperature"],old["noise_router"]["temperature"])
        self.assertEqual(noise["noise_router"]["vegetation"],old["noise_router"]["vegetation"])
        self.assertEqual(noise["noise_router"]["continents"],old["noise_router"]["continents"])
        self.assertEqual(noise["noise_router"]["depth"],
                         "realscale:tva_cinematic_integrated_v1/terrain_density")
        height=json.loads((WORLD/"density_function/tva_cinematic_integrated_v1/height_signal.json").read_text())
        self.assertEqual(height["argument"]["type"],"realscale:cinematic_landforms_integrated_v1")
        preset=json.loads((WORLD/"world_preset/tva_sacred_biodiversity_end3_spawn3_v3.json").read_text())
        self.assertEqual(preset["dimensions"]["minecraft:overworld"]["generator"]["settings"],
                         "realscale:tva_cinematic_integrated_v1")
        for v in ("v1","v2","v3"):
            self.assertTrue((WORLD/f"noise_settings/tva_cinematic_landforms_{v}.json").is_file())
        for required in ("tva_measured_global_landforms_three_realms",
                         "tva_alpine_three_realms","tva_canyon_three_realms"):
            p=json.loads((WORLD/"world_preset"/(required+".json")).read_text())
            self.assertNotEqual(p["dimensions"]["minecraft:overworld"]["generator"]["settings"],
                                "realscale:tva_cinematic_integrated_v1")
        menus=ROOT/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset"
        self.assertEqual(len(json.loads((menus/"normal.json").read_text())["values"]),4)

if __name__=="__main__":
    unittest.main()

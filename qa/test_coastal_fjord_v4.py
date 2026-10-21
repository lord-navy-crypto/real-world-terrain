"""Regression gates for experimental cold-coast fjord v4. Mathematical smoke != 3D in-game proof."""
import json,shutil,subprocess,tempfile,unittest
from pathlib import Path
R=Path(__file__).resolve().parents[1]
J=R/"neoforge/src/main/java/org/realscale/neoforge"
W=R/"neoforge/src/main/resources/data/realscale/worldgen"
def load(p):return json.loads(p.read_text())
class CoastalFjordV4(unittest.TestCase):
    def test_registration_and_isolation(self):
        self.assertIn('"coastal_fjord_v4"), CoastalFjordDensityV4.MAP_CODEC',(J/"RealScaleMod.java").read_text())
        h=load(W/"density_function/tva_cinematic_landforms_v4/height_signal.json")
        self.assertEqual(h["argument"]["type"],"realscale:coastal_fjord_v4")
        self.assertEqual(h["argument"]["base"],"realscale:tva_cinematic_landforms_v3/height_signal")
        old=load(W/"noise_settings/tva_cinematic_landforms_v3.json")
        new=load(W/"noise_settings/tva_cinematic_landforms_v4.json")
        a=old["noise_router"];b=new["noise_router"]
        for k in ("depth","initial_density_without_jaggedness"):a[k]=b[k]
        a["final_density"]["base"]=b["final_density"]["base"]
        self.assertEqual(old,new,"Do not mutate cave, aquifer, surface or climate routers")
        world=load(W/"world_preset/tva_northern_fjord_experiment_v4_three_realms.json")
        self.assertEqual(world["dimensions"]["minecraft:overworld"]["generator"]["settings"],
                         "realscale:tva_cinematic_landforms_v4")
        for tag in ("normal","extended"):
            root=R/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset"
            names=load(root/(tag+".json"))["values"]
            self.assertEqual(len(names),4)
            self.assertNotIn("realscale:tva_northern_fjord_experiment_v4_three_realms",names)
    def test_production_math_java21(self):
        if not shutil.which("javac") or not shutil.which("java"):self.skipTest("JDK unavailable")
        text=(J/"CoastalFjordDensityV4.java").read_text()
        math=text.split("// === BEGIN INDEPENDENT FJORD MATH ===")[1].split("// === END INDEPENDENT FJORD MATH ===")[0]
        prog="""package org.realscale.neoforge;
public class FjordSmoke {
""" + math + """
 static void require(boolean v,String s){if(!v)throw new AssertionError(s);}
 public static void main(String[] args) {
   for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++){
     long h=FjordMath.hash(x,z);
     double px=FjordMath.siteX(x,h),pz=FjordMath.siteZ(z,h);
     require(px>x*FjordMath.CELL+FjordMath.RADIUS && px<(x+1)*FjordMath.CELL-FjordMath.RADIUS,"x boundary");
     require(pz>z*FjordMath.CELL+FjordMath.RADIUS && pz<(z+1)*FjordMath.CELL-FjordMath.RADIUS,"z boundary");
   }
   double phase=FjordMath.phase(FjordMath.hash(0,0));
   for(int u=-700;u<=700;u+=20){
     double v=66*Math.sin(u/315.0+phase)+21*Math.sin(u/113.0-phase*0.7);
     double old=0.90+u*0.001;
     require(FjordMath.carve(old,u,v,0,phase)<-0.04,"submerged channel "+u);
   }
   for(int x=-1500;x<=1500;x+=17)for(int z=-1500;z<=1500;z+=43){
     double old=0.1+x*.001,now=FjordMath.carve(old,x,z,0,phase);
     require(Double.isFinite(now)&&now<=old,"finite erosion only");
     require(now==FjordMath.carve(old,x,z,0,phase),"deterministic");
     if(Math.hypot(x,z)>=FjordMath.RADIUS)require(now==old,"site rim");
   }
   System.out.println("FJORD_V4_JDK_SMOKE_PASS");
 }
}
"""
        with tempfile.TemporaryDirectory() as tmp:
            f=Path(tmp)/"FjordSmoke.java";f.write_text(prog)
            subprocess.run(["javac","-d",tmp,str(f)],check=True,capture_output=True,timeout=45)
            r=subprocess.run(["java","-cp",tmp,"org.realscale.neoforge.FjordSmoke"],
                             check=True,capture_output=True,text=True,timeout=45)
            self.assertIn("FJORD_V4_JDK_SMOKE_PASS",r.stdout)
if __name__=="__main__":unittest.main()

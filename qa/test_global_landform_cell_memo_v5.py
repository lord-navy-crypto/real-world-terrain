"""Compile production Global Landform V3 site memo and compare bit-exact
legacy world-absolute hash, center, radius, radius gate, and lazy kind results.
No migration, worldgen seed changes or geometry approximation permitted.
"""
import shutil,subprocess,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"

class GlobalLandformMemoV5QA(unittest.TestCase):
    def test_production_code_lazy_selector_and_fresh_density_memo(self):
        src=(JAVA/"RegionalGlobalLandformDensity.java").read_text()
        self.assertIn("ThreadLocal.withInitial(RegionalGlobalLandformCellMemo::new)",src)
        self.assertIn("site.at(cellX,cellZ)",src)
        self.assertIn("site.hash,selector.compute(anchor)",src)
        self.assertIn("site.edgeSquared",src)
        self.assertIn("base.mapAll(visitor)",src)
        self.assertLess(src.index("if(dx*dx+dz*dz>=site.edgeSquared)return current;"),
                        src.index("selector.compute(anchor)"))

    def test_real_java_v3_geometry_matches_original_exact_bits(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("JDK 21 unavailable")
        code="""package org.realscale.neoforge;
public class GlobalCellMemoV5Smoke {
 static void req(boolean truth,String msg){if(!truth)throw new AssertionError(msg);}
 static boolean same(double a,double b){
    return Double.doubleToRawLongBits(a)==Double.doubleToRawLongBits(b);
 }
 public static void main(String[] args) {
   RegionalGlobalLandformCellMemo m=new RegionalGlobalLandformCellMemo();
   long samples=0,inside=0;
   for(int cx=-2400;cx<=2400;cx+=97){
     for(int cz=-2400;cz<=2400;cz+=113){
       long h=RegionalLandformMath.hashV3(cx,cz);
       int x=RegionalLandformMath.centerXV3(cx,h),
           z=RegionalLandformMath.centerZV3(cz,h);
       double radius=RegionalLandformMath.radiusV3(h);
       for(int k=0;k<25;k++){
         int qx=x+k*191-1000,qz=z+k*173-1100;
         int gx=Math.floorDiv(qx,RegionalLandformMath.CELL_V3),
             gz=Math.floorDiv(qz,RegionalLandformMath.CELL_V3);
         m.at(gx,gz);
         long expected=RegionalLandformMath.hashV3(gx,gz);
         int mx=RegionalLandformMath.centerXV3(gx,expected);
         int mz=RegionalLandformMath.centerZV3(gz,expected);
         double rad=RegionalLandformMath.radiusV3(expected);
         req(m.hash==expected&&m.siteX==mx&&m.siteZ==mz,
             "Different global site at "+qx+","+qz);
         req(same(m.radius,rad),"Different radius");
         req(same(m.edgeSquared,1.166400001*rad*rad),
             "Different density gate (bit exact)");
         double dx=(double)qx-m.siteX,dz=(double)qz-m.siteZ;
         boolean original=dx*dx+dz*dz<1.166400001*rad*rad;
         boolean cached=dx*dx+dz*dz<m.edgeSquared;
         req(original==cached,"Candidate geometry changed");
         if(original)inside++;
         samples++;
       }
     }
   }
   m.at(-1,0);
   m.hasKind=true;
   m.kind=RegionalLandformMath.kindV3(m.hash,0.15);
   m.at(-1,0);
   req(m.hasKind,"same cell must keep previously computed selector");
   m.at(0,0);
   req(!m.hasKind,"different cell must invalidate selector");
   req(m.geometryHits>samples/2,"no geometry reuse");
   req(samples>40000 && inside>1000,"insufficient site coverage");
   System.out.println("GLOBAL_LANDFORM_V5_REAL_JAVA_PASS samples="+samples+
                      " geometryHits="+m.geometryHits+
                      " geometryMisses="+m.geometryMisses);
 }
}
"""
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            smoke=root/"GlobalCellMemoV5Smoke.java"
            smoke.write_text(code,encoding="utf-8")
            subprocess.run(["javac","-d",str(root),
                str(JAVA/"GeoMath.java"),str(JAVA/"RegionalLandformMath.java"),
                str(JAVA/"RegionalGlobalLandformCellMemo.java"),str(smoke)],
                capture_output=True,text=True,check=True,timeout=35)
            p=subprocess.run(["java","-cp",str(root),
                "org.realscale.neoforge.GlobalCellMemoV5Smoke"],
                capture_output=True,text=True,timeout=35)
            self.assertEqual(p.returncode,0,p.stdout+"\n"+p.stderr)
            self.assertIn("GLOBAL_LANDFORM_V5_REAL_JAVA_PASS",p.stdout)

if __name__=="__main__":unittest.main()

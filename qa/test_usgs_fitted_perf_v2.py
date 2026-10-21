"""Compile and exercise the ACTUAL production Java cache used by USGS V2.

Confirms output-value identity, signed absolute keys, overwrite collisions,
bounded memory, independent worker instances and vertical cache reuse. This
tests no mock Minecraft chunk generator and makes no unsupported FPS claim.
"""
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
WG=ROOT/"neoforge/src/main/resources/data/realscale/worldgen"

class ActualUSGSColumnCachePerfQA(unittest.TestCase):
    def test_only_y_invariant_continent_weight_is_memoized(self):
        continental=json.loads((WG/"density_function/realism_max/continental_field.json").read_text())
        self.assertEqual(continental["type"],"minecraft:flat_cache")
        self.assertEqual(continental["argument"]["type"],"minecraft:shifted_noise")
        self.assertEqual(continental["argument"]["y_scale"],0.0)
        source=(JAVA/"RealDemFittedFullDensityV2.java").read_text()
        self.assertIn("ThreadLocal.withInitial(RealDemFittedColumnCache::new)",source)
        self.assertIn("if(!memo.lookup(x,z))",source)
        self.assertIn("if(weight>=1.0)return fitted;",source)
        self.assertIn("if(weight<=0.0)return base.compute(ctx);",source)
        self.assertLess(source.index("if(weight>=1.0)return fitted;"),
                        source.index("final double original=base.compute(ctx);"))
        self.assertIn("base.mapAll(visitor),continents.mapAll(visitor)",source)
        math=(JAVA/"RealDemFittedMathV2.java").read_text()
        self.assertIn("if(d>=2560.0)return clamp(broad(x,z),MIN,MAX);",math)

    def test_real_java_4096_slot_cache_keys_collision_thread_isolation(self):
        if not (shutil.which("java") and shutil.which("javac")):
            self.skipTest("JDK 21 not installed")
        main="""package org.realscale.neoforge;
public final class FittedCacheBench {
  private static void ok(boolean b,String why){if(!b)throw new AssertionError(why);}
  public static void main(String[] args)throws Exception{
    RealDemFittedColumnCache c=new RealDemFittedColumnCache();
    ok(RealDemFittedColumnCache.SLOTS==4096,"Cache exceeded fixed bound");
    int[][] points=new int[1024][2];
    for(int i=0;i<1024;i++){
      points[i][0]=-120000+i*7;
      points[i][1]=100000-i*9;
      ok(!c.lookup(points[i][0],points[i][1]),"Fresh world coordinate hit");
      c.store(points[i][0],points[i][1],i*.00001,i*1.5);
    }
    long missesAfterFirstPass=c.misses();
    // In a height-2096 world, re-read the same horizontal signals across
    // many interleaved slices. A last-XZ-only cache would miss constantly.
    for(int layer=0;layer<128;layer++){
      for(int i=0;i<1024;i++){
        int x=points[i][0],z=points[i][1];
        if(!c.lookup(x,z))c.store(x,z,i*.00001,i*1.5);
        ok(Double.doubleToRawLongBits(c.weight())==
           Double.doubleToRawLongBits(i*.00001),"Bad cached weight");
        ok(Double.doubleToRawLongBits(c.height())==
           Double.doubleToRawLongBits(i*1.5),"Bad cached height");
      }
    }
    // Direct-map cache may legitimately collide, but must never serve a
    // WRONG column. The metrics are observational rather than mandatory hit
    // rate for this dataset (hash collisions are allowed).
    ok(c.hits()>90000,"No cache reuse during vertical traversal");
    ok(c.misses()<40000,"No meaningful reduction in X/Z recomputes");
    int x=123456789,z=-987654321;
    ok(!c.lookup(x,z),"Fresh signed pair accidentally aliased");
    c.store(x,z,.55,321);
    ok(c.lookup(x,z) && c.height()==321 && c.weight()==.55,
       "Negative Z not retained");
    ok(!c.lookup(-987654321,123456789),"Swapped signed coordinates aliased");
    // Force exact hash collision, verify old entry never returns wrong data.
    int x1=0,z1=0,index=RealDemFittedColumnCache.index(x1,z1),x2=0,z2=0;
    for(int i=1;i<200000;i++){
      if(RealDemFittedColumnCache.index(i,-i)==index){x2=i;z2=-i;break;}
    }
    ok(x2!=0,"No collision fixture found");
    c.store(x1,z1,0.125,18);
    c.store(x2,z2,0.75,99);
    ok(!c.lookup(x1,z1),"Cache collision leaked the previous X/Z");
    ok(c.lookup(x2,z2)&&c.weight()==0.75&&c.height()==99,
        "Collision corrupts the current column");
    RealDemFittedColumnCache second=new RealDemFittedColumnCache();
    ok(!second.lookup(x2,z2),"Cache leaked to fresh per-thread instance");
    System.out.println("USGS_V2_ACTUAL_JAVA_CACHE_PASS hits="+c.hits()+
                       " misses="+c.misses()+
                       " coldMisses="+missesAfterFirstPass);
  }
}
"""
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            java=root/"FittedCacheBench.java"
            java.write_text(main,encoding="utf-8")
            subprocess.run(["javac","-d",str(root),
                str(JAVA/"RealDemFittedColumnCache.java"),str(java)],
                check=True,capture_output=True,text=True,timeout=25)
            proc=subprocess.run(["java","-cp",str(root),
                    "org.realscale.neoforge.FittedCacheBench"],
                    capture_output=True,text=True,timeout=40)
            self.assertEqual(proc.returncode,0,proc.stdout+"\n"+proc.stderr)
            self.assertIn("USGS_V2_ACTUAL_JAVA_CACHE_PASS",proc.stdout)

if __name__=="__main__":unittest.main()

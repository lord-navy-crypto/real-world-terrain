"""Compile actual Java feature water cache; compare exact 7x7 scan results.

No terrain or structure geometry may change. Caching only lives until the
first write; a fresh placement gets a fresh cache and fresh source state.
"""
import shutil,subprocess,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"

class LandmarkWaterV4QA(unittest.TestCase):
    def test_production_feature_calls_one_placement_scoped_cache(self):
        src=(JAVA/"GeographicLandmarkFeature.java").read_text(encoding="utf-8")
        self.assertIn("new LandmarkWaterMemo((x,y,z)->",src)
        self.assertIn("if(!inside(chunk,p))continue;",src)
        self.assertIn("memo.isSourceWater(p.getX(),p.getY(),p.getZ())",src)
        self.assertIn("sourceWaterNearby(world,foot,chunk,waterMemo)",src)
        self.assertLess(src.index("LandmarkWaterMemo waterMemo="),
                        src.index("for(int attempt=0;attempt<3;attempt++)"))

    def test_compiled_production_java_exact_xyz_and_collision_parity(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("JDK unavailable")
        source="""package org.realscale.neoforge;
public final class LandmarkWaterV4Smoke {
 static void req(boolean b,String s){if(!b)throw new AssertionError(s);}
 static boolean water(int x,int y,int z){
   long h=((long)x*0x9e3779b97f4a7c15L) ^ ((long)z*0xc2b2ae3d27d4eb4fL)
          ^ ((long)y*0x165667b19e3779f9L);
   return Math.floorMod(h ^(h>>>21),7L)==0L;
 }
 static boolean near(int x,int y,int z,int minX,int minZ,
                     LandmarkWaterMemo memo){
   for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){
     int a=x+dx,b=z+dz;
     if(a<minX+1||a>minX+14||b<minZ+1||b>minZ+14)continue;
     if(memo.isSourceWater(a,y-1,b))return true;
   }
   return false;
 }
 static boolean reference(int x,int y,int z,int minX,int minZ) {
   for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){
     int a=x+dx,b=z+dz;
     if(a<minX+1||a>minX+14||b<minZ+1||b>minZ+14)continue;
     if(water(a,y-1,b))return true;
   }
   return false;
 }
 public static void main(String[] args){
   long total=0,hits=0,misses=0;
   for(int chunkX=-192;chunkX<=192;chunkX+=3){
     for(int chunkZ=-48;chunkZ<=48;chunkZ+=3){
       int minX=chunkX*16,minZ=chunkZ*16;
       LandmarkWaterMemo memo=new LandmarkWaterMemo(LandmarkWaterV4Smoke::water);
       for(int y:new int[]{-60,0,63,124,1788}){
         for(int anchor=0;anchor<3;anchor++){
           int x=minX+5+anchor*3,z=minZ+6+anchor*2;
           boolean old=reference(x,y,z,minX,minZ);
           boolean now=near(x,y,z,minX,minZ,memo);
           req(now==old,"water scan changed "+x+","+y+","+z);
           req(near(x,y,z,minX,minZ,memo)==old,"repeat scan changed");
           total++;
         }
       }
       hits+=memo.hits();misses+=memo.misses();
     }
   }
   req(total>60000,"too few comparisons");
   req(hits>misses/2,"overlap cache not reusing positions");
   int a=0,b=0,c=0,other=0;
   int goal=LandmarkWaterMemo.slot(a,b,c);
   for(int n=1;n<30000;n++){
     if(LandmarkWaterMemo.slot(n,n*7,-n)==goal){other=n;break;}
   }
   req(other!=0,"collision fixture missing");
   LandmarkWaterMemo cache=new LandmarkWaterMemo((x,y,z)->x==0);
   req(cache.isSourceWater(a,b,c),"first value");
   req(!cache.isSourceWater(other,other*7,-other),"alias returned");
   req(cache.isSourceWater(a,b,c),"collision recompute");
   req(cache.isSourceWater(0,10,0),"same X but new Y must recompute correctly");
   LandmarkWaterMemo otherPlace=new LandmarkWaterMemo((x,y,z)->false);
   req(!otherPlace.isSourceWater(0,0,0),"stale world state carried between placements");
   System.out.println("LANDMARK_WATER_V4_JAVA_PASS scans="+total+
                      " memoHits="+hits+" misses="+misses);
 }
}
"""
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp);runner=out/"LandmarkWaterV4Smoke.java"
            runner.write_text(source,encoding="utf-8")
            subprocess.run(["javac","-d",str(out),
                str(JAVA/"LandmarkWaterMemo.java"),str(runner)],
                capture_output=True,text=True,check=True,timeout=30)
            proc=subprocess.run(["java","-cp",str(out),
                "org.realscale.neoforge.LandmarkWaterV4Smoke"],
                capture_output=True,text=True,timeout=45)
            self.assertEqual(proc.returncode,0,proc.stdout+"\n"+proc.stderr)
            self.assertIn("LANDMARK_WATER_V4_JAVA_PASS",proc.stdout)

if __name__=="__main__":unittest.main()

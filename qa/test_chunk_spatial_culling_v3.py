"""Compile real production sky/wonder geometry, compare old 16x16 scans to
new conservative spatial culling at deterministic signed world coordinates.
No biome, terrain, cave or structure contents may be dropped.
"""
import shutil,subprocess,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"

class ChunkSpatialCullingV3QA(unittest.TestCase):
    def test_feature_integration(self):
        sky=(JAVA/"SkyIslandFeature.java").read_text()
        wonders=(JAVA/"GeoWonderRegionFeature.java").read_text()
        self.assertIn("if(!SkyIslandLayout.mayIntersectChunk(base,chunkX,chunkZ))",sky)
        self.assertIn("final int firstX=Math.max(minX,site.x-site.r);",wonders)
        self.assertIn("final int firstZ=Math.max(minZ,site.z-site.r);",wonders)
        self.assertIn("for(int x=firstX;x<=lastX;x++)for(int z=firstZ;z<=lastZ;z++)",wonders)

    def test_actual_wonder_site_cache_preserves_null_and_site_geometry(self):
        feature=(JAVA/"GeoWonderRegionFeature.java").read_text()
        self.assertIn("ThreadLocal.withInitial(",feature)
        self.assertIn("cachedSites.get().get(gx,gz)",feature)
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("JDK unavailable")
        runner="""package org.realscale.neoforge;
public class GeoSiteMemoSmoke {
  static void same(GeoWonderGeometry.Site got,GeoWonderGeometry.Site original,
                   int gx,int gz,int kind){
    if(original==null) {
      if(got!=null)throw new AssertionError("Null site changed "+gx+","+gz);
      return;
    }
    if(got==null||got.x!=original.x||got.z!=original.z||
       got.r!=original.r||got.seed!=original.seed)
       throw new AssertionError("Site mismatch "+gx+","+gz+" kind="+kind);
  }
  public static void main(String[] args) {
    long hits=0,misses=0;
    for(int kind=0;kind<11;kind++) {
      GeoWonderSiteCache cache=new GeoWonderSiteCache(kind);
      for(int ix=-90;ix<90;ix++)for(int iz=-10;iz<10;iz++){
        for(int sx=-1;sx<=1;sx++)for(int sz=-1;sz<=1;sz++){
          int gx=ix+sx,gz=iz+sz;
          GeoWonderGeometry.Site old=GeoWonderGeometry.surfaceSite(gx,gz,kind);
          GeoWonderGeometry.Site now=cache.get(gx,gz);
          same(now,old,gx,gz,kind);
        }
      }
      for(int i=0;i<1000;i++){
         int gx=(int)(i*14237L%200000)-100000,
             gz=(int)(i*-17257L%200000);
         same(cache.get(gx,gz),
              GeoWonderGeometry.surfaceSite(gx,gz,kind),gx,gz,kind);
      }
      hits+=cache.hits();misses+=cache.misses();
    }
    if(hits*2<misses)
      throw new AssertionError("No neighboring site memo reuse "+hits+"/"+misses);
    GeoWonderSiteCache first=new GeoWonderSiteCache(0);
    GeoWonderSiteCache other=new GeoWonderSiteCache(10);
    same(first.get(999999,-999999),
         GeoWonderGeometry.surfaceSite(999999,-999999,0),999999,-999999,0);
    same(other.get(999999,-999999),
         GeoWonderGeometry.surfaceSite(999999,-999999,10),999999,-999999,10);
    System.out.println("WONDER_SITE_CACHE_PASS hits="+hits+" misses="+misses);
  }
}
"""
        with tempfile.TemporaryDirectory() as tmp:
            work=Path(tmp);java=work/"GeoSiteMemoSmoke.java"
            java.write_text(runner,encoding="utf-8")
            subprocess.run(["javac","-d",str(work),
                str(JAVA/"GeoWonderGeometry.java"),str(JAVA/"GeoWonderSiteCache.java"),str(java)],
                capture_output=True,text=True,check=True,timeout=40)
            result=subprocess.run(["java","-cp",str(work),
                "org.realscale.neoforge.GeoSiteMemoSmoke"],
                capture_output=True,text=True,timeout=60)
            self.assertEqual(result.returncode,0,result.stdout+"\\n"+result.stderr)
            self.assertIn("WONDER_SITE_CACHE_PASS",result.stdout)

    def test_real_java_old_vs_new_preserves_every_candidate_block(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("JDK unavailable")
        runner="""package org.realscale.neoforge;
public final class SpatialCullingV3Smoke {
 static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
 static boolean intersectsIsland(SkyIslandLayout.Site island,int chunkX,int chunkZ){
   long minX=(long)chunkX*16,minZ=(long)chunkZ*16;
   int r=island.radius();
   return (long)island.x()+r>=minX&&(long)island.x()-r<minX+16
       &&(long)island.z()+r>=minZ&&(long)island.z()-r<minZ+16;
 }
 static long skyCandidates=0,skyRejected=0,wonderColumnsOld=0,wonderColumnsNew=0;
 static void testChunk(int cx,int cz){
   long centerX=(long)cx*16+8,centerZ=(long)cz*16+8;
   // Existing feature reads signed block ints.
   if(Math.abs(centerX)>29000000||Math.abs(centerZ)>29000000)return;
   int x=(int)centerX,z=(int)centerZ;
   for(int gx=-1;gx<=1;gx++)for(int gz=-1;gz<=1;gz++){
      SkyIslandLayout.Site base=SkyIslandLayout.at(
          x+gx*SkyIslandLayout.GRID,z+gz*SkyIslandLayout.GRID);
      boolean possible=SkyIslandLayout.mayIntersectChunk(base,cx,cz);
      skyCandidates++;
      if(!possible)skyRejected++;
      for(int slot=0;slot<SkyIslandLayout.SLOTS;slot++){
        SkyIslandLayout.Site island=SkyIslandLayout.satellite(base,slot);
        if(intersectsIsland(island,cx,cz)){
          check(possible,"Sky island accidentally culled at chunk "+cx+","+cz);
        }
      }
   }
   int minX=cx*16,minZ=cz*16;
   int ax=GeoWonderGeometry.gridAtChunk(minX),
       az=GeoWonderGeometry.gridAtChunk(minZ);
   for(int kind=0;kind<11;kind++)
     for(int a=-1;a<=1;a++)for(int b=-1;b<=1;b++){
       GeoWonderGeometry.Site site=
         GeoWonderGeometry.surfaceSite(ax+a,az+b,kind);
       if(site==null||!GeoWonderGeometry.intersects(site,minX,minZ))continue;
       int loX=Math.max(minX,site.x-site.r);
       int hiX=Math.min(minX+15,site.x+site.r);
       int loZ=Math.max(minZ,site.z-site.r);
       int hiZ=Math.min(minZ+15,site.z+site.r);
       for(int xx=minX;xx<minX+16;xx++)for(int zz=minZ;zz<minZ+16;zz++){
         boolean old=GeoWonderGeometry.contains(site,xx,zz);
         boolean candidate=xx>=loX&&xx<=hiX&&zz>=loZ&&zz<=hiZ;
         if(old)check(candidate,"Omitted wonder block "+xx+","+zz);
         wonderColumnsOld++;
         if(candidate)wonderColumnsNew++;
       }
     }
 }
 public static void main(String[] args){
   for(int cx=-180;cx<=180;cx+=3)
     for(int cz=-180;cz<=180;cz+=3)testChunk(cx,cz);
   // Include million-block and signed-lattice corners, not only near origin.
   for(int i=0;i<6000;i++){
     int cx=(int)(Math.floorMod(10007L*i*i+783711L*i,350001L)-175000);
     int cz=(int)(Math.floorMod(39119L*i*i+103011L*i,350001L)-175000);
     testChunk(cx,cz);
   }
   check(skyCandidates>100000,"insufficient sky coverage");
   check(skyRejected*10>skyCandidates*7,"sky filter not rejecting empty cells");
   check(wonderColumnsOld>wonderColumnsNew,"wonder AABB no work saved");
   System.out.println("CHUNK_SPATIAL_CULLING_PASS skySkipped="+skyRejected+
          " skyCandidates="+skyCandidates+" wonderOriginal="+wonderColumnsOld+
          " wonderBounding="+wonderColumnsNew);
 }
}
"""
        with tempfile.TemporaryDirectory() as tmp:
            work=Path(tmp);java=work/"SpatialCullingV3Smoke.java"
            java.write_text(runner,encoding="utf-8")
            subprocess.run(["javac","-d",str(work),
                str(JAVA/"SkyIslandLayout.java"),str(JAVA/"GeoWonderGeometry.java"),str(java)],
                capture_output=True,text=True,check=True,timeout=40)
            result=subprocess.run(["java","-cp",str(work),
                "org.realscale.neoforge.SpatialCullingV3Smoke"],
                capture_output=True,text=True,timeout=60)
            self.assertEqual(result.returncode,0,result.stdout+"\n"+result.stderr)
            self.assertIn("CHUNK_SPATIAL_CULLING_PASS",result.stdout)

if __name__=="__main__":unittest.main()

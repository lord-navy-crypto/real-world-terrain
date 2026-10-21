"""Production Java 21 cache validation for high-vertical-range biome samples.

Checks exact object identity, collisions, sampler-reset support and lookup
hit-rate vs the older 2048-entry direct map. Collision eviction is permitted;
a wrong biome Holder is never permitted. No gameplay speed-up is assumed.
"""
import shutil,subprocess,tempfile,unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"

class QuartResultsPerfQA(unittest.TestCase):
    def test_production_java_cache_bounded_collision_and_sampler_reset(self):
        if not shutil.which("java") or not shutil.which("javac"):
            self.skipTest("JDK unavailable")
        code="""package org.realscale.neoforge;
public final class QuartCachePerfSmoke {
  static void yes(boolean truth,String detail) {
    if(!truth)throw new AssertionError(detail);
  }
  static int oldIndex(long key){
    long h=key^(key>>>21)^(key>>>42);
    h^=h>>>32;
    return ((int)h^((int)h>>>16))&(2048-1);
  }
  public static void main(String[] args){
    QuartBiomeResultCache<Object> cache=new QuartBiomeResultCache<>();
    yes(QuartBiomeResultCache.SLOTS==8192,"unbounded or regressed capacity");
    Object one=new Object(),two=new Object();
    int collisionA=-1,collisionB=-1;
    for(int n=1;n<100000;n++){
      if(QuartBiomeResultCache.bucket(n)==QuartBiomeResultCache.bucket(0)){
        collisionA=0;collisionB=n;break;
      }
    }
    yes(collisionB>0,"missing collision fixture");
    cache.put(collisionA,one);
    cache.put(collisionB,two);
    yes(cache.get(collisionA)==one && cache.get(collisionB)==two,
        "two-way lookup must preserve both biome identities");
    long[] keys=new long[2560];
    Object[] exact=new Object[keys.length];
    // A deterministic interleaved sample of many XZ/Y quart coordinates.
    for(int i=0;i<keys.length;i++){
      keys[i]=(((long)(-64000+i*11))<<32)
                ^(0xffffffffL&(-1048576+i*13));
      exact[i]=new Object();
    }
    cache.clear();
    Object[] oldValues=new Object[2048];
    long[] oldKeys=new long[2048];
    int oldHit=0,newHit=0,oldMiss=0,newMiss=0;
    for(int round=0;round<32;round++){
      for(int i=0;i<keys.length;i++){
        long key=keys[i];
        Object found=cache.get(key);
        if(found==null){newMiss++;cache.put(key,exact[i]);}
        else{newHit++;yes(found==exact[i],"wrong biome on cache hit");}
        int slot=oldIndex(key);
        if(oldValues[slot]!=null && oldKeys[slot]==key){
          oldHit++;
          yes(oldValues[slot]==exact[i],"baseline cache alias");
        }else{oldMiss++;oldKeys[slot]=key;oldValues[slot]=exact[i];}
      }
    }
    yes(newHit>oldHit,"new two-way cache failed to improve hit count");
    yes(newMiss<oldMiss,"two-way cache did not reduce recomputation");
    cache.clear();
    for(long key:keys)yes(cache.get(key)==null,"sampler clear leaked biomes");
    Object fresh=new Object();
    cache.put(-9223372036854775807L,fresh);
    yes(cache.get(-9223372036854775807L)==fresh,"negative packed quart failure");
    yes(cache.get(-9223372036854775806L)==null,"packed quart collision mistaken for hit");
    QuartBiomeResultCache<Object> second=new QuartBiomeResultCache<>();
    yes(second.get(-9223372036854775807L)==null,"worker cache shared accidentally");
    System.out.println("QUART_RESULT_CACHE_REAL_JAVA_PASS oldHits="+oldHit+
        " newHits="+newHit+" oldMisses="+oldMiss+" newMisses="+newMiss);
  }
}
"""
        with tempfile.TemporaryDirectory() as tmp:
            tmp=Path(tmp);runner=tmp/"QuartCachePerfSmoke.java"
            runner.write_text(code,encoding="utf-8")
            subprocess.run(["javac","-d",str(tmp),
                str(JAVA/"QuartBiomeResultCache.java"),str(runner)],
                check=True,capture_output=True,text=True,timeout=30)
            p=subprocess.run(["java","-cp",str(tmp),
                "org.realscale.neoforge.QuartCachePerfSmoke"],
                capture_output=True,text=True,timeout=35)
            self.assertEqual(p.returncode,0,p.stdout+"\n"+p.stderr)
            self.assertIn("QUART_RESULT_CACHE_REAL_JAVA_PASS",p.stdout)
if __name__=="__main__":unittest.main()

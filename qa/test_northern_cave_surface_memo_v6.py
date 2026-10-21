"""Real JDK proof that northern v4 source-biome height memo is bounded,
sampler-specific, key-exact for negatives and collision-safe.
Cave habitat classification is checked against unchanged original math.
"""
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"

class NorthernV6SourceMemo(unittest.TestCase):
    def test_source_uses_same_y_and_exact_selector(self):
        code=(JAVA/"NorthernCaveBiomeSourceV4.java").read_text()
        self.assertIn("ThreadLocal.withInitial(NorthernCaveSurfaceMemoV6::new)",code)
        self.assertIn("if(qy>9||qy< -12)return original;",code)
        self.assertIn('if(originalName.equals("minecraft:deep_dark"))return original;',code)
        self.assertIn("memo.prepare(sampler);",code)
        self.assertIn("if(!memo.lookup(qx,qz))",code)
        self.assertIn("source.getNoiseBiome(qx,16,qz,sampler)",code)
        self.assertIn("NorthernCaveBiomeMathV4.regionHash(qx,qz)",code)
        self.assertIn("originalName,memo.name(),qy,memo.regionHash()",code)

    def test_production_cache_and_original_cave_math_in_jdk(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("No JDK")
        source="""package org.realscale.neoforge;
public final class NorthernV6MemoSmoke {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static String surface(int x,int z,int seed){
     long h=NorthernCaveBiomeMathV4.regionHash(x,z)^seed;
     return switch((int)Math.floorMod(h,9)){
       case 0 -> "minecraft:snowy_plains";
       case 1 -> "minecraft:stony_peaks";
       case 2 -> "minecraft:beach";
       case 3 -> "minecraft:river";
       case 4 -> "minecraft:badlands";
       case 5 -> "minecraft:ocean";
       case 6 -> "minecraft:grove";
       case 7 -> "minecraft:plains";
       default -> "minecraft:swamp";
     };
 }
 public static void main(String[] args) {
   NorthernCaveSurfaceMemoV6 c=new NorthernCaveSurfaceMemoV6();
   check(NorthernCaveSurfaceMemoV6.SLOTS==2048,"unbounded");
   Object firstSampler=new Object(),secondSampler=new Object();
   String[] originals={"minecraft:deep_dark","minecraft:lush_caves",
         "minecraft:dripstone_caves","minecraft:plains","minecraft:ocean"};
   long cases=0;
   for(int seed=0;seed<3;seed++){
     Object sampler=seed==0?firstSampler:seed==1?secondSampler:firstSampler;
     c.prepare(sampler);
     for(int col=0;col<560;col++){
       int x=-110000+col*23,z=104000-col*37;
       for(int qy=-12;qy<=9;qy++){
         if(!c.lookup(x,z))c.store(x,z,surface(x,z,seed),
                                   NorthernCaveBiomeMathV4.regionHash(x,z));
         String orig=originals[(col+qy+originals.length*100)%originals.length];
         String expect=NorthernCaveBiomeMathV4.select(orig,surface(x,z,seed),
                     qy,NorthernCaveBiomeMathV4.regionHash(x,z));
         String actual=NorthernCaveBiomeMathV4.select(orig,c.name(),qy,c.regionHash());
         check(java.util.Objects.equals(expect,actual),
             "changed cave classification seed="+seed+" "+x+" "+z+" "+qy);
         cases++;
       }
     }
   }
   check(c.hits()>20000,"no reuse across heights");
   c.prepare(new Object());
   check(!c.lookup(-110000,104000),"sampler invalidation failed");
   int a=0,b=0,k=NorthernCaveSurfaceMemoV6.slot(a,b),other=0;
   for(int x=1;x<200000;x++){
     if(NorthernCaveSurfaceMemoV6.slot(x,-x)==k){other=x;break;}
   }
   check(other!=0,"no collision found");
   c.store(0,0,"minecraft:plains",77);
   c.store(other,-other,"minecraft:ocean",88);
   check(!c.lookup(0,0),"cache collision incorrectly hit overwritten slot");
   check(c.lookup(other,-other)&&"minecraft:ocean".equals(c.name())&&c.regionHash()==88,
       "collision result changed");
   check(!c.lookup(other,-other-1),"different signed coordinates collided");
   System.out.println("NORTHERN_V6_SOURCE_MEMO_PASS cases="+cases+
       " hits="+c.hits()+" misses="+c.misses());
 }
}
"""
        with tempfile.TemporaryDirectory() as path:
            root=Path(path);java=root/"NorthernV6MemoSmoke.java"
            java.write_text(source,encoding="utf-8")
            subprocess.run(["javac","-d",str(root),
                str(JAVA/"NorthernCaveSurfaceMemoV6.java"),
                str(JAVA/"NorthernCaveBiomeMathV4.java"),str(java)],
                capture_output=True,text=True,check=True,timeout=35)
            p=subprocess.run(["java","-cp",str(root),
               "org.realscale.neoforge.NorthernV6MemoSmoke"],
               capture_output=True,text=True,timeout=45)
            self.assertEqual(p.returncode,0,p.stdout+"\n"+p.stderr)
            self.assertIn("NORTHERN_V6_SOURCE_MEMO_PASS",p.stdout)
if __name__=="__main__":unittest.main()

"""Exercise production read-only Anvil NBT sampler cache with real fixture.
No server, fake terrain, or world-save mutation in production code.
"""
import shutil,struct,subprocess,tempfile,unittest,zlib
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"

def nbt_heights(surface,floor):
    def entry(name,val):
        # A 2096-high world's 12-bit padded-packed heightmap: 52 long words.
        longs=[0]*52
        for i in range(256):
            word=i//5
            offset=(i%5)*12
            longs[word]|=(val&4095)<<offset
        label=name.encode("ascii")
        return bytes([12])+struct.pack(">H",len(label))+label+struct.pack(">I",len(longs))+b"".join(struct.pack(">Q",v) for v in longs)
    label=b"Heightmaps"
    return b"\x0a\x00\x00"+b"\x0a"+struct.pack(">H",len(label))+label+entry("WORLD_SURFACE",surface+64)+entry("OCEAN_FLOOR",floor+64)+b"\x00\x00"

def write_mca(world,x,z,surface=115,floor=105):
    file=world/"region"/f"r.{x//32}.{z//32}.mca"
    file.parent.mkdir(parents=True,exist_ok=True)
    compressed=zlib.compress(nbt_heights(surface,floor))
    payload=struct.pack(">I",len(compressed)+1)+b"\x02"+compressed
    sector_count=(len(payload)+4095)//4096
    header=bytearray(8192)
    slot=(x%32)+(z%32)*32
    struct.pack_into(">I",header,4*slot,(2<<8)|sector_count)
    file.write_bytes(header+payload.ljust(sector_count*4096,b"\x00"))

class AtlasHeightCacheV5QA(unittest.TestCase):
    def test_readonly_bounded_cache_contract(self):
        src=(JAVA/"WorldAtlasHeightmapReader.java").read_text()
        self.assertIn("HEIGHT_CACHE_SLOTS = 2048",src)
        self.assertIn("Sample found=readUncached(chunkX,chunkZ)",src)
        self.assertIn("if(found!=null)",src)
        self.assertIn("decodedHeights[slot]=found",src)
        self.assertIn("java.util.Arrays.fill(decodedHeights,null);",src)
        self.assertLess(src.index("RandomAccessFile in=regions.get(file);"),
                        src.index("if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))return null;"))
        self.assertNotIn("Files.write(",src)
        self.assertNotIn("new FileOutputStream(",src)

    def test_actual_anvil_java_saved_height_decoding_and_negative_coordinates(self):
        if not shutil.which("java") or not shutil.which("javac"):
            self.skipTest("JDK unavailable")
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp)
            world=base/"world"
            (world/"region").mkdir(parents=True)
            write_mca(world,0,0,115,105)
            write_mca(world,-1,-1,177,165)
            runner=base/"AtlasCacheV5Smoke.java"
            runner.write_text("""package org.realscale.neoforge;
import java.nio.file.Path;
public final class AtlasCacheV5Smoke {
 static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
   Path world=Path.of(args[0]);
   try(WorldAtlasHeightmapReader.Sampler s=WorldAtlasHeightmapReader.openSampler(world)){
     var start=s.read(0,0);
     check(start!=null&&start.surfaceY()==115&&start.oceanFloorY()==105,"raw stored height");
     for(int i=0;i<8000;i++)check(s.read(0,0)==start,"identity cache miss");
     check(s.decodedHits==8000,"expected 8000 saved chunk cache hits");
     check(s.read(-1,-1).surfaceY()==177,"negative quadrant height");
     check(s.read(-1,-1).oceanFloorY()==165,"negative quadrant floor");
     check(s.read(31,31)==null,"missing chunk must not synthesize land");
     check(s.read(31,31)==null,"missing cached as terrain");
   }
   try(WorldAtlasHeightmapReader.Sampler s=WorldAtlasHeightmapReader.openSampler(world)){
     check(s.decodedHits==0,"cross-task cache retained");
     check(s.read(0,0).surfaceY()==115,"fresh world re-read");
   }
   System.out.println("ATLAS_HEIGHT_CACHE_V5_REAL_ANVIL_PASS");
 }
}
""",encoding="utf-8")
            subprocess.run(["javac","-d",str(base),
                str(JAVA/"WorldAtlasHeightmapReader.java"),str(runner)],
                capture_output=True,text=True,check=True,timeout=30)
            proc=subprocess.run(["java","-cp",str(base),
                "org.realscale.neoforge.AtlasCacheV5Smoke",str(world)],
                capture_output=True,text=True,timeout=30)
            self.assertEqual(proc.returncode,0,proc.stdout+"\n"+proc.stderr)
            self.assertIn("ATLAS_HEIGHT_CACHE_V5_REAL_ANVIL_PASS",proc.stdout)

if __name__=="__main__":unittest.main()

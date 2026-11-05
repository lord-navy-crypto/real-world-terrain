"""Compare saved-world fixture math directly with the production Java layout.

This catches incorrect unsigned Python modulo of Java signed 64-bit hashes:
previously the test sought (16,47) while Minecraft generates (-48,47).
"""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"qa/neoforge"))
from sky_island_fixture import site_at, mix, java_floor_mod, SATELLITES, archipelago_at_origin

JAVA_ROOT=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
JAVA_TEST=ROOT/"neoforge/src/test/java/org/realscale/neoforge"

def satellites(base):
    x,z,top,_=base
    answer=[]
    for slot,(dx,dz) in enumerate(SATELLITES):
        if slot==0:
            answer.append((x,z,top,base[3]))
            continue
        h=mix(((x & ((1<<64)-1))<<32) ^ (z&0xffffffff)
               ^ ((0x9e3779b97f4a7c15*slot)&((1<<64)-1)))
        answer.append((x+dx,z+dz,top+java_floor_mod(h,7)-3,
                       7+((h>>27)%5)))
    return answer

class SkyIslandFixtureParity(unittest.TestCase):
    def test_signed_hash_golden_coordinates(self):
        self.assertEqual(site_at(0,0),(-48,47,183,10+((mix(0x3bc7f54a83e1d209)>>47)%5)))
        self.assertEqual(archipelago_at_origin(),
                         ((-48,47,183),(32,47,182),(-128,47,184),
                          (-48,127,182),(-48,-33,184)))
        self.assertEqual(java_floor_mod((1<<64)-1,96),95)
        self.assertEqual(java_floor_mod((1<<64)-1,7),6)

    def test_fixture_matches_production_java(self):
        with tempfile.TemporaryDirectory(prefix="tva-sky-fixture-") as directory:
            subprocess.run(["javac","-d",directory,
                    str(JAVA_ROOT/"SkyIslandLayout.java"),
                    str(JAVA_TEST/"SkyIslandFixtureOracle.java")],check=True)
            completed=subprocess.run(["java","-cp",directory,
                    "org.realscale.neoforge.SkyIslandFixtureOracle"],
                    check=True,text=True,capture_output=True)
            for line in completed.stdout.splitlines():
                bits=line.split()
                if bits[0]=="BASE":
                    x,z,xx,zz,top,radius=map(int,bits[1:])
                    self.assertEqual(site_at(x,z),(xx,zz,top,radius))
                elif bits[0]=="SLOT":
                    x,z,slot,xx,zz,top,radius=map(int,bits[1:])
                    self.assertEqual(satellites(site_at(x,z))[slot],
                                     (xx,zz,top,radius))
                else:
                    raise AssertionError("unexpected Java fixture output: "+line)

if __name__=="__main__":
    unittest.main()

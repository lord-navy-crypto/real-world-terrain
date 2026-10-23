"""Cave and marine decoration allocation regression checks.

These tests establish signed coordinate equivalence and reject-before-
allocation semantics. They do not substitute for dedicated-server parity.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
SRC=(ROOT/"neoforge/src/main/java/org/realscale/neoforge/CaveMarineHabitatFeature.java").read_text()

def signed32(i):
    i &= 0xffffffff
    return i if i < 0x80000000 else i - 0x100000000

class HabitatAllocationFastpaths(unittest.TestCase):
    def test_original_offset_and_new_int_coordinates_match_at_edges(self):
        # BlockPos.offset performs signed Java int sums; test all chunk edges
        # and extreme signed values with every allowed horizontal offset.
        xs=[-(2**31),-(2**31)+1,-65,-33,-32,-17,-16,-15,-1,0,1,
            15,16,17,31,32,33,65,2**31-2,2**31-1]
        for origin in xs:
            for dx in range(-5,6):
                target=signed32(origin+dx)
                for other in xs:
                    # Old: BlockPos candidate = origin.offset(dx,0,dz);
                    #      compare Math.floorDiv(origin,16), candidate;
                    # New: compare origin>>4 with (origin+dx)>>4 first.
                    self.assertEqual((origin//16)==(target//16),
                                     (origin>>4)==(target>>4))
                    if other in (-1,0,16,2**31-1):
                        tz=signed32(other-dx)
                        self.assertEqual((other//16)==(tz//16),
                                         (other>>4)==(tz>>4))

    def test_cross_chunk_check_before_candidate_allocation(self):
        cave=SRC.index("private boolean decorateCave(")
        marine=SRC.index("private boolean decorateMarine(")
        cave_body=SRC[cave:marine]
        marine_body=SRC[marine:SRC.index("@Override public boolean place(")]
        self.assertLess(cave_body.index("if(!sameChunk(origin,x,z))continue;"),
                        cave_body.index("BlockPos p=new BlockPos(x,anchor.getY(),z);"))
        self.assertLess(marine_body.index("if(!sameChunk(origin,x,z))continue;"),
                        marine_body.index("BlockPos bottom=new BlockPos(x,seabed,z);"))
        self.assertIn("int dx=random.nextInt(MAX_RADIUS*2+1)-MAX_RADIUS;",cave_body)
        self.assertIn("int dz=random.nextInt(MAX_RADIUS*2+1)-MAX_RADIUS;",cave_body)
        self.assertIn("int dx=random.nextInt(11)-5, dz=random.nextInt(11)-5;",marine_body)
        self.assertIn("if(dx*dx+dz*dz>MAX_RADIUS*MAX_RADIUS)continue;",cave_body)
        self.assertNotIn("origin.offset(dx,0,dz)",marine_body)
        self.assertNotIn("anchor.offset(dx,0,dz)",cave_body)

    def test_cave_pocket_short_circuit_order_and_position_reuse(self):
        method=SRC[SRC.index("private static boolean cavePocket("):
                   SRC.index("private static boolean nearWater(")]
        needles=[
            "if(!w.getBlockState(p).isAir())return false;",
            "BlockPos above=p.above();",
            "if(!w.getBlockState(above).isAir())return false;",
            "if(!w.getFluidState(p).isEmpty()",
            "||!w.getFluidState(above).isEmpty())return false;",
            "BlockPos below=p.below();",
            "naturalRock(w.getBlockState(below))",
            "&& w.getFluidState(below).isEmpty();"]
        indexes=[method.index(k) for k in needles]
        self.assertEqual(indexes, sorted(indexes))

    def test_spring_water_probe_preserves_direction_and_short_circuit(self):
        method=SRC[SRC.index("private static boolean nearWater("):
                   SRC.index("private static BlockPos findCave(")]
        self.assertIn("for(Direction d:Direction.Plane.HORIZONTAL)",method)
        self.assertEqual(method.count("new BlockPos.MutableBlockPos()"),1)
        self.assertIn("nx=x+d.getStepX(),nz=z+d.getStepZ()",method)
        self.assertIn("w.getFluidState(neighbor.set(nx,y,nz)).is(FluidTags.WATER)",method)
        self.assertIn("||w.getFluidState(neighbor.set(nx,y-1,nz)).is(FluidTags.WATER)",method)
        self.assertNotIn("p.relative(d)",method)
        self.assertNotIn("n.below()",method)

if __name__=="__main__":unittest.main()

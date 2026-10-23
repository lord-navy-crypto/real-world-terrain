package org.realscale.neoforge;

/** Standalone Test.14 equivalence oracle for conservative sky-island prefilter. */
public final class SkyIslandChunkCandidateSmoke {
    private static int tested;
    private static int rejected;

    private static void check(int cx,int cz) {
        final int rx=cx*16+8, rz=cz*16+8;
        for(int gx=-1;gx<=1;gx++) for(int gz=-1;gz<=1;gz++) {
            SkyIslandLayout.Site base=SkyIslandLayout.at(
                    rx+gx*SkyIslandLayout.GRID,
                    rz+gz*SkyIslandLayout.GRID);
            // Original enumeration and AABB comparisons, including Java int
            // overflow semantics. The optimization may reject ONLY groups
            // with zero original candidates.
            boolean oldHasCandidate=false;
            for(int slot=0;slot<SkyIslandLayout.SLOTS;slot++) {
                SkyIslandLayout.Site island=SkyIslandLayout.satellite(base,slot);
                int r=island.radius();
                if (island.x()+r<cx*16 || island.x()-r>=cx*16+16
                        || island.z()+r<cz*16 || island.z()-r>=cz*16+16)
                    continue;
                oldHasCandidate=true;
            }
            boolean keep=SkyIslandLayout.mayIntersectChunk(base,cx,cz);
            if (oldHasCandidate && !keep)
                throw new AssertionError("Pruned live sky candidates at chunk "
                        +cx+","+cz+" from base "+base);
            tested++;
            if(!keep)rejected++;
        }
    }

    public static void main(String[] args) {
        // Dense adjacent chunks cover island/chunk boundaries and negative floors.
        for(int cx=-38;cx<=38;cx++) for(int cz=-38;cz<=38;cz++)
            check(cx,cz);
        int[] extremes={Integer.MIN_VALUE,Integer.MIN_VALUE/16,
                -1_875_000,-262145,-65536,-1,0,1,65536,262145,
                1_875_000,Integer.MAX_VALUE/16,Integer.MAX_VALUE};
        for(int cx:extremes)for(int cz:extremes)check(cx,cz);
        long state=0x70ac3be519ab2d4fL;
        for(int i=0;i<12000;i++) {
            state=state*6364136223846793005L+1442695040888963407L;
            int cx=(int)(state>>>32);
            state=state*6364136223846793005L+1442695040888963407L;
            int cz=(int)(state>>>32);
            check(cx,cz);
        }
        if(rejected<30000)
            throw new AssertionError("Sky candidate pruning not exercised: "+rejected);
        System.out.println("SKY_CHUNK_CANDIDATE_PRUNE_PARITY_PASS groups="+tested
                +" avoided="+rejected);
    }
}

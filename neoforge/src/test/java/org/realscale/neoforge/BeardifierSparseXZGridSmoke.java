package org.realscale.neoforge;

import java.util.Random;

/** Geometry proof for sparse 16x16 cell occupancy with enlarged support. */
public final class BeardifierSparseXZGridSmoke {
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }
    private static boolean contains(long minX,long maxX,long minZ,long maxZ,
            long x,long z){
        return minX-32<=x&&x<=maxX+32&&minZ-32<=z&&z<=maxZ+32;
    }
    public static void main(String[] args){
        BeardifierSparseXZGrid empty=new BeardifierSparseXZGrid();
        check(!empty.canSkip(0,0),"Unfinished grid fails open");
        empty.finish();
        check(empty.canSkip(Integer.MIN_VALUE,Integer.MAX_VALUE),"Empty grid");
        BeardifierSparseXZGrid scattered=new BeardifierSparseXZGrid();
        scattered.includePiece(-96,-80,-96,-80);
        scattered.includePiece(80,96,80,96);
        scattered.includeJunction(-95,90);
        scattered.finish();
        check(scattered.enabled(),"Small sparse grid should build");
        check(scattered.canSkip(0,0),"Large empty middle gap should be culled");
        check(!scattered.canSkip(-96,-96),"First piece must be occupied");
        check(!scattered.canSkip(95,95),"Second piece must be occupied");
        check(!scattered.canSkip(-95,90),"Junction must be occupied");
        // Any guard edge must remain marked even crossing negative cells.
        for(int x=-128;x<=-48;x++)
            for(int z=-128;z<=-48;z++)
                if(contains(-96,-80,-96,-80,x,z))
                    check(!scattered.canSkip(x,z),"Piece guard false-positive");
        BeardifierSparseXZGrid tooLarge=new BeardifierSparseXZGrid();
        tooLarge.includePiece(-1000000,1000000,0,0);
        tooLarge.finish();
        check(!tooLarge.enabled()&&!tooLarge.canSkip(0,0),
                "Oversized grid must fail open");
        BeardifierSparseXZGrid invalid=new BeardifierSparseXZGrid();
        invalid.includePiece(6,5,0,0);
        invalid.finish();
        check(!invalid.enabled()&&!invalid.canSkip(999,999),
                "Invalid rectangle must fail open");
        BeardifierSparseXZGrid extremes=new BeardifierSparseXZGrid();
        extremes.includeJunction(Integer.MAX_VALUE,Integer.MIN_VALUE);
        extremes.finish();
        check(extremes.enabled(),"Extreme coordinates with longs");
        check(!extremes.canSkip(Integer.MAX_VALUE,Integer.MIN_VALUE),
                "Extreme junction covered");
        check(extremes.canSkip(0,0),"Far away must skip");

        Random r=new Random(20261021L);
        for(int t=0;t<3000;t++){
            BeardifierSparseXZGrid grid=new BeardifierSparseXZGrid();
            int count=r.nextInt(8)+1;
            long[][] bounds=new long[count][];
            int baseX=r.nextInt(2000)-1000,baseZ=r.nextInt(2000)-1000;
            for(int i=0;i<count;i++){
                int x=baseX+r.nextInt(121)-60;
                int z=baseZ+r.nextInt(121)-60;
                int x1=x+r.nextInt(14),z1=z+r.nextInt(14);
                grid.includePiece(x,x1,z,z1);
                bounds[i]=new long[]{x,x1,z,z1};
            }
            grid.finish();
            check(grid.enabled(),"Compact random grid must build");
            for(int q=0;q<250;q++){
                int x=baseX+r.nextInt(261)-130,z=baseZ+r.nextInt(261)-130;
                boolean support=false;
                for(long[] b:bounds)
                    if(contains(b[0],b[1],b[2],b[3],x,z)){
                        support=true;break;
                    }
                if(support)check(!grid.canSkip(x,z),
                        "False positive: canSkip inside guaranteed support at "+
                        x+","+z);
            }
        }
        System.out.println("REALSCALE_BEARDIFIER_SPARSE_XZ_PASS randomized=750000");
    }
}

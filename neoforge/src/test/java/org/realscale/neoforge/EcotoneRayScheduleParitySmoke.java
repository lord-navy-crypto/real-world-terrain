package org.realscale.neoforge;

import java.util.Random;

/** Compare original per-heading vs exact squared-distance ordered ecotone rays. */
public final class EcotoneRayScheduleParitySmoke {
    private static final int[][] DIRECTIONS={
            {1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
    private static final int[][] SORTED=EcotoneRaySchedule.build(DIRECTIONS,6);
    private static int rule(int center,int neighbor) {
        if(center==neighbor)return 0;
        if(center==0 && neighbor==1)return 48;
        if(center==0 && neighbor==2)return 32;
        if(center==0 && neighbor==3)return 16;
        return 0;
    }
    private static int sample(int[][] grid,int dx,int dz) {
        return grid[dx+6][dz+6];
    }
    private static long original(int[][] g,int max) {
        int best=Integer.MAX_VALUE,chosen=0;
        for(int heading=0;heading<DIRECTIONS.length;heading++) {
            int[] dir=DIRECTIONS[heading];
            for(int step=1;step<=max;step++) {
                int dist=EcotoneMath.quartDistanceSquared(
                        dir[0],dir[1],step);
                if(dist>16*max*max||dist>=best)break;
                int next=sample(g,dir[0]*step,dir[1]*step);
                if(next==0)continue;
                int width=rule(0,next);
                if(width>=16 && 4L*dist<=((long)width)*width) {
                    best=dist;
                    chosen=next;
                }
                break;
            }
        }
        return ((long)best<<32)|chosen;
    }
    private static long optimized(int[][] g,int max) {
        int best=Integer.MAX_VALUE,chosen=0,blocked=0;
        for(int[] ray:SORTED) {
            int dist=ray[2],heading=ray[0],step=ray[1];
            if(dist>16*max*max||dist>=best)break;
            if((blocked&(1<<heading))!=0)continue;
            int[] dir=DIRECTIONS[heading];
            int next=sample(g,dir[0]*step,dir[1]*step);
            if(next==0)continue;
            blocked|=1<<heading;
            int width=rule(0,next);
            if(width>=16 && 4L*dist<=((long)width)*width) {
                best=dist;
                chosen=next;
            }
        }
        return ((long)best<<32)|chosen;
    }
    public static void main(String[] args) {
        Random random=new Random(20261021);
        int checks=0;
        for(int max=1;max<=6;max++)for(int trial=0;trial<30000;trial++) {
            int[][] grid=new int[13][13];
            // Vast interiors, coasts and dense patchwork; independently
            // seeded neighboring classifications including unmatched types.
            double mismatch=trial%4==0?.01:trial%4==1?.08:
                    trial%4==2?.35:.75;
            for(int dx=0;dx<13;dx++)for(int dz=0;dz<13;dz++)
                grid[dx][dz]=random.nextDouble()<mismatch
                        ? 1+random.nextInt(5):0;
            grid[6][6]=0;
            long before=original(grid,max),after=optimized(grid,max);
            if(before!=after)
                throw new AssertionError("Ray order changed result max="+max+
                        " trial="+trial+" original="+before+" updated="+after);
            checks++;
        }
        if(SORTED.length!=48)throw new AssertionError("Incomplete rays");
        boolean[][] seen=new boolean[8][7];
        int previousDist=0,previousHeading=-1;
        for(int[] ray:SORTED) {
            if(seen[ray[0]][ray[1]])throw new AssertionError("Repeated ray");
            seen[ray[0]][ray[1]]=true;
            if(ray[2]<previousDist || (ray[2]==previousDist
                    && ray[0]<previousHeading))
                throw new AssertionError("Incorrect stable priority");
            previousDist=ray[2];previousHeading=ray[0];
        }
        System.out.println("REALSCALE_ECOTONE_RADIAL_RAY_PARITY_PASS cases="+checks);
    }
}

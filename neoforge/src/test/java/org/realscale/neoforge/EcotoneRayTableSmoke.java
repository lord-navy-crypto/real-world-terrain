package org.realscale.neoforge;

import java.util.Random;

/** Independent Test.16 old-math versus lookup parity for ray ordering. */
public final class EcotoneRayTableSmoke {
    private static final int[][] DIRECTIONS = {
            {1,0},{-1,0},{0,1},{0,-1},
            {1,1},{1,-1},{-1,1},{-1,-1}
    };
    private static final int MAX_STEP = 6;
    private static final EcotoneRayTable TABLE =
            new EcotoneRayTable(DIRECTIONS,MAX_STEP);
    private static int comparisons;

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        comparisons++;
    }

    private static int selected(int maxStep, int[] firstBoundary, int[] widths,
                                boolean optimized) {
        int nearest = -1;
        int shortest = Integer.MAX_VALUE;
        // This is exactly the original priority: headings before distance,
        // then the first nonmatching biome in each heading.
        for (int heading = 0; heading < DIRECTIONS.length; heading++) {
            int[] direction = DIRECTIONS[heading];
            for (int step = 1; step <= maxStep; step++) {
                int distance = optimized ? TABLE.squared(heading,step)
                        : EcotoneMath.quartDistanceSquared(
                                direction[0],direction[1],step);
                if (distance > 16*maxStep*maxStep) break;
                if (distance >= shortest) break;
                if (firstBoundary[heading] != step) continue;
                boolean match = optimized
                        ? EcotoneRayTable.withinBand(distance,widths[heading])
                        : EcotoneMath.withinRadialBand(
                                direction[0],direction[1],step,widths[heading]);
                if(match) {
                    nearest = heading*16 + step;
                    shortest = distance;
                }
                break;
            }
        }
        return nearest;
    }

    public static void main(String[] args) {
        int[] widths={-1,0,8,15,16,17,24,31,32,40,48,63,64,65,128};
        for(int h=0;h<DIRECTIONS.length;h++) {
            int[] dir=DIRECTIONS[h];
            for(int step=1;step<=MAX_STEP;step++) {
                int old=EcotoneMath.quartDistanceSquared(dir[0],dir[1],step);
                int now=TABLE.squared(h,step);
                require(old==now,"Ray distance mismatch "+h+"/"+step);
                for(int width:widths) {
                    boolean a=EcotoneMath.withinRadialBand(dir[0],dir[1],step,width);
                    boolean b=EcotoneRayTable.withinBand(now,width);
                    require(a==b,"Transition band changed "+h+"/"+step+"/"+width);
                }
            }
        }
        Random random=new Random(0x1600aec07L);
        for(int n=0;n<50_000;n++) {
            int[] boundaries=new int[DIRECTIONS.length];
            int[] choices=new int[DIRECTIONS.length];
            for(int h=0;h<DIRECTIONS.length;h++) {
                boundaries[h]=random.nextInt(MAX_STEP+2); // 0/7: no boundary
                choices[h]=widths[random.nextInt(widths.length)];
            }
            int maxStep=1+random.nextInt(MAX_STEP);
            int before=selected(maxStep,boundaries,choices,false);
            int after=selected(maxStep,boundaries,choices,true);
            require(before==after,"Ecotone tie-break/first-boundary changed at "+n);
        }
        System.out.println("ECOTONE_RAY_TABLE_PARITY_PASS checks="+comparisons
                +" syntheticBoundaryCases=50000");
    }
}

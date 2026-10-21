package org.realscale.neoforge;

import java.util.Arrays;
import java.util.Comparator;

/** Precomputed stable distance-first visit order for biome boundary rays. */
final class EcotoneRaySchedule {
    private EcotoneRaySchedule(){}

    // Each entry: [original heading index, quart step, exact squared blocks].
    // Original heading index resolves rule priority when distances are tied.
    static int[][] build(int[][] directions,int maxSteps) {
        int[][] rays=new int[directions.length*maxSteps][3];
        int count=0;
        for(int heading=0;heading<directions.length;heading++) {
            int dx=directions[heading][0],dz=directions[heading][1];
            for(int step=1;step<=maxSteps;step++) {
                rays[count++]=new int[]{heading,step,
                        EcotoneMath.quartDistanceSquared(dx,dz,step)};
            }
        }
        Arrays.sort(rays,Comparator.<int[]>comparingInt(ray->ray[2])
                .thenComparingInt(ray->ray[0]));
        return rays;
    }
}

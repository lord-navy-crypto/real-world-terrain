package org.realscale.neoforge;

import java.util.concurrent.atomic.AtomicInteger;

public final class HabitatFloorSearchSmoke {
    private static void yes(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        int missing = HabitatFloorSearch.NOT_FOUND;
        yes(HabitatFloorSearch.nearest(20, -58, 45, 18, y->false)==missing,
                "Solid rock without cave must not place decorations");
        yes(HabitatFloorSearch.nearest(10, -58, 45, 18, y->y==10)==10,
                "Air cave floor directly at anchor");
        yes(HabitatFloorSearch.nearest(-24, -58, 45, 18, y->y==-27)==-27,
                "Find underground floor through three blocks of stone");
        yes(HabitatFloorSearch.nearest(18, -58, 45, 18, y->y==14||y==22)==14,
                "Prefer descending cave pocket on equal-distance tie");
        yes(HabitatFloorSearch.nearest(40, -58, 45, 18, y->y==46)==missing,
                "Do not climb out of underground habitat height limit");
        yes(HabitatFloorSearch.nearest(-55, -58, 45, 18, y->y==-60)==missing,
                "Do not sample below underground floor limit");
        yes(HabitatFloorSearch.nearest(-20, -58, 45, 18, y->y==0)==missing,
                "Do not search beyond bounded vertical scan");
        AtomicInteger visits = new AtomicInteger();
        int found = HabitatFloorSearch.nearest(0, -58, 45, 18, y -> {
            visits.incrementAndGet(); return y == -3;
        });
        yes(found == -3 && visits.get() <= 7,
                "Nearby cave support should short-circuit unnecessary world reads");
        // High-altitude RealScale mountains can hide enormous cave floors
        // hundreds or thousands of blocks above vanilla cave Y levels.
        yes(HabitatFloorSearch.nearest(850,-40,1776,40,y->y==821)==821,
                "Find a large dry cave at Y821");
        yes(HabitatFloorSearch.nearest(1590,-40,1776,40,y->y==1559)==1559,
                "Find an upper mountain cave floor above Y1500");
        yes(HabitatFloorSearch.nearest(800,-40,780,40,y->y==792)==missing,
                "A cave above roof-protected maximum must not be decorated");
        yes(HabitatFloorSearch.nearest(1300,-40,1270,40,y->y==1268)==1268,
                "Find a safe pocket even if starting above the permitted roof depth");
        yes(HabitatFloorSearch.nearest(1680,-40,1630,40,y->y==1615)==missing,
                "Never breach a restricted upper roof boundary");
        System.out.println("PASS: cave floor bounds, short-circuit, highland Y1500, roof limits and vertical ties");
    }
}

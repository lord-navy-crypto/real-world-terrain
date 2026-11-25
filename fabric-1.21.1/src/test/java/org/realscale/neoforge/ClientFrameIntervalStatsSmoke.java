package org.realscale.neoforge;

/** Pure-Java regression for histogram, FPS, percentiles and long stalls. */
public final class ClientFrameIntervalStatsSmoke {
    private ClientFrameIntervalStatsSmoke() {}
    private static void yes(boolean condition,String label) {
        if(!condition)throw new AssertionError(label);
    }
    public static void main(String[] args) {
        var empty=new ClientFrameIntervalStats();
        yes(empty.frames()==0&&empty.fps()==0&&empty.percentileUpperMs(.95)==0,
                "Empty client profile must remain well-defined");
        empty.add(-100);
        empty.add(0);
        yes(empty.frames()==0,"Invalid clock deltas are not frames");

        var normal=new ClientFrameIntervalStats();
        for(int i=0;i<90;i++)normal.add(16_500_000L);
        for(int i=0;i<10;i++)normal.add(40_000_000L);
        yes(normal.frames()==100,"Each frame should be recorded");
        double expectedFps=100e9/(90*16_500_000.0+10*40_000_000.0);
        yes(Math.abs(normal.fps()-expectedFps)<.001,
                "Arithmetic mean of per-frame FPS is invalid: use time-weighted FPS");
        yes(Math.abs(normal.percentileUpperMs(.50)-16.5)<.0001,"p50 bucket");
        yes(Math.abs(normal.percentileUpperMs(.95)-40.0)<.0001,"p95 bucket");
        yes(normal.onePercentLowFpsLowerBound()>24
                &&normal.onePercentLowFpsLowerBound()<=25,
                "Conservative inverse p99 1%-low FPS estimate");
        yes(normal.slowFramesOver50ms()==0,"40ms frame not >50ms");

        var stutter=new ClientFrameIntervalStats();
        for(int i=0;i<95;i++)stutter.add(16_666_667L);
        for(int i=0;i<5;i++)stutter.add(80_000_000L);
        yes(stutter.slowFramesOver50ms()==5,"Long frame classification");
        yes(stutter.percentileUpperMs(.99)==80,"Tail percentile misses stutter");
        yes(stutter.report("cave_1000","SEALED_CAVE").contains("slow_over_50ms=5"),
                "Log data should retain stutter count");
        yes(stutter.report("cave_1000","SEALED_CAVE").contains(
                    "method=post_to_post_including_frame_pacing_not_gpu"),
                "Cannot advertise an interval as pure GPU draw cost");

        var overflow=new ClientFrameIntervalStats();
        for(int i=0;i<100;i++)overflow.add(600_000_000L);
        yes(Double.isNaN(overflow.percentileUpperMs(.95)),
                "Overflow percentiles must not report a fabricated upper bound");
        yes(overflow.report("far","TRANSITION").contains("p95_interval_ms=256+"),
                "Overflow must be labeled honestly");
        boolean thrown=false;
        try{overflow.percentileUpperMs(1.01);}
        catch(IllegalArgumentException expected){thrown=true;}
        yes(thrown,"Out-of-range percentile should fail");
        System.out.println("REAL_SCALE_FPS_FRAME_INTERVAL_SMOKE_PASS");
    }
}

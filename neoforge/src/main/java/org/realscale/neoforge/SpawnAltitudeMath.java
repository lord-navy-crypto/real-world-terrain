package org.realscale.neoforge;

/**
 * Deterministic target-altitude policy for first-world spawn selection.
 *
 * The world seed adds a small repeatable variation; the selected spawn column
 * is still chosen from measured terrain heights by SpawnAltitudeSelector.
 */
final class SpawnAltitudeMath {
    private SpawnAltitudeMath() {}

    static int targetAltitude(String preset,int seaLevel,long worldSeed) {
        int baseOffset=switch (preset) {
            case "tva_alpine_smoothed", "himalayan", "realistic_mountains" -> 88;
            case "tva_canyon_smoothed", "grand_canyon", "canyon" -> 64;
            case "usgs_big_sur_v2", "tva_pr25_measured_climate" -> 72;
            default -> 80;
        };
        long mixed=mix64(worldSeed ^ ((long)preset.hashCode()<<32)
                ^ 0x9E3779B97F4A7C15L);
        int variation=(int)Long.remainderUnsigned(mixed,33L)-16;
        return seaLevel+baseOffset+variation;
    }

    static double candidateScore(int surfaceY,int targetY,int seaLevel,
                                 int horizontalDistance,int minBuildHeight,
                                 int maxBuildHeight) {
        double score=Math.abs((double)surfaceY-targetY);
        int preferredLow=seaLevel+24;
        int preferredHigh=seaLevel+180;
        if(surfaceY<preferredLow)
            score+=(preferredLow-surfaceY)*3.0;
        if(surfaceY>preferredHigh)
            score+=(surfaceY-preferredHigh)*2.0;
        score+=horizontalDistance*0.08;
        if(surfaceY<=seaLevel+8 || surfaceY<=minBuildHeight+1
                || surfaceY>=maxBuildHeight-32)
            score+=100_000.0;
        return score;
    }

    static long mix64(long value) {
        value=(value^(value>>>30))*0xBF58476D1CE4E5B9L;
        value=(value^(value>>>27))*0x94D049BB133111EBL;
        return value^(value>>>31);
    }
}

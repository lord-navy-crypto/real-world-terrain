package org.realscale.geo;

/** Sparse large-area QA: diverse oceans, coastlines and mountain ranges. */
public final class GeoSurvey {
    public static void main(String[] args) {
        for (long seed : new long[]{42L,785312L,20261011L}) {
            GeoTerrainEngine engine = new GeoTerrainEngine(seed);
            double min = Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            int sea=0,land=0,alpine=0;
            for(int z=-24000;z<=24000;z+=6000)
                for(int x=-24000;x<=24000;x+=6000){
                    double h=engine.heightAt(x,z);
                    min=Math.min(min,h);max=Math.max(max,h);
                    if(h<GeoTerrainEngine.SEA_Y)sea++;else land++;
                    if(h>750)alpine++;
                }
            System.out.printf(java.util.Locale.ROOT,
                 "seed=%d samples=%d sea=%d land=%d alpine-above-750m=%d min=%.2f max=%.2f generated-tiles=%d%n",
                 seed,sea+land,sea,land,alpine,min,max,engine.generatedTiles());
        }
    }
}

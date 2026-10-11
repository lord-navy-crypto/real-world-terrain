package org.realscale.geo;

public final class GeoTests {
    private static int assertions;
    private static void check(boolean ok,String message) {
        assertions++;
        if(!ok)throw new AssertionError(message);
    }
    private static void approximately(double a,double b,double tolerance,String message){
        check(Math.abs(a-b)<=tolerance,message+": expected "+a+" ≈ "+b);
    }
    public static void main(String[] args){
        long t0=System.nanoTime();
        GeoTerrainEngine a=new GeoTerrainEngine(785312L), b=new GeoTerrainEngine(785312L);
        int[] px={-10246,-4096,-64,-1,0,1,63,1024,4095,4096,4097,8096,12288,22014};
        int[] pz={-9098,-4097,-1,0,1,63,256,4095,4096,8192};
        for(int x:px)for(int z:pz){
            double ha=a.heightAt(x,z),hb=b.heightAt(x,z);
            check(Double.doubleToLongBits(ha)==Double.doubleToLongBits(hb),"Determinism failed");
            check(Double.isFinite(ha) && ha>=-28 && ha<=1940,"Height bound failed");
            check(a.blockHeightAt(x,z)>=GeoTerrainEngine.MIN_Y && a.blockHeightAt(x,z)<=GeoTerrainEngine.TOP_Y,"Block Y bounds");
        }
        // Seam continuity: no abrupt 10-m cliffs from 1-mm position change.
        for(int edge=-2;edge<=2;edge++)for(int z=-1100;z<=1100;z+=211){
            double x=edge*GeoTerrainEngine.TILE_METRES;
            double h0=a.heightAt(x-.001,z),h1=a.heightAt(x+.001,z);
            approximately(h0,h1,.50,"X seam height discontinuity");
            double v0=a.heightAt(z,x-.001),v1=a.heightAt(z,x+.001);
            approximately(v0,v1,.50,"Z seam height discontinuity");
        }
        // Finite slope across seam and same height independent of query order/cache eviction.
        double x=4096.0,z=4096.0;
        double base=a.heightAt(x,z);
        for(int i=0;i<60;i++) a.heightAt(i*1237-30000,i*1823-40000);
        approximately(base,a.heightAt(x,z),1e-10,"Cache eviction changes sample");
        // Priority-Flood parent graph reaches edge without cycles. Conserves rainfall flux.
        for(int tx=-1;tx<=1;tx++)for(int tz=-1;tz<=1;tz++) {
            var d=a.diagnostics(tx,tz);
            check(d.samples()==GeoTerrainEngine.SIDE*GeoTerrainEngine.SIDE,"Tile size");
            check(d.cycleCount()==0,"Priority flood cycle detected");
            check(d.missingOutlets()==0,"Hydrology has unreachable outlet");
            approximately(d.rainInputSquareMeters(),d.catchmentOutputSquareMeters(),.0001,"Catchment mass not conserved");
            check(d.minHeight()>=-28.00000001&&d.maxHeight()<=1940.000001,"Tile relief out of range");
            check(d.totalErosionCubicMetres()>=0,"Negative erosion");
            check(d.totalDepositionCubicMetres()>=0,"Negative deposition");
            approximately(d.totalErosionCubicMetres(),d.totalDepositionCubicMetres()+d.sedimentExportCubicMetres(),.01,"Sediment accounting does not close");
        }
        for(int s=0;s<4;s++){
            var e=new GeoTerrainEngine(42L+s*10000);
            for(int iz=-2500;iz<2500;iz+=275)for(int ix=-2500;ix<2500;ix+=275){
                double y=e.heightAt(ix,iz);
                check(Double.isFinite(y)&&y>=-28&&y<=1940,"Multiseed finite output");
            }
        }
        System.out.printf(java.util.Locale.ROOT,"PASS %d assertions, %.2f seconds, generated %d tiles%n",
            assertions,(System.nanoTime()-t0)/1e9,a.generatedTiles());
    }
}

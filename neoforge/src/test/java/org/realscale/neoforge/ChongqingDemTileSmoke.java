package org.realscale.neoforge;

/** Real Chaotianmen source data, not simulated/chosen heights. */
public final class ChongqingDemTileSmoke {
    private ChongqingDemTileSmoke(){}
    private static void require(boolean ok,String msg) {
        if(!ok)throw new AssertionError(msg);
    }
    public static void main(String[] args) {
        ChongqingDemTile tile=ChongqingDemTile.load();
        require(tile.width==129 && tile.step==64 &&
                tile.minX==-4096 && tile.minZ==-4096,
                "Wrong georeferenced source grid");
        require(tile.contains(0,0),"Chaotianmen center not covered");
        require(!tile.contains(5000,0)&&!tile.contains(0,-5000),
                "Unknown Chinese geography cannot be synthesized");
        require(Double.isNaN(tile.metresAt(10000,10000)),
                "Out-of-coverage region must remain explicitly missing");
        int minimum=Integer.MAX_VALUE,maximum=Integer.MIN_VALUE;
        for(int z=0;z<tile.width;z++)
            for(int x=0;x<tile.width;x++) {
                int sample=tile.sampleRaw(x,z);
                minimum=Math.min(minimum,sample);
                maximum=Math.max(maximum,sample);
                require(Double.doubleToRawLongBits(sample)==
                        Double.doubleToRawLongBits(tile.metresAt(
                                tile.minX+x*tile.step,tile.minZ+z*tile.step)),
                        "Bilinear sampler changed exact measured grid node");
            }
        require(minimum==145 && maximum==664,
                "Unexpected Chongqing raster raw height range");
        for(int z=0;z<tile.width-1;z+=3)
            for(int x=0;x<tile.width-1;x+=3) {
                double posX=tile.minX+(x+0.35)*tile.step;
                double posZ=tile.minZ+(z+0.75)*tile.step;
                double got=tile.metresAt(posX,posZ);
                double a=tile.sampleRaw(x,z),b=tile.sampleRaw(x+1,z);
                double c=tile.sampleRaw(x,z+1),d=tile.sampleRaw(x+1,z+1);
                double low=Math.min(Math.min(a,b),Math.min(c,d));
                double high=Math.max(Math.max(a,b),Math.max(c,d));
                require(got>=low-1e-8 && got<=high+1e-8,
                        "Interpolation invents a nonexistent DEM ridge");
            }
        double center=tile.metresAt(0,0);
        require(center>=145 && center<=664,"City center elevation invalid");
        System.out.printf(java.util.Locale.ROOT,
                "TVA_CHONGQING_REAL_DEM_PASS width=%d step=%d grid=%d "+
                "min=%d max=%d center_elevation=%.1f missing=NaN%n",
                tile.width,tile.step,tile.width*tile.width,
                minimum,maximum,center);
    }
}

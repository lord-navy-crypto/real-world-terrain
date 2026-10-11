package org.realscale.neoforge;
import java.nio.file.*;
public final class RealDemTileSmoke {
    public static void main(String[] args) throws Exception {
        RealDemTile tile = RealDemTile.load();
        double y=tile.worldSolidYAt(0,0);
        if(y<700||y>1600)throw new AssertionError("DEM wrong altitude "+y);
        if(tile.influence(0,0,96)!=1)throw new AssertionError("DEM center should be full strength");
        for(int z=-240;z<=240;z+=4)for(int x=-240;x<=240;x+=4){
            double v=tile.worldSolidYAt(x,z);
            if(v < -64||v >2031||!Double.isFinite(v))throw new AssertionError("DEM bounds: "+x+","+z);
            double t=tile.influence(x,z,96);
            if(t<0||t>1)throw new AssertionError("DEM blend weight");
        }
        if(tile.influence(512,512,96)!=0)throw new AssertionError("Outside DEM not zero influence");
        System.out.println("OK RealDemTile: 14,641 4m-grid samples, original data bound checks, constant 1m scale; centerY="+y);
    }
}

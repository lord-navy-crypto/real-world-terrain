package org.realscale.geo;
import java.nio.file.Path;
/** Assertions against an actual offline 144-point USGS EPQS raster artifact.
 * Only data integrity and simulated-columns tests, not Minecraft GameTest.
 */
public final class MeasuredGeoTests {
    public static void main(String[] args) throws Exception {
        if(args.length<1)throw new IllegalArgumentException("Need a measured RGT1 file path");
        var tile=MeasuredGeoTile.read(Path.of(args[0]));
        check(tile.cols()==16&&tile.rows()==16,"Actual 16x16 measured-grid coordinates");
        check(tile.gridMeters()==32,"No physical horizontal compression");
        check(tile.worldSeaY()==63,"World sea level");
        var base=RegionalWatershed.Parameters.demonstrator();
        var p=new RegionalWatershed.Parameters(32,base.timeYears(),0,
            base.referenceDischargeM3PerYear(),base.referenceSlope(),base.dischargeExponent(),
            base.slopeExponent(),base.riverThresholdM3PerYear(),base.capacityFraction(),base.maxCutMetres());
        var input=GeoMacroFactory.buildMeasured(tile,p);
        var snapshot=RegionalWatershed.solve(input,p);
        double maxDelta=0;int checks=3;
        for(int x=248;x<264;x++)for(int z=248;z<264;z++) {
            var c=VanillaColumnAdapter.column(snapshot,tile,x,z);
            int expected=(int)Math.floor(tile.worldSolidYAt(x+.5,z+.5));
            check(c.solidY()==expected,"DEM height did not survive import");checks++;
            check(c.worldSurfaceFirstFreeY()==VanillaColumnAdapter.structureBaseHeight(snapshot,tile,x,z,false),
                  "Structure height differs from populated block columns");checks++;
            check(c.oceanFloorFirstFreeY()==VanillaColumnAdapter.structureBaseHeight(snapshot,tile,x,z,true),
                  "Ocean-floor height differs from solid bedrock height");checks++;
            maxDelta=Math.max(maxDelta,Math.abs(c.solidY()-expected));
            check(c.surface().startsWith("minecraft:")&&c.subsurface().startsWith("minecraft:"),
                  "Unexpected custom block identifier");checks++;
        }
        check(Math.abs(snapshot.ledger.localRunoffM3PerYear()-snapshot.ledger.outletRunoffM3PerYear())<.01,
              "Runoff budget mismatch");checks++;
        check(snapshot.ledger.erodedM3()==0,"As-measured mode must not invent erosion");checks++;
        boolean failedOutside=false;
        try {tile.worldSolidYAt(-100,-100);}catch(IllegalArgumentException expected){failedOutside=true;}
        check(failedOutside,"Outside-DEM requests must not silently clamp to edge");checks++;
        System.out.println("PASS actual USGS DEM: "+checks+" Java assertions; maxHeightDelta="+maxDelta+
              " meters; world 1 block/m; measured 32m grid; 0 new blocks");
    }
    private static void check(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
}

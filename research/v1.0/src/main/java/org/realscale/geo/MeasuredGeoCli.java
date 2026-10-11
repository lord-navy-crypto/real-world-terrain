package org.realscale.geo;

import java.nio.file.*;
import java.util.*;
/** Example of REAL DATA injected into existing v0.9 watershed and 1m block columns.
 * NOT A NEOFORGE MOD. Does not fabricate data download or claim 1m survey resolution.
 */
public final class MeasuredGeoCli {
    public static void main(String[] args) throws Exception {
        if(args.length<2)throw new IllegalArgumentException(
           "Usage: java ...MeasuredGeoCli path_to_dem.rgt.gz output_directory [scenario: measured|evolved]");
        var dem=MeasuredGeoTile.read(Path.of(args[0]));
        Path out=Path.of(args[1]);Files.createDirectories(out);
        boolean evolve=args.length>2&&args[2].equals("evolved");
        var base=RegionalWatershed.Parameters.demonstrator();
        // In measured mode preserve survey DEM: a zero erosion rate and zero uplift.
        var p=new RegionalWatershed.Parameters((int)dem.gridMeters(),
            base.timeYears(),evolve?base.erodibilityMetersPerYear():0,
            base.referenceDischargeM3PerYear(),base.referenceSlope(),base.dischargeExponent(),
            base.slopeExponent(),base.riverThresholdM3PerYear(),base.capacityFraction(),
            base.maxCutMetres());
        var in=GeoMacroFactory.buildMeasured(dem,p);
        var snapshot=RegionalWatershed.solve(in,p);
        int x0=dem.worldX0()+(dem.cols()/2)*(int)dem.gridMeters()-8;
        int z0=dem.worldZ0()+(dem.rows()/2)*(int)dem.gridMeters()-8;
        // Grid must include the 16m block column footprint; intentionally fail otherwise.
        double maxError=0;
        try(var w=Files.newBufferedWriter(out.resolve("measured_chunk_16x16.csv"))) {
            w.write("worldX,worldZ,observedDEMmeters,solidY,worldSurfaceFirstFreeY,oceanFloorFirstFreeY,waterY,block,observedRainfallMYear\n");
            for(int z=z0;z<z0+16;z++)for(int x=x0;x<x0+16;x++) {
                var c=VanillaColumnAdapter.column(snapshot,dem,x,z);
                double surveyY=dem.worldSolidYAt(x+.5,z+.5);
                maxError=Math.max(maxError,Math.abs(c.solidY()-Math.floor(surveyY)));
                if(c.worldSurfaceFirstFreeY()!=VanillaColumnAdapter.structureBaseHeight(snapshot,dem,x,z,false))
                    throw new IllegalStateException("Height query and column disagreed");
                w.write(String.format(Locale.ROOT,"%d,%d,%.4f,%d,%d,%d,%d,%s,%.6f%n",
                    x,z,dem.measuredAltitudeAt(x+.5,z+.5),c.solidY(),
                    c.worldSurfaceFirstFreeY(),c.oceanFloorFirstFreeY(),c.waterY(),c.surface(),
                    dem.annualRainfallAt(x+.5,z+.5)));
            }
        }
        var l=snapshot.ledger;
        String report=String.format(Locale.ROOT,
          "{\"mode\":\"%s\",\"measured_cells\":%d,\"grid_meters\":%.3f,"+
          "\"surface_max_change_meters\":%.5f,\"local_runoff_m3_yr\":%.4f,"+
          "\"outlet_runoff_m3_yr\":%.4f,\"cross_tile_receivers\":%d,"+
          "\"erosion_volume_m3\":%.4f,\"minecraft_new_blocks\":0,"+
          "\"minecraft_client_tested\":false}",evolve?"evolved":"measured",dem.cols()*dem.rows(),
          dem.gridMeters(),maxError,l.localRunoffM3PerYear(),l.outletRunoffM3PerYear(),
          l.internalTileBoundaryCrossings(),l.erodedM3());
        Files.writeString(out.resolve("measured_model_report.json"),report+"\n");
        System.out.println(report);
        if(!evolve&&maxError>0)throw new IllegalStateException("Measured surface must be preserved in measured mode");
    }
}

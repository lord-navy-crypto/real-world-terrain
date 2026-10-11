package org.realscale.geo;

import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Generates an inspectable real 1m-coordinate 16x16 chunk from the shared snapshot. */
public final class GeoRegionCli {
    public static void main(String[] args) throws Exception {
        long seed=args.length>0?Long.parseLong(args[0]):785312L;
        int side=args.length>1?Integer.parseInt(args[1]):128;
        Path output=Path.of(args.length>2?args[2]:"geocore_v09_demo");
        int offsetX=args.length>3?Integer.parseInt(args[3]):0;
        int offsetZ=args.length>4?Integer.parseInt(args[4]):0;
        Files.createDirectories(output);
        var p=RegionalWatershed.Parameters.demonstrator();
        long start=System.nanoTime();
        var in=GeoMacroFactory.build(seed,side,side,offsetX,offsetZ,p);
        var result=RegionalWatershed.solve(in,p);
        var fineGeology=new GeoTerrainEngine(seed,4);
        long end=System.nanoTime();
        int maxCells=(side-1)*p.gridMeters();
        int blockX=offsetX+(maxCells/2/16)*16;
        int blockZ=offsetZ+(maxCells/2/16)*16;
        try(var writer=Files.newBufferedWriter(output.resolve("chunk_16x16_columns.csv"))) {
            writer.write("x,z,solidY,worldSurfaceFirstFreeY,oceanFloorFirstFreeY,river,surfaceBlock,waterY\n");
            for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++){
                var c=VanillaColumnAdapter.column(result,fineGeology,blockX+dx,blockZ+dz);
                writer.write(String.format(Locale.ROOT,"%d,%d,%d,%d,%d,%s,%s,%d%n",
                    c.x(),c.z(),c.solidY(),c.worldSurfaceFirstFreeY(),
                    c.oceanFloorFirstFreeY(),c.river(),c.surface(),c.waterY()));
            }
        }
        var l=result.ledger;
        var summary="{\n  \"seed\": "+seed+",\n  \"grid_cells\": "+side+",\n"+
            "  \"metres_per_cell\": "+p.gridMeters()+",\n"+
            "  \"cross_tile_receivers\": "+l.internalTileBoundaryCrossings()+",\n"+
            "  \"river_cells\": "+l.riverCells()+",\n"+
            "  \"water_input_m3_per_year\": "+l.localRunoffM3PerYear()+",\n"+
            "  \"water_output_m3_per_year\": "+l.outletRunoffM3PerYear()+",\n"+
            "  \"sediment_eroded_m3\": "+l.erodedM3()+",\n"+
            "  \"sediment_deposited_m3\": "+l.depositedM3()+",\n"+
            "  \"sediment_exported_m3\": "+l.exportedM3()+",\n"+
            "  \"elapsed_ms\": "+((end-start)/1_000_000.0)+"\n}\n";
        Files.writeString(output.resolve("region_report.json"),summary);
        System.out.println(summary);
    }
}

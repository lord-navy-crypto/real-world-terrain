package org.realscale.geo;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Deterministic numeric QA output, not a Minecraft mod launcher. */
public final class GeoCli {
    public static void main(String[] args) throws IOException {
        if(args.length!=4) {
            System.err.println("Usage: GeoCli <seed> <centerX-metres> <centerZ-metres> <output-directory>");
            System.exit(2);
        }
        long seed=Long.parseLong(args[0]);int cx=Integer.parseInt(args[1]),cz=Integer.parseInt(args[2]);
        Path out=Path.of(args[3]);Files.createDirectories(out);
        var engine=new GeoTerrainEngine(seed);
        StringBuilder heights=new StringBuilder("x_m,z_m,elevation_m,catchment_km2\n");
        int n=80,step=32;double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        double[][] h=new double[n][n];
        for(int z=0;z<n;z++)for(int x=0;x<n;x++){
            int mx=cx+(x-n/2)*step,mz=cz+(z-n/2)*step;
            double y=engine.heightAt(mx,mz);
            double area=engine.contributingAreaKm2(mx,mz);
            h[z][x]=y;min=Math.min(min,y);max=Math.max(max,y);
            heights.append(mx).append(',').append(mz).append(',')
                   .append(String.format(Locale.ROOT,"%.3f,%.6f\n",y,area));
        }
        Files.writeString(out.resolve("terrain_samples.csv"),heights,StandardCharsets.UTF_8);
        StringBuilder pgm=new StringBuilder("P2\n# Grayscale terrain height diagnostics (not a Minecraft texture)\n")
                .append(n).append(' ').append(n).append("\n255\n");
        for(int z=0;z<n;z++){
            for(int x=0;x<n;x++)pgm.append((int)Math.round((h[z][x]-min)/Math.max(.01,max-min)*255)).append(' ');
            pgm.append('\n');
        }
        Files.writeString(out.resolve("elevation_preview.pgm"),pgm,StandardCharsets.US_ASCII);
        var d=engine.diagnostics(Math.floorDiv(cx,GeoTerrainEngine.TILE_METRES),Math.floorDiv(cz,GeoTerrainEngine.TILE_METRES));
        String report="""
            {
              "seed": %d,
              "metres_per_block": 1,
              "grid_cell_metres": %d,
              "min_sample_height": %.3f,
              "max_sample_height": %.3f,
              "tiles_generated": %d,
              "drainage_cycle_count": %d,
              "missing_outlet_count": %d,
              "river_cells_in_center_tile": %d,
              "river_sediment_eroded_m3": %.4f,
              "river_sediment_deposited_m3": %.4f,
              "river_sediment_exported_m3": %.4f,
              "catchment_input_km2": %.4f,
              "catchment_output_km2": %.4f,
              "minecraft_integration": false,
              "new_blocks": 0
            }
            """.formatted(seed,GeoTerrainEngine.CELL_METRES,min,max,engine.generatedTiles(),
                d.cycleCount(),d.missingOutlets(),d.riverCells(),
                d.totalErosionCubicMetres(),d.totalDepositionCubicMetres(),d.sedimentExportCubicMetres(),
                d.rainInputSquareMeters()/1e6,d.catchmentOutputSquareMeters()/1e6);
        Files.writeString(out.resolve("geo_qa_report.json"),report,StandardCharsets.UTF_8);
        System.out.println(report);
    }
}

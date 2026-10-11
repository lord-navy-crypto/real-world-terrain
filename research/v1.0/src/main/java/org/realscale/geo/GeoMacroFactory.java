package org.realscale.geo;

/** Deterministic v0.7 geology baseline sampled into one macro-regional hydrology grid. */
public final class GeoMacroFactory {
    private GeoMacroFactory() {}

    public static RegionalWatershed.Inputs build(long seed,int nx,int nz,int x0,int z0,
            RegionalWatershed.Parameters p) {
        var model=new GeoTerrainEngine(seed,4);
        var in=new RegionalWatershed.Inputs(nx,nz,x0,z0);
        for(int z=0;z<nz;z++)for(int x=0;x<nx;x++){
            int i=in.at(x,z);
            double wx=x0+(double)x*p.gridMeters(), wz=z0+(double)z*p.gridMeters();
            double elevation=model.geologyBaselineAt(wx,wz);
            in.bedrockY[i]=elevation;
            // Smooth physical-unit rainfall, controlled by orographic exposure;
            // simple demonstration climate (NOT regional climate calibration).
            double oceanDistFactor=(Math.sin(wx/15000+seed*.00001)+1)*.5;
            double rain=Math.max(.15,1.2-.00028*Math.max(0,elevation-180)
                                  +.2*oceanDistFactor+.13*Math.sin(wz/12000));
            in.rainMetresPerYear[i]=rain;
            in.runoffFraction[i]=elevation>1000?.72: elevation<70?.35:.52;
            // 2D resistance field, not a claim of identifying real-world geology.
            in.rockResistance[i]=.8+.5*(1+Math.sin(wx/2100)*Math.cos(wz/3600));
            in.upliftMetresPerYear[i]=Math.max(0,elevation-800)*0.0000003;
        }
        return in;
    }

    /**
     * Measured DEM input is never blended with procedural mountains. Its explicit
     * climate/rock inputs override synthetic defaults; missing fields keep labeled
     * fallback values. River observations can be compared externally to the solved
     * water network, but are not misrepresented as flow-graph constraints.
     */
    public static RegionalWatershed.Inputs buildMeasured(MeasuredGeoTile dem,
            RegionalWatershed.Parameters p) {
        if(Math.abs(dem.gridMeters()-p.gridMeters())>1e-8)
            throw new IllegalArgumentException("DEM resolution must match watershed grid");
        var in=new RegionalWatershed.Inputs(dem.cols(),dem.rows(),dem.worldX0(),dem.worldZ0());
        for(int z=0;z<dem.rows();z++)for(int x=0;x<dem.cols();x++) {
            int i=in.at(x,z);
            double wx=dem.worldX0()+x*dem.gridMeters();
            double wz=dem.worldZ0()+z*dem.gridMeters();
            in.bedrockY[i]=dem.worldSolidYAt(wx,wz);
            double r=dem.annualRainfallAt(wx,wz);
            in.rainMetresPerYear[i]=Double.isFinite(r)?r:1.0; // synthetic fallback, logged by caller
            in.runoffFraction[i]=.55; // scenario parameter, not empirical runoff observation
            double resistance=dem.resistanceAt(wx,wz);
            in.rockResistance[i]=Double.isFinite(resistance)?resistance:1.0;
            in.upliftMetresPerYear[i]=0; // already-observed DEM, no fabricated tectonic uplift
        }
        return in;
    }
}

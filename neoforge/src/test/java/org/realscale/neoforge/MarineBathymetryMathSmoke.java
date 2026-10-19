package org.realscale.neoforge;

/** Fast CI gate for mathematically safe oceans, DEM boundaries and 8 morphologies. */
public final class MarineBathymetryMathSmoke {
    private static void ok(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    public static void main(String[] args) {
        final double radius=2600.0;
        final int[] reach=new int[OceanFloorMorphologyMath.TYPES];
        for(int gx=-15;gx<=15;gx++)for(int gz=-15;gz<=15;gz++){
            long h=OceanFloorMorphologyMath.hash(gx,gz);
            int kind=OceanFloorMorphologyMath.kind(h,0.21);
            ok(kind>=0 && kind<8,"invalid kind");
            reach[kind]++;
            double x=OceanFloorMorphologyMath.centerX(gx,h),z=OceanFloorMorphologyMath.centerZ(gz,h);
            double rad=OceanFloorMorphologyMath.radius(h)*1.04;
            ok(x-rad>(double)gx*8192 && x+rad<(double)(gx+1)*8192,
                  "X tile seam or overlapping marine sites");
            ok(z-rad>(double)gz*8192 && z+rad<(double)(gz+1)*8192,
                  "Z tile seam or overlapping marine sites");
        }
        for(int kind=0;kind<8;kind++){
            ok(reach[kind]>0,"unreachable seafloor archetype: "+kind);
            for(int i=0;i<120;i++){
                double phi=Math.PI*2*i/120;
                double x=Math.cos(phi)*radius*1.04,z=Math.sin(phi)*radius*1.04;
                double d=OceanFloorMorphologyMath.delta(kind,x,z,radius,-.95,-.9,0);
                ok(Math.abs(d)<1e-8,"ocean feature crosses chunk/cell boundary "+kind);
            }
            for(int i=0;i<64;i++){
                double x=(i-32)*radius/40.0;
                double z=radius*Math.sin(i*1.3)/2;
                for(double base:new double[]{-1.8,-1.1,-.7,-.43,-.25}){
                    double delta=OceanFloorMorphologyMath.delta(kind,x,z,radius,-.95,base,0);
                    ok(Double.isFinite(delta),"non-finite seafloor "+kind);
                    ok(base+delta<=Math.max(base,-0.30)+1e-9,
                       "ocean floor raised into air "+kind);
                    ok(OceanFloorMorphologyMath.delta(kind,x,z,radius,.3,base,0)==0,
                       "continental land deformed by bathymetry");
                    ok(OceanFloorMorphologyMath.delta(kind,x,z,radius,-.95,base,1)==0,
                       "USGS measured DEM changed");
                }
            }
        }
        // Density gate only affects offshore submerged regions.
        ok(OceanFloorMorphologyMath.marineGate(-.96,-.8)>.99,"abyssal floor gated off");
        ok(OceanFloorMorphologyMath.marineGate(.1,-.8)==0,"coastal/landgate changed");
        ok(OceanFloorMorphologyMath.marineGate(-.9,0.1)==0,"dry terrain modified");
        // Real geomorphologies are not aliases for a common noise function.
        ok(OceanFloorMorphologyMath.anomaly(OceanFloorMorphologyMath.MID_OCEAN_RIDGE,0,0,-.9)>0,
           "ridge must rise");
        ok(OceanFloorMorphologyMath.anomaly(OceanFloorMorphologyMath.HADAL_TRENCH,0,0,-.9)<-1,
           "trench must deepen");
        ok(OceanFloorMorphologyMath.anomaly(OceanFloorMorphologyMath.SEAMOUNT_CHAIN,0,0,-.9)>.4,
           "submerged sea mountain missing");
        ok(OceanFloorMorphologyMath.anomaly(OceanFloorMorphologyMath.GUYOT,0,0,-.9)>.9,
           "flat guyot absent");
        ok(OceanFloorMorphologyMath.anomaly(OceanFloorMorphologyMath.GUYOT,0,0,-.9)
             >OceanFloorMorphologyMath.anomaly(OceanFloorMorphologyMath.GUYOT,950,0,-.9),
             "guyot should have flat summit edge");
        ok(OceanFloorMorphologyMath.anomaly(OceanFloorMorphologyMath.SUBMARINE_CANYON,0,0,-.9)<-.8,
           "submarine canyon absent");
        ok(OceanFloorMorphologyMath.anomaly(OceanFloorMorphologyMath.ABYSSAL_HILLS,500,340,-.9)!=
           OceanFloorMorphologyMath.anomaly(OceanFloorMorphologyMath.ABYSSAL_HILLS,120,110,-.9),
           "abyssal hills not variable");
        ok(OceanFloorMorphologyMath.anomaly(OceanFloorMorphologyMath.TRANSFORM_FRACTURE_ZONE,0,190,-.9)>0,
           "fault step absent");
        System.out.println("MARINE_BATHYMETRY_MATH_PASS: eight seafloor forms, offshore gates, DEM fidelity, dry-sea cap and 8192-block seams");
    }
}

package org.realscale.geo;

public final class GeoPhysicsTests {
    private static int assertions=0;
    static void ok(boolean c,String label){assertions++;if(!c)throw new AssertionError(label);}
    static void near(double a,double b,String label){assertions++;
        if(Math.abs(a-b)>Math.max(1.e-5,Math.max(Math.abs(a),Math.abs(b))*1e-10))
            throw new AssertionError(label+" "+a+" vs "+b);}
    static GeoPhysicsKernels.Grid makeGrid(){
        int n=128;
        var g=new GeoPhysicsKernels.Grid(n,n);
        for(int z=0;z<n;z++)for(int x=0;x<n;x++){
            int i=g.index(x,z);
            double dx=(x-64)*32.0,dz=(z-64)*32.0;
            g.heightMeters[i]=140.0+0.005*(64-z)*32.0
                    +200*Math.exp(-(dx*dx+dz*dz)/(2*1400.0*1400.0))
                    +10*Math.sin(x*.18)*Math.cos(z*.10);
            g.runoffRainMetersPerYear[i]=0.8;
            g.runoffFraction[i]=0.6;
            g.upliftMetersPerYear[i]=x>50&&x<75 ? 0.0005:0;
            g.rockResistance[i]=x<50?1.8:0.6;
        }
        return g;
    }
    public static void main(String[] args){
        var settings=GeoPhysicsKernels.Settings.demonstration();
        var a=makeGrid();var b=a.copy();
        double before=a.heightMeters[a.index(64,64)];
        var r=GeoPhysicsKernels.evolve(a,settings);
        var rr=GeoPhysicsKernels.evolve(b,settings);
        ok(r.visitedCells()==128*128,"all cells visited");
        ok(r.outletCells()>0,"outlets");
        ok(r.changedCells()>0,"actual erosion detected");
        ok(a.heightMeters[a.index(64,64)]!=before,"terrain evolved");
        near(r.rainfallRunoffCubicMetersPerYear(),r.waterOutletCubicMetersPerYear(),"water flux conserved");
        near(r.rockErodedCubicMeters(),r.depositedCubicMeters()+r.sedimentExportedCubicMeters(),"sediment conserved");
        near(r.rockErodedCubicMeters(),rr.rockErodedCubicMeters(),"deterministic erosion");
        for(int i=0;i<a.heightMeters.length;i++){
            ok(Double.isFinite(a.heightMeters[i]),"finite height "+i);
            near(a.heightMeters[i],b.heightMeters[i],"reproducible height at cell "+i);
        }
        // Zero rainfall prevents stream incision (but tectonic uplift continues).
        var dry=makeGrid();
        for(int i=0;i<dry.heightMeters.length;i++)dry.runoffRainMetersPerYear[i]=0;
        var dr=GeoPhysicsKernels.evolve(dry,settings);
        near(dr.rockErodedCubicMeters(),0.0,"dry model cannot fluvially erode");
        near(dr.waterOutletCubicMetersPerYear(),0.0,"dry model cannot discharge");
        System.out.printf(java.util.Locale.ROOT,
           "PASS %d assertions: runoff=%.3f m3/year, erosion=%.3f m3, deposition=%.3f m3, export=%.3f m3, uplift=%.3f m3%n",
           assertions,r.waterOutletCubicMetersPerYear(),r.rockErodedCubicMeters(),
           r.depositedCubicMeters(),r.sedimentExportedCubicMeters(),r.netUpliftCubicMeters());
    }
}

package org.realscale.neoforge;

/** Standalone Java21 acceptance: basins, raised rims, plateaus, coasts, seams. */
public final class MountainReliefMathSmoke {
    private MountainReliefMathSmoke(){}
    private static void yes(boolean pass, String reason) {
        if(!pass) throw new AssertionError(reason);
    }
    private static void close(double a,double b,String reason) {
        yes(Math.abs(a-b)<1e-9,reason+" ("+a+" vs "+b+")");
    }
    /** Frozen pre-optimization arithmetic; parity must be raw-bit exact. */
    private static double originalRelief(double original,double anchorHeight,
            double continent,int kind,double radiusFraction,double basinDepth,
            double plateauRise) {
        double gate=MountainReliefMath.inlandUplandGate(continent,original);
        if(gate<=0.0||radiusFraction>=1.10||kind==MountainReliefMath.NONE)
            return original;
        if(kind==MountainReliefMath.BASIN) {
            double depression=basinDepth*MountainReliefMath.basinCore(radiusFraction);
            double rim=basinDepth*0.15*MountainReliefMath.raisedRim(radiusFraction);
            return original+gate*(rim-depression);
        }
        if(kind==MountainReliefMath.MESA) {
            double target=Math.max(anchorHeight,1.4)+plateauRise*0.65;
            double fit=1.0-GeoMath.smoothstep(1.4,3.0,Math.abs(original-target));
            return original+gate*fit*MountainReliefMath.mesaCrest(radiusFraction)
                    *(target-original);
        }
        double target=Math.max(anchorHeight,0.9)+plateauRise;
        double heightFit=1.0-GeoMath.smoothstep(1.0,2.4,Math.abs(original-target));
        return original+gate*heightFit*MountainReliefMath.plateauCrest(radiusFraction)
                *(target-original);
    }

    public static void main(String[] ignored) {
        // Lowlands: in any continental setting this path must be identical
        // to the pre-optimization mountain function.
        for (double base=-5.0;base<=0.65;base+=0.125)
            for (double cont=-2.0;cont<=2.0;cont+=0.125) {
                yes(MountainReliefMath.inlandUplandGate(cont,base)==0.0,
                    "Lowland gate unexpectedly requires continent noise");
                close(MountainReliefMath.relief(base,2.3,cont,
                        MountainReliefMath.BASIN,0.0,1.0,0.6),base,
                    "Lowland early rejection changes output");
            }
        // Basin climate can also skip expensive height / continental
        // density sampling outside the core. At exactly >=0.78, the
        // original basinCore() is zero in every quadrant.
        long climateOutside=0,climateInside=0;
        for (int radius=470;radius<=701;radius+=11)
            for(int x=-950;x<=950;x+=17)
                for(int z=-950;z<=950;z+=23) {
                    double limit=radius*0.780000001;
                    if((double)x*x+(double)z*z>=limit*limit) {
                        climateOutside++;
                        double r=Math.hypot(x,z)/radius;
                        yes(r>=0.78 && MountainReliefMath.basinCore(r)==0.0,
                            "Basin core rejection alters climate at region rim");
                    } else climateInside++;
                }
        yes(climateOutside>5000 && climateInside>500,
            "Geological basin climate fast-path not exercised");
        // Invariant behind performance fast-path: it must never skip a
        // column that could receive relief. Check all quadrants, site radii
        // and close-to-rim positions, including negative coordinates.
        int rejected=0,kept=0;
        for (int radius=470;radius<=701;radius+=11)
            for(int x=-1000;x<=1000;x+=13)
                for(int z=-1000;z<=1000;z+=17) {
                    boolean reject=MountainReliefMath.definitelyOutsideEnvelope(x,z,radius);
                    if(reject) {
                        rejected++;
                        yes(Math.hypot(x,z)/radius>=1.10,
                            "Fast reject erased a potentially nonzero rim");
                    } else kept++;
                }
        yes(rejected>1000&&kept>1000,"Radial fast-path is inactive");

        int basins=0,plateaus=0,mesas=0,unchanged=0;
        for(int gx=-24;gx<=24;gx++)for(int gz=-24;gz<=24;gz++) {
            long hash=MountainReliefMath.siteHash(gx,gz);
            int kind=MountainReliefMath.kind(hash,Math.sin((gx+gz)*0.13));
            if(kind==MountainReliefMath.BASIN)basins++;
            else if(kind==MountainReliefMath.PLATEAU)plateaus++;
            else if(kind==MountainReliefMath.MESA)mesas++;
            else unchanged++;
            yes(Math.abs(MountainReliefMath.centerOffsetX(hash))<=150,
                    "X center outside expected region");
            yes(Math.abs(MountainReliefMath.centerOffsetZ(hash))<=150,
                    "Z center outside expected region");
            yes(MountainReliefMath.radius(hash)<=701,
                    "Regional feature may cross into foreign world cell");
            // Worst-case generated footprint is 1.1*701+150 < 1024.
        }
        yes(basins>500 && plateaus>500 && mesas>200 && unchanged>200,
                "Mountain landscapes should include all four types");

        double inland=0.65, height=3.0, roof=3.35;
        close(MountainReliefMath.relief(height,roof,inland,
                    MountainReliefMath.BASIN,0.0,1.0,0.6),
              2.0, "Large enclosed basin floor is genuinely depressed");
        yes(MountainReliefMath.relief(height,roof,inland,
                    MountainReliefMath.BASIN,0.87,1.0,0.6) > height,
                "Natural basin has an elevated partial rim");
        close(MountainReliefMath.relief(height,roof,inland,
                    MountainReliefMath.BASIN,1.11,1.0,0.6),
              height,"Landform fully fades outside its radius");

        close(MountainReliefMath.relief(3.5,roof,inland,
                    MountainReliefMath.PLATEAU,0.05,1,0.65),
              MountainReliefMath.relief(4.0,roof,inland,
                    MountainReliefMath.PLATEAU,0.05,1,0.65),
              "Plateau crest must be near a common anchored height");
        yes(MountainReliefMath.relief(height,roof,inland,
                    MountainReliefMath.PLATEAU,0.90,1,0.65) > height,
                "Transition from a flat mesa to an escarpment is smooth");

        yes(MountainReliefMath.relief(2.0,3.0,0.8,
                    MountainReliefMath.MESA,0.0,1,0.65) > 2.0,
                "Mesa center forms a real elevated caprock remnant");
        close(MountainReliefMath.relief(2.0,3.0,0.8,
                    MountainReliefMath.MESA,1.11,1,0.65),
              2.0,"Mesa shoulder must fade to original world terrain");
        close(MountainReliefMath.relief(-0.4,3.0,-0.9,
                    MountainReliefMath.BASIN,0.0,1.4,0.8),
              -0.4,"Ocean basin must never be reshaped");
        close(MountainReliefMath.relief(0.6,3.0,0.8,
                    MountainReliefMath.PLATEAU,0.0,1.4,0.8),
              0.6,"Coastal plain must retain its old elevation");
        close(MountainReliefMath.relief(3.0,3.0,0.8,
                    MountainReliefMath.NONE,0.0,1.4,0.8),
              3.0,"Unmodified region should keep old terrain");

        for(int i=0;i<=2000;i++) {
            double f=i/2000.0*1.11;
            double basin=MountainReliefMath.relief(3.0,3.0,0.8,
                    MountainReliefMath.BASIN,f,1.15,0.7);
            double plateau=MountainReliefMath.relief(3.0,3.0,0.8,
                    MountainReliefMath.PLATEAU,f,1.15,0.7);
            yes(Double.isFinite(basin)&&Double.isFinite(plateau),
                    "No NaN/Inf across escarpment or rim");
            yes(basin>-2 && plateau<8,"Bounded terrain displacement");
        }

        double levelsA=MountainReliefMath.plateauLevel(123456789L,0.3);
        double levelsB=MountainReliefMath.plateauLevel(123456789L,0.3);
        close(levelsA,levelsB,"Plateau shelf heights must be deterministic");
        yes(levelsA>1.5 && levelsA<13,"Seeded plateau shelf within alpine range");
        yes(Math.abs(MountainReliefMath.relief(12.0,3.0,0.7,
                MountainReliefMath.PLATEAU,0.05,1.1,0.65)-12.0)<1e-9,
                "Never level a very tall summit all the way down to a low plateau");

        // Verify that passing the precomputed gate saves a repeated pair
        // of smoothsteps WITHOUT changing a single bit of generated relief.
        long gateParity=0;
        for (int kind : new int[]{MountainReliefMath.NONE,
                                  MountainReliefMath.BASIN,
                                  MountainReliefMath.PLATEAU,
                                  MountainReliefMath.MESA}) {
            for (double base : new double[]{-3.0,0.0,0.65,0.66,1.0,2.2,3.4,8.0}) {
                for (double continent : new double[]{-1.0,-0.1,0.0,0.22,0.5,1.0}) {
                    for (double radius : new double[]{0.0,0.25,0.62,0.78,
                                                      0.91,1.0,1.1,1.11}) {
                        double gate=MountainReliefMath.inlandUplandGate(continent,base);
                        double baseline=originalRelief(base,3.25,continent,kind,
                                radius,1.18,0.86);
                        double optimized=MountainReliefMath.reliefWithGate(
                                base,3.25,continent,kind,radius,1.18,0.86,gate);
                        yes(Double.doubleToRawLongBits(baseline)==
                                Double.doubleToRawLongBits(optimized),
                                "Mountain precomputed gate changed terrain for kind="
                                +kind+" base="+base+" continent="+continent
                                +" radius="+radius);
                        gateParity++;
                    }
                }
            }
        }
        yes(gateParity>=1500,"Mountain parity insufficient");

        // Old valleys and cave calculation are retained by separate
        // worldgen graphs. This kernel is strictly local height relief.
        System.out.println("TVA_MOUNTAIN_BASIN_PLATEAU_PASS basins="+basins+
                " plateaus="+plateaus+" mesas="+mesas+" natural_regions="+unchanged);
    }
}

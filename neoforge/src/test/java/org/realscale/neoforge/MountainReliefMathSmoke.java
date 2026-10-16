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
    public static void main(String[] ignored) {
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

        // Old valleys and cave calculation are retained by separate
        // worldgen graphs. This kernel is strictly local height relief.
        System.out.println("TVA_MOUNTAIN_BASIN_PLATEAU_PASS basins="+basins+
                " plateaus="+plateaus+" mesas="+mesas+" natural_regions="+unchanged);
    }
}

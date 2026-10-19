package org.realscale.neoforge;

/** Pure-Java geometry gate: world-coordinate stability, region seams and morphology. */
public final class GlobalRareLandformV3Smoke {
    private static void check(boolean ok,String reason) {
        if(!ok)throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        check(RegionalLandformMath.KINDS==8, "original v1 IDs changed");
        check(RegionalLandformMath.KINDS_V2==16, "original v2 IDs changed");
        check(RegionalLandformMath.KINDS_V3==24, "v3 must have 24 unique forms");
        check(RegionalLandformMath.CELL==4096, "v1 cells changed");
        check(RegionalLandformMath.CELL_V2==8192, "v2 cells changed");
        check(RegionalLandformMath.CELL_V3==12288, "v3 macroregions changed");

        int[] counts=new int[24];
        for(int cx=-16;cx<=16;cx++)for(int cz=-16;cz<=16;cz++) {
            long hash=RegionalLandformMath.hashV3(cx,cz);
            int kind=RegionalLandformMath.kindV3(hash,0.12);
            check(kind>=0&&kind<24, "kind out of bounds");
            counts[kind]++;
            double x=RegionalLandformMath.centerXV3(cx,hash);
            double z=RegionalLandformMath.centerZV3(cz,hash);
            double radius=RegionalLandformMath.radiusV3(hash)*1.08;
            double startX=(double)cx*RegionalLandformMath.CELL_V3;
            double startZ=(double)cz*RegionalLandformMath.CELL_V3;
            check(x-radius>startX && x+radius<startX+RegionalLandformMath.CELL_V3,
                    "v3 region X overlaps neighboring cell");
            check(z-radius>startZ && z+radius<startZ+RegionalLandformMath.CELL_V3,
                    "v3 region Z overlaps neighboring cell");
            // Deterministic regardless of sampling order and negative chunk grid.
            check(kind==RegionalLandformMath.kindV3(hash,0.12),"unstable kind");
        }
        for(int i=0;i<24;i++)check(counts[i]>0,"unreachable geomorphology ID "+i);
        double radius=4450;
        for(int k=0;k<24;k++) {
            for(int angle=0;angle<72;angle++){
                double theta=angle*Math.PI/36;
                double x=Math.cos(theta)*radius*1.08;
                double z=Math.sin(theta)*radius*1.08;
                double a=RegionalLandformMath.apply(k,2.5,x,z,radius,.7);
                check(Double.isFinite(a),"non-finite terrain on boundary");
                check(Math.abs(a-2.5)<1e-8,"terrain border discontinuity "+k);
            }
            check(RegionalLandformMath.apply(k,2.5,0,0,radius,-0.8)==2.5,
                    "ocean uplift/erosion regression");
            check(RegionalLandformMath.apply(k,0.2,0,0,radius,.6)==0.2,
                    "sea-level protection regression");
        }
        var m=RegionalLandformMath.class;
        check(RegionalLandformMath.effect(RegionalLandformMath.GUIANA_TEPUI,0,0,radius)>3,
                "tepui plateau missing");
        check(RegionalLandformMath.effect(RegionalLandformMath.GUIANA_TEPUI,0,0,radius)>
              RegionalLandformMath.effect(RegionalLandformMath.GUIANA_TEPUI,2500,0,radius)+2,
                "tepui cliff plateau is not distinct");
        check(RegionalLandformMath.effect(RegionalLandformMath.TROPICAL_INSELBERGS,0,0,radius)>2,
                "inselberg granite domes missing");
        check(RegionalLandformMath.effect(RegionalLandformMath.GLACIAL_DRUMLIN_FIELD,0,0,radius)>
              RegionalLandformMath.effect(RegionalLandformMath.GLACIAL_DRUMLIN_FIELD,300,350,radius),
                "drumlin hills not differentiated");
        check(RegionalLandformMath.effect(RegionalLandformMath.GLACIAL_ESKER_RIDGE,0,0,radius)>
              RegionalLandformMath.effect(RegionalLandformMath.GLACIAL_ESKER_RIDGE,0,500,radius)+1.0,
                "esker narrow ridge missing");
        check(RegionalLandformMath.effect(RegionalLandformMath.NAMIB_STAR_DUNES,850,0,radius)>
              RegionalLandformMath.effect(RegionalLandformMath.NAMIB_STAR_DUNES,736,425,radius)+.5,
                "dune arms not radiating");
        check(RegionalLandformMath.effect(RegionalLandformMath.EAST_AFRICAN_RIFT,0,0,radius)<
              RegionalLandformMath.effect(RegionalLandformMath.EAST_AFRICAN_RIFT,0,510,radius),
                "rift graben must lie between shoulders");
        check(RegionalLandformMath.effect(RegionalLandformMath.UTAH_SLOT_CANYON,0,0,radius)<
              RegionalLandformMath.effect(RegionalLandformMath.UTAH_SLOT_CANYON,0,400,radius)-1,
                "slot canyon channel not incised");
        check(RegionalLandformMath.effect(RegionalLandformMath.ICELAND_CALDERA,0,0,radius)<
              RegionalLandformMath.effect(RegionalLandformMath.ICELAND_CALDERA,0,1740,radius)-1,
                "caldera ring wall missing");
        System.out.println("GLOBAL_LANDFORM_V3_PASS: 24 shapes, 12km cells, all attainable, ocean and seams protected");
    }
}

package org.realscale.neoforge;

/** No Minecraft dependency: 16 isolated, chunk-order invariant atlas shapes. */
public final class RegionalLandformV2Smoke {
    private static void check(boolean b,String reason) {
        if(!b)throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        check(RegionalLandformMath.KINDS==8,"original v1 kind count modified");
        check(RegionalLandformMath.KINDS_V2==16,"expected 16 diverse shapes");
        check(RegionalLandformMath.CELL==4096,"v1 world cell changed");
        check(RegionalLandformMath.CELL_V2==8192,"v2 site is not 8km");
        // V1 math must use exactly its original palette, regardless of v2.
        for(int gx=-7;gx<=7;gx++)for(int gz=-7;gz<=7;gz++) {
            long old=RegionalLandformMath.hash(gx,gz);
            int kind=RegionalLandformMath.kind(old,0.20);
            check(kind>=0&&kind<8,"v1 switched to v2 classification");
            long h=RegionalLandformMath.hashV2(gx,gz);
            double x=RegionalLandformMath.centerXV2(gx,h);
            double z=RegionalLandformMath.centerZV2(gz,h);
            double radius=RegionalLandformMath.radiusV2(h);
            double loX=gx*8192.0,loZ=gz*8192.0;
            check(x-radius*1.08>loX && x+radius*1.08<loX+8192,
                    "v2 regional X seam");
            check(z-radius*1.08>loZ && z+radius*1.08<loZ+8192,
                    "v2 regional Z seam");
            int newer=RegionalLandformMath.kindV2(h,0.20);
            check(newer>=0&&newer<16,"new atlas selected invalid ID");
        }
        double radius=2750.0;
        for(int k=0;k<16;k++) {
            for(int j=0;j<96;j++) {
                double angle=j*Math.PI*2/96;
                double x=radius*1.08*Math.cos(angle),z=radius*1.08*Math.sin(angle);
                double value=RegionalLandformMath.apply(k,2.4,x,z,radius,.6);
                check(Double.isFinite(value),"nonfinite site "+k);
                check(Math.abs(value-2.4)<1e-8,"regional boundary leaked "+k);
            }
            check(RegionalLandformMath.apply(k,2.4,0,0,radius,-.5)==2.4,
                    "ocean destroyed by atlas "+k);
            check(RegionalLandformMath.apply(k,.2,0,0,radius,.8)==.2,
                    "sea-level continental flats destroyed "+k);
        }
        check(RegionalLandformMath.effect(RegionalLandformMath.TIBETAN_GLACIAL_TROUGH,
                        0,800,radius)>
              RegionalLandformMath.effect(RegionalLandformMath.TIBETAN_GLACIAL_TROUGH,
                        0,0,radius),"glacial trough lacks raised shoulders");
        check(RegionalLandformMath.effect(RegionalLandformMath.TIBETAN_ENDORHEIC_BASIN,
                        0,0,radius)<0,"Tibet basin needs a depression");
        check(RegionalLandformMath.effect(RegionalLandformMath.ANDEAN_SALT_PAN,
                        0,0,radius)<0,"Altiplano salt pan needs a floor");
        check(RegionalLandformMath.effect(RegionalLandformMath.ANDEAN_BOFEDEL_SHELF,
                        0,0,radius)<0,"Bofedal bench needs a lower core");
        check(RegionalLandformMath.effect(RegionalLandformMath.ECUADOR_VOLCANIC_CHAIN,
                        0,0,radius)>0.6,"volcanic chain lacks massifs");
        check(RegionalLandformMath.effect(RegionalLandformMath.YUNGUI_FENGLIN,
                        0,0,radius)>1,"tower karst lacks limestone peaks");
        check(RegionalLandformMath.effect(RegionalLandformMath.WULONG_TIAN_KENG,
                        0,0,radius)<0.35,"Wulong collapse depressions are missing");
        check(RegionalLandformMath.effect(RegionalLandformMath.SICHUAN_ESCARPMENT,
                        0,850,radius)>
              RegionalLandformMath.effect(RegionalLandformMath.SICHUAN_ESCARPMENT,
                        0,0,radius),"Sichuan basin margin must rise");
        System.out.println("REGIONAL_LANDFORM_V2_PASS: 16 shapes, 8km regions, 8 geologies, untouched v1/ocean");
    }
}

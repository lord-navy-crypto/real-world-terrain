package org.realscale.neoforge;

/**
 * Independent v3 cold-north macro morphology. The V1 and V2 codecs and
 * historical V1/V2 noise graphs remain byte-for-byte unchanged.
 * Uses the same world-absolute, seam-free site cells, now with two additional
 * original geological archetypes. No world reads, block writes or fluid edits.
 */
final class CinematicLandformMathV3 {
    static final int CELL=CinematicLandformMath.CELL;
    static final int STAR_DUNE_FIELD=6;
    static final int TEPUI_TABLELANDS=7;
    static final int CIRQUE_BASIN=8;
    static final int MORAINE_RIDGES=9;
    static final int NONE=10;

    private CinematicLandformMathV3() {}
    // Reuse the exact world-absolute v1 spatial cell layout for continuity.
    // The source functions themselves retain their v1 implementations.
    static long hash(int cx,int cz){return CinematicLandformMath.hash(cx,cz);}
    static int siteX(int cx,long h){return CinematicLandformMath.siteX(cx,h);}
    static int siteZ(int cz,long h){return CinematicLandformMath.siteZ(cz,h);}
    static double radius(long h){return CinematicLandformMath.radius(h);}
    static double gate(double continent,double height) {
        return CinematicLandformMath.gate(continent,height);
    }
    static int kind(long hash,double temperature,double humidity) {
        // Keep existing cold/wet archetypes, but allow climate-matched
        // diversity within broad wet tropics and hot arid environments.
        if(temperature>=0.04 && humidity>=0.16) {
            int variant=(int)Math.floorMod(hash>>>13,3L);
            return variant==0?CinematicLandformMath.SANDSTONE_TOWERS
                   :variant==1?CinematicLandformMath.KARST_TOWERS
                   :TEPUI_TABLELANDS;
        }
        if(temperature>=0.09 && humidity<=-0.09)
            return ((hash>>>21)&1L)==0L
                    ?CinematicLandformMath.CAPROCK_MESA:STAR_DUNE_FIELD;
        if(humidity<=0.09 && temperature>=-0.35)
            return CinematicLandformMath.INCISED_CANYON;
        if(temperature<=-0.30) {
            if(humidity < -0.04)return CinematicLandformMath.VOLCANIC_CALDERA;
            // Cold, moisture-supported uplands cycle through distinct
            // erosional U valleys, amphitheatre cirques and depositional
            // moraines. Selection is per immutable world-coordinate site.
            return switch((int)Math.floorMod(hash>>>27,3L)) {
                case 0 -> CinematicLandformMath.GLACIAL_U_TROUGH;
                case 1 -> CIRQUE_BASIN;
                default -> MORAINE_RIDGES;
            };
        }
        return NONE;
    }

    static double relief(int kind,double dx,double dz,double radius,long hash) {
        double r=Math.hypot(dx,dz)/radius;
        if(r>=1.0 || kind==NONE)return 0.0;
        double envelope=CinematicLandformMath.envelope(r);
        if(kind<STAR_DUNE_FIELD) {
            // V2 scenic erosion: replace perfectly circular rims and orderly
            // dot grids with smooth site-specific folding, without changing
            // a single byte of the V1 production shape function.
            // The original radius gate is applied last so warped shapes NEVER
            // leak across 4-km site boundaries.
            double seed=Math.floorMod(hash>>>36,6284L)/1000.0;
            double wx=dx*1.035+58.0*Math.sin(dz/290.0+seed)
                    +21.0*Math.sin((dx+dz)/124.0-seed);
            double wz=dz*0.955+52.0*Math.cos(dx/325.0-seed)
                    +24.0*Math.sin((dx-dz)/138.0+seed);
            double weather=0.88+0.16*
                    (0.5+0.5*Math.sin(wx/270.0+wz/410.0+seed));
            return envelope*weather*
                    CinematicLandformMath.relief(kind,wx,wz,radius,hash);
        }
        double angle=Math.floorMod(hash>>>45,6284L)/1000.0;
        double u=dx*Math.cos(angle)+dz*Math.sin(angle);
        double v=-dx*Math.sin(angle)+dz*Math.cos(angle);
        return switch(kind) {
            case STAR_DUNE_FIELD -> {
                // An asymmetric wind-aligned dune complex with branching
                // slipface arms, not a single symmetrical conical hill.
                // Positive-only sand relief; natural sea-floor and base
                // continental thresholds are safeguarded by density gate.
                double windward=0.5+0.5*Math.sin(u/195.0+0.33*Math.sin(v/315.0));
                double crossing=0.5+0.5*Math.sin(v/240.0-0.25*Math.sin(u/300.0));
                double ridge=Math.pow(windward,5.0);
                double arms=Math.pow(crossing,7.0);
                double star=Math.pow(Math.max(0.0,
                        Math.cos(5.0*Math.atan2(v,u))),8.0);
                double core=Math.exp(-Math.pow(Math.hypot(u,v)/830.0,2.0));
                yield envelope*(0.07+1.30*ridge+0.66*arms
                        +0.48*core*star);
            }
            case TEPUI_TABLELANDS -> {
                // Two caprock massifs with independent flat tops and steep
                // sides; the saddle between them opens a traverse corridor.
                double first=Math.pow(
                        Math.pow(Math.abs((u+450.0)/480.0),4)
                        +Math.pow(Math.abs((v+95.0)/440.0),4),0.25);
                double second=Math.pow(
                        Math.pow(Math.abs((u-450.0)/455.0),4)
                        +Math.pow(Math.abs((v-130.0)/395.0),4),0.25);
                double plateau1=1.0-CinematicLandformMath.smooth(0.68,0.84,first);
                double plateau2=1.0-CinematicLandformMath.smooth(0.68,0.84,second);
                double capped=Math.max(plateau1,plateau2);
                yield envelope*(0.10+3.15*capped);
            }
            case CIRQUE_BASIN -> {
                // Erosion-style amphitheatre in a cold mountain shoulder:
                // a concave basin, semicircular higher headwall and a
                // low, open outlet extending along +v. This is not a lake.
                double ellipse=Math.hypot(u/440.0,(v+185.0)/380.0);
                double basin=1.0-CinematicLandformMath.smooth(0.18,0.91,ellipse);
                double wall=CinematicLandformMath.smooth(0.54,0.76,ellipse)
                        *(1.0-CinematicLandformMath.smooth(1.00,1.27,ellipse));
                double opening=1.0-CinematicLandformMath.smooth(-40,270,v);
                double outlet=(1.0-CinematicLandformMath.smooth(100,250,
                        Math.abs(u-31*Math.sin(v/210.0))))
                        *CinematicLandformMath.smooth(-190,90,v)
                        *(1.0-CinematicLandformMath.smooth(570,870,v));
                yield envelope*(2.05*wall*opening-0.91*basin
                        -0.36*outlet);
            }
            case MORAINE_RIDGES -> {
                // Glacial till: two irregular side-ridges + arced terminal
                // ridge with a depressed inter-ridge corridor. No water,
                // glacial ice or unsupported floating structures are placed.
                double lateralOffset=Math.abs(u+26*Math.sin(v/170.0))-230.0;
                double leftRight=Math.exp(-Math.pow(lateralOffset/78.0,2))
                        *(1.0-CinematicLandformMath.smooth(620,960,Math.abs(v)));
                double elliptic=Math.hypot(u/495.0,(v-80.0)/520.0);
                double terminal=Math.exp(-Math.pow((elliptic-0.82)/0.12,2))
                        *CinematicLandformMath.smooth(-290,110,v);
                double hollow=(1.0-CinematicLandformMath.smooth(85,220,Math.abs(u)))
                        *(1.0-CinematicLandformMath.smooth(160,490,Math.abs(v)));
                yield envelope*(1.05*leftRight+0.87*terminal-0.24*hollow);
            }
            default -> 0.0;
        };
    }
    static double apply(int kind,double baseline,double dx,double dz,
                        double radius,long hash,double continental) {
        double gate=CinematicLandformMath.gate(continental,baseline);
        return gate==0?baseline:baseline+gate*relief(kind,dx,dz,radius,hash);
    }
}

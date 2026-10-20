package org.realscale.neoforge;

/**
 * Independent, opt-in v2 macro morphology. The tested V1 maths/codec and
 * historical V1 noise graph are not changed when the featured world upgrades.
 * Uses the same world-absolute, seam-free site cells, now with two additional
 * original geological archetypes. No world reads, block writes or fluid edits.
 */
final class CinematicLandformMathV2 {
    static final int CELL=CinematicLandformMath.CELL;
    static final int STAR_DUNE_FIELD=6;
    static final int TEPUI_TABLELANDS=7;
    static final int NONE=8;

    private CinematicLandformMathV2() {}
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
        if(temperature<=-0.30)
            return humidity>=-0.04?CinematicLandformMath.GLACIAL_U_TROUGH
                    :CinematicLandformMath.VOLCANIC_CALDERA;
        return NONE;
    }

    static double relief(int kind,double dx,double dz,double radius,long hash) {
        if(kind<STAR_DUNE_FIELD)
            return CinematicLandformMath.relief(kind,dx,dz,radius,hash);
        double r=Math.hypot(dx,dz)/radius;
        if(r>=1.0 || kind==NONE)return 0.0;
        double envelope=CinematicLandformMath.envelope(r);
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
                        Math.pow(Math.abs((u+380.0)/530.0),4)
                        +Math.pow(Math.abs((v+95.0)/440.0),4),0.25);
                double second=Math.pow(
                        Math.pow(Math.abs((u-385.0)/475.0),4)
                        +Math.pow(Math.abs((v-130.0)/395.0),4),0.25);
                double plateau1=1.0-CinematicLandformMath.smooth(0.68,0.84,first);
                double plateau2=1.0-CinematicLandformMath.smooth(0.68,0.84,second);
                double capped=Math.max(plateau1,plateau2);
                yield envelope*(0.10+3.15*capped);
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

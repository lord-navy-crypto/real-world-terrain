package org.realscale.neoforge;

/** Rare geological feature mask: world-seeded, 12km cells, smooth in every chunk. */
final class MeasuredHybridRareMath {
    private MeasuredHybridRareMath() {}
    static final int RARITY_DIVISOR=5;
    static final int ACTIVE_RESIDUES=2;
    static boolean isRare(long hash) {
        return Math.floorMod(hash, RARITY_DIVISOR)<ACTIVE_RESIDUES;
    }
    /** Unmodified measured baseline outside the eligible dry inland feature zone. */
    static double weight(long hash,double dx,double dz,double radius,
                         double continentalness,double proceduralHeight) {
        if(!isRare(hash))return 0.0;
        double r=Math.hypot(dx,dz)/radius;
        if(r>=1.04)return 0.0;
        double land=RegionalLandformMath.gate(continentalness,proceduralHeight);
        double shape=1.0-GeoMath.smoothstep(0.58,1.04,r);
        return GeoMath.clamp(land*shape,0.0,1.0);
    }
    static double combine(double measured,double synthetic,double weight) {
        if(weight<=0)return measured; // bit-exact original DEM and fallback
        if(weight>=1)return synthetic;
        return measured+(synthetic-measured)*weight;
    }
}

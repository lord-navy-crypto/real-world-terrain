package org.realscale.neoforge;

/**
 * Thread-confined cell-invariant geometry for the opt-in global landform V3.
 * The site hash/centre/radius DO NOT depend on Y, the base density, climate
 * sampler, or feature placement order. A 12,288-block cell is reused across
 * numerous density evaluations, especially in 2096-high worlds.
 *
 * Only memoize geometry. Keep the selector-based landform KIND lazy so
 * completely disjoint or marine samples never compute the selector.
 * No static world cache and no generated-block/biome information is stored.
 */
final class RegionalGlobalLandformCellMemo {
    int cellX=Integer.MIN_VALUE,cellZ=Integer.MIN_VALUE;
    long hash;
    int siteX,siteZ;
    double radius,edgeSquared;
    int kind;
    boolean hasKind;
    long geometryHits,geometryMisses;

    void at(int cx,int cz){
        if(cellX==cx&&cellZ==cz){
            geometryHits++;
            return;
        }
        cellX=cx;cellZ=cz;
        hash=RegionalLandformMath.hashV3(cx,cz);
        siteX=RegionalLandformMath.centerXV3(cx,hash);
        siteZ=RegionalLandformMath.centerZV3(cz,hash);
        radius=RegionalLandformMath.radiusV3(hash);
        edgeSquared=1.166400001*radius*radius;
        hasKind=false;
        geometryMisses++;
    }
}

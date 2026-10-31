package org.realscale.neoforge;
public final class RealScaleSpawnMathSmoke {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        var natural=RealScaleSpawnMath.profileFor("tva_natural_smoothed");
        var alpine=RealScaleSpawnMath.profileFor("tva_alpine_smoothed");
        var canyon=RealScaleSpawnMath.profileFor("tva_canyon_smoothed");
        var usgs=RealScaleSpawnMath.profileFor("usgs_big_sur");
        check(natural!=null&&alpine!=null&&canyon!=null&&usgs!=null,"profiles missing");
        int target=RealScaleSpawnMath.targetHeight(42L,63,natural);
        check(target==RealScaleSpawnMath.targetHeight(42L,63,natural),"target not deterministic");
        check(target>=63+natural.minOffset()&&target<=63+natural.maxOffset(),"target outside band");
        check(alpine.maxOffset()<=40&&canyon.maxOffset()<=32,"highland/canyon profile too high");
        check(RealScaleSpawnMath.score(82,82,2,0)<RealScaleSpawnMath.score(130,82,20,6400),"scoring prefers distant high relief");
        check(RealScaleSpawnMath.isHeightAllowed(90,63,natural),"mid elevation rejected");
        check(!RealScaleSpawnMath.isHeightAllowed(180,63,natural),"high peak allowed");
        check(RealScaleSpawnMath.profileFor("unrecognized_settings").minOffset()==natural.minOffset(),"fallback profile differs");
        System.out.println("REALSCALE_SPAWN_MATH_OK");
    }
}

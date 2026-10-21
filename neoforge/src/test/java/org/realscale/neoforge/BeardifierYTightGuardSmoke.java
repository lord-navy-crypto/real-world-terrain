package org.realscale.neoforge;
import java.util.Random;

/** Run only with -Drealscale.experimental.beardifier.tight_y=true. */
public final class BeardifierYTightGuardSmoke {
    private static void check(boolean pass,String message){
        if(!pass)throw new AssertionError(message);
    }
    public static void main(String[] args){
        BeardifierYExtent e=new BeardifierYExtent();
        check(e.guard()==64&&e.isTight(),"Tight-Y guard explicitly enabled");
        check(e.canSkip(1000),"Empty original Beardifier remains zero");
        e.includePiece(-10,30,20);
        e.includeJunction(50);
        for(int y=-74;y<=114;y++)
            check(!e.canSkip(y),"Cannot skip inside 64-block support");
        check(e.canSkip(-75)&&e.canSkip(115),"Tight guard exact borders");
        check(!e.canSkipOriginal(-75)&&!e.canSkipOriginal(115),
                "These positions must represent NEW tight-only skips");
        e.disable();
        check(!e.canSkip(Integer.MAX_VALUE),"Unknown fails open");
        Random r=new Random(20261021L);
        for(int i=0;i<100000;i++){
            int min=r.nextInt(800)-350,height=r.nextInt(150)+1;
            int delta=r.nextInt(500)-250;
            BeardifierYExtent b=new BeardifierYExtent();
            b.includePiece(min,min+height,delta);
            long lo=Math.min(min,(long)min+delta);
            long hi=Math.max((long)min+height,(long)min+height+delta);
            for(long y=lo-64;y<=hi+64;y+=13)
                check(!b.canSkip((int)y),"False positive within tight guard");
            check(b.canSkip((int)(lo-65))&&b.canSkip((int)(hi+65)),
                    "Outside support must skip");
            check(!b.canSkipOriginal((int)(lo-65))&&
                  !b.canSkipOriginal((int)(hi+65)),"Legacy guard comparison");
        }
        System.out.println("REALSCALE_BEARDIFIER_TIGHT_Y_GUARD_PASS cases=100000");
    }
}

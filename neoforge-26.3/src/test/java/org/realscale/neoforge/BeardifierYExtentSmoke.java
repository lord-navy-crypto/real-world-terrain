package org.realscale.neoforge;

import java.util.Random;
public final class BeardifierYExtentSmoke {
    private static void check(boolean pass,String message){
        if(!pass)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        BeardifierYExtent a=new BeardifierYExtent();
        check(a.canSkip(-64)&&a.canSkip(2031),"Empty Beardifier must remain zero");
        a.includePiece(-10,30,20);
        a.includeJunction(50);
        for(int y=-266;y<=306;y++)
            check(!a.canSkip(y),"Within conservative structure envelope at "+y);
        check(a.canSkip(-267)&&a.canSkip(307),"Outside guard must be rejected");
        a.disable();
        for(int y:new int[]{Integer.MIN_VALUE,-2000,-64,0,1000,2031,Integer.MAX_VALUE})
            check(!a.canSkip(y),"Unknown structure must run vanilla at "+y);
        Random rng=new Random(20261021);
        for(int i=0;i<100000;i++) {
            BeardifierYExtent e=new BeardifierYExtent();
            int min=rng.nextInt(700)-200;
            int height=rng.nextInt(180)+1;
            int delta=rng.nextInt(300)-150;
            e.includePiece(min,min+height,delta);
            long lower=Math.min(min,(long)min+delta);
            long upper=Math.max((long)min+height,(long)min+height+delta);
            for(long y=lower-256;y<=upper+256;y+=11)
                check(!e.canSkip((int)y),"Inside conservative Y envelope");
            check(e.canSkip((int)(lower-257)),"Below envelope must skip");
            check(e.canSkip((int)(upper+257)),"Above envelope must skip");
        }
        System.out.println("REALSCALE_EXPERIMENTAL_BEARDIFIER_Y_ENVELOPE_PASS cases=100000");
    }
}

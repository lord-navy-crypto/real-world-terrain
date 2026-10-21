package org.realscale.neoforge;

import java.util.Random;

/** Pure Java guard geometry test; true vanilla parity is checked on servers. */
public final class BeardifierXZExtentSmoke {
    private static void check(boolean pass,String message){
        if(!pass)throw new AssertionError(message);
    }
    public static void main(String[] args){
        BeardifierXZExtent empty=new BeardifierXZExtent();
        check(empty.canSkip(0,0),"Empty iterator must skip");
        check(empty.canSkip(Integer.MIN_VALUE,Integer.MAX_VALUE),"Empty extremes");
        BeardifierXZExtent b=new BeardifierXZExtent();
        b.includePiece(-20,20,-50,-10);
        b.includeJunction(100,50);
        for(int x=-52;x<=132;x+=4)
            for(int z=-82;z<=82;z+=4)
                check(!b.canSkip(x,z),"Inside conservative union rectangle");
        check(b.canSkip(-53,0)&&b.canSkip(133,0),"X borders");
        check(b.canSkip(0,-83)&&b.canSkip(0,83),"Z borders");
        b.disable();
        check(!b.canSkip(Integer.MIN_VALUE,Integer.MAX_VALUE),"Fail open");
        BeardifierXZExtent invalid=new BeardifierXZExtent();
        invalid.includePiece(20,-20,0,10);
        check(!invalid.canSkip(1000,1000),"Invalid box must fail open");
        BeardifierXZExtent extremes=new BeardifierXZExtent();
        extremes.includePiece(Integer.MAX_VALUE-2,Integer.MAX_VALUE,
                Integer.MIN_VALUE,Integer.MIN_VALUE+2);
        check(!extremes.canSkip(Integer.MAX_VALUE,Integer.MIN_VALUE),
                "Extreme inside");
        check(extremes.canSkip(0,0),"No coordinate overflow");
        Random rng=new Random(20261021L);
        for(int i=0;i<100000;i++){
            int x=rng.nextInt(60000)-30000,z=rng.nextInt(60000)-30000;
            int w=rng.nextInt(140),d=rng.nextInt(140);
            BeardifierXZExtent e=new BeardifierXZExtent();
            e.includePiece(x,x+w,z,z+d);
            for(int j=-32;j<=w+32;j++)
                check(!e.canSkip(x+j,z),"Interior X");
            for(int j=-32;j<=d+32;j++)
                check(!e.canSkip(x,z+j),"Interior Z");
            check(e.canSkip(x-33,z)&&e.canSkip(x+w+33,z),"Outside X");
            check(e.canSkip(x,z-33)&&e.canSkip(x,z+d+33),"Outside Z");
        }
        System.out.println("REALSCALE_BEARDIFIER_XZ_EXTENT_PASS 100000");
    }
}

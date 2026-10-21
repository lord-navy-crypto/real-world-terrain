package org.realscale.neoforge;
/** Pure Java v4 shapes, same legacy island centers but irregular cliffs and skirts. */
public final class SkyIslandShapeV4 {
    private SkyIslandShapeV4(){}
    public static int top(int centerY,int dx,int dz,double ratio) {
        double r=Math.max(0,Math.min(1,ratio));
        double folds=2.5*Math.sin(dx/5.8+dz/11.0)
            *Math.cos(dz/6.2-dx/13.0)*(1.0-r);
        return centerY-(int)Math.floor(4.0*r)+(int)Math.round(folds);
    }
    public static int thickness(double ratio,int dx,int dz) {
        double r=Math.max(0,Math.min(1,ratio));
        return 3+(int)Math.round(10.0*Math.pow(1.0-r,1.2))
            +(Math.floorMod(dx*13+dz*19,7)==0?2:0);
    }
}

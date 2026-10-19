package org.realscale.neoforge;

public final class PermafrostPolygonMathSmoke {
    public static void main(String[] args) {
        int edges=0, total=0;
        for (int x=-144;x<144;x++) for (int z=-144;z<144;z++) {
            boolean rim=PermafrostPolygonMath.isRim(x,z);
            if (rim) edges++;
            total++;
            if(rim!=PermafrostPolygonMath.isRim(x,z))
                throw new AssertionError("Pattern not deterministic at "+x+","+z);
        }
        double coverage=(double)edges/total;
        if(coverage<0.035||coverage>0.25)
            throw new AssertionError("Pattern too sparse or dense: "+coverage);
        for(int x=-160;x<=160;x+=16) {
            int a=0,b=0;
            for(int z=-96;z<=96;z++) {
                if(PermafrostPolygonMath.isRim(x-1,z))a++;
                if(PermafrostPolygonMath.isRim(x,z))b++;
            }
            if(a==0||b==0)throw new AssertionError("Broken edge seam at x="+x);
        }
        System.out.println("PERMAFROST_POLYGON_WORLD_COORDINATE_PASS "+coverage);
    }
}

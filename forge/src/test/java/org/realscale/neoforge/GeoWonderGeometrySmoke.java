package org.realscale.neoforge;

/** Actual logic exercised from production region geometry, without game mods. */
public final class GeoWonderGeometrySmoke {
    private static void require(boolean valid,String message) {
        if(!valid)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        int found=0;
        for(int kind=0;kind<10;kind++) {
            for(int gx=-60;gx<=60;gx++)for(int gz=-4;gz<=4;gz++) {
                GeoWonderGeometry.Site site=GeoWonderGeometry.surfaceSite(gx,gz,kind);
                if(site==null)continue;
                found++;
                GeoWonderGeometry.Site repeat=GeoWonderGeometry.surfaceSite(gx,gz,kind);
                require(site.x==repeat.x && site.z==repeat.z && site.r==repeat.r,
                        "nondeterministic site");
                require(GeoWonderGeometry.contains(site,site.x,site.z),"center outside");
                require(!GeoWonderGeometry.contains(site,site.x+site.r+1,site.z),
                        "outside radius accepted");
                int chunkX=Math.floorDiv(site.x,16)*16;
                int chunkZ=Math.floorDiv(site.z,16)*16;
                require(GeoWonderGeometry.intersects(site,chunkX,chunkZ),
                        "center chunk does not intersect");
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
                    int x=chunkX+dx*16,z=chunkZ+dz*16;
                    boolean expected=site.x+site.r>=x && site.x-site.r<x+16
                            && site.z+site.r>=z && site.z-site.r<z+16;
                    require(GeoWonderGeometry.intersects(site,x,z)==expected,
                            "chunk-border discontinuity");
                }
            }
        }
        require(found>50,"rare region sites are missing");
        for(int x=-10000;x<=10000;x+=89) {
            int min=Math.floorDiv(x,16)*16;
            int gx=GeoWonderGeometry.gridAtChunk(min);
            require(gx==(int)Math.floorDiv((long)min+8+128,256),
                    "incorrect grid index in negative space");
        }
        System.out.println("PASS "+found+" deterministic region sites / chunk boundaries");
    }
}

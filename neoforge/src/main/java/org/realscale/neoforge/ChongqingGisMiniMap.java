package org.realscale.neoforge;

/**
 * Bounded, purely local 2D schematic of the TRUE OSM GIS rows.
 * Never downloads a map, opens chunks, creates terrain or fakes buildings.
 * The preview is a geographic X-east/Z-south occupancy guide, not a
 * heightfield, aerial photograph or 3D model.
 */
public final class ChongqingGisMiniMap {
    public static final int SIZE = 72;
    public static final int BUILDINGS=1, ROADS=2, RAILWAYS=4, WATER=8;
    private final int[] pixels=new int[SIZE*SIZE];

    private ChongqingGisMiniMap() {}

    public static ChongqingGisMiniMap of(ChongqingGisPlan plan) {
        ChongqingGisMiniMap map=new ChongqingGisMiniMap();
        final int side=plan.radius*2+1;
        for (ChongqingGisPlan.Row row : plan.rows) {
            final int mask=switch (row.kind()) {
                case "building_footprint" -> BUILDINGS;
                case "road" -> ROADS;
                case "railway" -> RAILWAYS;
                case "water_reference" -> WATER;
                default -> 0;
            };
            if (mask==0) continue;
            int z=Math.floorDiv((row.z()+plan.radius)*SIZE,side);
            z=Math.clamp(z,0,SIZE-1);
            int first=Math.clamp(
                    Math.floorDiv((row.x0()+plan.radius)*SIZE,side),0,SIZE-1);
            int last=Math.clamp(
                    Math.floorDiv((row.x1()+plan.radius)*SIZE,side),0,SIZE-1);
            for (int x=first;x<=last;x++) map.pixels[z*SIZE+x]|=mask;
        }
        return map;
    }

    public int at(int x,int z) {
        if (x<0 || x>=SIZE || z<0 || z>=SIZE)
            throw new IndexOutOfBoundsException("GIS map pixel outside 72x72");
        return pixels[z*SIZE+x];
    }

    public int occupied() {
        int count=0;
        for(int value:pixels) if(value!=0)count++;
        return count;
    }
}

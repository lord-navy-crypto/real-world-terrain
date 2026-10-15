package org.realscale.neoforge;

/** No-Minecraft Java 21 regression for basin flow direction and tributaries. */
public final class WatershedRoutingMathSmoke {
    private WatershedRoutingMathSmoke() {}
    private static void yes(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        // The central valley receives two hill-slope tributaries.
        WatershedRoutingMath.Elevation valley=(x,z)->
                200-10*z+15*Math.abs(x-2);
        var basin=WatershedRoutingMath.solve(0,0,5,5,1,valley,(x,z)->1);
        yes(basin.downstreamX(1,1)==2 && basin.downstreamZ(1,1)==2,
                "Western hillside must feed center");
        yes(basin.downstreamX(3,1)==2 && basin.downstreamZ(3,1)==2,
                "Eastern hillside must feed center");
        yes(basin.accumulation(2,2)>=3,
                "Downstream flow must accumulate tributary runoff");
        yes(basin.streamOrder(2,2)>=2,
                "Confluence must raise stream order");
        for(int z=0;z<5;z++) for(int x=0;x<5;x++) {
            int nx=basin.downstreamX(x,z);
            int nz=basin.downstreamZ(x,z);
            if(nx!=Integer.MIN_VALUE)
                yes(valley.at(nx,nz)<valley.at(x,z),
                        "Flow path must strictly descend");
            yes(basin.accumulation(x,z)>=basin.localRunoff(x,z),
                    "Discharge must include local runoff");
        }

        // Direction is invariant across region/chunk boundaries because the
        // elevation sampler always receives absolute world coordinates.
        WatershedRoutingMath.Elevation world=(x,z)->300-3*x-7*z;
        var west=WatershedRoutingMath.solve(-8,-8,6,6,4,world,(x,z)->4);
        var east=WatershedRoutingMath.solve(0,-8,6,6,4,world,(x,z)->4);
        for(int z=0;z<6;z++) for(int x=0;x<4;x++) {
            yes(west.downstreamX(x+2,z)==east.downstreamX(x,z)
                    && west.downstreamZ(x+2,z)==east.downstreamZ(x,z),
                    "Routing must not depend on rectangular grid boundary");
        }
        // But local accumulation DOES depend on region boundaries; full
        // watershed calculations must stitch upstream edge contributions.
        yes(west.edgeOutflow(5,5)>0, "Boundary outflow must be explicit");

        var flat=WatershedRoutingMath.solve(-64,-64,8,8,16,
                (x,z)->100,(x,z)->2);
        for(int z=0;z<8;z++)for(int x=0;x<8;x++) {
            yes(flat.downstreamX(x,z)==Integer.MIN_VALUE,
                    "Flat depression must not invent a downhill neighbor");
            yes(flat.accumulation(x,z)==2,
                    "Closed flat basin must retain local runoff");
            yes(flat.streamOrder(x,z)==1, "Undrained flat reach order");
        }

        var repeat=WatershedRoutingMath.solve(0,0,5,5,1,valley,(x,z)->1);
        for(int z=0;z<5;z++)for(int x=0;x<5;x++) {
            yes(basin.accumulation(x,z)==repeat.accumulation(x,z)
                    && basin.streamOrder(x,z)==repeat.streamOrder(x,z),
                    "Regional runoff and stream order must be deterministic");
        }
        boolean rejected=false;
        try { WatershedRoutingMath.solve(0,0,513,513,1,world,(x,z)->1); }
        catch(IllegalArgumentException expected) { rejected=true; }
        yes(rejected,"Bounded area allocation gate missing");
        System.out.println("TVA_WATERSHED_ROUTING_PASS: downhill routing, tributaries, "
                +"stream order, signed coordinates, boundary consistency and limits");
    }
}

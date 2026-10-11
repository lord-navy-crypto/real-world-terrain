package org.realscale.geo;

import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Educational, deterministic, SI-unit landscape-evolution kernel.
 * This is NOT a Minecraft mod and is NOT a validated reconstruction of an actual locality.
 * Boundary cells are outlets; global cross-tile drainage requires an additional solver.
 * No Minecraft blocks or registry entries are defined here.
 */
public final class GeoPhysicsKernels {
    public record Settings(double gridMeters,
                           double stepYears,
                           double channelErodibilityMetersPerYear,
                           double referenceDischargeCubicMetersPerYear,
                           double referenceSlope,
                           double dischargeExponent,
                           double slopeExponent,
                           double transportCapacityFraction) {
        public Settings {
            if (!(gridMeters > 0 && stepYears > 0 && channelErodibilityMetersPerYear >= 0
                    && referenceDischargeCubicMetersPerYear > 0 && referenceSlope > 0
                    && dischargeExponent > 0 && slopeExponent > 0
                    && transportCapacityFraction >= 0 && transportCapacityFraction <= 1)) {
                throw new IllegalArgumentException("Invalid physical parameter");
            }
        }
        public static Settings demonstration() {
            // Explicitly EXAMPLE values. Rock-specific K and run-off must be calibrated.
            return new Settings(32.0, 100.0, 0.0004, 1_000_000.0,
                                0.01, 0.5, 1.0, 0.9);
        }
    }
    public static final class Grid {
        public final int width, height;
        public final double[] heightMeters, runoffRainMetersPerYear, runoffFraction;
        public final double[] upliftMetersPerYear, rockResistance;
        public Grid(int width, int height) {
            if(width < 3 || height < 3) throw new IllegalArgumentException("At least 3x3");
            this.width=width; this.height=height;
            int n=width*height;
            this.heightMeters=new double[n];
            this.runoffRainMetersPerYear=new double[n];
            this.runoffFraction=new double[n];
            this.upliftMetersPerYear=new double[n];
            this.rockResistance=new double[n];
            Arrays.fill(rockResistance,1.0);
            Arrays.fill(runoffFraction,0.7);
        }
        public int index(int x,int z){return z*width+x;}
        public Grid copy(){
            Grid g=new Grid(width,height);
            System.arraycopy(heightMeters,0,g.heightMeters,0,heightMeters.length);
            System.arraycopy(runoffRainMetersPerYear,0,g.runoffRainMetersPerYear,0,heightMeters.length);
            System.arraycopy(runoffFraction,0,g.runoffFraction,0,heightMeters.length);
            System.arraycopy(upliftMetersPerYear,0,g.upliftMetersPerYear,0,heightMeters.length);
            System.arraycopy(rockResistance,0,g.rockResistance,0,heightMeters.length);
            return g;
        }
    }
    public record StepReport(double rainfallRunoffCubicMetersPerYear,
                             double waterOutletCubicMetersPerYear,
                             double rockErodedCubicMeters,
                             double depositedCubicMeters,
                             double sedimentExportedCubicMeters,
                             double netUpliftCubicMeters,
                             int visitedCells,int outletCells,int changedCells) {}

    private record Entry(int index, double spillHeight) {}
    private static final int[][] DIRS={{-1,-1},{0,-1},{1,-1},{-1,0},{1,0},{-1,1},{0,1},{1,1}};

    public static StepReport evolve(Grid g, Settings s) {
        int n=g.heightMeters.length, w=g.width, ht=g.height;
        double area=s.gridMeters()*s.gridMeters();
        double netUplift=0;
        for(int i=0;i<n;i++){
            double h=g.heightMeters[i], r=g.rockResistance[i], rain=g.runoffRainMetersPerYear[i], frac=g.runoffFraction[i];
            if(!Double.isFinite(h)||!Double.isFinite(r)||r<=0||!Double.isFinite(rain)||rain<0
                    ||!Double.isFinite(frac)||frac<0||frac>1||!Double.isFinite(g.upliftMetersPerYear[i]))
                throw new IllegalArgumentException("Bad grid input at cell " + i);
            double dz=g.upliftMetersPerYear[i]*s.stepYears();
            g.heightMeters[i]+=dz; netUplift+=dz*area;
        }
        // Priority-Flood topology; parents form an acyclic graph to tile perimeter.
        int[] receiver=new int[n], floodOrder=new int[n];
        double[] spill=new double[n], water=new double[n];
        Arrays.fill(receiver,-2);
        PriorityQueue<Entry> pq=new PriorityQueue<>(Comparator.comparingDouble(Entry::spillHeight)
                   .thenComparingInt(Entry::index));
        for(int z=0;z<ht;z++)for(int x=0;x<w;x++){
            if(x!=0&&z!=0&&x!=w-1&&z!=ht-1)continue;
            int i=z*w+x; receiver[i]=-1; spill[i]=g.heightMeters[i];
            pq.add(new Entry(i,spill[i]));
        }
        int visited=0;
        while(!pq.isEmpty()){
            Entry node=pq.remove();int i=node.index();floodOrder[visited++]=i;
            int x=i%w,z=i/w;
            for(int[] d:DIRS){
                int nx=x+d[0],nz=z+d[1];
                if(nx<0||nz<0||nx>=w||nz>=ht)continue;
                int j=nz*w+nx;
                if(receiver[j]!=-2)continue;
                receiver[j]=i;
                spill[j]=Math.max(g.heightMeters[j],Math.nextUp(spill[i]));
                pq.add(new Entry(j,spill[j]));
            }
        }
        if(visited!=n)throw new IllegalStateException("Flood network incomplete");
        double localWater=0;
        for(int i=0;i<n;i++){
            water[i]=g.runoffRainMetersPerYear[i]*area*g.runoffFraction[i];
            localWater+=water[i];
        }
        // Downstream propagation in reverse flood order, m3/year.
        for(int k=n-1;k>=0;k--){int i=floodOrder[k],p=receiver[i];if(p>=0)water[p]+=water[i];}
        double waterOut=0;int outlets=0;
        for(int i=0;i<n;i++)if(receiver[i]<0){waterOut+=water[i];outlets++;}
        // Erode solid rock using stream power and resistance. K is a dimensional
        // erosion rate; rainfall and river slope determine dimensionless modifiers.
        double[] sediment=new double[n];
        double totalErosion=0,totalDeposition=0,totalExport=0;
        int changed=0;
        for(int i=0;i<n;i++){
            int p=receiver[i];if(p<0)continue;
            int ix=i%w,iz=i/w,px=p%w,pz=p/w;
            double dist=s.gridMeters()*Math.hypot(px-ix,pz-iz);
            double slope=Math.max(0,(g.heightMeters[i]-g.heightMeters[p])/dist);
            if(slope<=0 || water[i]<=0)continue;
            double erosionRate=s.channelErodibilityMetersPerYear()
                    *Math.pow(water[i]/s.referenceDischargeCubicMetersPerYear(),s.dischargeExponent())
                    *Math.pow(slope/s.referenceSlope(),s.slopeExponent())
                    /g.rockResistance[i];
            // Stability guard: never erode more than 1/4 of the local relief
            // toward the receiver in a single time step. Full PDE solvers require
            // adaptive stepping or implicit schemes instead.
            double maxCut=Math.max(0,(g.heightMeters[i]-g.heightMeters[p])*0.25);
            double depth=Math.min(erosionRate*s.stepYears(),maxCut);
            if(depth<=0)continue;
            g.heightMeters[i]-=depth;sediment[i]+=depth*area;
            totalErosion+=depth*area;changed++;
        }
        // Eroded material is carried downstream; a bounded fraction can settle
        // in low-slope reaches. Erosion = deposition + sediment exported, m3.
        for(int k=n-1;k>=0;k--){
            int i=floodOrder[k],p=receiver[i];
            double supply=sediment[i];if(supply<=0)continue;
            double grade=p<0?0:Math.max(0,(g.heightMeters[i]-g.heightMeters[p])/
                      (s.gridMeters()*Math.hypot((i%w)-(p%w),(i/w)-(p/w))));
            double lowSlopeFactor=Math.max(0,1.0-Math.min(1,grade/0.04));
            double settle=supply*(1-s.transportCapacityFraction())*lowSlopeFactor;
            if(p<0)settle=0; // Boundary sediment exits macro-tile.
            g.heightMeters[i]+=settle/area;
            totalDeposition+=settle;
            supply-=settle;
            if(p>=0)sediment[p]+=supply;
            else totalExport+=supply;
        }
        return new StepReport(localWater,waterOut,totalErosion,totalDeposition,totalExport,
                              netUplift,visited,outlets,changed);
    }
    private GeoPhysicsKernels(){}
}

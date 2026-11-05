package org.realscale.geo;

import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Region-wide hydrology with a single flow graph across MANY 4 km terrain tiles.
 * 1 horizontal world block = 1 metre. No Minecraft dependency or new block types.
 *
 * Model boundary: all cells on the OUTER macro-region border are outlets;
 * internal terrain tiles are not outlets. This solves cross-TILE flow continuity
 * inside the region, NOT infinite-world cross-REGION drainage. This distinction
 * is central to the correctness contract and is checked in tests.
 *
 * This is a reproducible numerical model, not a calibrated Earth model.
 */
public final class RegionalWatershed {
    private RegionalWatershed() {}
    public static final int SEA_Y = 63, MIN_Y = -64, MAX_Y = 2031;
    private static final int[][] D8 = {
        {-1,-1},{0,-1},{1,-1},{-1,0},{1,0},{-1,1},{0,1},{1,1}
    };
    private record QueueCell(int index, double h) {}

    public record Parameters(int gridMeters, double timeYears, double erodibilityMetersPerYear,
            double referenceDischargeM3PerYear, double referenceSlope, double dischargeExponent,
            double slopeExponent, double riverThresholdM3PerYear,
            double capacityFraction, double maxCutMetres) {
        public Parameters {
            if (gridMeters < 1 || timeYears <= 0 || erodibilityMetersPerYear < 0
                || referenceDischargeM3PerYear <= 0 || referenceSlope <= 0
                || dischargeExponent <= 0 || slopeExponent <= 0 || riverThresholdM3PerYear <= 0
                || capacityFraction < 0 || capacityFraction > 1 || maxCutMetres < 0
                || !Double.isFinite(timeYears) || !Double.isFinite(erodibilityMetersPerYear)
                || !Double.isFinite(referenceDischargeM3PerYear)
                || !Double.isFinite(referenceSlope) || !Double.isFinite(riverThresholdM3PerYear)
                || !Double.isFinite(maxCutMetres)) throw new IllegalArgumentException("Invalid watershed parameters");
        }
        public static Parameters demonstrator() {
            return new Parameters(64, 800, 0.0003, 1_000_000.0, .01, .45, 1.0,
                    1_500_000.0, .8, 15);
        }
    }

    public static final class Inputs {
        public final int nx, nz;
        public final int originXMetres, originZMetres;
        public final double[] bedrockY, rainMetresPerYear, runoffFraction, upliftMetresPerYear, rockResistance;
        public Inputs(int nx, int nz, int originXMetres, int originZMetres) {
            if (nx < 8 || nz < 8 || (long)nx * nz > 4_000_000L)
                throw new IllegalArgumentException("Expected 8..4,000,000 macro-region cells");
            this.nx=nx; this.nz=nz;this.originXMetres=originXMetres;this.originZMetres=originZMetres;
            int n=nx*nz;
            bedrockY=new double[n];rainMetresPerYear=new double[n];
            runoffFraction=new double[n];upliftMetresPerYear=new double[n];rockResistance=new double[n];
            Arrays.fill(rainMetresPerYear,.9);Arrays.fill(runoffFraction,.55);
            Arrays.fill(rockResistance,1);
        }
        public int at(int x,int z){return z*nx+x;}
    }

    public record Ledger(double localRunoffM3PerYear, double outletRunoffM3PerYear,
                         double erodedM3, double depositedM3, double exportedM3,
                         double upliftM3, int receiverLoops, int internalTileBoundaryCrossings,
                         int riverCells, int pitCells, int regionOutlets) { }

    public static final class Result {
        public final int nx,nz,originX,originZ,gridMeters;
        final double[] bedY, effectiveSpillY, dischargeM3PerYear, channelCutMetres;
        final int[] receiver, outlet;
        final boolean[] river, pond, ocean;
        final double[] inputBaselineY;
        public final Ledger ledger;
        Result(Inputs in, Parameters p, double[] bed, double[] spill, double[] q,
               double[] cut, int[] parent, int[] outlet, boolean[] river, boolean[] pond,
               boolean[] ocean, double[] originalBaselineY, Ledger ledger) {
            this.nx=in.nx;this.nz=in.nz;originX=in.originXMetres;originZ=in.originZMetres;
            gridMeters=p.gridMeters();
            this.bedY=bed;this.effectiveSpillY=spill;this.dischargeM3PerYear=q;
            this.channelCutMetres=cut;this.receiver=parent;this.outlet=outlet;
            this.river=river;this.pond=pond;this.ocean=ocean;
            this.inputBaselineY=originalBaselineY;this.ledger=ledger;
        }
        public int at(int x,int z){return z*nx+x;}
        /** One-metre positions share the same interpolated surface, regardless of Minecraft chunk order. */
        public double heightAt(double x,double z) {return bilinear(bedY,x,z);}
        public double dischargeAt(double x,double z) {return bilinear(dischargeM3PerYear,x,z);}
        public double originalBaselineAt(double x,double z) {return bilinear(inputBaselineY,x,z);}
        public double spillLevelAt(double x,double z) {return bilinear(effectiveSpillY,x,z);}
        private int nearestIndex(double x,double z) {
            int ix=Math.min(nx-1,Math.max(0,(int)Math.round((x-originX)/(double)gridMeters)));
            int iz=Math.min(nz-1,Math.max(0,(int)Math.round((z-originZ)/(double)gridMeters)));
            return at(ix,iz);
        }
        public boolean isOceanAt(double x,double z){return ocean[nearestIndex(x,z)];}
        public boolean isPondAt(double x,double z){return pond[nearestIndex(x,z)];}
        /** Channel center-lines follow D8 edges, so rivers do not become 64m-wide square strips. */
        public boolean isChannelAt(double x,double z) {
            int gx=(int)Math.round((x-originX)/(double)gridMeters);
            int gz=(int)Math.round((z-originZ)/(double)gridMeters);
            for(int dz=-2;dz<=2;dz++)for(int dx=-2;dx<=2;dx++){
                int sx=gx+dx,sz=gz+dz;
                if(sx<0||sz<0||sx>=nx||sz>=nz)continue;
                int i=at(sx,sz),r=receiver[i];
                if(!river[i]||r<0)continue;
                double ax=originX+(double)sx*gridMeters,az=originZ+(double)sz*gridMeters;
                double bx=originX+(double)(r%nx)*gridMeters,bz=originZ+(double)(r/nx)*gridMeters;
                double ux=bx-ax,uz=bz-az,len=ux*ux+uz*uz;
                if(len==0)continue;
                double t=Math.max(0,Math.min(1,((x-ax)*ux+(z-az)*uz)/len));
                double distance=Math.hypot(x-(ax+ux*t),z-(az+uz*t));
                double width=Math.max(2,Math.min(15,1.5+2.0*Math.sqrt(dischargeM3PerYear[i]/1_000_000.0)));
                if(distance<=width)return true;
            }
            return false;
        }
        public double riverProximityAt(double x,double z) {
            double gx=(x-originX)/(double)gridMeters,gz=(z-originZ)/(double)gridMeters;
            if(gx<0||gz<0||gx>nx-1||gz>nz-1) throw new IllegalArgumentException("Outside macro-region");
            int ix=Math.min(nx-2,(int)Math.floor(gx)),iz=Math.min(nz-2,(int)Math.floor(gz));
            double fx=gx-ix,fz=gz-iz;
            int i=at(ix,iz);
            return ((river[i]?1:0)*(1-fx)+(river[i+1]?1:0)*fx)*(1-fz)+
                   ((river[i+nx]?1:0)*(1-fx)+(river[i+nx+1]?1:0)*fx)*fz;
        }
        public boolean isRiverAt(double x,double z) {
            int ix=Math.min(nx-1,Math.max(0,(int)Math.round((x-originX)/(double)gridMeters)));
            int iz=Math.min(nz-1,Math.max(0,(int)Math.round((z-originZ)/(double)gridMeters)));
            return river[at(ix,iz)];
        }
        private double bilinear(double[] a,double x,double z){
            double gx=(x-originX)/(double)gridMeters,gz=(z-originZ)/(double)gridMeters;
            if(gx<0 || gz<0 || gx>nx-1 || gz>nz-1)
                throw new IllegalArgumentException("Outside cached watershed macro-region");
            int ix=Math.min(nx-2,(int)Math.floor(gx)), iz=Math.min(nz-2,(int)Math.floor(gz));
            double fx=gx-ix,fz=gz-iz;
            int i=at(ix,iz);
            return (a[i]*(1-fx)+a[i+1]*fx)*(1-fz) +
                   (a[i+nx]*(1-fx)+a[i+nx+1]*fx)*fz;
        }
    }

    public static Result solve(Inputs in, Parameters p) {
        int w=in.nx,h=in.nz,n=w*h;double area=(double)p.gridMeters()*p.gridMeters();
        double[] bed=in.bedrockY.clone(),spill=new double[n],q=new double[n],sed=new double[n],cut=new double[n];
        int[] receiver=new int[n],outlet=new int[n],order=new int[n];
        boolean[] river=new boolean[n],pond=new boolean[n];
        double[] rawBaseline=in.bedrockY.clone();
        Arrays.fill(receiver,-2);Arrays.fill(outlet,-1);
        double upliftM3=0,localQ=0;
        for(int i=0;i<n;i++){
            if(!Double.isFinite(bed[i])||!Double.isFinite(in.rainMetresPerYear[i]) ||
                !Double.isFinite(in.runoffFraction[i])||!Double.isFinite(in.upliftMetresPerYear[i])||
                !Double.isFinite(in.rockResistance[i]) || in.rainMetresPerYear[i]<0 ||
                in.runoffFraction[i]<0||in.runoffFraction[i]>1||in.rockResistance[i]<=0)
                throw new IllegalArgumentException("Bad input cell="+i);
            double uplift=in.upliftMetresPerYear[i]*p.timeYears();
            bed[i]+=uplift;upliftM3+=uplift*area;
            q[i]=in.rainMetresPerYear[i]*in.runoffFraction[i]*area;
            localQ+=q[i];
        }
        var queue=new PriorityQueue<QueueCell>(Comparator.comparingDouble(QueueCell::h).thenComparingInt(QueueCell::index));
        // Only sub-sea cells CONNECTED TO A SUB-SEA REGION BOUNDARY count as ocean.
        // An isolated depression below sea level is an inland lake, not an ocean outlet.
        boolean[] ocean=new boolean[n];int[] oceanQueue=new int[n];int first=0,last=0;
        for(int z=0;z<h;z++)for(int x=0;x<w;x++){
            if(x!=0&&z!=0&&x!=w-1&&z!=h-1)continue;
            int i=in.at(x,z);
            if(bed[i]<=SEA_Y && !ocean[i]){ocean[i]=true;oceanQueue[last++]=i;}
        }
        while(first<last){
            int i=oceanQueue[first++],x=i%w,z=i/w;
            for(var d:D8){
                int a=x+d[0],b=z+d[1];if(a<0||b<0||a>=w||b>=h)continue;
                int j=in.at(a,b);
                if(!ocean[j] && bed[j]<=SEA_Y){ocean[j]=true;oceanQueue[last++]=j;}
            }
        }
        int roots=0;
        for(int z=0;z<h;z++)for(int x=0;x<w;x++){
            int i=in.at(x,z);
            if(x!=0&&z!=0&&x!=w-1&&z!=h-1&&!ocean[i])continue;
            receiver[i]=-1;outlet[i]=i;spill[i]=bed[i];queue.add(new QueueCell(i,spill[i]));roots++;
        }
        int k=0,ponds=0;
        while(!queue.isEmpty()){
            var cell=queue.remove();int i=cell.index(); order[k++]=i;
            int x=i%w,z=i/w;
            for(var d:D8){
                int a=x+d[0],b=z+d[1];if(a<0||b<0||a>=w||b>=h)continue;
                int j=in.at(a,b);if(receiver[j]!=-2)continue;
                receiver[j]=i;outlet[j]=outlet[i];
                spill[j]=Math.max(bed[j],Math.nextUp(spill[i]));
                if(spill[j]-bed[j] > .01){pond[j]=true;ponds++;}
                queue.add(new QueueCell(j,spill[j]));
            }
        }
        if(k!=n)throw new IllegalStateException("Unassigned drainage cells");
        for(int t=n-1;t>=0;t--){int i=order[t],r=receiver[i];if(r>=0)q[r]+=q[i];}
        double outQ=0;for(int i=0;i<n;i++)if(receiver[i]==-1)outQ+=q[i];
        // Single connected flow graph across ALL internal 4km tile boundaries.
        int crossings=0,channels=0;
        double eroded=0,deposited=0,exported=0;
        int tileCell=4096/p.gridMeters();
        for(int i=0;i<n;i++){
            int r=receiver[i];if(r<0)continue;
            int x=i%w,z=i/w,rx=r%w,rz=r/w;
            if(tileCell>0 && (x/tileCell!=rx/tileCell || z/tileCell!=rz/tileCell))crossings++;
            if(q[i]<p.riverThresholdM3PerYear() || bed[i]<=SEA_Y + 1 || pond[i])continue;
            double dist=p.gridMeters()*Math.hypot(x-rx,z-rz);
            double slope=Math.max(0,(bed[i]-bed[r])/dist);
            if(slope<=0)continue;
            river[i]=true;channels++;
            double rate=p.erodibilityMetersPerYear() *
                    Math.pow(q[i]/p.referenceDischargeM3PerYear(),p.dischargeExponent()) *
                    Math.pow(slope/p.referenceSlope(),p.slopeExponent()) / in.rockResistance[i];
            double safeRelief=Math.min(p.maxCutMetres(),(bed[i]-bed[r])*.3);
            double depth=Math.min(safeRelief,rate*p.timeYears());
            if(depth<=0)continue;
            cut[i]=depth;bed[i]-=depth;sed[i]+=depth*area;eroded+=depth*area;
        }
        // Sediment volume accounting: erosion = local deposition + exported by outlets.
        for(int t=n-1;t>=0;t--){
            int i=order[t],r=receiver[i];
            double amount=sed[i];if(amount<=0)continue;
            if(r>=0){
                double dist=p.gridMeters()*Math.hypot((i%w)-(r%w),(i/w)-(r/w));
                double slope=Math.max(0,(bed[i]-bed[r])/dist);
                double depositedFraction=(1-p.capacityFraction()) * Math.max(0,1-Math.min(1,slope/.035));
                double deposit=Math.min(amount*depositedFraction, 2.0*area);
                bed[i]+=deposit/area;deposited+=deposit;sed[r]+=amount-deposit;
            } else exported+=amount;
        }
        // Invariants validated here as well as independent tests.
        if(Math.abs(localQ-outQ)>Math.max(1e-5,localQ*1e-8) ||
           Math.abs(eroded-(deposited+exported))>Math.max(1e-4,eroded*1e-8))
            throw new IllegalStateException("Hydrology or sediment budget failed");
        for(int i=0;i<n;i++)if(!Double.isFinite(bed[i]))throw new IllegalStateException("Bad output cell");
        return new Result(in,p,bed,spill,q,cut,receiver,outlet,river,pond,
            ocean,rawBaseline,new Ledger(localQ,outQ,eroded,deposited,exported,upliftM3,0,crossings,channels,ponds,roots));
    }
}

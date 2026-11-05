package org.realscale.geo;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Deterministic, dependency-free 1-block=1-metre terrain prototype.
 * All heights, world coordinates and river discharges are in SI units.
 * Tiled catchments drain to each tile's halo border; this prototype does
 * NOT provide globally connected rivers, biomes or Minecraft chunk generation.
 *
 * No blocks, registries, dimensions, biomes or content are introduced here.
 */
public final class GeoTerrainEngine {
    public static final int MIN_Y = -64, TOP_Y = 2031, SEA_Y = 63;
    public static final int CELL_METRES = 32, TILE_CELLS = 128, HALO = 16;
    public static final int SIDE = TILE_CELLS + 2 * HALO;
    public static final int TILE_METRES = TILE_CELLS * CELL_METRES;
    private static final int[][] NEIGHBORS = {
        {-1,-1}, {0,-1}, {1,-1}, {-1,0}, {1,0}, {-1,1}, {0,1}, {1,1}
    };
    private static final double MAX_FLOOR = -28, MAX_PEAK = 1940;
    private final long seed;
    private final int maxCachedTiles;
    private int generatedTiles = 0;
    private final Map<Long, Tile> cache;

    public GeoTerrainEngine(long seed) { this(seed, 12); }
    public GeoTerrainEngine(long seed, int cacheSize) {
        if (cacheSize < 4) throw new IllegalArgumentException("Cache must allow 4 adjacent tiles");
        this.seed = seed;
        this.maxCachedTiles = cacheSize;
        this.cache = new LinkedHashMap<>(16, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long,Tile> e) {
                return size() > maxCachedTiles;
            }
        };
    }

    public synchronized int generatedTiles() { return generatedTiles; }
    public synchronized void clearCache() { cache.clear(); }

    /** Sample continuous terrain height in metres; coordinates in 1m blocks. */
    public synchronized double heightAt(double worldX, double worldZ) {
        if (!Double.isFinite(worldX) || !Double.isFinite(worldZ))
            throw new IllegalArgumentException("Coordinates must be finite");
        double foundation = rawHeight(worldX, worldZ);
        return clamp(foundation + blendedCorrection(worldX, worldZ), MAX_FLOOR, MAX_PEAK);
    }

    /** Inclusive solid block top, respecting world's legal Y bounds. */
    public int blockHeightAt(int x, int z) {
        return (int)Math.floor(heightAt(x + .5, z + .5));
    }

    /** Measured precipitation-weighted upstream contributing area (km^2). */
    public synchronized double contributingAreaKm2(double x, double z) {
        int tx=(int)Math.floor(x/TILE_METRES), tz=(int)Math.floor(z/TILE_METRES);
        return tile(tx,tz).areaAt(x,z)/1_000_000.0;
    }

    /** Regression / diagnostic summary of the catchment graph for a tile. */
    public synchronized TileDiagnostics diagnostics(int tileX, int tileZ) {
        return tile(tileX,tileZ).diagnostics();
    }

    public record TileDiagnostics(int samples, int cycleCount, int missingOutlets,
                                  int riverCells, double minHeight, double maxHeight,
                                  double totalErosionCubicMetres, double totalDepositionCubicMetres,
                                  double sedimentExportCubicMetres, double rainInputSquareMeters,
                                  double catchmentOutputSquareMeters) { }

    private double blendedCorrection(double x, double z) {
        int tx=(int)Math.floor(x / TILE_METRES), tz=(int)Math.floor(z / TILE_METRES);
        Blend bx=axisBlend(x, tx), bz=axisBlend(z, tz);
        double a=tile(tx,tz).correctionAt(x,z);
        double b=bx.other==tx ? a : tile(bx.other,tz).correctionAt(x,z);
        double c=bz.other==tz ? a : tile(tx,bz.other).correctionAt(x,z);
        double d=(bx.other==tx && bz.other==tz) ? a
             : (bx.other==tx ? c : bz.other==tz ? b : tile(bx.other,bz.other).correctionAt(x,z));
        return lerp(lerp(a,b,bx.weightOther),lerp(c,d,bx.weightOther),bz.weightOther);
    }

    private record Blend(int other, double weightOther) {}
    private static Blend axisBlend(double x,int tileNum) {
        double frac=x - (double)tileNum*TILE_METRES;
        final double margin=CELL_METRES*4;
        if(frac<margin) return new Blend(tileNum-1, .5*(1.0-smooth(frac/margin)));
        if(frac>TILE_METRES-margin) return new Blend(tileNum+1,.5*smooth((frac-(TILE_METRES-margin))/margin));
        return new Blend(tileNum,0);
    }

    private Tile tile(int tx,int tz) {
        long key=(((long)tx)<<32) ^ (tz & 0xffffffffL);
        Tile t=cache.get(key);
        if(t==null) { t=new Tile(tx,tz);cache.put(key,t);generatedTiles++; }
        return t;
    }

    /** Domain-warped, hierarchical mountain relief, not real plate tectonics. */
    private double rawHeight(double x, double z) {
        double wx=fbm(x/10500,z/10500,77L,3)*470;
        double wz=fbm(x/11000,z/11000,98L,3)*470;
        double xx=x+wx, zz=z+wz;
        double continent=fbm(xx/22000, zz/22000,11L,5);
        // Oceans, coastal plains, then continental interiors: mountain zones
        // are only active along selected broad orogenic belts, not everywhere.
        double continentalLand=smooth((continent+.10)/.38);
        double baseline=SEA_Y+continent*195+continentalLand*12;
        double plateZone=fbm(xx/18500,zz/18500,89L,4);
        double orogeny=smooth((plateZone-.03)/.22);
        double mountainAxis=fbm(xx/6500,zz/6500,33L,4);
        double ridge=1-Math.abs(mountainAxis);
        double mountainMask=continentalLand*orogeny*smooth((ridge-.47)/.40);
        double mountain=1350*mountainMask* (0.65+.35*smooth((fbm(xx/5200,zz/5200,44L,4)+.7)/1.3));
        double subordinate=190*mountainMask*(0.5+0.5*fbm(xx/2100,zz/2100,46L,3));
        double foot=continentalLand*fbm(xx/1600,zz/1600,51L,4)*83;
        double detail=fbm(xx/225,zz/225,56L,3)*13+fbm(xx/43,zz/43,60L,2)*2.2;
        return clamp(baseline + mountain + subordinate + foot + detail,MAX_FLOOR,MAX_PEAK);
    }

    private double fbm(double x,double z,long channel,int octaves) {
        double sum=0,amp=1,denom=0;
        for(int i=0;i<octaves;i++) {
            sum+=amp*gradientNoise(x,z,channel+i*191);
            denom+=amp;amp*=.48;x*=2.07;z*=2.07;
        }
        return sum/denom;
    }
    private double gradientNoise(double x,double z,long channel) {
        // Avoid the all-zero gradient-noise origin across distinct seeds.
        long salt=mix(seed ^ mix(channel*0x9e3779b97f4a7c15L));
        x+=((salt&0xfffffL)/1048576.0)*691.0;
        z+=(((salt>>>20)&0xfffffL)/1048576.0)*691.0;
        int ix=(int)Math.floor(x), iz=(int)Math.floor(z);
        double u=x-ix,v=z-iz;
        double a=dot(ix,iz,u,v,channel);
        double b=dot(ix+1,iz,u-1,v,channel);
        double c=dot(ix,iz+1,u,v-1,channel);
        double d=dot(ix+1,iz+1,u-1,v-1,channel);
        return lerp(lerp(a,b,smoother(u)),lerp(c,d,smoother(u)),smoother(v))*1.35;
    }
    private double dot(int ix,int iz,double x,double z,long channel) {
        long h=mix(seed ^ mix(((long)ix)*0x9e3779b97f4a7c15L)
                     ^ mix(((long)iz)*0xc2b2ae3d27d4eb4fL) ^ mix(channel));
        // Eight gradient directions, normalized to unit length.
        int g=(int)(h&7);
        double a=(g==0||g==3||g==4||g==7)? .7071067811865476:1;
        double b=(g==1||g==2||g==5||g==6)? .7071067811865476:1;
        return switch(g) {
            case 0 -> a*x+a*z;case 1 -> b*x-b*z;
            case 2 -> -b*x+b*z;case 3 -> -a*x-a*z;
            case 4 -> a*x-a*z;case 5 -> b*x+b*z;
            case 6 -> -b*x-b*z;default -> -a*x+a*z;
        };
    }
    private static long mix(long x) {
        x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;
        x=(x^(x>>>27))*0x94d049bb133111ebL;
        return x^(x>>>31);
    }
    private static double smoother(double t) { return t*t*t*(t*(t*6-15)+10); }
    private static double smooth(double t) { t=clamp(t,0,1);return t*t*(3-2*t); }
    private static double clamp(double v,double lo,double hi) { return Math.max(lo,Math.min(hi,v)); }
    private static double lerp(double a,double b,double t){return a+(b-a)*t;}

    private final class Tile {
        private final int tx,tz;
        private final double[] raw, h, rain, fill, area, correction;
        private final int[] parent;
        private final int[] floodOrder;
        private final int[][] gridDirs=NEIGHBORS;
        private int riverCells;
        private double totalErosion, totalDeposition, sedimentExport;
        Tile(int tx,int tz) {
            this.tx=tx;this.tz=tz;
            int n=SIDE*SIDE;
            raw=new double[n];h=new double[n];rain=new double[n];fill=new double[n];area=new double[n];
            parent=new int[n];floodOrder=new int[n];correction=new double[n];
            Arrays.fill(parent,-1);
            for(int z=0;z<SIDE;z++)for(int x=0;x<SIDE;x++) {
                int i=z*SIDE+x;
                double worldX=(double)(tx*TILE_CELLS+x-HALO)*CELL_METRES;
                double worldZ=(double)(tz*TILE_CELLS+z-HALO)*CELL_METRES;
                raw[i]=rawHeight(worldX,worldZ);h[i]=raw[i];
                double localRain=0.75+1.15*smooth((fbm(worldX/17000,worldZ/17000,70L,3)+.7)/1.4);
                rain[i]=CELL_METRES*CELL_METRES*localRain;
            }
            thermalErosion(5);
            priorityFlood();
            riverErosion();
            for(int i=0;i<n;i++)correction[i]=h[i]-raw[i];
        }

        /** Thermal creep redistributes elevation locally; pairwise sum is conserved. */
        private void thermalErosion(int iterations) {
            final double threshold=15.0; // coarse-grid max stable gradient (metres / 32m cell)
            for(int it=0;it<iterations;it++) {
                double[] dh=new double[h.length];
                for(int z=1;z<SIDE-1;z++)for(int x=1;x<SIDE-1;x++) {
                    int i=z*SIDE+x;
                    if(x<SIDE-2)transfer(i,i+1,dh,threshold);
                    if(z<SIDE-2)transfer(i,i+SIDE,dh,threshold);
                }
                for(int i=0;i<h.length;i++)h[i]+=dh[i];
            }
        }
        private void transfer(int a,int b,double[] dh,double threshold) {
            double diff=h[a]-h[b];
            if(Math.abs(diff)<=threshold)return;
            double moved=.075*(Math.abs(diff)-threshold)*Math.signum(diff);
            dh[a]-=moved;dh[b]+=moved;
        }
        private void priorityFlood() {
            // Deterministic Priority-Flood: every interior cell has one downstream
            // parent assigned when first visited. The parent graph is acyclic.
            record Cell(int id,double level) {};
            PriorityQueue<Cell> pq=new PriorityQueue<>((a,b)->{
                int v=Double.compare(a.level,b.level);
                return v==0?Integer.compare(a.id,b.id):v;
            });
            boolean[] seen=new boolean[h.length];
            for(int z=0;z<SIDE;z++)for(int x=0;x<SIDE;x++){
                if(x!=0&&z!=0&&x!=SIDE-1&&z!=SIDE-1)continue;
                int i=z*SIDE+x;seen[i]=true;fill[i]=h[i];pq.add(new Cell(i,fill[i]));
            }
            int n=0;
            while(!pq.isEmpty()){
                Cell c=pq.remove();int i=c.id;floodOrder[n++]=i;
                int x=i%SIDE,z=i/SIDE;
                for(int[] dir:gridDirs){
                    int nx=x+dir[0],nz=z+dir[1];
                    if(nx<0||nx>=SIDE||nz<0||nz>=SIDE)continue;
                    int j=nz*SIDE+nx;
                    if(seen[j])continue;
                    seen[j]=true; parent[j]=i;
                    fill[j]=Math.max(h[j],Math.nextUp(fill[i]));
                    pq.add(new Cell(j,fill[j]));
                }
            }
            if(n!=h.length)throw new IllegalStateException("Flood did not visit all cells");
            for(int i=0;i<area.length;i++)area[i]=rain[i];
            for(int k=floodOrder.length-1;k>=0;k--){
                int i=floodOrder[k];
                if(parent[i]>=0)area[parent[i]]+=area[i];
            }
        }
        private void riverErosion(){
            double[] cut=new double[h.length];
            final double threshold=350_000; // weighted contributing area square metres
            for(int z=2;z<SIDE-2;z++)for(int x=2;x<SIDE-2;x++){
                int i=z*SIDE+x;
                if(h[i]<SEA_Y+5 || area[i]<threshold)continue;
                double depth=clamp(3.5+7.4*Math.log(area[i]/threshold),0,44);
                // Localised incision in the river corridor. At 32m resolution,
                // this is a simplified subgrid channel cross-section.
                riverCells++;
                for(int dz=-2;dz<=2;dz++)for(int dx=-2;dx<=2;dx++){
                    int j=(z+dz)*SIDE+x+dx;
                    double dist=Math.hypot(dx,dz);
                    double influence=Math.exp(-.7*dist*dist);
                    cut[j]=Math.max(cut[j],depth*influence);
                }
            }
            // Apply actual incision and conservatively account for sediment.
            // Sediment amounts are 'metres of sediment per coarse grid cell',
            // which can be transported along the previously built D8 tree.
            double[] sediment=new double[h.length];
            for(int i=0;i<h.length;i++) {
                double before=h[i];
                h[i]=clamp(h[i]-cut[i],MAX_FLOOR,MAX_PEAK);
                double eroded=Math.max(0,before-h[i]);
                totalErosion+=eroded;
                sediment[i]=eroded;
            }
            // Upstream first. Deposits are limited in low-gradient coastal
            // reaches; remainder is exported through the tile boundary.
            // This is a simple material budget, NOT a full sediment PDE.
            for(int k=floodOrder.length-1;k>=0;k--) {
                int i=floodOrder[k], p=parent[i];
                double available=sediment[i];
                if(available==0)continue;
                double slope = p<0 ? 0 : Math.abs(fill[i]-fill[p])/CELL_METRES;
                if(h[i] >= SEA_Y-5 && h[i] <= SEA_Y+36 && slope < .22) {
                    double deposit=Math.min(2.5,available*.075);
                    deposit=Math.min(deposit,MAX_PEAK-h[i]);
                    h[i]+=deposit; available-=deposit;
                    totalDeposition+=deposit;
                }
                if(p<0) sedimentExport+=available;
                else sediment[p]+=available;
            }
        }
        private double interpolate(double[] grid,double worldX,double worldZ){
            double gx=worldX/CELL_METRES-(double)tx*TILE_CELLS+HALO;
            double gz=worldZ/CELL_METRES-(double)tz*TILE_CELLS+HALO;
            int x=(int)Math.floor(gx),z=(int)Math.floor(gz);
            if(x<0||x>=SIDE-1||z<0||z>=SIDE-1)throw new IllegalArgumentException("Outside tile halo");
            double a=gx-x,b=gz-z;
            int i=z*SIDE+x;
            return lerp(lerp(grid[i],grid[i+1],a),
                        lerp(grid[i+SIDE],grid[i+SIDE+1],a),b);
        }
        double correctionAt(double x,double z){return interpolate(correction,x,z);}
        double areaAt(double x,double z){return interpolate(area,x,z);}
        TileDiagnostics diagnostics(){
            int cycles=0, missing=0;
            for(int i=0;i<h.length;i++) {
                int p=i,n=0;
                while(parent[p]>=0 && n<=h.length){ p=parent[p];n++; }
                if(n>h.length)cycles++;
                else if(p/SIDE!=0 && p/SIDE!=SIDE-1 && p%SIDE!=0 && p%SIDE!=SIDE-1)missing++;
            }
            double incoming=0,outgoing=0,lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
            for(int i=0;i<h.length;i++){
                incoming+=rain[i];lo=Math.min(lo,h[i]);hi=Math.max(hi,h[i]);
                if(parent[i]<0)outgoing+=area[i];
            }
            return new TileDiagnostics(h.length,cycles,missing,riverCells,lo,hi,
                    totalErosion*CELL_METRES*CELL_METRES,
                    totalDeposition*CELL_METRES*CELL_METRES,
                    sedimentExport*CELL_METRES*CELL_METRES,incoming,outgoing);
        }
    }
}

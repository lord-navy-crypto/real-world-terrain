package org.realscale.geo;

import java.util.Arrays;
import java.util.concurrent.*;

public final class RegionalWatershedTests {
    private static int checks=0;
    private static void check(boolean truth,String msg){checks++;if(!truth)throw new AssertionError(msg);}
    private static void near(double a,double b,String msg){checks++;if(Math.abs(a-b)>Math.max(1e-7,Math.max(Math.abs(a),Math.abs(b))*1e-9))throw new AssertionError(msg+": "+a+" vs "+b);}
    private static RegionalWatershed.Inputs synthetic(int side, int x0,int z0){
        var p=RegionalWatershed.Parameters.demonstrator();
        var input=new RegionalWatershed.Inputs(side,side,x0,z0);
        for(int z=0;z<side;z++)for(int x=0;x<side;x++){
            int i=input.at(x,z);
            input.bedrockY[i]=180+(side-1-x)*1.9+20*Math.sin(z*.046) +
                15*Math.cos(x*.039) + 9*Math.sin((z+x)*.11);
            input.runoffFraction[i]=.52;
            input.rainMetresPerYear[i]=.7+.1*Math.sin(z*.05);
            input.rockResistance[i]=.6+1.3*Math.abs(Math.sin(x*.09));
        }
        return input;
    }
    private static void testSyntheticRouting(){
        var p=RegionalWatershed.Parameters.demonstrator();
        var a=synthetic(128,0,0);var result=RegionalWatershed.solve(a,p);
        check(result.ledger.internalTileBoundaryCrossings()>0,"flow crosses internal 4km tile boundary");
        check(result.ledger.receiverLoops()==0,"no receiver loops");
        check(result.ledger.riverCells()>0,"synthetic precipitation produces channels");
        near(result.ledger.localRunoffM3PerYear(),result.ledger.outletRunoffM3PerYear(),"annual runoff conserved");
        near(result.ledger.erodedM3(),result.ledger.depositedM3()+result.ledger.exportedM3(),"sediment mass conserved");
        for(int i=0;i<result.receiver.length;i++){
            int curr=i,n=0;
            while(result.receiver[curr]>=0&&n<=result.receiver.length){curr=result.receiver[curr];n++;}
            check(n<=result.receiver.length,"no basin loops "+i);
            check(curr==result.outlet[i],"all cells trace to preassigned outlet "+i);
            check(Double.isFinite(result.bedY[i]),"finite bed "+i);
            check(result.dischargeM3PerYear[i]>=0,"nonnegative Q "+i);
        }
        var duplicate=RegionalWatershed.solve(synthetic(128,0,0),p);
        for(int i=0;i<result.bedY.length;i++){
            near(result.bedY[i],duplicate.bedY[i],"seed exact reproducibility "+i);
            near(result.dischargeM3PerYear[i],duplicate.dischargeM3PerYear[i],"flow exact reproducibility "+i);
        }
        double at4095=result.heightAt(4095.5,4094.5),at4096=result.heightAt(4096.5,4094.5);
        check(Math.abs(at4095-at4096)<5,"metre resolution does not jump at 4km internal tile boundary");
        check(result.dischargeAt(4096,4096)>=0,"query across tile border");
        System.out.println("PASS synthetic region, crossings="+result.ledger.internalTileBoundaryCrossings()+", riverCells="+result.ledger.riverCells());
    }
    private static void testNoRainAndLowRelief(){
        var p=RegionalWatershed.Parameters.demonstrator();
        var a=synthetic(64,0,0);
        Arrays.fill(a.rainMetresPerYear,0);
        var r=RegionalWatershed.solve(a,p);
        near(r.ledger.erodedM3(),0,"no water=no stream incision");
        near(r.ledger.outletRunoffM3PerYear(),0,"no rain=no discharge");
        var pond=new RegionalWatershed.Inputs(64,64,0,0);
        Arrays.fill(pond.bedrockY,165);
        pond.bedrockY[pond.at(30,30)]=25;
        var waters=RegionalWatershed.solve(pond,p);
        check(waters.pond[pond.at(30,30)],"endorheic closed pit appears as water-storage candidate");
        check(waters.effectiveSpillY[pond.at(30,30)]>waters.bedY[pond.at(30,30)],"pit storage head determined");
        check(!waters.isOceanAt(30*64,30*64),"isolated inland pit below sea is NOT ocean");
        check(waters.isPondAt(30*64,30*64),"isolated inland pit classified as lake candidate");
        var lakeColumn=VanillaColumnAdapter.column(waters,30*64,30*64);
        check(lakeColumn.pond(),"inland lake column identified");
        check(lakeColumn.waterY()>lakeColumn.solidY(),"inland water reaches calculated spill level");
        check(lakeColumn.blockAt(lakeColumn.solidY()+1).equals("minecraft:water"),"no gap under inland lake water");
        var ocean=new RegionalWatershed.Inputs(64,64,0,0);
        Arrays.fill(ocean.bedrockY,165);
        for(int x=0;x<64;x++)ocean.bedrockY[ocean.at(x,32)]=40;
        var sea=RegionalWatershed.solve(ocean,p);
        check(sea.isOceanAt(20*64,32*64),"boundary-connected below-sea path is genuine ocean");
        check(!sea.isPondAt(20*64,32*64),"ocean never mislabeled inland lake");
        var seaCol=VanillaColumnAdapter.column(sea,20*64,32*64);
        check(seaCol.waterY()==63,"connected ocean rises to sea level 63");
        // Input validation must reject invalid physics rather than silently output NaNs.
        var bad=synthetic(8,0,0);bad.runoffFraction[12]=1.1;
        boolean rejected=false;
        try { RegionalWatershed.solve(bad,p); }catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"invalid runoff coefficient rejected");
        System.out.println("PASS dry-run, closed depression, invalid inputs");
    }
    private static void testSharedColumnAndVanillaPalette(){
        var p=RegionalWatershed.Parameters.demonstrator();
        var in=synthetic(96,-4096,-4096);
        var r=RegionalWatershed.solve(in,p);
        var allowed=java.util.Set.of("minecraft:air","minecraft:water","minecraft:andesite","minecraft:granite",
            "minecraft:stone","minecraft:deepslate","minecraft:dirt","minecraft:grass_block",
            "minecraft:gravel","minecraft:snow_block","minecraft:sand","minecraft:clay");
        for(int i=0;i<500;i++){
            int x=-4096+(i*93%((96-2)*p.gridMeters()));
            int z=-4096+(i*271%((96-2)*p.gridMeters()));
            var c=VanillaColumnAdapter.column(r,x,z);
            check(c.solidY()>=-64&&c.solidY()<=2031,"within 1:1 height bounds");
            check(c.solidY()+1==VanillaColumnAdapter.structureBaseHeight(r,x,z,true),"structure OCEAN_FLOOR grounded on same column");
            check(c.worldSurfaceFirstFreeY()==VanillaColumnAdapter.structureBaseHeight(r,x,z,false),"structure WORLD_SURFACE matches fill");
            check(allowed.contains(c.surface())&&allowed.contains(c.subsurface())&&allowed.contains(c.bedrock()),"only vanilla block identifiers");
            check(allowed.contains(c.blockAt(c.solidY()))&&c.blockAt(c.solidY()).equals(c.surface()),"top material matches column");
            if(c.waterY()>c.solidY()){
                check(c.blockAt(c.solidY()+1).equals("minecraft:water"),"water rests on solid bank/bed");
                check(c.blockAt(c.waterY()+1).equals("minecraft:air"),"no floating water after river surface");
            } else check(c.blockAt(c.solidY()+1).equals("minecraft:air"),"air above dry ground");
        }
        // Verify both 1m detail sampling and structure predictions use one source.
        var detailModel=new GeoTerrainEngine(42,4);
        for(int i=0;i<500;i++){
            int x=-4096+(i*91%((96-2)*p.gridMeters()));
            int z=-4096+(i*71%((96-2)*p.gridMeters()));
            var c=VanillaColumnAdapter.column(r,detailModel,x,z);
            check(c.oceanFloorFirstFreeY()==VanillaColumnAdapter.structureBaseHeight(r,detailModel,x,z,true),
                  "high frequency terrain and structure use shared exact height");
            check(c.worldSurfaceFirstFreeY()==VanillaColumnAdapter.structureBaseHeight(r,detailModel,x,z,false),
                  "high frequency water height and structure agree");
        }
        System.out.println("PASS 1m columns, shared height/structure prediction, vanilla-only material IDs");
    }
    private static void testExistingGeologicalBaseline(){
        var p=RegionalWatershed.Parameters.demonstrator();
        for(long seed:new long[]{42,20261011,785312}){
            long t0=System.nanoTime();
            var a=GeoMacroFactory.build(seed,128,128,-8192,-8192,p);
            var r=RegionalWatershed.solve(a,p);
            check(r.ledger.internalTileBoundaryCrossings()>0,"geology seed"+seed+" crosses 4km tiles");
            near(r.ledger.localRunoffM3PerYear(),r.ledger.outletRunoffM3PerYear(),"geology runoff conserved "+seed);
            near(r.ledger.erodedM3(),r.ledger.depositedM3()+r.ledger.exportedM3(),"geology sediment conserved "+seed);
            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(double v:r.bedY){min=Math.min(min,v);max=Math.max(max,v);check(v>=-64&&v<=2031,"natural 1m height legal "+seed);}
            System.out.printf(java.util.Locale.ROOT,"PASS geological seed=%d height=%.1f..%.1f Qout=%.2e cross=%d elapsed=%.1fms%n",
                seed,min,max,r.ledger.outletRunoffM3PerYear(),r.ledger.internalTileBoundaryCrossings(),(System.nanoTime()-t0)/1e6);
        }
    }
    private static void testRegionCacheOrderAndThreading() {
        var p=RegionalWatershed.Parameters.demonstrator();
        var cache=new GeoSnapshotCache(2);
        var a=cache.load(42,128,128,-8192,-8192,p);
        var b=cache.load(42,128,128,-8192,-8192,p);
        check(a==b,"cache returns exactly same in-memory hydrology region");
        check(cache.hits()==1&&cache.misses()==1,"cache hit/miss accounting");
        var pool=Executors.newFixedThreadPool(4);
        try{
            var futures=new java.util.ArrayList<Future<RegionalWatershed.Result>>();
            for(int k=0;k<16;k++)futures.add(pool.submit(() ->cache.load(42,128,128,-8192,-8192,p)));
            for(var f:futures)check(f.get()==a,"concurrent chunk requests share one snapshot");
        }catch(Exception e){throw new AssertionError("concurrency failure",e);}
        finally{pool.shutdownNow();}
        check(cache.hits()==17,"concurrent accesses do not recompute region");
        cache.load(785312,128,128,-8192,-8192,p);
        cache.load(20261011,128,128,-8192,-8192,p);
        check(cache.size()==2,"bounded cache evicts LRU region");
        var rebuilt=cache.load(42,128,128,-8192,-8192,p);
        check(rebuilt!=a,"evicted old region regenerated");
        for(int i=0;i<a.bedY.length;i++)near(a.bedY[i],rebuilt.bedY[i],"cache eviction does not change terrain");
        System.out.println("PASS region cache and concurrent requests, hits="+cache.hits()+", misses="+cache.misses());
    }
    public static void main(String[] args) {
        long start=System.nanoTime();
        testSyntheticRouting();testNoRainAndLowRelief();
        testSharedColumnAndVanillaPalette();testExistingGeologicalBaseline();
        testRegionCacheOrderAndThreading();
        System.out.printf(java.util.Locale.ROOT,"PASS %d assertions in %.2f seconds%n",checks,(System.nanoTime()-start)*1e-9);
    }
}

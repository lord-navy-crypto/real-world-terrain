package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Chunk-local painter for continuous, rare multi-chunk surface wonder regions.
 * Site identity and footprint are deterministic in world coordinates, not
 * feature origin, and are independent of chunk visitation order.
 *
 * This is geological SURFACE GEOMETRY, not a replacement for the existing
 * DEM/river/cave density functions. Never modifies fluids, water columns,
 * structures or neighboring chunks. RealScale world presets only.
 */
public final class GeoWonderRegionFeature extends Feature<NoneFeatureConfiguration> {
    public enum Kind {
        PLAYA_BASIN, ALLUVIAL_FAN, COLD_DESERT_PAVEMENT,
        HOODOO_AMPHITHEATER, TRAVERTINE_STEPS, MANGROVE_ESTUARY,
        COOLED_LAVA_FIELD, GLACIAL_MORAINE_RING, DELTA_DISTRIBUTARIES,
        KARST_DOLINE_RIM
    }
    private static final int GRID = GeoWonderGeometry.GRID;
    private final Kind kind;

    public GeoWonderRegionFeature(Kind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind=kind;
    }
    private static long mix(long n) {return GeoWonderGeometry.mix(n);}
    private GeoWonderGeometry.Site site(int gx,int gz) {
        return GeoWonderGeometry.surfaceSite(gx,gz,kind.ordinal());
    }
    private static boolean match(String id,String... values) {
        for(String v:values)if(id.equals("minecraft:"+v))return true;
        return false;
    }
    private boolean allowedBiome(String id) {
        return switch(kind) {
            case PLAYA_BASIN -> match(id,"desert","badlands","eroded_badlands");
            case ALLUVIAL_FAN -> match(id,"desert","badlands","savanna","stony_peaks");
            case COLD_DESERT_PAVEMENT -> match(id,"snowy_plains","snowy_slopes",
                    "stony_peaks","windswept_gravelly_hills");
            case HOODOO_AMPHITHEATER -> match(id,"badlands","eroded_badlands",
                    "wooded_badlands","desert");
            case TRAVERTINE_STEPS -> match(id,"stony_peaks","meadow","windswept_hills");
            case MANGROVE_ESTUARY -> match(id,"mangrove_swamp");
            case COOLED_LAVA_FIELD -> match(id,"stony_peaks","windswept_gravelly_hills",
                    "badlands");
            case GLACIAL_MORAINE_RING -> match(id,"snowy_slopes","jagged_peaks",
                    "frozen_peaks");
            case DELTA_DISTRIBUTARIES -> match(id,"swamp","mangrove_swamp","beach");
            case KARST_DOLINE_RIM -> match(id,"stony_peaks","meadow","windswept_hills");
        };
    }
    private boolean naturalFloor(BlockState b) {
        return switch(kind) {
            case PLAYA_BASIN -> b.is(Blocks.SAND) || b.is(Blocks.CLAY)
                    || b.is(Blocks.TERRACOTTA) || b.is(Blocks.RED_SAND);
            case ALLUVIAL_FAN -> b.is(Blocks.GRAVEL) || b.is(Blocks.SAND)
                    || b.is(Blocks.CLAY) || b.is(Blocks.TERRACOTTA);
            case COLD_DESERT_PAVEMENT -> b.is(Blocks.STONE)||b.is(Blocks.GRAVEL)
                    ||b.is(Blocks.SNOW_BLOCK)||b.is(Blocks.COARSE_DIRT);
            case HOODOO_AMPHITHEATER -> b.is(Blocks.TERRACOTTA)
                    ||b.is(Blocks.RED_SANDSTONE)||b.is(Blocks.SANDSTONE)
                    ||b.is(Blocks.ORANGE_TERRACOTTA);
            case TRAVERTINE_STEPS -> b.is(Blocks.STONE)
                    ||b.is(Blocks.ANDESITE)||b.is(Blocks.CALCITE);
            case MANGROVE_ESTUARY -> b.is(Blocks.MUD)||b.is(Blocks.CLAY);
            case COOLED_LAVA_FIELD -> b.is(Blocks.STONE)||b.is(Blocks.BASALT)
                    ||b.is(Blocks.TUFF)||b.is(Blocks.GRAVEL);
            case GLACIAL_MORAINE_RING -> b.is(Blocks.STONE)||b.is(Blocks.GRAVEL)
                    ||b.is(Blocks.SNOW_BLOCK);
            case DELTA_DISTRIBUTARIES -> b.is(Blocks.MUD)||b.is(Blocks.CLAY)
                    ||b.is(Blocks.SAND)||b.is(Blocks.GRAVEL);
            case KARST_DOLINE_RIM -> b.is(Blocks.STONE)||b.is(Blocks.CALCITE);
        };
    }
    private static boolean nearWater(WorldGenLevel w,int x,int y,int z,
                                     int minX,int minZ) {
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            if(Math.abs(dx)+Math.abs(dz)!=1)continue;
            int nx=x+dx,nz=z+dz;
            if(nx<minX||nz<minZ||nx>=minX+16||nz>=minZ+16)continue;
            if(!w.getFluidState(new BlockPos(nx,y-1,nz)).isEmpty()
                    ||!w.getFluidState(new BlockPos(nx,y,nz)).isEmpty())
                return true;
        }
        return false;
    }
    private int heightFor(GeoWonderGeometry.Site s,int x,int z,double u,double v,long point) {
        double r=Math.hypot(u,v);
        switch(kind) {
            case HOODOO_AMPHITHEATER -> {
                // Eroded stratified ridge with surviving pinnacle clusters.
                if(r<0.30||r>0.95 || Math.floorMod(point,15L)!=0)return 0;
                return 2+(int)Math.floorMod(point>>>12,5L);
            }
            case TRAVERTINE_STEPS -> {
                if(r>0.80)return 0;
                return r<0.22?3:r<0.48?2:1;
            }
            case GLACIAL_MORAINE_RING -> {
                return r>0.55&&r<0.77&&Math.floorMod(point,5L)!=0?2:0;
            }
            case KARST_DOLINE_RIM -> {
                // Non-destructive doline RIM, not a claimed excavated sinkhole.
                return r>0.48&&r<0.68&&Math.floorMod(point,4L)!=0?2:0;
            }
            default -> {return 1;}
        }
    }
    private BlockState material(int x,int z,long point,int layer) {
        int n=(int)Math.floorMod(point,10L);
        return switch(kind) {
            case PLAYA_BASIN -> (n<7?Blocks.CALCITE:Blocks.WHITE_TERRACOTTA)
                    .defaultBlockState();
            case ALLUVIAL_FAN -> (n<6?Blocks.GRAVEL:n<8?Blocks.COARSE_DIRT:
                    Blocks.SAND).defaultBlockState();
            case COLD_DESERT_PAVEMENT -> (n<6?Blocks.GRAVEL:Blocks.ANDESITE)
                    .defaultBlockState();
            case HOODOO_AMPHITHEATER -> (layer%3==0?Blocks.RED_SANDSTONE:
                    layer%3==1?Blocks.TERRACOTTA:Blocks.ORANGE_TERRACOTTA)
                    .defaultBlockState();
            case TRAVERTINE_STEPS -> Blocks.CALCITE.defaultBlockState();
            case MANGROVE_ESTUARY -> Blocks.MANGROVE_ROOTS.defaultBlockState();
            case COOLED_LAVA_FIELD -> (n<5?Blocks.BASALT:n<8?Blocks.SMOOTH_BASALT:
                    Blocks.TUFF).defaultBlockState();
            case GLACIAL_MORAINE_RING -> (n<8?Blocks.GRAVEL:Blocks.PACKED_ICE)
                    .defaultBlockState();
            case DELTA_DISTRIBUTARIES -> (n<5?Blocks.CLAY:Blocks.GRAVEL)
                    .defaultBlockState();
            case KARST_DOLINE_RIM -> (n<7?Blocks.CALCITE:Blocks.TUFF)
                    .defaultBlockState();
        };
    }
    private boolean paint(WorldGenLevel w,GeoWonderGeometry.Site site,int minX,int minZ) {
        boolean placed=false;
        for(int x=minX;x<minX+16;x++)for(int z=minZ;z<minZ+16;z++) {
            long dx=(long)x-site.x,dz=(long)z-site.z;
            if(!GeoWonderGeometry.contains(site,x,z))continue;
            long point=mix(site.seed ^ ((long)x*0x27d4eb2dL)
                    ^ ((long)z*0x165667b1L));
            if(Math.floorMod(point,7L)==0)continue;
            double u=(double)dx/site.r,v=(double)dz/site.r;
            // Fan-shaped runoff and braided deposits are oriented
            // consistently across chunk boundaries, not reset per chunk.
            if(kind==Kind.ALLUVIAL_FAN||kind==Kind.DELTA_DISTRIBUTARIES) {
                int sign=((site.seed&1L)==0L)?1:-1;
                double forward=sign*u;
                if(forward<0||Math.abs(v)>0.14+forward*0.78)continue;
                if(kind==Kind.DELTA_DISTRIBUTARIES
                        && Math.abs(v-Math.sin(forward*11.0)*0.09)>0.09
                        && Math.abs(v+Math.sin(forward*8.0)*0.12)>0.08)
                    continue;
            }
            if(kind==Kind.COOLED_LAVA_FIELD) {
                double lobe=Math.sin(u*12+site.seed%11)*0.10;
                if(u*u+(v+lobe)*(v+lobe)>0.80)continue;
            }
            int y=w.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            if(y<=w.getMinBuildHeight()+8||y>=w.getMaxBuildHeight()-9)continue;
            if(kind==Kind.PLAYA_BASIN&&y>135)continue;
            if((kind==Kind.MANGROVE_ESTUARY||kind==Kind.DELTA_DISTRIBUTARIES)&&y>97)
                continue;
            if(kind==Kind.GLACIAL_MORAINE_RING&&y<108)continue;
            BlockPos p=new BlockPos(x,y,z);
            BlockPos below=p.below();
            BlockState base=w.getBlockState(below);
            if(!naturalFloor(base)||!w.getFluidState(below).isEmpty())continue;
            if(!w.getBlockState(p).isAir()||!w.getFluidState(p).isEmpty())continue;
            String biome=w.getBiome(p).unwrapKey()
                    .map(k->k.location().toString()).orElse("");
            if(!allowedBiome(biome))continue;
            if((kind==Kind.MANGROVE_ESTUARY||kind==Kind.DELTA_DISTRIBUTARIES)
                    && !nearWater(w,x,y,z,minX,minZ))continue;
            if(kind==Kind.COLD_DESERT_PAVEMENT
                    && w.getBiome(p).value().getBaseTemperature()>0.35f)continue;
            if(kind==Kind.GLACIAL_MORAINE_RING
                    && w.getBiome(p).value().getBaseTemperature()>0.25f)continue;
            int height=heightFor(site,x,z,u,v,point);
            if(height==0)continue;
            boolean clear=true;
            for(int h=0;h<height;h++) {
                BlockPos at=p.above(h);
                if(!w.getBlockState(at).isAir()||!w.getFluidState(at).isEmpty()) {
                    clear=false;break;
                }
            }
            if(!clear)continue;
            for(int h=0;h<height;h++)
                placed|=w.setBlock(p.above(h),material(x,z,point,h),2);
        }
        return placed;
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if(!TVAWorldGuard.hasExperimentalWonders(context))return false;
        if(!context.level().getLevel().dimension().location().toString()
                .equals("minecraft:overworld"))return false;
        int minX=Math.floorDiv(context.origin().getX(),16)*16;
        int minZ=Math.floorDiv(context.origin().getZ(),16)*16;
        int gx=GeoWonderGeometry.gridAtChunk(minX);
        int gz=GeoWonderGeometry.gridAtChunk(minZ);
        boolean placed=false;
        for(int sx=-1;sx<=1;sx++)for(int sz=-1;sz<=1;sz++) {
            GeoWonderGeometry.Site candidate=site(gx+sx,gz+sz);
            if(candidate==null)continue;
            if(!GeoWonderGeometry.intersects(candidate,minX,minZ))
                continue;
            placed|=paint(context.level(),candidate,minX,minZ);
        }
        return placed;
    }
}

package org.realscale.neoforge;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Geological landmark microforms inspired by real locations, not copies of
 * game scenery. Natural biome/substrate proxies; never claims georeferenced
 * placement. Does not change density, water, structures or the ore-bearing rock.
 */
public final class GeographicLandmarkFeature extends Feature<NoneFeatureConfiguration> {
    public enum Kind {
        WULINGYUAN_SANDSTONE, SOUTH_CHINA_KARST, ASWAN_GRANITE,
        SOCOTRA_LIMESTONE, ICELAND_LAVA_FIELD
    }
    private final Kind kind;
    public GeographicLandmarkFeature(Kind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind=kind;
    }
    private static long mix(long v) {
        v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;
        v=(v^(v>>>27))*0x94d049bb133111ebL;
        return v^(v>>>31);
    }
    private static boolean natural(BlockState state) {
        return state.is(Blocks.STONE)||state.is(Blocks.ANDESITE)
                ||state.is(Blocks.DIORITE)||state.is(Blocks.GRANITE)
                ||state.is(Blocks.TUFF)||state.is(Blocks.CALCITE)
                ||state.is(Blocks.BASALT)||state.is(Blocks.SANDSTONE)
                ||state.is(Blocks.SAND)||state.is(Blocks.RED_SAND)
                ||state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.DIRT)
                ||state.is(Blocks.COARSE_DIRT)||state.is(Blocks.PODZOL)
                ||state.is(Blocks.TERRACOTTA);
    }
    private static boolean inside(ChunkPos chunk,BlockPos pos) {
        return pos.getX()>=chunk.getMinBlockX()+1
                &&pos.getX()<=chunk.getMaxBlockX()-1
                &&pos.getZ()>=chunk.getMinBlockZ()+1
                &&pos.getZ()<=chunk.getMaxBlockZ()-1;
    }
    private boolean allowedBiome(String key) {
        return switch(kind) {
            case WULINGYUAN_SANDSTONE ->
                    key.equals("minecraft:windswept_forest")
                    ||key.equals("minecraft:stony_peaks")
                    ||key.equals("realscale:montane_mist_forest")
                    ||key.equals("minecraft:jungle");
            case SOUTH_CHINA_KARST ->
                    key.equals("minecraft:jungle")
                    ||key.equals("minecraft:sparse_jungle")
                    ||key.equals("minecraft:bamboo_jungle")
                    ||key.equals("realscale:limestone_island_scrub");
            case ASWAN_GRANITE ->
                    key.equals("minecraft:desert")
                    ||key.equals("minecraft:savanna")
                    ||key.equals("minecraft:badlands");
            case SOCOTRA_LIMESTONE ->
                    key.equals("realscale:socotra_dragonblood_woodland")
                    ||key.equals("realscale:socotra_endemic_scrub");
            case ICELAND_LAVA_FIELD ->
                    key.equals("minecraft:stony_peaks")
                    ||key.equals("minecraft:windswept_hills")
                    ||key.equals("minecraft:snowy_slopes")
                    ||key.equals("realscale:geothermal_sulfur_basin");
        };
    }
    private static boolean sourceWaterNearby(WorldGenLevel world,BlockPos at,
                                             ChunkPos chunk) {
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
            BlockPos p=at.offset(dx,-1,dz);
            if(!inside(chunk,p))continue;
            if(world.getBlockState(p).is(Blocks.WATER)
                    &&world.getFluidState(p).isSource())return true;
        }
        return false;
    }
    private BlockState rock(long district,int column,int y,int height) {
        return switch(kind) {
            // Quartz sandstone is represented by sandstone with weathered caps.
            case WULINGYUAN_SANDSTONE ->
                    y==height-1 && (column%3==0)
                    ?Blocks.MOSS_BLOCK.defaultBlockState()
                    :Blocks.SANDSTONE.defaultBlockState();
            case SOUTH_CHINA_KARST ->
                    y==height-1?Blocks.POINTED_DRIPSTONE.defaultBlockState()
                    :Blocks.CALCITE.defaultBlockState();
            case ASWAN_GRANITE ->
                    (column+y)%4==0?Blocks.POLISHED_GRANITE.defaultBlockState()
                    :Blocks.GRANITE.defaultBlockState();
            case SOCOTRA_LIMESTONE ->
                    y==height-1&&column%2==0?Blocks.TUFF.defaultBlockState()
                    :Blocks.CALCITE.defaultBlockState();
            case ICELAND_LAVA_FIELD ->
                    y==height-1&&(district&1L)==0L
                    ?Blocks.MOSS_BLOCK.defaultBlockState()
                    :Blocks.BASALT.defaultBlockState();
        };
    }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if(!TVAWorldGuard.hasRealScaleGenerator(ctx))return false;
        WorldGenLevel world=ctx.level();
        if(!world.getLevel().dimension().equals(Level.OVERWORLD))return false;
        ChunkPos chunk=new ChunkPos(ctx.origin());
        long district=mix(world.getSeed()
                ^(long)Math.floorDiv(chunk.x,6)*0x9e3779b97f4a7c15L
                ^(long)Math.floorDiv(chunk.z,6)*0x632be59bd9b4e019L
                ^(long)kind.ordinal()*0x516de55dL);
        if(Math.floorMod(district,7L)!=0L)return false;
        // Several short columns follow local topography, not a flat pasted slab.
        for(int attempt=0;attempt<3;attempt++) {
            int anchorX=chunk.getMinBlockX()+4+ctx.random().nextInt(8);
            int anchorZ=chunk.getMinBlockZ()+4+ctx.random().nextInt(8);
            Map<BlockPos,BlockState> shape=new LinkedHashMap<>();
            int columns=kind==Kind.ASWAN_GRANITE?3:4;
            boolean eligible=true;
            for(int column=0;column<columns && eligible;column++) {
                int dx=(column%2)*3-1;
                int dz=(column/2)*3-1;
                int x=anchorX+dx,z=anchorZ+dz;
                int ground=world.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z);
                BlockPos foot=new BlockPos(x,ground,z);
                if(!inside(chunk,foot)||ground>=world.getMaxBuildHeight()-12
                        ||ground<=world.getMinBuildHeight()+3) {
                    eligible=false;break;
                }
                String biome=world.getBiome(foot).unwrapKey()
                        .map(key->key.location().toString()).orElse("");
                if(!allowedBiome(biome)||!natural(world.getBlockState(foot.below()))) {
                    eligible=false;break;
                }
                if(kind==Kind.ASWAN_GRANITE
                        && !sourceWaterNearby(world,foot,chunk)) {
                    eligible=false;break;
                }
                int height=switch(kind) {
                    case WULINGYUAN_SANDSTONE -> 4+(int)Math.floorMod(mix(district+column*37L),5L);
                    case SOUTH_CHINA_KARST -> 3+(int)Math.floorMod(mix(district+column*43L),4L);
                    case ASWAN_GRANITE -> 1+(int)Math.floorMod(mix(district+column*29L),3L);
                    case SOCOTRA_LIMESTONE -> 2+(int)Math.floorMod(mix(district+column*31L),4L);
                    case ICELAND_LAVA_FIELD -> 1+(int)Math.floorMod(mix(district+column*17L),4L);
                };
                for(int y=0;y<height;y++) {
                    BlockPos p=foot.above(y);
                    if(!world.getBlockState(p).isAir()
                            ||!world.getFluidState(p).isEmpty()) {
                        eligible=false;break;
                    }
                    shape.put(p,rock(district,column,y,height));
                }
            }
            if(!eligible||shape.isEmpty()||shape.size()>38)continue;
            // Preflight all positions before writing to avoid partial geometry.
            boolean safe=true;
            for(BlockPos p:shape.keySet()) {
                if(!inside(chunk,p)||!world.getBlockState(p).isAir()
                        ||!world.getFluidState(p).isEmpty()) {safe=false;break;}
                if(!shape.containsKey(p.below())
                        &&!natural(world.getBlockState(p.below()))) {
                    safe=false;break;
                }
            }
            if(!safe)continue;
            for(var cell:shape.entrySet())
                world.setBlock(cell.getKey(),cell.getValue(),2);
            return true;
        }
        return false;
    }
}

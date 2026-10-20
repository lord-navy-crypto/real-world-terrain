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
 * Original scenic landforms inspired by exploration-game environmental design.
 * Rainforest terraces, misty seaside crags, basalt uplands and underwater
 * arches use generic geography, not artwork, maps or game asset copies.
 * All features only touch air (or source water for the arch) in one chunk;
 * there are no terrain carvers, springs, fluids, or per-tick effects.
 */
public final class ScenicLandscapeFeature extends Feature<NoneFeatureConfiguration> {
    public enum Kind { RAINFOREST_TERRACE, MISTY_COAST_CRAG, BASALT_UPLAND, UNDERWATER_ARCH }
    private final Kind kind;
    public ScenicLandscapeFeature(Kind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind=kind;
    }
    private static long hash(long x) {
        x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;
        x=(x^(x>>>27))*0x94d049bb133111ebL;
        return x^(x>>>31);
    }
    private static boolean natural(BlockState b) {
        return b.is(Blocks.STONE)||b.is(Blocks.ANDESITE)||b.is(Blocks.TUFF)
                ||b.is(Blocks.CALCITE)||b.is(Blocks.BASALT)||b.is(Blocks.GRAVEL)
                ||b.is(Blocks.SAND)||b.is(Blocks.DIRT)||b.is(Blocks.GRASS_BLOCK)
                ||b.is(Blocks.MOSS_BLOCK)||b.is(Blocks.PODZOL)
                ||b.is(Blocks.TERRACOTTA)||b.is(Blocks.RED_SAND);
    }
    private static boolean owns(ChunkPos chunk,BlockPos p) {
        return p.getX()>=chunk.getMinBlockX()&&p.getX()<=chunk.getMaxBlockX()
                &&p.getZ()>=chunk.getMinBlockZ()&&p.getZ()<=chunk.getMaxBlockZ();
    }
    private boolean commit(WorldGenLevel w,ChunkPos chunk,
                           Map<BlockPos,BlockState> blocks) {
        if(blocks.isEmpty() || blocks.size()>45)return false;
        for(BlockPos p:blocks.keySet()) {
            if(!owns(chunk,p)||p.getY()>=w.getMaxBuildHeight()-1
                    ||p.getY()<w.getMinBuildHeight()+2)return false;
            BlockState existing=w.getBlockState(p);
            if(kind==Kind.UNDERWATER_ARCH) {
                if(!existing.is(Blocks.WATER))return false;
            } else if(!existing.isAir()||!w.getFluidState(p).isEmpty())return false;
        }
        for(var block:blocks.entrySet())w.setBlock(block.getKey(),block.getValue(),2);
        return true;
    }
    private static String biome(WorldGenLevel w,BlockPos p) {
        return w.getBiome(p).unwrapKey()
                .map(key->key.location().toString()).orElse("");
    }
    private static boolean fits(Kind kind,String biome) {
        return switch(kind) {
            case RAINFOREST_TERRACE -> biome.equals("minecraft:jungle")
                    ||biome.equals("minecraft:sparse_jungle")
                    ||biome.equals("minecraft:bamboo_jungle")
                    ||biome.equals("realscale:cool_temperate_rainforest");
            case MISTY_COAST_CRAG -> biome.equals("minecraft:stony_shore")
                    ||biome.equals("minecraft:windswept_forest")
                    ||biome.equals("realscale:montane_mist_forest")
                    ||biome.equals("realscale:coastal_pine_barrens");
            case BASALT_UPLAND -> biome.equals("minecraft:badlands")
                    ||biome.equals("minecraft:eroded_badlands")
                    ||biome.equals("minecraft:stony_peaks")
                    ||biome.equals("realscale:geothermal_sulfur_basin")
                    ||biome.equals("realscale:acidic_geothermal_mudflat");
            case UNDERWATER_ARCH -> biome.equals("minecraft:ocean")
                    ||biome.equals("minecraft:deep_ocean")
                    ||biome.equals("minecraft:cold_ocean")
                    ||biome.equals("minecraft:deep_cold_ocean")
                    ||biome.equals("minecraft:lukewarm_ocean")
                    ||biome.equals("minecraft:deep_lukewarm_ocean");
        };
    }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if(!TVAWorldGuard.hasRealScaleGenerator(ctx))return false;
        WorldGenLevel w=ctx.level();
        if(!w.getLevel().dimension().equals(Level.OVERWORLD))return false;
        ChunkPos chunk=new ChunkPos(ctx.origin());
        long district=hash(w.getSeed()^((long)Math.floorDiv(chunk.x,6)*0x9e3779b97f4a7c15L)
                ^((long)Math.floorDiv(chunk.z,6)*0x632be59bd9b4e019L)
                ^(kind.ordinal()*0x1231efL));
        // Landscape clusters: only a subset of 96-block districts are chosen.
        if(Math.floorMod(district,5)!=0)return false;
        for(int i=0;i<4;i++) {
            int x=chunk.getMinBlockX()+3+ctx.random().nextInt(10);
            int z=chunk.getMinBlockZ()+3+ctx.random().nextInt(10);
            int y=w.getHeight(kind==Kind.UNDERWATER_ARCH
                    ? Heightmap.Types.OCEAN_FLOOR_WG:Heightmap.Types.WORLD_SURFACE_WG,x,z);
            BlockPos root=new BlockPos(x,y,z);
            if(!fits(kind,biome(w,root)))continue;
            if(!natural(w.getBlockState(root.below())))continue;
            if(kind==Kind.UNDERWATER_ARCH && (w.getSeaLevel()-y<7 || y>w.getMaxBuildHeight()-7))
                continue;
            if(kind!=Kind.UNDERWATER_ARCH && !w.getBlockState(root).isAir())
                continue;
            Map<BlockPos,BlockState> blocks=new LinkedHashMap<>();
            if(kind==Kind.RAINFOREST_TERRACE) {
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
                    if(dx*dx+dz*dz>5)continue;
                    int height=(Math.abs(dx)+Math.abs(dz)<=1)?3:1;
                    for(int yy=0;yy<height;yy++)blocks.put(root.offset(dx,yy,dz),
                            yy==height-1?Blocks.MOSS_BLOCK.defaultBlockState()
                            :Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                }
            } else if(kind==Kind.MISTY_COAST_CRAG) {
                for(int dx=-2;dx<=2;dx++)for(int dz=-1;dz<=1;dz++) {
                    int height=dx==0?5:(Math.abs(dx)==1?3:1);
                    if(dz!=0)height=Math.max(0,height-2);
                    for(int yy=0;yy<height;yy++)blocks.put(root.offset(dx,yy,dz),
                            yy==height-1?Blocks.MOSS_BLOCK.defaultBlockState()
                            :Blocks.ANDESITE.defaultBlockState());
                }
            } else if(kind==Kind.BASALT_UPLAND) {
                for(int dx=-2;dx<=2;dx++)for(int dz=-1;dz<=1;dz++) {
                    if(Math.abs(dx)==2&&dz!=0)continue;
                    int height=1+(int)Math.floorMod(hash(district+dx*31L+dz*17L),4L);
                    for(int yy=0;yy<height;yy++)blocks.put(root.offset(dx,yy,dz),
                            yy==height-1?Blocks.SMOOTH_BASALT.defaultBlockState()
                            :Blocks.BASALT.defaultBlockState());
                }
            } else {
                // Three-high bridge with open source-water passage below it.
                // This is a natural rock arch proxy; the opening is never excavated.
                for(int dx=-2;dx<=2;dx++) {
                    if(Math.abs(dx)==2)for(int yy=0;yy<=3;yy++)
                        blocks.put(root.offset(dx,yy,0),Blocks.TUFF.defaultBlockState());
                    else blocks.put(root.offset(dx,3,0),Blocks.CALCITE.defaultBlockState());
                }
            }
            if(commit(w,chunk,blocks))return true;
        }
        return false;
    }
}

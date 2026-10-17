package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Rare deterministic underground tubes following one continuous world-space
 * centerline. Every decorating chunk handles ONLY its own solid blocks.
 * No bedrock, ore, structures, aquifers or water is excavated.
 *
 * A tube is a short karst solution passage or cooled volcanic conduit,
 * not a replacement for the existing large TVA cave-density networks.
 */
public final class CaveWonderNetworkFeature extends Feature<NoneFeatureConfiguration> {
    public enum Kind { LAVA_TUBE, KARST_PASSAGE }
    private final Kind kind;
    private static final int CELL=256;
    private CaveWonderNetworkFeature.Kind type(){return kind;}
    public CaveWonderNetworkFeature(Kind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind=kind;
    }
    private static long mix(long n) {
        n^=n>>>30;n*=0xbf58476d1ce4e5b9L;
        n^=n>>>27;n*=0x94d049bb133111ebL;
        return n^(n>>>31);
    }
    private static boolean isNaturalRock(BlockState s) {
        return s.is(Blocks.STONE)||s.is(Blocks.ANDESITE)||s.is(Blocks.DIORITE)
                ||s.is(Blocks.GRANITE)||s.is(Blocks.TUFF)||s.is(Blocks.BASALT)
                ||s.is(Blocks.CALCITE)||s.is(Blocks.DRIPSTONE_BLOCK);
    }
    private boolean acceptable(BlockState s) {
        return kind==Kind.LAVA_TUBE
                ? (s.is(Blocks.STONE)||s.is(Blocks.BASALT)||s.is(Blocks.TUFF)
                   ||s.is(Blocks.ANDESITE))
                : isNaturalRock(s);
    }
    private static boolean dry(WorldGenLevel w,BlockPos p) {
        if(!w.getFluidState(p).isEmpty())return false;
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)
            for(int dz=-1;dz<=1;dz++)
                if(Math.abs(dx)+Math.abs(dy)+Math.abs(dz)==1
                        && !w.getFluidState(p.offset(dx,dy,dz)).isEmpty())
                    return false;
        return true;
    }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if(!TVAWorldGuard.hasExperimentalWonders(context))return false;
        WorldGenLevel w=context.level();
        if(!w.getLevel().dimension().location().toString().equals("minecraft:overworld"))
            return false;
        int minX=Math.floorDiv(context.origin().getX(),16)*16;
        int minZ=Math.floorDiv(context.origin().getZ(),16)*16;
        int gridX=(int)Math.floorDiv((long)minX+8+CELL/2,CELL);
        int gridZ=(int)Math.floorDiv((long)minZ+8+CELL/2,CELL);
        boolean any=false;
        for(int gx=gridX-1;gx<=gridX+1;gx++)
            for(int gz=gridZ-1;gz<=gridZ+1;gz++) {
                long hash=mix((long)gx*0x9e3779b97f4a7c15L
                        ^ (long)gz*0xc2b2ae3d27d4eb4fL
                        ^ (kind==Kind.LAVA_TUBE?0x9876afL:0x72ca51L));
                if(Math.floorMod(hash,31L)!=0)continue;
                int cx=gx*CELL+(int)Math.floorMod(hash>>>13,65L)-32;
                int cz=gz*CELL+(int)Math.floorMod(hash>>>25,65L)-32;
                int cy=38+(int)Math.floorMod(hash>>>42,47L);
                // Axis-aligned lengths vary by site. Tangent is continuous
                // across chunks; tunnel section never references chunk origin.
                boolean horizontal=(hash&1L)==0L;
                int length=56+(int)Math.floorMod(hash>>>18,32L);
                if(cx-length>minX+16||cx+length<minX
                        ||cz-length>minZ+16||cz+length<minZ)continue;
                for(int x=minX;x<minX+16;x++)for(int z=minZ;z<minZ+16;z++) {
                    int along=horizontal?x-cx:z-cz;
                    int side=horizontal?z-cz:x-cx;
                    if(Math.abs(along)>length)continue;
                    double deviation=Math.sin(along*0.080+((hash>>>8)%15))*1.5;
                    double lateral=Math.abs(side-deviation);
                    if(lateral>2.4)continue;
                    int y=cy+(int)Math.round(Math.sin(along*0.045+1.1)*3.0);
                    if(y<w.getMinBuildHeight()+25
                            ||y+10>=w.getMaxBuildHeight())continue;
                    BlockPos roof=new BlockPos(x,y+9,z);
                    // A thick, solid cover prevents carving into exposed cliffs
                    // or opening an aquifer, even with high relief.
                    if(!acceptable(w.getBlockState(roof)))continue;
                    String biome=w.getBiome(new BlockPos(x,y,z)).unwrapKey()
                            .map(k->k.location().toString()).orElse("");
                    if(kind==Kind.LAVA_TUBE) {
                        if(!biome.equals("minecraft:stony_peaks")
                                && !biome.equals("minecraft:badlands")
                                && !biome.equals("minecraft:windswept_hills"))continue;
                    } else if(!biome.equals("minecraft:stony_peaks")
                            && !biome.equals("minecraft:meadow")
                            && !biome.equals("minecraft:windswept_hills"))continue;
                    // Do not excavate until the full two-high passage column
                    // has been checked. Existing natural caves are allowed.
                    BlockPos foot=new BlockPos(x,y,z);
                    BlockPos head=foot.above();
                    BlockState f=w.getBlockState(foot),h=w.getBlockState(head);
                    if((!f.isAir()&&!acceptable(f))||(!h.isAir()&&!acceptable(h)))
                        continue;
                    if(!dry(w,foot)||!dry(w,head))continue;
                    if(!f.isAir())any|=w.setBlock(foot,Blocks.CAVE_AIR.defaultBlockState(),2);
                    if(!h.isAir())any|=w.setBlock(head,Blocks.CAVE_AIR.defaultBlockState(),2);
                    if(Math.floorMod((long)x*29+z*17,9)==0) {
                        BlockPos low=foot.below();
                        BlockState stone=w.getBlockState(low);
                        if(acceptable(stone)&&dry(w,low))
                            w.setBlock(low,kind==Kind.LAVA_TUBE
                                    ? Blocks.SMOOTH_BASALT.defaultBlockState()
                                    : Blocks.CALCITE.defaultBlockState(),2);
                    }
                }
            }
        return any;
    }
}

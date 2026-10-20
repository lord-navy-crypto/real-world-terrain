package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Small, regionally sparse terrestrial habitat signatures using vanilla blocks.
 * No new liquids, no carving, no underground replacement or chunk-edge writes.
 * Stylized tree analogues are NOT representations of real endemic species.
 */
public final class RareEarthHabitatFeature extends Feature<NoneFeatureConfiguration> {
    public RareEarthHabitatFeature() { super(NoneFeatureConfiguration.CODEC); }

    private static boolean soil(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) ||
                s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL) ||
                s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.MUD) ||
                s.is(Blocks.ROOTED_DIRT);
    }
    private static boolean substrate(BlockState s) {
        return soil(s) || s.is(Blocks.SAND) || s.is(Blocks.RED_SAND) ||
                s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) ||
                s.is(Blocks.STONE) || s.is(Blocks.TUFF) ||
                s.is(Blocks.CALCITE) || s.is(Blocks.TERRACOTTA) ||
                s.is(Blocks.YELLOW_TERRACOTTA) || s.is(Blocks.ORANGE_TERRACOTTA) ||
                s.is(Blocks.LIGHT_GRAY_TERRACOTTA) || s.is(Blocks.WHITE_TERRACOTTA);
    }
    private static String rareId(WorldGenLevel w,BlockPos pos) {
        return w.getBiome(pos).unwrapKey().map(k ->
                k.location().getNamespace().equals("realscale")
                        ? k.location().getPath() : "").orElse("");
    }
    private static boolean allowedAir(WorldGenLevel w,BlockPos pos) {
        return pos.getY()<w.getMaxBuildHeight()-2 &&
                w.getBlockState(pos).isAir() && w.getFluidState(pos).isEmpty();
    }
    private static boolean flora(WorldGenLevel w,BlockPos pos,BlockState plant) {
        if(!allowedAir(w,pos) || !substrate(w.getBlockState(pos.below())))return false;
        if(!plant.canSurvive(w,pos))return false;
        w.setBlock(pos,plant,2);
        return true;
    }
    private static BlockState species(String id,int choice) {
        return switch(id) {
            case "congo_peat_swamp_forest", "sundarbans_tidal_mangrove",
                 "montane_mist_forest" -> switch(choice%4) {
                    case 0 -> Blocks.FERN.defaultBlockState();
                    case 1 -> Blocks.MOSS_CARPET.defaultBlockState();
                    case 2 -> Blocks.AZALEA.defaultBlockState();
                    default -> Blocks.SHORT_GRASS.defaultBlockState();
                 };
            case "permafrost_fen" -> choice%3==0 ?
                    Blocks.MOSS_CARPET.defaultBlockState() :
                    Blocks.SHORT_GRASS.defaultBlockState();
            case "namib_fog_desert", "white_dune_lagoon",
                 "steppe_saline_lake", "limestone_island_scrub",
                 "serpentine_barrens" -> Blocks.DEAD_BUSH.defaultBlockState();
            case "socotra_dragonblood_woodland", "socotra_endemic_scrub" ->
                    choice%2==0 ? Blocks.DEAD_BUSH.defaultBlockState() :
                    Blocks.SHORT_GRASS.defaultBlockState();
            default -> null; // Geothermal mineral zones remain mostly bare.
        };
    }

    /** Compact umbrella and woodland silhouettes, entirely inside this chunk. */
    private static boolean grove(WorldGenLevel w,BlockPos root,ChunkPos chunk,
                                  String biome,int height) {
        BlockState ground=w.getBlockState(root.below());
        if(!soil(ground))return false;
        boolean dragon=biome.equals("socotra_dragonblood_woodland");
        boolean shrub=biome.equals("socotra_endemic_scrub");
        boolean cloud=biome.equals("montane_mist_forest");
        boolean mangrove=biome.equals("sundarbans_tidal_mangrove");
        if(!(dragon||shrub||cloud||mangrove||
                biome.equals("congo_peat_swamp_forest")))return false;
        BlockState log=(cloud ? Blocks.SPRUCE_LOG :
                mangrove ? Blocks.MANGROVE_LOG :
                shrub||dragon ? Blocks.ACACIA_LOG : Blocks.OAK_LOG).defaultBlockState();
        BlockState leaf=(cloud ? Blocks.SPRUCE_LEAVES :
                mangrove ? Blocks.MANGROVE_LEAVES :
                shrub ? Blocks.FLOWERING_AZALEA_LEAVES :
                dragon ? Blocks.AZALEA_LEAVES : Blocks.OAK_LEAVES)
                .defaultBlockState().setValue(LeavesBlock.PERSISTENT,true);
        int radius=dragon?3:shrub?1:2;
        if(root.getX()-radius<chunk.getMinBlockX() ||
                root.getX()+radius>chunk.getMaxBlockX() ||
                root.getZ()-radius<chunk.getMinBlockZ() ||
                root.getZ()+radius>chunk.getMaxBlockZ())return false;
        if(root.getY()+height+2>=w.getMaxBuildHeight())return false;
        // Preflight complete shape to avoid partially overwriting structures.
        for(int y=0;y<=height+1;y++) {
            int r=(y>=height-1)?radius:0;
            for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++) {
                if(y>=height-1 && dx*dx+dz*dz>radius*radius+1)continue;
                if(!allowedAir(w,root.offset(dx,y,dz)))return false;
            }
        }
        for(int y=0;y<height;y++)w.setBlock(root.above(y),log,2);
        for(int y=height-1;y<=height+1;y++) {
            int r=y==height+1 ? Math.max(1,radius-1) : radius;
            for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
                if(dx*dx+dz*dz>r*r+1 || (dx==0&&dz==0&&y<height))continue;
                w.setBlock(root.offset(dx,y,dz),leaf,2);
            }
        }
        return true;
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if(!TVAWorldGuard.hasRealScaleGenerator(context))return false;
        WorldGenLevel w=context.level();
        if(!w.getLevel().dimension().equals(Level.OVERWORLD))return false;
        ChunkPos chunk=new ChunkPos(context.origin());
        int created=0;
        boolean treeAttempted=false;
        for(int attempt=0;attempt<32&&created<15;attempt++) {
            int x=chunk.getMinBlockX()+context.random().nextInt(16);
            int z=chunk.getMinBlockZ()+context.random().nextInt(16);
            int y=w.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z);
            if(y<=w.getMinBuildHeight()+2||y>=w.getMaxBuildHeight()-8)continue;
            BlockPos p=new BlockPos(x,y,z);
            if(!allowedAir(w,p)||!substrate(w.getBlockState(p.below())))continue;
            String biome=rareId(w,p);
            if(!treeAttempted && attempt<12 && context.random().nextInt(7)==0) {
                treeAttempted=true;
                int height=biome.equals("socotra_dragonblood_woodland")?5:
                        biome.equals("socotra_endemic_scrub")?3:4;
                if(grove(w,p,chunk,biome,height)) {created+=8;continue;}
            }
            BlockState plant=species(biome,context.random().nextInt(8));
            if(plant!=null && context.random().nextInt(3)==0 &&
                    flora(w,p,plant))created++;
        }
        return created>0;
    }
}

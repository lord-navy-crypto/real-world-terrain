package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Benthic life in the SINGLE USGS+global-geology+marine world.
 *
 * Source-biome temperature, water-column depth and natural substrate choose
 * what can grow; world-seeded regional fields make coherent ecological groves.
 * Use only existing vanilla blocks as *visual* coral/sponge/kelp analogues.
 * Does not add real marine animals, deep-sea biome registries or custom taxa.
 *
 * Biome modifiers attach one placed-feature key across vanilla oceans, but
 * strict generator-settings ID gating prevents any impact on vanilla,
 * Sacred, historic Measured or third-party worlds.
 */
public final class MarineEcologyFeature extends Feature<NoneFeatureConfiguration> {
    private static final ResourceLocation ENABLED=
            ResourceLocation.fromNamespaceAndPath("realscale","usgs_global_hybrid_marine");

    public MarineEcologyFeature(){super(NoneFeatureConfiguration.CODEC);}

    private static boolean hard(BlockState s){
        return s.is(Blocks.STONE)||s.is(Blocks.DEEPSLATE)||s.is(Blocks.TUFF)
            ||s.is(Blocks.ANDESITE)||s.is(Blocks.DIORITE)||s.is(Blocks.GRANITE)
            ||s.is(Blocks.BASALT)||s.is(Blocks.CALCITE)||s.is(Blocks.TERRACOTTA);
    }
    private static boolean natural(BlockState s){
        return hard(s)||s.is(Blocks.SAND)||s.is(Blocks.GRAVEL)
                ||s.is(Blocks.CLAY)||s.is(Blocks.DIRT)||s.is(Blocks.MUD);
    }
    private static BlockState coral(RandomSource r,boolean fan){
        return switch(r.nextInt(5)){
            case 0 -> fan ? Blocks.TUBE_CORAL_FAN.defaultBlockState()
                         : Blocks.TUBE_CORAL.defaultBlockState();
            case 1 -> fan ? Blocks.BRAIN_CORAL_FAN.defaultBlockState()
                         : Blocks.BRAIN_CORAL.defaultBlockState();
            case 2 -> fan ? Blocks.BUBBLE_CORAL_FAN.defaultBlockState()
                         : Blocks.BUBBLE_CORAL.defaultBlockState();
            case 3 -> fan ? Blocks.FIRE_CORAL_FAN.defaultBlockState()
                         : Blocks.FIRE_CORAL.defaultBlockState();
            default -> fan ? Blocks.HORN_CORAL_FAN.defaultBlockState()
                           : Blocks.HORN_CORAL.defaultBlockState();
        };
    }
    // Native decorative proxy: waterlogged pickles light up sunny reef pockets.
    private static BlockState pickle(RandomSource r){
        return Blocks.SEA_PICKLE.defaultBlockState()
                .setValue(SeaPickleBlock.PICKLES,1+r.nextInt(4));
    }

    // Color/rock variation acts only on the natural seafloor immediately below
    // a water voxel. Never touch structural blocks, reef features or tile entities.
    private static void seabedAccent(WorldGenLevel w,BlockPos floor,
                                     MarineEcologyZonationMath.Habitat h,
                                     RandomSource r){
        BlockState existing=w.getBlockState(floor);
        if(!natural(existing)||r.nextInt(7)!=0)return;
        BlockState accent=switch(h){
            case TROPICAL_REEF -> r.nextBoolean() ? Blocks.CALCITE.defaultBlockState()
                    : Blocks.GRAVEL.defaultBlockState();
            case TROPICAL_SEAGRASS,TEMPERATE_SEAGRASS -> r.nextBoolean()
                    ? Blocks.CLAY.defaultBlockState():Blocks.GRAVEL.defaultBlockState();
            case TEMPERATE_KELP,COLD_SPONGE_GARDEN,POLAR_BENTHIC ->
                    r.nextBoolean()?Blocks.TUFF.defaultBlockState():Blocks.ANDESITE.defaultBlockState();
            case COLD_CORAL_GARDEN -> r.nextBoolean()
                    ? Blocks.CALCITE.defaultBlockState():Blocks.TUFF.defaultBlockState();
            case DEEP_SILTY_FLOOR -> r.nextBoolean()
                    ? Blocks.CLAY.defaultBlockState():Blocks.GRAVEL.defaultBlockState();
            case NONE -> existing;
        };
        if(h!=MarineEcologyZonationMath.Habitat.NONE && !existing.equals(accent))
            w.setBlock(floor,accent,2);
    }

    private static boolean kelp(WorldGenLevel w,BlockPos pos,int sea,RandomSource random){
        int height=3+random.nextInt(5);
        if(pos.getY()+height>=sea-1)return false;
        for(int j=0;j<height;j++){
            BlockPos p=pos.above(j);
            if(!w.getBlockState(p).is(Blocks.WATER))return false;
        }
        if(!w.getBlockState(pos.below()).isCollisionShapeFullBlock(w,pos.below()))
            return false;
        // All required spaces checked BEFORE changing any blocks.
        for(int j=0;j<height;j++){
            BlockState plant=j==height-1
                    ? Blocks.KELP.defaultBlockState()
                    : Blocks.KELP_PLANT.defaultBlockState();
            w.setBlock(pos.above(j),plant,2);
        }
        return true;
    }
    private static boolean placeAt(WorldGenLevel w,BlockPos source,
                                   RandomSource random){
        int sea=w.getSeaLevel();
        int x=source.getX(),z=source.getZ();
        int y=w.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z);
        int depth=sea-y;
        if(depth<4||depth>160||y<=w.getMinBuildHeight()+2)return false;
        BlockPos p=new BlockPos(x,y,z);
        BlockState original=w.getBlockState(p);
        BlockState support=w.getBlockState(p.below());
        if(!original.is(Blocks.WATER)||!natural(support))return false;
        String biome=w.getBiome(new BlockPos(x,Math.min(sea-2,y),z))
                .unwrapKey().map(k->k.location().toString()).orElse("");
        if(!MarineEcologyZonationMath.ocean(biome))return false;
        double patch=MarineEcologyZonationMath.patch(x,z,w.getSeed());
        MarineEcologyZonationMath.Habitat h=MarineEcologyZonationMath.classify(
                biome,depth,hard(support),patch);
        // Deep-sea communities are scattered oases, not uniform seafloor carpets.
        BlockState target=switch(h){
            case TROPICAL_REEF -> random.nextInt(5)==0 ? pickle(random)
                    : coral(random,random.nextBoolean());
            case TROPICAL_SEAGRASS,TEMPERATE_SEAGRASS -> random.nextInt(9)==0
                    ? pickle(random) : Blocks.SEAGRASS.defaultBlockState();
            case COLD_SPONGE_GARDEN -> random.nextInt(4)==0
                    ? Blocks.WET_SPONGE.defaultBlockState()
                    : coral(random,true);
            case COLD_CORAL_GARDEN -> random.nextInt(5)==0
                    ? Blocks.WET_SPONGE.defaultBlockState()
                    : coral(random,random.nextBoolean());
            case POLAR_BENTHIC -> random.nextInt(3)==0
                    ? Blocks.WET_SPONGE.defaultBlockState()
                    : coral(random,true);
            case TEMPERATE_KELP -> null;
            case DEEP_SILTY_FLOOR,NONE -> null;
        };
        if(h==MarineEcologyZonationMath.Habitat.TEMPERATE_KELP){
            if(random.nextInt(3)!=0 && kelp(w,p,sea,random)){
                seabedAccent(w,p.below(),h,random);
                return true;
            }
            return false;
        }
        if(target==null){
            // Muddy abyss: sparse gravel/clay variation rather than coral carpets.
            if(h==MarineEcologyZonationMath.Habitat.DEEP_SILTY_FLOOR
                    && random.nextInt(4)==0){
                seabedAccent(w,p.below(),h,random);
                return true;
            }
            return false;
        }
        if(random.nextInt(h==MarineEcologyZonationMath.Habitat.COLD_CORAL_GARDEN
                        ||h==MarineEcologyZonationMath.Habitat.POLAR_BENTHIC
                        ?3:2)!=0)return false;
        if(!target.canSurvive(w,p))return false;
        // No overwrite of waterlogged features, corals, shipwrecks,
        // artifacts, structures or fish; only a native water source voxel.
        if(!w.getBlockState(p).is(Blocks.WATER))return false;
        w.setBlock(p,target,2);
        seabedAccent(w,p.below(),h,random);
        return true;
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context){
        if(!(context.chunkGenerator() instanceof NoiseBasedChunkGenerator noise))
            return false;
        if(!noise.generatorSettings().unwrapKey()
                .map(k->k.location().equals(ENABLED)).orElse(false))
            return false;
        WorldGenLevel w=context.level();
        BlockPos origin=context.origin();
        ChunkPos chunk=new ChunkPos(origin);
        RandomSource random=context.random();
        int placed=0;
        for(int i=0;i<9;i++){
            int x=chunk.getMinBlockX()+random.nextInt(16);
            int z=chunk.getMinBlockZ()+random.nextInt(16);
            if(placeAt(w,new BlockPos(x,origin.getY(),z),random))placed++;
            if(placed>=5)break;
        }
        return placed>0;
    }
}

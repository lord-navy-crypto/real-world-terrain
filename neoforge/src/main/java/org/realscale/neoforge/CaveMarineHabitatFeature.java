package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Small, biome-dependent additions to REAL pre-existing cave pockets and ocean
 * floors. This is not a biome source or a cave carver. It never alters water
 * sources, ore blocks, bedrock, player structures, or original cave topology.
 *
 * Vanilla-only blocks; the extra cave habitats and marine shelves are
 * selected by NeoForge biome modifiers and restricted to RealScale worlds.
 */
public final class CaveMarineHabitatFeature extends Feature<NoneFeatureConfiguration> {
    public enum Kind {
        CAVE_FUNGAL, CAVE_CALCITE, CAVE_VOLCANIC, CAVE_GLACIAL, CAVE_SPRING,
        COLD_WATER_REEF, KELP_MEADOW, WARM_REEF_GARDEN
    }
    private static final int MAX_RADIUS=3;
    private static final int ROOF_DEPTH=24;
    private final Kind kind;

    public CaveMarineHabitatFeature(Kind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind=kind;
    }
    public Kind kind(){return kind;}

    private static boolean sameChunk(BlockPos origin,BlockPos candidate){
        return Math.floorDiv(origin.getX(),16)==Math.floorDiv(candidate.getX(),16)
                && Math.floorDiv(origin.getZ(),16)==Math.floorDiv(candidate.getZ(),16);
    }
    private static boolean naturalRock(BlockState s){
        return s.is(Blocks.STONE)||s.is(Blocks.DEEPSLATE)
                ||s.is(Blocks.TUFF)||s.is(Blocks.ANDESITE)
                ||s.is(Blocks.DIORITE)||s.is(Blocks.GRANITE)
                ||s.is(Blocks.CALCITE)||s.is(Blocks.DRIPSTONE_BLOCK)
                ||s.is(Blocks.BASALT)||s.is(Blocks.SMOOTH_BASALT);
    }
    private static boolean naturalSeabed(BlockState s) {
        return naturalRock(s)||s.is(Blocks.GRAVEL)
                ||s.is(Blocks.CLAY)||s.is(Blocks.SAND);
    }
    private static boolean cavePocket(WorldGenLevel w,BlockPos p) {
        return w.getBlockState(p).isAir()
                && w.getBlockState(p.above()).isAir()
                && w.getFluidState(p).isEmpty()
                && w.getFluidState(p.above()).isEmpty()
                && naturalRock(w.getBlockState(p.below()))
                && w.getFluidState(p.below()).isEmpty();
    }
    private static boolean nearWater(WorldGenLevel w,BlockPos p) {
        for(Direction d:Direction.Plane.HORIZONTAL) {
            BlockPos n=p.relative(d);
            if(w.getFluidState(n).is(FluidTags.WATER)
                    ||w.getFluidState(n.below()).is(FluidTags.WATER))
                return true;
        }
        return false;
    }

    /**
     * Bounded vertical search centered on the configured placement Y. Never
     * scans an entire tall column or requests neighboring chunks. Skip any
     * site too close to the REAL upper world surface.
     */
    private static BlockPos findCave(WorldGenLevel w,BlockPos origin){
        int surface=w.getHeight(Heightmap.Types.WORLD_SURFACE_WG,
                origin.getX(),origin.getZ())-1;
        int min=Math.max(w.getMinBuildHeight()+4,GrandOverworldCaveNetwork.MIN_Y);
        int max=Math.min(w.getMaxBuildHeight()-4,
                Math.min(GrandOverworldCaveNetwork.MAX_Y,surface-ROOF_DEPTH));
        if(min>max)return null;
        int y=HabitatFloorSearch.nearest(origin.getY(),min,max,36,
                v->cavePocket(w,new BlockPos(origin.getX(),v,origin.getZ())));
        return y==HabitatFloorSearch.NOT_FOUND ? null
                : new BlockPos(origin.getX(),y,origin.getZ());
    }
    private boolean decorateCave(WorldGenLevel w,BlockPos origin,
                                 net.minecraft.util.RandomSource random) {
        BlockPos anchor=findCave(w,origin);
        if(anchor==null)return false;
        int changed=0;
        for(int attempt=0;attempt<15 && changed<6;attempt++){
            int dx=random.nextInt(MAX_RADIUS*2+1)-MAX_RADIUS;
            int dz=random.nextInt(MAX_RADIUS*2+1)-MAX_RADIUS;
            if(dx*dx+dz*dz>MAX_RADIUS*MAX_RADIUS)continue;
            BlockPos p=anchor.offset(dx,0,dz);
            if(!sameChunk(origin,p))continue;
            // Do not overwrite bedrock, veins, existing plants or structures.
            if(!cavePocket(w,p))continue;
            BlockPos ground=p.below();
            BlockState original=w.getBlockState(ground);
            BlockState deposit=null,plant=null;
            switch(kind){
                case CAVE_FUNGAL -> {
                    plant=random.nextInt(4)==0
                            ? Blocks.RED_MUSHROOM.defaultBlockState()
                            : Blocks.BROWN_MUSHROOM.defaultBlockState();
                    if(random.nextInt(3)==0)
                        plant=Blocks.MOSS_CARPET.defaultBlockState();
                }
                case CAVE_CALCITE -> {
                    if(original.is(Blocks.STONE)||original.is(Blocks.DEEPSLATE)
                            ||original.is(Blocks.TUFF)){
                        deposit=random.nextInt(3)==0
                                ? Blocks.DRIPSTONE_BLOCK.defaultBlockState()
                                : Blocks.CALCITE.defaultBlockState();
                    }
                }
                case CAVE_VOLCANIC -> {
                    if(original.is(Blocks.STONE)||original.is(Blocks.DEEPSLATE)
                            ||original.is(Blocks.ANDESITE)||original.is(Blocks.TUFF)){
                        deposit=random.nextBoolean()
                                ? Blocks.BASALT.defaultBlockState()
                                : Blocks.SMOOTH_BASALT.defaultBlockState();
                    }
                }
                case CAVE_GLACIAL -> {
                    if(original.is(Blocks.STONE)||original.is(Blocks.ANDESITE)
                            ||original.is(Blocks.DEEPSLATE))
                        deposit=random.nextBoolean()
                                ? Blocks.CALCITE.defaultBlockState()
                                : Blocks.PACKED_ICE.defaultBlockState();
                }
                case CAVE_SPRING -> {
                    if(!nearWater(w,p))continue;
                    plant=Blocks.MOSS_CARPET.defaultBlockState();
                }
                default -> {continue;}
            }
            if(deposit!=null) {
                // Only existing plain natural stone is changed; never the
                // air/void volume or nearby fluid, ores, geodes and structures.
                if(w.setBlock(ground,deposit,2))changed++;
            }else if(plant!=null && plant.canSurvive(w,p)) {
                if(w.setBlock(p,plant,2))changed++;
            }
        }
        return changed>0;
    }

    private boolean decorateMarine(WorldGenLevel w,BlockPos origin,
                                   int seaLevel,net.minecraft.util.RandomSource random){
        int changed=0;
        for(int attempt=0;attempt<18 && changed<7;attempt++){
            int dx=random.nextInt(11)-5, dz=random.nextInt(11)-5;
            BlockPos column=origin.offset(dx,0,dz);
            if(!sameChunk(origin,column))continue;
            int x=column.getX(),z=column.getZ();
            int seabed=w.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z)-1;
            if(seabed<=w.getMinBuildHeight()+3
                    ||seabed>=seaLevel-10)continue;
            BlockPos bottom=new BlockPos(x,seabed,z);
            BlockPos water=bottom.above();
            if(!w.getFluidState(water).is(FluidTags.WATER)
                    ||!w.getFluidState(water.above()).is(FluidTags.WATER)
                    ||!w.getBlockState(water).is(Blocks.WATER))continue;
            BlockState substrate=w.getBlockState(bottom);
            if(!naturalSeabed(substrate)
                    ||!w.getFluidState(bottom).isEmpty())continue;
            if(kind==Kind.COLD_WATER_REEF) {
                // Cold deep reefs represented with inert calcite/tuff hard
                // substrate. Unlike a sponge block, these do not absorb water.
                if(!(substrate.is(Blocks.STONE)||substrate.is(Blocks.DEEPSLATE)
                        ||substrate.is(Blocks.ANDESITE)||substrate.is(Blocks.TUFF)
                        ||substrate.is(Blocks.GRAVEL)))continue;
                BlockState rock=random.nextInt(3)==0
                        ? Blocks.CALCITE.defaultBlockState()
                        : Blocks.TUFF.defaultBlockState();
                if(!substrate.equals(rock)&&w.setBlock(bottom,rock,2))
                    changed++;
            }else{
                // Kelp and sea pickles retain vanilla water FluidState,
                // rather than carving or damming ocean columns.
                BlockState plant=kind==Kind.KELP_MEADOW
                        ? Blocks.KELP.defaultBlockState()
                        : Blocks.SEA_PICKLE.defaultBlockState();
                if(!plant.canSurvive(w,water))continue;
                if(!plant.getFluidState().is(FluidTags.WATER))continue;
                if(w.setBlock(water,plant,2))changed++;
            }
        }
        return changed>0;
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context){
        if(!TVAWorldGuard.hasRealScaleGenerator(context))return false;
        WorldGenLevel w=context.level();
        if(!w.getLevel().dimension().location().toString()
                .equals("minecraft:overworld"))return false;
        BlockPos origin=context.origin();
        boolean cave=kind.ordinal()<=Kind.CAVE_SPRING.ordinal();
        return cave ? decorateCave(w,origin,context.random())
                : decorateMarine(w,origin,context.chunkGenerator().getSeaLevel(),
                        context.random());
    }
}

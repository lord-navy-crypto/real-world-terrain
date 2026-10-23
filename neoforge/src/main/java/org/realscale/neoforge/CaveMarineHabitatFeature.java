package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.Level;
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
        CAVE_GLOW, CAVE_FOSSIL,
        COLD_WATER_REEF, KELP_MEADOW, WARM_REEF_GARDEN
    }
    private static final int MAX_RADIUS=3;
    private static final int ROOF_DEPTH=24;
    private static final int CAVE_KINDS=Kind.CAVE_FOSSIL.ordinal();
    // Optional release diagnostics must not take atomic hot-path updates during
    // normal world generation. Enable only for a targeted server QA session:
    // -Drealscale.habitat.report=true
    private static final boolean REPORT_HABITATS =
            Boolean.getBoolean("realscale.habitat.report");
    private final Kind kind;
    private static final java.util.concurrent.atomic.LongAdder[] ATTEMPTS=newCounters();
    private static final java.util.concurrent.atomic.LongAdder[] PLACEMENTS=newCounters();
    private static java.util.concurrent.atomic.LongAdder[] newCounters(){
        Kind[] kinds=Kind.values();
        java.util.concurrent.atomic.LongAdder[] counts=
                new java.util.concurrent.atomic.LongAdder[kinds.length];
        for(int i=0;i<counts.length;i++)
            counts[i]=new java.util.concurrent.atomic.LongAdder();
        return counts;
    }
    static {
        if(REPORT_HABITATS) Runtime.getRuntime().addShutdownHook(new Thread(()->{
            StringBuilder s=new StringBuilder("REALSCALE_CAVE_MARINE_HABITATS");
            for(Kind k:Kind.values())
                s.append(' ').append(k.name()).append("=")
                 .append(ATTEMPTS[k.ordinal()].sum()).append(":")
                 .append(PLACEMENTS[k.ordinal()].sum());
            System.err.println(s);
        },"realscale-cave-marine-report"));
    }

    public CaveMarineHabitatFeature(Kind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind=kind;
    }
    public Kind kind(){return kind;}

    private static boolean sameChunk(BlockPos origin,int x,int z){
        // Check signed X/Z chunk boundaries BEFORE allocating a candidate.
        // >>4 is exactly Math.floorDiv(value,16), even on negative edges.
        return (origin.getX()>>4)==(x>>4)
                && (origin.getZ()>>4)==(z>>4);
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
        // Keep the exact original short-circuit/read order while constructing
        // each adjacent coordinate at most once. Many probes fail at the
        // first air check, so do not create adjacent positions beforehand.
        if(!w.getBlockState(p).isAir())return false;
        BlockPos above=p.above();
        if(!w.getBlockState(above).isAir())return false;
        if(!w.getFluidState(p).isEmpty()
                ||!w.getFluidState(above).isEmpty())return false;
        BlockPos below=p.below();
        return naturalRock(w.getBlockState(below))
                && w.getFluidState(below).isEmpty();
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
        int min=w.getMinBuildHeight()+4; // Include vanilla caves below the grand-network floor.
        int max=Math.min(w.getMaxBuildHeight()-4,
                Math.min(GrandOverworldCaveNetwork.MAX_Y,surface-ROOF_DEPTH));
        if(min>max)return null;
        // Keep the EXACT original nearest-floor visit order, but reuse one
        // mutable coordinate instead of allocating up to 73 immutable
        // BlockPos objects for each failed candidate in tall cave searches.
        // The predicate is synchronous and no position escapes the probe.
        final int x=origin.getX(),z=origin.getZ();
        final BlockPos.MutableBlockPos probe=new BlockPos.MutableBlockPos();
        int y=HabitatFloorSearch.nearest(origin.getY(),min,max,36,
                v->cavePocket(w,probe.set(x,v,z)));
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
            final int x=anchor.getX()+dx,z=anchor.getZ()+dz;
            // A cross-chunk attempt never reads the world or draws any
            // additional random values: skip it before allocating BlockPos.
            if(!sameChunk(origin,x,z))continue;
            BlockPos p=new BlockPos(x,anchor.getY(),z);
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
                        if(random.nextInt(5)==0){
                            BlockState spike=Blocks.POINTED_DRIPSTONE.defaultBlockState();
                            if(spike.canSurvive(w,p))plant=spike;
                        }
                        if(plant==null)
                            deposit=random.nextInt(3)==0
                                    ?Blocks.DRIPSTONE_BLOCK.defaultBlockState()
                                    :Blocks.CALCITE.defaultBlockState();
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
                case CAVE_GLOW -> {
                    // Downward lichen face attaches to solid natural cave floor.
                    // Only place if the actual surviving vanilla block state
                    // is valid, and never displace a fluid or carved tunnel.
                    plant=Blocks.GLOW_LICHEN.defaultBlockState()
                            .setValue(BlockStateProperties.DOWN,true);
                }
                case CAVE_FOSSIL -> {
                    // Scattered bone-rich rock matrix; not a claimed intact
                    // fossil skeleton or a replacement for vanilla fossils.
                    if(original.is(Blocks.STONE)||original.is(Blocks.DEEPSLATE)
                            ||original.is(Blocks.TUFF)){
                        deposit=random.nextInt(4)==0
                                ?Blocks.BONE_BLOCK.defaultBlockState()
                                :Blocks.CALCITE.defaultBlockState();
                    }
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
            final int x=origin.getX()+dx,z=origin.getZ()+dz;
            if(!sameChunk(origin,x,z))continue;
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
                // Cold-water assemblage: non-absorbing mineral hardground,
                // sparse coral-skeleton fan analogues and seagrass.
                // Dead vanilla fans are visual skeletal forms, not a claim
                // that the reef hosts living tropical reef species.
                if(!(substrate.is(Blocks.STONE)||substrate.is(Blocks.DEEPSLATE)
                        ||substrate.is(Blocks.ANDESITE)||substrate.is(Blocks.TUFF)
                        ||substrate.is(Blocks.GRAVEL)))continue;
                if(random.nextInt(4)==0){
                    BlockState fan=random.nextBoolean()
                            ?Blocks.DEAD_BRAIN_CORAL_FAN.defaultBlockState()
                            :Blocks.DEAD_HORN_CORAL_FAN.defaultBlockState();
                    if(fan.hasProperty(BlockStateProperties.WATERLOGGED))
                        fan=fan.setValue(BlockStateProperties.WATERLOGGED,true);
                    if(fan.getFluidState().is(FluidTags.WATER)
                            &&fan.canSurvive(w,water)&&w.setBlock(water,fan,2)){
                        changed++;continue;
                    }
                }
                if(random.nextInt(5)==0){
                    BlockState grass=Blocks.SEAGRASS.defaultBlockState();
                    if(grass.getFluidState().is(FluidTags.WATER)
                            &&grass.canSurvive(w,water)
                            &&w.setBlock(water,grass,2)){changed++;continue;}
                }
                BlockState rock=random.nextInt(3)==0
                        ? Blocks.CALCITE.defaultBlockState()
                        : Blocks.TUFF.defaultBlockState();
                if(!substrate.equals(rock)&&w.setBlock(bottom,rock,2))
                    changed++;
            }else{
                // All plant placements replace ONLY standing vanilla water
                // with an underwater block whose FluidState remains water.
                // Warm reefs gain several different live CORAL FANS instead
                // of a uniform sea-pickle monoculture. Sea grass and kelp
                // form a mixed temperate shelf meadow. Neither can displace
                // water, reefs, wrecks, fish or source aquifer blocks.
                BlockState plant;
                if(kind==Kind.KELP_MEADOW){
                    plant=random.nextInt(3)==0
                            ? Blocks.SEAGRASS.defaultBlockState()
                            : Blocks.KELP.defaultBlockState();
                }else{
                    int choice=random.nextInt(9);
                    plant=switch(choice){
                        case 0->Blocks.TUBE_CORAL_FAN.defaultBlockState();
                        case 1->Blocks.BRAIN_CORAL_FAN.defaultBlockState();
                        case 2->Blocks.BUBBLE_CORAL_FAN.defaultBlockState();
                        case 3->Blocks.FIRE_CORAL_FAN.defaultBlockState();
                        case 4->Blocks.HORN_CORAL_FAN.defaultBlockState();
                        default->Blocks.SEA_PICKLE.defaultBlockState();
                    };
                    if(plant.hasProperty(BlockStateProperties.WATERLOGGED))
                        plant=plant.setValue(BlockStateProperties.WATERLOGGED,true);
                }
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
        // Avoid ResourceLocation.toString() allocation per placed feature.
        if(!w.getLevel().dimension().equals(Level.OVERWORLD))return false;
        BlockPos origin=context.origin();
        if(REPORT_HABITATS)ATTEMPTS[kind.ordinal()].increment();
        boolean cave=kind.ordinal()<=CAVE_KINDS;
        boolean changed=cave ? decorateCave(w,origin,context.random())
                : decorateMarine(w,origin,context.chunkGenerator().getSeaLevel(),
                        context.random());
        if(REPORT_HABITATS && changed)PLACEMENTS[kind.ordinal()].increment();
        return changed;
    }
}

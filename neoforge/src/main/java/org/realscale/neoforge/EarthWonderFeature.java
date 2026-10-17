package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Natural Wonders manufacturing batch B/C: eight small, real-world-inspired
 * geological structures. The larger landform shape still belongs to the
 * existing density, hydrology and biome layers; do not mistake these accents
 * for large-scale basin, glacier, delta or lava-tube simulations.
 *
 * Only enabled by RealScale biome modifiers, overworld-only, chunk-local,
 * deterministic, and never carves solid natural ground or overwrites water.
 */
public final class EarthWonderFeature extends Feature<NoneFeatureConfiguration> {
    public enum Kind {
        HOODOO_SPIRES, TRAVERTINE_TERRACES, MANGROVE_TIDAL_ROOTS,
        COOLED_LAVA_FIELD, LAVA_TUBE_LINING, GLACIAL_MORAINE,
        DELTA_SEDIMENT, KARST_SINKHOLE_RIM
    }
    private final Kind kind;

    public EarthWonderFeature(Kind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind = kind;
    }

    private static boolean nativeFloor(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.ANDESITE)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND) || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.TERRACOTTA)
                || state.is(Blocks.ORANGE_TERRACOTTA) || state.is(Blocks.CALCITE)
                || state.is(Blocks.CLAY) || state.is(Blocks.MUD)
                || state.is(Blocks.TUFF) || state.is(Blocks.BASALT)
                || state.is(Blocks.SNOW_BLOCK);
    }
    private boolean compatible(BlockState base,float temperature,int y) {
        return switch (kind) {
            case HOODOO_SPIRES -> temperature>=0.65f &&
                    (base.is(Blocks.RED_SANDSTONE) || base.is(Blocks.SANDSTONE)
                    || base.is(Blocks.TERRACOTTA) || base.is(Blocks.ORANGE_TERRACOTTA));
            case TRAVERTINE_TERRACES -> y>=65 && (base.is(Blocks.STONE)
                    || base.is(Blocks.CALCITE) || base.is(Blocks.ANDESITE));
            case MANGROVE_TIDAL_ROOTS -> y<=100 &&
                    (base.is(Blocks.MUD) || base.is(Blocks.CLAY));
            case COOLED_LAVA_FIELD -> base.is(Blocks.BASALT)
                    || base.is(Blocks.TUFF) || base.is(Blocks.STONE)
                    || base.is(Blocks.GRAVEL);
            case LAVA_TUBE_LINING -> base.is(Blocks.STONE)
                    || base.is(Blocks.TUFF) || base.is(Blocks.BASALT);
            case GLACIAL_MORAINE -> y>=110 && temperature<=0.25f
                    && (base.is(Blocks.GRAVEL) || base.is(Blocks.STONE)
                    || base.is(Blocks.SNOW_BLOCK));
            case DELTA_SEDIMENT -> y<=96 &&
                    (base.is(Blocks.MUD) || base.is(Blocks.CLAY)
                    || base.is(Blocks.SAND) || base.is(Blocks.GRAVEL));
            case KARST_SINKHOLE_RIM -> base.is(Blocks.STONE)
                    || base.is(Blocks.CALCITE);
        };
    }
    private static boolean inChunk(int x,int z,int cx,int cz) {
        return Math.floorDiv(x,16)==cx && Math.floorDiv(z,16)==cz;
    }
    private static boolean waterNearby(WorldGenLevel world,BlockPos pos,int cx,int cz) {
        final int[] dx={1,-1,0,0},dz={0,0,1,-1};
        for (int i=0;i<4;i++) {
            int x=pos.getX()+dx[i],z=pos.getZ()+dz[i];
            if (inChunk(x,z,cx,cz) &&
                    (!world.getFluidState(new BlockPos(x,pos.getY(),z)).isEmpty()
                    || !world.getFluidState(new BlockPos(x,pos.getY()-1,z)).isEmpty()))
                return true;
        }
        return false;
    }
    private static boolean emptyColumn(WorldGenLevel world,BlockPos p,int height) {
        for (int y=0;y<height;y++) {
            BlockPos at=p.above(y);
            if (!world.getBlockState(at).isAir()
                    || !world.getFluidState(at).isEmpty())return false;
        }
        return true;
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!TVAWorldGuard.hasRealScaleGenerator(context)) return false;
        WorldGenLevel world=context.level();
        if (!world.getLevel().dimension().location().toString().equals("minecraft:overworld"))
            return false;
        BlockPos center=context.origin();
        int y=center.getY();
        if (y<world.getMinBuildHeight()+8 || y>world.getMaxBuildHeight()-12)
            return false;
        int cx=Math.floorDiv(center.getX(),16),cz=Math.floorDiv(center.getZ(),16);
        float temperature=world.getBiome(center).value().getBaseTemperature();

        if (kind==Kind.LAVA_TUBE_LINING) {
            // Existing cave only: line an air-filled recess, never excavate.
            if (!world.getBlockState(center).isAir() || y>110
                    || !world.getFluidState(center).isEmpty())return false;
            BlockPos floor=center.below();
            if (!compatible(world.getBlockState(floor),temperature,y))return false;
            BlockPos roof=center.above(3);
            if (!nativeFloor(world.getBlockState(roof))
                    || !emptyColumn(world,center,3))return false;
            return world.setBlock(center,Blocks.SMOOTH_BASALT.defaultBlockState(),2);
        }

        BlockState floor=world.getBlockState(center.below());
        if (!centerIsReady(world,center) || !compatible(floor,temperature,y))
            return false;
        if ((kind==Kind.MANGROVE_TIDAL_ROOTS || kind==Kind.DELTA_SEDIMENT)
                && !waterNearby(world,center,cx,cz))
            return false;
        int radius=switch(kind) {
            case HOODOO_SPIRES -> 5;
            case TRAVERTINE_TERRACES -> 4;
            case COOLED_LAVA_FIELD -> 6;
            default -> 3;
        };
        boolean placed=false;
        for (int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
            int distance=dx*dx+dz*dz;
            if (distance>radius*radius)continue;
            int x=center.getX()+dx,z=center.getZ()+dz;
            if (!inChunk(x,z,cx,cz))continue;
            int pattern=Math.floorMod(x*31+z*17,11);
            if (pattern==0 || pattern==7)continue;
            BlockPos p=new BlockPos(x,y,z);
            if (!centerIsReady(world,p))continue;
            BlockState under=world.getBlockState(p.below());
            if (!compatible(under,temperature,y))continue;
            if ((kind==Kind.MANGROVE_TIDAL_ROOTS || kind==Kind.DELTA_SEDIMENT)
                    && !waterNearby(world,p,cx,cz))continue;
            BlockState material=null;
            int height=1;
            switch(kind) {
                case HOODOO_SPIRES -> {
                    // Erosion remnants: sparse clustered, height-varied spires.
                    if ((dx*3+dz*5)%4!=0)continue;
                    height=2+Math.floorMod(x*13+z*19,5);
                    material=(pattern%3==0?Blocks.RED_SANDSTONE:Blocks.SANDSTONE)
                            .defaultBlockState();
                }
                case TRAVERTINE_TERRACES -> {
                    height=distance<=2?3:distance<=8?2:1;
                    material=Blocks.CALCITE.defaultBlockState();
                }
                case MANGROVE_TIDAL_ROOTS -> {
                    // Roots on naturally muddy banks, never fill tidal water.
                    if (pattern>4)continue;
                    material=Blocks.MANGROVE_ROOTS.defaultBlockState();
                }
                case COOLED_LAVA_FIELD -> {
                    material=(pattern<4?Blocks.BASALT:
                              pattern<8?Blocks.SMOOTH_BASALT:Blocks.TUFF)
                              .defaultBlockState();
                }
                case GLACIAL_MORAINE -> {
                    if (Math.abs(dx+dz)>2)continue;
                    material=(pattern<8?Blocks.GRAVEL:Blocks.PACKED_ICE)
                            .defaultBlockState();
                }
                case DELTA_SEDIMENT -> {
                    if (Math.floorMod(dx-2*dz,5)>2)continue;
                    material=(pattern<6?Blocks.GRAVEL:Blocks.CLAY)
                            .defaultBlockState();
                }
                case KARST_SINKHOLE_RIM -> {
                    if (distance<3 || distance>9)continue;
                    material=(pattern<8?Blocks.CALCITE:Blocks.TUFF)
                            .defaultBlockState();
                }
                case LAVA_TUBE_LINING -> { continue; }
            }
            if (material==null || !emptyColumn(world,p,height))continue;
            for (int h=0;h<height;h++)
                placed |= world.setBlock(p.above(h),material,2);
        }
        return placed;
    }
    private static boolean centerIsReady(WorldGenLevel world,BlockPos p) {
        BlockPos under=p.below();
        BlockState floor=world.getBlockState(under);
        return world.getBlockState(p).isAir()
                && world.getFluidState(p).isEmpty()
                && world.getFluidState(under).isEmpty()
                && nativeFloor(floor)
                && floor.isCollisionShapeFullBlock(world,under);
    }
}

package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The six directional surface-material companions to the experimental true
 * cross-realm biome source. Sparse habitat islands, NOT dynamic spreading.
 * Strictly opt-in: works only for cross_realm_* world presets.
 *
 * All changes are within the decorating chunk on originally natural, dry
 * substrate. No fluid, bedrock, structured or neighboring chunks replaced.
 */
public final class CrossRealmTerrainPatchFeature extends Feature<NoneFeatureConfiguration> {
    public enum Kind {
        NETHER_IN_OVERWORLD("minecraft:overworld"),
        END_IN_OVERWORLD("minecraft:overworld"),
        OVERWORLD_IN_NETHER("minecraft:the_nether"),
        END_IN_NETHER("minecraft:the_nether"),
        OVERWORLD_IN_END("minecraft:the_end"),
        NETHER_IN_END("minecraft:the_end");

        final String target;
        Kind(String target) { this.target=target; }
    }
    private final Kind kind;

    public CrossRealmTerrainPatchFeature(Kind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind=kind;
    }
    private boolean sourceMatches(String biome) {
        return switch(kind) {
            case NETHER_IN_OVERWORLD,NETHER_IN_END -> biome.equals("minecraft:nether_wastes")
                    || biome.equals("minecraft:crimson_forest")
                    || biome.equals("minecraft:warped_forest")
                    || biome.equals("minecraft:soul_sand_valley")
                    || biome.equals("minecraft:basalt_deltas");
            case END_IN_OVERWORLD,END_IN_NETHER -> biome.equals("minecraft:end_highlands")
                    || biome.equals("minecraft:end_midlands")
                    || biome.equals("minecraft:the_end")
                    || biome.equals("minecraft:end_barrens");
            case OVERWORLD_IN_NETHER,OVERWORLD_IN_END -> biome.equals("minecraft:forest")
                    || biome.equals("minecraft:plains")
                    || biome.equals("minecraft:meadow")
                    || biome.equals("minecraft:cherry_grove")
                    || biome.equals("minecraft:taiga");
        };
    }
    private boolean allowedFloor(BlockState state) {
        return switch(kind.target) {
            case "minecraft:overworld" -> state.is(Blocks.STONE)
                    || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                    || state.is(Blocks.GRAVEL) || state.is(Blocks.SAND)
                    || state.is(Blocks.TERRACOTTA);
            case "minecraft:the_nether" -> state.is(Blocks.NETHERRACK)
                    || state.is(Blocks.BLACKSTONE) || state.is(Blocks.BASALT)
                    || state.is(Blocks.SOUL_SOIL);
            case "minecraft:the_end" -> state.is(Blocks.END_STONE);
            default -> false;
        };
    }
    private BlockState paint(int x,int z) {
        int v=Math.floorMod(x*17+z*31,10);
        return switch(kind) {
            case NETHER_IN_OVERWORLD,NETHER_IN_END ->
                (v<5?Blocks.NETHERRACK:v<8?Blocks.BLACKSTONE:Blocks.BASALT)
                    .defaultBlockState();
            case END_IN_OVERWORLD,END_IN_NETHER ->
                (v<7?Blocks.END_STONE:v<9?Blocks.END_STONE_BRICKS:Blocks.PURPUR_BLOCK)
                    .defaultBlockState();
            case OVERWORLD_IN_NETHER,OVERWORLD_IN_END ->
                (v<6?Blocks.MOSS_BLOCK:v<9?Blocks.DIRT:Blocks.COBBLESTONE)
                    .defaultBlockState();
        };
    }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!TVAWorldGuard.hasRealScaleGenerator(context))return false;
        if (!(context.chunkGenerator().getBiomeSource() instanceof CrossRealmShowcaseBiomeSource)
                && !(context.chunkGenerator().getBiomeSource() instanceof CrossRealmBiomeSource)
                && !(context.chunkGenerator().getBiomeSource() instanceof SkyOceanBiomeSource sky
                     && sky.containsCrossRealm()))
            return false;
        WorldGenLevel world=context.level();
        if (!world.getLevel().dimension().location().toString().equals(kind.target))return false;
        BlockPos origin=context.origin();
        if (origin.getY()<=world.getMinBuildHeight()+4
                || origin.getY()>=world.getMaxBuildHeight()-4)return false;
        int homeX=Math.floorDiv(origin.getX(),16),homeZ=Math.floorDiv(origin.getZ(),16);
        int radius=4;
        boolean placed=false;
        for (int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
            if (dx*dx+dz*dz>radius*radius)continue;
            int x=origin.getX()+dx,z=origin.getZ()+dz;
            if (Math.floorDiv(x,16)!=homeX || Math.floorDiv(z,16)!=homeZ)continue;
            if (Math.floorMod(x*17+z*13,9)==0)continue;
            // Gentle local slopes; never scan below the sampled cave band.
            for (int dy=2;dy>=-2;dy--) {
                int y=origin.getY()+dy;
                if (y<=world.getMinBuildHeight()+1
                        || y>=world.getMaxBuildHeight()-3)continue;
                BlockPos air=new BlockPos(x,y,z),floor=air.below();
                BlockState surface=world.getBlockState(floor);
                if (!allowedFloor(surface)
                        || !world.getFluidState(floor).isEmpty()
                        || !world.getBlockState(air).isAir()
                        || !world.getFluidState(air).isEmpty())continue;
                String id=world.getBiome(air).unwrapKey()
                        .map(k->k.location().toString()).orElse("");
                if (!sourceMatches(id))continue;
                // Replace ONLY a whitelisted natural substrate block:
                // this is visible 3D terrain transfer, not an F3-only rename.
                if (world.setBlock(floor,paint(x,z),2))placed=true;
                // Rare upright remnants; allow partial light-independent growth
                // in Nether/End, without initiating leaf decay or fluid ticks.
                if ((kind==Kind.OVERWORLD_IN_NETHER || kind==Kind.OVERWORLD_IN_END)
                        && Math.floorMod(x*7+z*23,23)==0
                        && world.getBlockState(air.above()).isAir()
                        && world.getFluidState(air.above()).isEmpty()) {
                    world.setBlock(air,Blocks.OAK_LOG.defaultBlockState(),2);
                    world.setBlock(air.above(),Blocks.OAK_LOG.defaultBlockState(),2);
                }
                break;
            }
        }
        return placed;
    }
}

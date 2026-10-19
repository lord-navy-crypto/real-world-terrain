package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Three tiny, safe decoration palettes using vanilla blocks. Does not carve,
 * reshape or repaint terrain; never overwrites structures/foreign mod blocks,
 * adds fluids, or writes outside its own 16x16 decorating chunk.
 */
public final class EndAccentFeature extends Feature<NoneFeatureConfiguration> {
    public enum Kind {
        CHORUS_GARDENS("end_chorus_gardens"),
        CRYSTAL_BARRENS("end_crystal_barrens"),
        SHATTERED_GLADES("end_shattered_glades");
        final ResourceKey<Biome> biome;
        Kind(String name) {
            biome = ResourceKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath("realscale", name));
        }
    }

    private final Kind kind;
    public EndAccentFeature(Kind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind=kind;
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!TVAWorldGuard.hasRealScaleGenerator(context)) return false;
        WorldGenLevel world=context.level();
        if (!world.getLevel().dimension().equals(Level.END)) return false;

        BlockPos origin=context.origin();
        int chunkX=Math.floorDiv(origin.getX(),16),chunkZ=Math.floorDiv(origin.getZ(),16);
        boolean placed=false;
        for (int dx=-2;dx<=2;dx++) for (int dz=-2;dz<=2;dz++) {
            if(dx*dx+dz*dz>5) continue;
            BlockPos above=origin.offset(dx,0,dz);
            if(Math.floorDiv(above.getX(),16)!=chunkX
                    || Math.floorDiv(above.getZ(),16)!=chunkZ) continue;
            if (above.getY() < world.getMinBuildHeight()+3
                    || above.getY()+3 >= world.getMaxBuildHeight()) continue;
            // Only touch clear air above genuine End Stone, in the expected
            // custom End biome. No overwriting player/mod structures.
            if(!world.getBiome(above).is(kind.biome)
                    || !world.getBlockState(above.below()).is(Blocks.END_STONE)
                    || !world.getFluidState(above).isEmpty()
                    || !world.getBlockState(above).isAir()) continue;

            BlockState marker;
            switch (kind) {
                case CHORUS_GARDENS -> {
                    marker=Blocks.CHORUS_FLOWER.defaultBlockState();
                    if(!marker.canSurvive(world,above)) continue;
                }
                case CRYSTAL_BARRENS -> marker=Blocks.AMETHYST_BLOCK.defaultBlockState();
                case SHATTERED_GLADES -> {
                    marker=((Math.floorMod(above.getX()*11+above.getZ()*17,4)==0)
                        ?Blocks.PURPUR_BLOCK:Blocks.END_STONE_BRICKS).defaultBlockState();
                }
                default -> throw new IllegalStateException("Unknown End accent");
            }
            if(!world.setBlock(above,marker,2)) continue;
            placed=true;
            if(kind==Kind.CRYSTAL_BARRENS && world.getBlockState(above.above()).isAir()){
                BlockState cluster=Blocks.AMETHYST_CLUSTER.defaultBlockState();
                if(cluster.canSurvive(world,above.above()))
                    world.setBlock(above.above(),cluster,2);
            } else if(kind==Kind.SHATTERED_GLADES
                    && Math.floorMod(above.getX()*7+above.getZ()*13,13)==0
                    && world.getBlockState(above.above()).isAir()){
                BlockState rod=Blocks.END_ROD.defaultBlockState();
                if(rod.canSurvive(world,above.above()))
                    world.setBlock(above.above(),rod,2);
            }
        }
        return placed;
    }
}

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
 * Three more recognisable, small End habitats using Minecraft's own blocks:
 *   Chorus Gardens: uneven end-stone/purpur mosaic, living chorus shoots.
 *   Crystal Barrens: calcite islands, amethyst pedestals and upright buds.
 *   Shattered Glades: broken purpur brick terraces and occasional end rods.
 *
 * Purely additive, chunk-local and natural-End-Stone-only. Structures,
 * foreign mod blocks, bedrock, fluids and all other biomes are untouched.
 * The existing Grand End 512-height noise router remains unchanged.
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

    private static long mix(long n) {
        n^=n>>>30; n*=0xbf58476d1ce4e5b9L;
        n^=n>>>27; n*=0x94d049bb133111ebL;
        return n^(n>>>31);
    }

    private static boolean clearColumn(WorldGenLevel world, BlockPos pos, int height) {
        for(int dy=0;dy<height;dy++) {
            BlockPos p=pos.above(dy);
            if(!world.getBlockState(p).isAir()
                    || !world.getFluidState(p).isEmpty()) return false;
        }
        return true;
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!TVAWorldGuard.hasRealScaleGenerator(context)) return false;
        WorldGenLevel world=context.level();
        if (!world.getLevel().dimension().equals(Level.END)) return false;

        BlockPos origin=context.origin();
        int chunkX=Math.floorDiv(origin.getX(),16),chunkZ=Math.floorDiv(origin.getZ(),16);
        boolean placed=false;
        // Limit decorated area to a 9x9 circular patch; two attempts per
        // chunk are inexpensive even in a 512-block-tall Grand End.
        for (int dx=-4;dx<=4;dx++) for (int dz=-4;dz<=4;dz++) {
            if(dx*dx+dz*dz>18)continue;
            BlockPos above=origin.offset(dx,0,dz);
            if(Math.floorDiv(above.getX(),16)!=chunkX
                    || Math.floorDiv(above.getZ(),16)!=chunkZ) continue;
            if (above.getY() < world.getMinBuildHeight()+3
                    || above.getY()+5 >= world.getMaxBuildHeight()) continue;
            // The biome predicate is required even though the placed
            // feature's minecraft:biome modifier also tests its origin.
            if(!world.getBiome(above).is(kind.biome)
                    || !world.getBlockState(above.below()).is(Blocks.END_STONE)
                    || !world.getFluidState(above).isEmpty()
                    || !world.getBlockState(above).isAir()) continue;

            long h=mix(((long)above.getX()<<32)
                  ^ ((long)above.getZ()&0xffffffffL)
                  ^ 0x7e1a30c5L ^ kind.ordinal()*0x9e3779b97f4a7c15L);
            int variant=(int)Math.floorMod(h,16L);
            BlockPos floor=above.below();
            // All height/decor checks complete BEFORE the first write.
            // Every tree/spire is naturally supported by original End Stone.
            if(kind==Kind.CHORUS_GARDENS) {
                if(variant<=3) {
                    if(!clearColumn(world,above,4))continue;
                    // Leave an End Stone root beneath the living chorus plant.
                    world.setBlock(above,Blocks.CHORUS_PLANT.defaultBlockState(),2);
                    world.setBlock(above.above(),Blocks.CHORUS_PLANT.defaultBlockState(),2);
                    if(variant==0) {
                        world.setBlock(above.above(2),Blocks.CHORUS_PLANT.defaultBlockState(),2);
                        world.setBlock(above.above(3),Blocks.CHORUS_FLOWER.defaultBlockState(),2);
                    } else {
                        world.setBlock(above.above(2),Blocks.CHORUS_FLOWER.defaultBlockState(),2);
                    }
                } else if(variant<=9) {
                    // A thin violet path provides a visual biome signature
                    // without introducing water, new soil or custom blocks.
                    world.setBlock(floor,(variant<7?Blocks.PURPUR_BLOCK:
                                      Blocks.END_STONE_BRICKS).defaultBlockState(),2);
                } else continue;
                placed=true;
            } else if(kind==Kind.CRYSTAL_BARRENS) {
                if(variant<=3) {
                    int height=variant==0?3:variant==1?2:1;
                    if(!clearColumn(world,above,height+1))continue;
                    // Amethyst spires are self-supported; cap with a
                    // physically supported upward-pointing crystal bud.
                    for(int dy=0;dy<height;dy++)
                        world.setBlock(above.above(dy),
                            Blocks.AMETHYST_BLOCK.defaultBlockState(),2);
                    BlockPos tip=above.above(height);
                    BlockState crystal=Blocks.AMETHYST_CLUSTER.defaultBlockState();
                    if(crystal.canSurvive(world,tip))
                        world.setBlock(tip,crystal,2);
                } else if(variant<=11) {
                    world.setBlock(floor,(variant<9?Blocks.CALCITE:
                                      Blocks.AMETHYST_BLOCK).defaultBlockState(),2);
                } else continue;
                placed=true;
            } else {
                // Broken masonry and illuminated ruins, never tall towers.
                if(variant<=2) {
                    if(!clearColumn(world,above,3))continue;
                    world.setBlock(above,Blocks.PURPUR_PILLAR.defaultBlockState(),2);
                    if(variant==0) {
                        world.setBlock(above.above(),Blocks.PURPUR_PILLAR.defaultBlockState(),2);
                        BlockState rod=Blocks.END_ROD.defaultBlockState();
                        if(rod.canSurvive(world,above.above(2)))
                            world.setBlock(above.above(2),rod,2);
                    }
                } else if(variant<=11) {
                    world.setBlock(floor,(variant<7?Blocks.END_STONE_BRICKS:
                                      Blocks.PURPUR_BLOCK).defaultBlockState(),2);
                } else continue;
                placed=true;
            }
        }
        return placed;
    }
}

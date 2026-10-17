package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Optional three-realm invasion remnants. An uncommon local scar of another
 * realm's block palette, not a change of biome source, portals or world seed.
 * Safe for vanilla oceans/caves: only air is filled, substrate is untouched,
 * and writes never cross the originating chunk.
 */
public final class DimensionalInvasionFeature extends Feature<NoneFeatureConfiguration> {
    public enum Invader { NETHER, END, OVERWORLD }
    private final Invader invader;

    public DimensionalInvasionFeature(Invader invader) {
        super(NoneFeatureConfiguration.CODEC);
        this.invader = invader;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!TVAWorldGuard.hasCrossRealmInvasion(context)) return false;
        WorldGenLevel level = context.level();
        ResourceLocation realm = level.getLevel().dimension().location();
        String home = realm.getPath();
        if (!"minecraft".equals(realm.getNamespace())) return false;
        if (invader == Invader.NETHER && !"overworld".equals(home) && !"the_end".equals(home))
            return false;
        if (invader == Invader.END && !"overworld".equals(home) && !"the_nether".equals(home))
            return false;
        if (invader == Invader.OVERWORLD && !"the_nether".equals(home) && !"the_end".equals(home))
            return false;
        BlockPos origin = context.origin();
        // The placement heightmap may resolve foliage or air cavities.
        // A natural, solid substrate and 3 clear overhead blocks are mandatory.
        if (origin.getY() < level.getMinBuildHeight()+4
                || origin.getY()+3 >= level.getMaxBuildHeight()) return false;
        BlockState floor = level.getBlockState(origin.below());
        if (!level.getFluidState(origin.below()).isEmpty()
                || !(floor.is(Blocks.STONE) || floor.is(Blocks.GRASS_BLOCK)
                    || floor.is(Blocks.DIRT) || floor.is(Blocks.NETHERRACK)
                    || floor.is(Blocks.END_STONE) || floor.is(Blocks.BASALT)
                    || floor.is(Blocks.BLACKSTONE) || floor.is(Blocks.GRAVEL)))
            return false;
        for (int dy=0;dy<3;dy++)
            if (!level.getBlockState(origin.above(dy)).isAir()
                    || !level.getFluidState(origin.above(dy)).isEmpty())
                return false;
        BlockState core = switch(invader) {
            case NETHER -> Blocks.NETHERRACK.defaultBlockState();
            case END -> Blocks.END_STONE.defaultBlockState();
            case OVERWORLD -> Blocks.MOSS_BLOCK.defaultBlockState();
        };
        BlockState crest = switch(invader) {
            case NETHER -> Blocks.BLACKSTONE.defaultBlockState();
            case END -> Blocks.PURPUR_BLOCK.defaultBlockState();
            case OVERWORLD -> Blocks.OAK_LOG.defaultBlockState();
        };
        // Three-dimensional intrusion footprint, entirely within ONE chunk.
        // Validate everything before writing so a cliff/structure does not
        // leave a partially generated alien ruin.
        int cx=origin.getX()>>4, cz=origin.getZ()>>4;
        for (int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
            BlockPos p=origin.offset(dx,0,dz);
            if ((p.getX()>>4)!=cx || (p.getZ()>>4)!=cz) return false;
            BlockState support=level.getBlockState(p.below());
            if (!(support.is(Blocks.STONE) || support.is(Blocks.GRASS_BLOCK)
                    || support.is(Blocks.DIRT) || support.is(Blocks.NETHERRACK)
                    || support.is(Blocks.END_STONE) || support.is(Blocks.BASALT)
                    || support.is(Blocks.BLACKSTONE) || support.is(Blocks.GRAVEL))
                    || !level.getFluidState(p.below()).isEmpty()
                    || !level.getBlockState(p).isAir()
                    || !level.getFluidState(p).isEmpty()) return false;
        }
        for (int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
            BlockPos p=origin.offset(dx,0,dz);
            level.setBlock(p, (Math.abs(dx)+Math.abs(dz)==2) ? crest : core, 2);
        }
        level.setBlock(origin.above(), core, 2);
        level.setBlock(origin.above(2), crest, 2);
        return true;
    }
}

package org.realscale.neoforge.lotm;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** A tiny cache-bearing ruined platform, added only by an optional biome modifier. */
public final class LotmRelicSiteFeature extends Feature<NoneFeatureConfiguration> {
    public LotmRelicSiteFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!LotmConfig.ENABLED.get() || !LotmConfig.RELIC_SITES.get()) return false;
        if (context.random().nextInt(LotmConfig.RELIC_RARITY.get()) != 0) return false;
        WorldGenLevel level = context.level();
        BlockPos center = context.origin();
        if (!level.getBlockState(center).isAir() || !level.getBlockState(center.below()).isSolidRender(level, center.below())) return false;
        if (!level.getBlockState(center.above()).isAir()) return false;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            BlockPos ground = center.offset(x, -1, z);
            if (!level.getBlockState(ground).isSolidRender(level, ground)) return false;
            if (!level.getBlockState(ground.above()).isAir() && (x != 0 || z != 0)) return false;
        }
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            BlockPos ground = center.offset(x, -1, z);
            level.setBlock(ground, ((x + z) & 1) == 0
                    ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                    : Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), 2);
        }
        level.setBlock(center, LotmContent.RELIC_CACHE.get().defaultBlockState(), 2);
        return true;
    }
}

package org.realscale.neoforge;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
record RealScaleFeatureContext(WorldGenLevel level, ChunkGenerator chunkGenerator,
                               RandomSource random, BlockPos origin) {}

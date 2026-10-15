package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Above-sea-level river and intentionally floodable mountain cascade
 * restore for vanilla River biomes in RealScale worlds.
 *
 * No direct cross-chunk writes, no forced sea-level changes, no use of minecraft:lake
 * (known to be unsafe for 1.21.x water generation).
 *
 * This is a shallow stream, NOT surveyed basin-scale river hydrology.
 */
public final class RiverChannelFeature extends Feature<NoneFeatureConfiguration> {
    public RiverChannelFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    private static boolean natural(BlockState state) {
        return state.is(Blocks.STONE)
            || state.is(Blocks.GRASS_BLOCK)
            || state.is(Blocks.DIRT)
            || state.is(Blocks.COARSE_DIRT)
            || state.is(Blocks.PODZOL)
            || state.is(Blocks.GRAVEL)
            || state.is(Blocks.SAND)
            || state.is(Blocks.RED_SAND)
            || state.is(Blocks.CLAY)
            || state.is(Blocks.MUD)
            || state.is(Blocks.TERRACOTTA)
            || state.is(Blocks.ANDESITE)
            || state.is(Blocks.GRANITE)
            || state.is(Blocks.DIORITE)
            || state.is(Blocks.DEEPSLATE)
            || state.is(Blocks.SNOW_BLOCK);
    }

    /**
     * Surface-heightmaps include thin snow and low vegetation. Treating their
     * blocks as the rock surface made snow-covered highland streams dry.
     * Only one non-structural cover block is removable; logs, leaves and
     * player-made surfaces must not be stripped.
     */
    private static boolean removableCover(BlockState state) {
        return state.is(Blocks.SNOW)
            || state.is(Blocks.SHORT_GRASS)
            || state.is(Blocks.FERN)
            || state.is(Blocks.DEAD_BUSH);
    }

    private static boolean solidBank(BlockState state) {
        return state.blocksMotion() && !state.is(Blocks.LAVA)
            && !state.is(Blocks.WATER);
    }

    /**
     * A high-altitude source is unsafe without banks: water may flow into
     * lower neighboring terrain and spread well beyond the river biome.
     * Avoid carving into uncontained slopes and downhill river steps.
     * Only READ adjacent blocks and keep all writes in the current chunk.
     */
    private static boolean enclosedByBanks(WorldGenLevel level, BlockPos top, int inset, int maxGrade) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = top.relative(side);
            BlockState atWaterSurface = level.getBlockState(neighbor.below(inset));
            BlockState atWaterBase = level.getBlockState(neighbor.below(inset + 1));
            boolean existingRiverWater =
                (atWaterSurface.is(Blocks.WATER) || atWaterSurface.is(Blocks.ICE))
                && atWaterBase.is(Blocks.WATER);
            if (!existingRiverWater
                && (!solidBank(atWaterSurface) || !solidBank(atWaterBase)))
                return false;
            boolean adjacentRiver = level.getBiome(neighbor).is(Biomes.RIVER)
                || level.getBiome(neighbor).is(Biomes.FROZEN_RIVER);
            if (adjacentRiver) {
                int nextTop = level.getHeight(
                    Heightmap.Types.WORLD_SURFACE_WG,
                    neighbor.getX(), neighbor.getZ()) - 1;
                // Riverbeds naturally descend. Requiring EXACTLY equal
                // neighboring surface heights eliminated every river in the
                // mountain-spawn QA (180 river columns, 0 water).
                // A gentle, enclosed drop allows small stream cascades;
                // a cliff-like step still cannot become a source.
                // Non-river banks remain solid at both water levels.
                boolean alreadyFilled = existingRiverWater;
                if (Math.abs(nextTop - top.getY()) > maxGrade && !alreadyFilled)
                    return false;
            }
        }
        return true;
    }



    // Highland reaches deliberately allow LOCAL overbank flooding/cascades.
    // Limited source pairs per placement keep new chunks from becoming
    // endless flat artificial lakes. Vanilla fluid simulation controls any
    // downhill flow once those limited sources are placed.
    private static final int MAX_HIGHLAND_REACHES = 8;
    private static final int MAX_OPEN_BANK_FACES = 3;

    /**
     * Creates a paired riverbed with a common water level on a mountain
     * slope. Unlike the enclosed pool path, a few exterior faces may be
     * open, creating small waterfalls and overbank flow. Both columns
     * must have natural bedrock-like foundations and biome labels.
     * The entire pair is validated BEFORE touching any blocks.
     */
    private static boolean tryHighlandOverflowReach(WorldGenLevel level,
            ChunkPos chunk, BlockPos first, int floor, int maxGrade) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int x = first.getX() + side.getStepX();
            int z = first.getZ() + side.getStepZ();
            if (x < chunk.getMinBlockX() || x > chunk.getMaxBlockX()
                    || z < chunk.getMinBlockZ() || z > chunk.getMaxBlockZ())
                continue;
            int h = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            BlockPos surface = new BlockPos(x, h, z);
            BlockPos second = removableCover(level.getBlockState(surface))
                    ? surface.below() : surface;
            if (!level.getBiome(second).is(Biomes.RIVER)
                    && !level.getBiome(second).is(Biomes.FROZEN_RIVER))
                continue;
            // Accept slope steps for natural cascades, but never excavate
            // a sheer cliff or a deep well just to produce water.
            if (Math.abs(first.getY() - second.getY()) > Math.min(3, maxGrade))
                continue;
            int waterY = Math.min(first.getY(), second.getY()) - 1;
            if (waterY <= floor || waterY > level.getMaxBuildHeight() - 7)
                continue;
            BlockPos[] pair = {first, second};
            boolean valid = true;
            for (BlockPos cell : pair) {
                BlockState cap = level.getBlockState(cell.above());
                if (!cap.isAir() && !removableCover(cap)) {
                    valid = false;
                    break;
                }
                for (int y = cell.getY(); y >= waterY - 2; y--) {
                    if (!natural(level.getBlockState(
                            new BlockPos(cell.getX(), y, cell.getZ())))) {
                        valid = false;
                        break;
                    }
                }
                if (!valid || !natural(level.getBlockState(
                        new BlockPos(cell.getX(), waterY - 3, cell.getZ())))) {
                    valid = false;
                    break;
                }
            }
            if (!valid) continue;

            // Six exterior bank faces surround a two-cell reach. Up to
            // three may open to a downslope, which makes flooding possible
            // without excavating an entirely unsupported water platform.
            int openFaces = 0;
            for (BlockPos cell : pair) {
                for (Direction wall : Direction.Plane.HORIZONTAL) {
                    int nx = cell.getX() + wall.getStepX();
                    int nz = cell.getZ() + wall.getStepZ();
                    if ((nx == first.getX() && nz == first.getZ())
                            || (nx == second.getX() && nz == second.getZ()))
                        continue;
                    BlockPos bank = new BlockPos(nx, waterY, nz);
                    BlockState upper = level.getBlockState(bank);
                    BlockState lower = level.getBlockState(bank.below());
                    boolean existingReach =
                        (upper.is(Blocks.WATER) || upper.is(Blocks.ICE))
                        && lower.is(Blocks.WATER);
                    if (!existingReach && (!solidBank(upper) || !solidBank(lower)))
                        openFaces++;
                }
            }
            if (openFaces > MAX_OPEN_BANK_FACES) continue;

            // Each block modified belongs to this 16x16 generating chunk.
            // The original DEM density and reference heights are untouched.
            for (BlockPos cell : pair) {
                if (removableCover(level.getBlockState(cell.above())))
                    level.setBlock(cell.above(), Blocks.AIR.defaultBlockState(), 2);
                for (int y = cell.getY(); y > waterY; y--)
                    level.setBlock(new BlockPos(cell.getX(), y, cell.getZ()),
                            Blocks.AIR.defaultBlockState(), 2);
                BlockPos water = new BlockPos(cell.getX(), waterY, cell.getZ());
                level.setBlock(water.below(2), Blocks.GRAVEL.defaultBlockState(), 2);
                level.setBlock(water.below(), Blocks.WATER.defaultBlockState(), 2);
                level.setBlock(water,
                    level.getBiome(cell).is(Biomes.FROZEN_RIVER)
                        ? Blocks.ICE.defaultBlockState()
                        : Blocks.WATER.defaultBlockState(), 2);
            }
            return true;
        }
        return false;
    }

    /**
     * Conservative connected streamlet: two adjacent river cells share a
     * water elevation only after both cells and ALL exterior bank faces have
     * been validated. Never writes outside the generating chunk.
     *
     * This complements, but does not replace, basin-wide river hydrology.
     * Rejects uncontained slopes rather than risking a mountainside flood.
     */
    private static boolean tryAdjacentSafeRiverPool(WorldGenLevel level,
            ChunkPos chunk, BlockPos first, int floor, int maxGrade) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int x = first.getX() + side.getStepX();
            int z = first.getZ() + side.getStepZ();
            if (x < chunk.getMinBlockX() || x > chunk.getMaxBlockX()
                    || z < chunk.getMinBlockZ() || z > chunk.getMaxBlockZ())
                continue;
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            BlockPos cover = new BlockPos(x, surfaceY, z);
            BlockPos second = removableCover(level.getBlockState(cover))
                    ? cover.below() : cover;
            if (Math.abs(first.getY() - second.getY()) > Math.min(2, maxGrade))
                continue;
            if (!level.getBiome(second).is(Biomes.RIVER)
                    && !level.getBiome(second).is(Biomes.FROZEN_RIVER))
                continue;
            int waterY = Math.min(first.getY(), second.getY()) - 1;
            if (waterY <= floor || waterY > level.getMaxBuildHeight() - 7)
                continue;
            BlockPos[] cells = {first, second};
            boolean enclosed = true;
            // Two liquid levels, a gravel floor, and intact rock beneath.
            for (BlockPos cell : cells) {
                BlockState cap = level.getBlockState(cell.above());
                if (!cap.isAir() && !removableCover(cap)) {
                    enclosed = false;
                    break;
                }
                for (int y = cell.getY(); y >= waterY - 2; y--) {
                    if (!natural(level.getBlockState(
                            new BlockPos(cell.getX(), y, cell.getZ())))) {
                        enclosed = false;
                        break;
                    }
                }
                if (!enclosed || !level.getBlockState(
                        new BlockPos(cell.getX(), waterY - 3, cell.getZ()))
                        .blocksMotion()) {
                    enclosed = false;
                    break;
                }
            }
            if (!enclosed) continue;
            // The perimeter remains a two-level solid bank. Both water
            // surfaces of the pair are internal and need no bank between.
            for (BlockPos cell : cells) {
                for (Direction wall : Direction.Plane.HORIZONTAL) {
                    int nx = cell.getX() + wall.getStepX();
                    int nz = cell.getZ() + wall.getStepZ();
                    if ((nx == first.getX() && nz == first.getZ())
                            || (nx == second.getX() && nz == second.getZ()))
                        continue;
                    BlockPos bank = new BlockPos(nx, waterY, nz);
                    BlockState upper = level.getBlockState(bank);
                    BlockState lower = level.getBlockState(bank.below());
                    boolean joined = (upper.is(Blocks.WATER) || upper.is(Blocks.ICE))
                            && lower.is(Blocks.WATER);
                    if (!joined && (!solidBank(upper) || !solidBank(lower))) {
                        enclosed = false;
                        break;
                    }
                }
                if (!enclosed) break;
            }
            if (!enclosed) continue;

            // All checks passed. Commit only these two in-chunk columns.
            for (BlockPos cell : cells) {
                if (removableCover(level.getBlockState(cell.above())))
                    level.setBlock(cell.above(), Blocks.AIR.defaultBlockState(), 2);
                for (int y = cell.getY(); y > waterY; y--)
                    level.setBlock(new BlockPos(cell.getX(), y, cell.getZ()),
                            Blocks.AIR.defaultBlockState(), 2);
                BlockPos water = new BlockPos(cell.getX(), waterY, cell.getZ());
                level.setBlock(water.below(2), Blocks.GRAVEL.defaultBlockState(), 2);
                level.setBlock(water.below(), Blocks.WATER.defaultBlockState(), 2);
                level.setBlock(water, level.getBiome(cell).is(Biomes.FROZEN_RIVER)
                        ? Blocks.ICE.defaultBlockState()
                        : Blocks.WATER.defaultBlockState(), 2);
            }
            return true;
        }
        return false;
    }

    /**
     * Navigation-first river fill for NEW world generation.
     *
     * Earlier bank-guarded riverlets often left natural sediment at boat
     * depth, and rejected water wherever slopes could spill. The optional
     * hovercraft cannot fix a solid channel blockage, so new river columns
     * now receive a three-block open waterway over one gravel floor.
     *
     * Deliberately permits vanilla water simulation to spill down exposed
     * natural slopes. This implementation never builds artificial soil dams,
     * never writes outside the current channel column, never excavates
     * non-natural blocks, and preserves vanilla oceans and the sea level.
     */
    private static boolean tryOpenNavigableChannel(
            WorldGenLevel level, BlockPos top, BlockPos coverPos,
            boolean covered, boolean frozen, int floor) {
        if (top.getY() - 3 <= floor) return false;
        // The fourth supporting layer was already checked by the caller.
        // No deep cave punctures or unsupported fluid columns.
        if (!natural(level.getBlockState(top.below(3))))
            return false;
        if (covered) {
            level.setBlock(coverPos, Blocks.AIR.defaultBlockState(), 2);
        }
        level.setBlock(top.below(3), Blocks.GRAVEL.defaultBlockState(), 2);
        level.setBlock(top.below(2), Blocks.WATER.defaultBlockState(), 2);
        level.setBlock(top.below(), Blocks.WATER.defaultBlockState(), 2);
        level.setBlock(top, frozen
                ? Blocks.ICE.defaultBlockState()
                : Blocks.WATER.defaultBlockState(), 2);
        return true;
    }

    /**
     * Lab world types embed their persistent river-grade setting in the
     * immutable registry ID. No client-only or mutable global config is read
     * during generation; existing saved worlds retain their original rules.
     */
    private static int riverGradeForWorld(String id) {
        if (!id.startsWith("lab_")) return 6;
        // Preserve the existing tested persistent grade parsing contract.
        for (int grade : new int[] {0, 3, 6, 9}) {
            if (id.endsWith("_g" + grade)) return grade;
        }
        return -1; // Unsupported / malformed Lab id: never place fluids
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        // The common path adds only a constant false branch; no clock reads.
        if (!WorldgenProfiler.ENABLED) return placeUnprofiled(context);
        long started = WorldgenProfiler.begin();
        try {
            return placeUnprofiled(context);
        } finally {
            WorldgenProfiler.finish(WorldgenProfiler.Section.RIVER, started);
        }
    }

    private boolean placeUnprofiled(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        ChunkGenerator base = context.chunkGenerator();
        if (!(base instanceof NoiseBasedChunkGenerator noise)) return false;
        // NeoForge biome modifiers are global: never touch vanilla or
        // foreign-mod worlds. Legacy USGS DEM has high river biomes far
        // above sea level; allow the same bank-guarded shallow channels as
        // other RealScale presets. The underlying DEM density and sample
        // elevations remain unchanged; only safe river surface columns in
        // newly generated chunks may receive the two-layer stream.
        var settingsKey = noise.generatorSettings().unwrapKey();
        if (settingsKey.isEmpty()) return false;
        ResourceLocation id = settingsKey.get().location();
        if (!id.getNamespace().equals("realscale"))
            return false;
        int maxGrade = riverGradeForWorld(id.getPath());
        if (maxGrade <= 0) return false;

        WorldGenLevel level = context.level();
        ChunkPos chunk = new ChunkPos(context.origin());
        int floor = Math.max(level.getMinBuildHeight() + 8, noise.getSeaLevel() + 6);
        boolean made = false;
        int highlandReaches = 0;

        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                BlockPos coverPos = new BlockPos(x, surfaceY, z);
                boolean covered = removableCover(level.getBlockState(coverPos));
                int y = covered ? surfaceY - 1 : surfaceY;
                if (y <= floor || y > level.getMaxBuildHeight() - 6) continue;
                BlockPos top = new BlockPos(x, y, z);
                // A single biome lookup covers both checks. This matters for
                // decorated biome sources that scan nearby quart boundaries.
                var biome = level.getBiome(top);
                boolean frozen = biome.is(Biomes.FROZEN_RIVER);
                if (!frozen && !biome.is(Biomes.RIVER)) continue;

                // Prefer natural ground, and refuse to carve through player-like
                // structures, fluids, dangling snow, cave roofs or vegetation.
                BlockState surface = level.getBlockState(top);
                BlockState below = level.getBlockState(top.below());
                BlockState bed = level.getBlockState(top.below(2));
                BlockState foundation = level.getBlockState(top.below(3));
                BlockState deepFoundation = level.getBlockState(top.below(4));
                // Skip the overhead lookup entirely for unsuitable riverbeds.
                if (!natural(surface) || !natural(below) || !natural(bed)
                    || !natural(foundation) || !deepFoundation.blocksMotion())
                    continue;
                BlockState overhead = level.getBlockState(top.above());
                if (!overhead.isAir()
                        && !(covered && removableCover(overhead)))
                    continue;

                // Prioritize open navigable water above sea level; shore
                // overflow is preferable to solid plugs in the channel.
                if (tryOpenNavigableChannel(
                        level, top, coverPos, covered, frozen, floor)) {
                    made = true;
                    continue;
                }

                // In high mountains, bank overflow is an intended natural
                // effect. Paired ice/water reaches can descend into cascades.
                // Other climates retain the protected-bank algorithm.
                if (top.getY() >= Math.max(128, floor + 32)
                        && highlandReaches < MAX_HIGHLAND_REACHES
                        && tryHighlandOverflowReach(level, chunk, top, floor, maxGrade)) {
                    highlandReaches++;
                    made = true;
                    continue;
                }

                // Prefer a mutually contained water pair over an isolated spring.
                if (tryAdjacentSafeRiverPool(level, chunk, top, floor, maxGrade)) {
                    made = true;
                    continue;
                }

                // A shallow one-block inset works on gentle valley floors.
                // On slopes with a lower neighboring bank, lower the water
                // line another block INSTEAD of exposing the source above it.
                // Never lower farther than two blocks: avoid artificial wells.
                int inset = enclosedByBanks(level, top, 1, maxGrade) ? 1 : 0;
                if (inset == 0 && natural(deepFoundation)
                    && level.getBlockState(top.below(5)).blocksMotion()
                    && enclosedByBanks(level, top, 2, maxGrade))
                    inset = 2;
                if (inset == 0) continue;

                // Do not leave the one-block snow/fern cap floating above
                // the stream. All bank and deep-foundation checks have passed.
                if (covered) {
                    level.setBlock(coverPos, Blocks.AIR.defaultBlockState(), 2);
                }
                // Two water layers and a gravel riverbed. Four-side bank
                // checks precede both depth choices. Never alter a column
                // outside the 16x16 chunk being populated.
                level.setBlock(top.below(inset + 2), Blocks.GRAVEL.defaultBlockState(), 2);
                level.setBlock(top.below(inset + 1), Blocks.WATER.defaultBlockState(), 2);
                level.setBlock(top.below(inset), frozen
                    ? Blocks.ICE.defaultBlockState()
                    : Blocks.WATER.defaultBlockState(), 2);
                for (int airDepth = 0; airDepth < inset; airDepth++) {
                    level.setBlock(top.below(airDepth), Blocks.AIR.defaultBlockState(), 2);
                }
                made = true;
            }
        }
        return made;
    }
}

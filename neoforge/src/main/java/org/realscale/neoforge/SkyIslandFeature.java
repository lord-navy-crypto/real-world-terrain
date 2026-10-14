package org.realscale.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Actual floating stone/soil landmass in the special, opt-in sky-ocean preset.
 * Worldgen only, at most five islands per 512x512 block grid and only over
 * biome-source-confirmed far-ocean conditions. Does not touch sea or seabed.
 */
public final class SkyIslandFeature extends Feature<NoneFeatureConfiguration> {
    private static final ResourceKey<Biome> SKY = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("realscale", "sky_blossom_isles"));

    public SkyIslandFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!(context.chunkGenerator().getBiomeSource() instanceof SkyOceanBiomeSource))
            return false; // Existing worlds and vanilla presets cannot create islands.
        WorldGenLevel world = context.level();
        BlockPos origin = context.origin();
        SkyIslandLayout.Site base = SkyIslandLayout.at(origin.getX(), origin.getZ());
        boolean any = false;
        // A group shares the same 512-block lattice cell. Each distinct island
        // is generated only by its own home chunk, never by the other islands'
        // decorating chunks. Unselected far-ocean satellites do nothing.
        for (int slot = 0; slot < SkyIslandLayout.SLOTS; slot++) {
            SkyIslandLayout.Site site = SkyIslandLayout.satellite(base, slot);
            if (Math.floorDiv(site.x(), 16) != Math.floorDiv(origin.getX(), 16)
                    || Math.floorDiv(site.z(), 16) != Math.floorDiv(origin.getZ(), 16))
                continue;
            any |= placeIsland(world, site);
        }
        return any;
    }

    private static boolean placeIsland(WorldGenLevel world, SkyIslandLayout.Site site) {
        if (site.topY() + 4 >= world.getMaxBuildHeight()
                || site.topY() - 17 <= world.getMinBuildHeight())
            return false;
        // Biome selection enforces the sampled ocean isolation tier, so an
        // inactive island slot cannot write a real landmass.
        // Must be selected by the actual high-altitude climate sampler.
        if (!world.getBiome(new BlockPos(site.x(), site.topY(), site.z())).is(SKY))
            return false;
        final int r = site.radius();
        // First validate the entire footprint; no accidental edits to pre-existing
        // terrain, ocean water, structures, or partial islands on obstruction.
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            double ratio = (double)(dx * dx + dz * dz) / (r * r);
            if (ratio > 1.0) continue;
            int top = surface(site, dx, dz, ratio);
            int thickness = thickness(ratio);
            for (int y = top - thickness; y <= top + 1; ++y) {
                BlockPos p = new BlockPos(site.x() + dx, y, site.z() + dz);
                if (!world.getBlockState(p).isAir() || !world.getFluidState(p).isEmpty())
                    return false;
            }
        }
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            double ratio = (double)(dx * dx + dz * dz) / (r * r);
            if (ratio > 1.0) continue;
            int top = surface(site, dx, dz, ratio);
            int thickness = thickness(ratio);
            int x = site.x() + dx, z = site.z() + dz;
            for (int y = top - thickness; y <= top; ++y) {
                int depth = top - y;
                BlockState state = depth == 0
                        ? (ratio < .65 && ((dx * 17 + dz * 11) & 7) == 0
                                ? Blocks.MOSS_BLOCK.defaultBlockState()
                                : Blocks.GRASS_BLOCK.defaultBlockState())
                        : depth <= 2 ? Blocks.DIRT.defaultBlockState()
                        : depth >= 6 && ((dx * 7 + dz * 13) & 3) == 0
                                ? Blocks.END_STONE.defaultBlockState()
                                : Blocks.STONE.defaultBlockState();
                world.setBlock(new BlockPos(x, y, z), state, 2);
            }
            // Fantastical flowering meadow, solely using vanilla blocks.
            if (ratio < .70 && ((x * 31 ^ z * 17) & 15) == 3) {
                BlockPos flower = new BlockPos(x, top + 1, z);
                BlockState plant = ((x ^ z) & 1) == 0
                        ? Blocks.ALLIUM.defaultBlockState()
                        : Blocks.BLUE_ORCHID.defaultBlockState();
                if (plant.canSurvive(world, flower))
                    world.setBlock(flower, plant, 2);
            }
        }
        // A glowing violet beacon makes the sky island recognizably fictional.
        BlockPos beacon = new BlockPos(site.x(), surface(site, 0, 0, 0) + 1, site.z());
        if (world.getBlockState(beacon).isAir()) {
            world.setBlock(beacon, Blocks.AMETHYST_BLOCK.defaultBlockState(), 2);
            world.setBlock(beacon.above(), Blocks.SEA_LANTERN.defaultBlockState(), 2);
        }
        return true;
    }

    private static int surface(SkyIslandLayout.Site site, int dx, int dz, double ratio) {
        return site.topY() - (int)Math.floor(3.0 * ratio);
    }
    private static int thickness(double ratio) {
        return 2 + (int)Math.floor(6.0 * (1.0 - ratio));
    }
}

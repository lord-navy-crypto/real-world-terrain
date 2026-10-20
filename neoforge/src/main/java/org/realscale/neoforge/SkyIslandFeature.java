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
        // The common path adds only a constant false branch; no clock reads.
        if (!WorldgenProfiler.ENABLED) return placeUnprofiled(context);
        long started = WorldgenProfiler.begin();
        try {
            return placeUnprofiled(context);
        } finally {
            WorldgenProfiler.finish(WorldgenProfiler.Section.SKY_ISLAND, started);
        }
    }

    private boolean placeUnprofiled(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!(context.chunkGenerator().getBiomeSource() instanceof SkyOceanBiomeSource))
            return false; // Existing worlds and vanilla presets cannot create islands.
        WorldGenLevel world = context.level();
        BlockPos origin = context.origin();
        boolean enhanced=TVAWorldGuard.hasNorthernFjordV4(context);
        int chunkX=Math.floorDiv(origin.getX(),16);
        int chunkZ=Math.floorDiv(origin.getZ(),16);
        boolean any=false;
        // Feature origins are placement-specific; use the actual chunk
        // center as the lattice reference. Neighbor cells are considered
        // because the footprint, not just an island center, may overlap.
        // Each chunk writes only its own columns.
        int referenceX=chunkX*16+8;
        int referenceZ=chunkZ*16+8;
        for (int gx=-1;gx<=1;gx++) for(int gz=-1;gz<=1;gz++) {
            SkyIslandLayout.Site base=SkyIslandLayout.at(
                referenceX+gx*SkyIslandLayout.GRID,
                referenceZ+gz*SkyIslandLayout.GRID);
            for (int slot=0;slot<SkyIslandLayout.SLOTS;slot++) {
                SkyIslandLayout.Site island=SkyIslandLayout.satellite(base,slot);
                int r=island.radius();
                if (island.x()+r<chunkX*16 || island.x()-r>=chunkX*16+16
                        || island.z()+r<chunkZ*16 || island.z()-r>=chunkZ*16+16)
                    continue;
                any |= placeIsland(world,island,chunkX,chunkZ,enhanced);
            }
        }
        return any;
    }

    private static boolean placeIsland(WorldGenLevel world, SkyIslandLayout.Site site,
                                       int chunkX,int chunkZ,boolean enhanced) {
        if (site.topY() + 8 >= world.getMaxBuildHeight()
                || site.topY() - (enhanced?26:17) <= world.getMinBuildHeight())
            return false;
        // Biome selection enforces the sampled ocean isolation tier, so an
        // inactive island slot cannot write a real landmass.
        // Must be selected by the actual high-altitude climate sampler.
        // Query the biome INSIDE the decorating chunk. The island center
        // usually belongs to another chunk; asking WorldGenRegion for a
        // remote center biome can trigger unsafe neighbor chunk access.
        int localX = Math.max(chunkX * 16, Math.min(site.x(), chunkX * 16 + 15));
        int localZ = Math.max(chunkZ * 16, Math.min(site.z(), chunkZ * 16 + 15));
        if (!world.getBiome(new BlockPos(localX, site.topY(), localZ)).is(SKY))
            return false;
        final int r = site.radius();
        // Validate each column independently. One obstructed edge column must
        // not cancel the entire chunk (or leave the island center hollow).
        boolean placed=false;
        // Iterate only the 16x16 columns owned by this chunk. The previous
        // (-r..r)^2 scan discarded out-of-chunk positions AFTER calculating
        // their distance. This produces identical candidate coordinates,
        // iteration order, block choices and random calls.
        int minDx=Math.max(-r,chunkX*16-site.x());
        int maxDx=Math.min(r,chunkX*16+15-site.x());
        int minDz=Math.max(-r,chunkZ*16-site.z());
        int maxDz=Math.min(r,chunkZ*16+15-site.z());
        for (int dx=minDx; dx<=maxDx; dx++) for (int dz=minDz; dz<=maxDz; dz++) {
            double ratio = (double)(dx * dx + dz * dz) / (r * r);
            if (ratio > 1.0) continue;
            int top = enhanced ? SkyIslandShapeV4.top(site.topY(),dx,dz,ratio)
                    : surface(site,dx,dz,ratio);
            int thickness = enhanced ? SkyIslandShapeV4.thickness(ratio,dx,dz)
                    : thickness(ratio);
            int x = site.x() + dx, z = site.z() + dz;
            boolean clear=true;
            for (int y=top-thickness;y<=top;y++) {
                BlockPos check=new BlockPos(x,y,z);
                BlockState existing=world.getBlockState(check);
                // Complete partially generated islands without overwriting
                // anything other than our own known stone/soil palette.
                if (!existing.isAir() && !isIslandMaterial(existing)) {
                    clear=false;
                    break;
                }
                if (!world.getFluidState(check).isEmpty()) {
                    clear=false;
                    break;
                }
            }
            BlockPos above=new BlockPos(x,top+1,z);
            BlockState overhead=world.getBlockState(above);
            // Existing island beacons and flowers are legitimate; all other
            // solid obstacles prevent replacing their support columns.
            if (!overhead.isAir() && !isIslandDecoration(overhead)) clear=false;
            if (!world.getFluidState(above).isEmpty()) clear=false;
            if (!clear) continue;
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
                placed=true;
            }
            // V4-only sheltered understory, within the owning chunk.
            if(enhanced && ratio<.55 && ((x*13 ^ z*37)&31)==7) {
                BlockPos shrub=new BlockPos(x,top+1,z);
                BlockState plant=((x^z)&1)==0
                    ? Blocks.AZALEA.defaultBlockState()
                    : Blocks.FLOWERING_AZALEA.defaultBlockState();
                if(world.getBlockState(shrub).isAir()&&plant.canSurvive(world,shrub))
                    world.setBlock(shrub,plant,2);
            }
            // Fantastical flowering meadow, solely using vanilla blocks.
            if (ratio < .70 && ((x * 31 ^ z * 17) & (enhanced?7:15)) == 3) {
                BlockPos flower = new BlockPos(x, top + 1, z);
                BlockState plant = ((x ^ z) & 1) == 0
                        ? Blocks.ALLIUM.defaultBlockState()
                        : Blocks.BLUE_ORCHID.defaultBlockState();
                if (plant.canSurvive(world, flower))
                    world.setBlock(flower, plant, 2);
            }
        }
        // A glowing violet beacon makes the sky island recognizably fictional.
        BlockPos beacon = new BlockPos(site.x(), (enhanced
                ? SkyIslandShapeV4.top(site.topY(),0,0,0)
                : surface(site,0,0,0))+1, site.z());
        if (placed && Math.floorDiv(beacon.getX(),16)==chunkX
                && Math.floorDiv(beacon.getZ(),16)==chunkZ
                && world.getBlockState(beacon).isAir()
                && isIslandMaterial(world.getBlockState(beacon.below()))) {
            world.setBlock(beacon, Blocks.AMETHYST_BLOCK.defaultBlockState(), 2);
            world.setBlock(beacon.above(), Blocks.SEA_LANTERN.defaultBlockState(), 2);
        }
        return placed;
    }

    private static boolean isIslandMaterial(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.END_STONE)
                || state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.MOSS_BLOCK);
    }
    private static boolean isIslandDecoration(BlockState state) {
        return state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.SEA_LANTERN)
                || state.is(Blocks.ALLIUM) || state.is(Blocks.BLUE_ORCHID);
    }

    private static int surface(SkyIslandLayout.Site site, int dx, int dz, double ratio) {
        return site.topY() - (int)Math.floor(3.0 * ratio);
    }
    private static int thickness(double ratio) {
        return 2 + (int)Math.floor(6.0 * (1.0 - ratio));
    }
}

package org.realscale.neoforge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class RealScaleCacheCapacityTest {
    @Test
    void compactMeasuredMemoRecomputesExactlyAfterRepeatedEviction() {
        MeasuredColumnMemo memo = new MeasuredColumnMemo(16);
        assertEquals(16, memo.capacity());
        for (int i = 0; i < 20_000; i++) {
            int x = (i * 73) % 30_001 - 15_000;
            int z = (i * 127) % 32_003 - 16_001;
            double expected = 0.75 + x * 0.03125 + z * 0.0078125;
            if (!memo.lookup(x, z)) memo.store(expected);
            assertEquals(Double.doubleToRawLongBits(expected),
                    Double.doubleToRawLongBits(memo.value()));
        }
    }

    @Test
    void compactBiomeCachesRemainExactAfterEviction() {
        QuartBiomeBaseCache<String> base = new QuartBiomeBaseCache<>(16);
        QuartBiomeResultCache<String> result = new QuartBiomeResultCache<>(16);
        assertEquals(16, base.capacity());
        assertEquals(16, result.capacity());
        for (long key = -10_000; key < 10_000; key++) {
            String expected = "biome/" + key;
            base.put(key, expected);
            result.put(key, expected);
            assertEquals(expected, base.get(key));
            assertEquals(expected, result.get(key));
        }
    }

    @Test
    void compactMountainSiteCacheRebuildsTheOriginalGeometry() {
        MountainReliefSiteCache cache = new MountainReliefSiteCache(16);
        assertEquals(16, cache.capacity());
        for (int i = 0; i < 12_000; i++) {
            int gx = (i * 31) % 4097 - 2048;
            int gz = (i * 47) % 4099 - 2049;
            MountainReliefSiteCache.Site site = cache.get(gx, gz);
            long hash = MountainReliefMath.siteHash(gx, gz);
            long x = (long) gx * MountainReliefMath.CELL
                    + MountainReliefMath.CELL / 2
                    + MountainReliefMath.centerOffsetX(hash);
            long z = (long) gz * MountainReliefMath.CELL
                    + MountainReliefMath.CELL / 2
                    + MountainReliefMath.centerOffsetZ(hash);
            assertEquals(gx, site.gx);
            assertEquals(gz, site.gz);
            assertEquals(hash, site.hash);
            assertEquals((double) Math.toIntExact(x), site.centerX, 0.0);
            assertEquals((double) Math.toIntExact(z), site.centerZ, 0.0);
            assertEquals(Double.doubleToRawLongBits(MountainReliefMath.radius(hash)),
                    Double.doubleToRawLongBits(site.radius));
        }
    }

    @Test
    void compactSkyIslandCacheRebuildsTheOriginalBaseAndSatellites() {
        SkyIslandSiteCache cache = new SkyIslandSiteCache(16);
        assertEquals(16, cache.capacity());
        for (int i = 0; i < 8_000; i++) {
            int x = (i * 113) % 1_000_003 - 500_001;
            int z = (i * 179) % 1_200_007 - 600_003;
            SkyIslandSiteCache.Entry entry = cache.at(x, z);
            SkyIslandLayout.Site base = SkyIslandLayout.at(x, z);
            assertEquals(base, entry.base());
            for (int slot = 0; slot < SkyIslandLayout.SLOTS; slot++) {
                assertEquals(SkyIslandLayout.satellite(base, slot),
                        entry.satellite(slot));
            }
        }
    }

    @Test
    void compactDemCachePreservesOriginalMeasuredSamples() {
        RealDemTile tile = RealDemTile.load();
        RealDemColumnCache cache = new RealDemColumnCache(tile, 16);
        assertEquals(16, cache.capacity());
        for (int i = 0; i < 4_000; i++) {
            int x = tile.originX() - 24
                    + (i * 37) % (tile.maxX() - tile.originX() + 49);
            int z = tile.originZ() - 24
                    + (i * 53) % (tile.maxZ() - tile.originZ() + 49);
            for (int blend : new int[]{32, 96, 128, 256}) {
                RealDemColumnCache.Column sample = cache.sample(x, z, blend);
                double weight = tile.influence(x, z, blend);
                assertEquals(Double.doubleToRawLongBits(weight),
                        Double.doubleToRawLongBits(sample.influence()));
                if (weight > 0.0) {
                    assertEquals(Double.doubleToRawLongBits(tile.worldSolidYAt(x, z)),
                            Double.doubleToRawLongBits(sample.elevation()));
                }
            }
        }
    }
}

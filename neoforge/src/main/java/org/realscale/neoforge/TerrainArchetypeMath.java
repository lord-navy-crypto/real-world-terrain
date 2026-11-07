package org.realscale.neoforge;

/**
 * Pure-Java terrain morphology kernel.
 *
 * <p>This class intentionally has no Minecraft/registry dependencies so the
 * exact production formulas can be exercised by fast unit tests. The runtime
 * DensityFunction delegates here after sampling its three source fields.
 */
final class TerrainArchetypeMath {
    private static final int CELL = 2048;
    private static final double TAU = Math.PI * 2.0;

    private final String family;
    private final double macroGain;
    private final double mesoGain;
    private final double microGain;
    private final double reliefGain;
    private final double ridgeSharpness;
    private final double cliffIndex;
    private final double valleyIndex;
    private final double ridgeIndex;
    private final double anisotropyGain;
    private final double duneAxisRadians;
    private final double dominantWavelengthM;
    private final double axisCos;
    private final double axisSin;
    // Fixed at codec/preset creation: these measured-wavelength scales do not
    // vary by X/Z or Y. Compute them once with the original floating-point
    // expression; no resampling, approximation or rounding is introduced.
    private final double glacialWidth;
    private final double volcanicRadius;
    private final double karstTowerRadius;
    private final double karstSinkRadius;

    // Volcanic geometry depends only on horizontal block coordinates. Worldgen
    // samples many Y values for the same X/Z, so cache the expensive radial
    // shield/caldera shape per worker rather than repeating hash/hypot/pow/exp.
    private static final int VOLCANIC_CACHE_SLOTS = 1024;
    // A 2048-block fitted region has a fixed, seed-derived geographic center.
    // Reusing this per worker avoids re-hashing region seeds and generating
    // the same tower/sinksite positions for every horizontal density sample.
    // Replacements only change performance; all coordinates are recomputed
    // with the original expressions on a region miss.
    private final ThreadLocal<SiteGeometry> siteGeometryCache =
            ThreadLocal.withInitial(SiteGeometry::new);
    private static final class SiteGeometry {
        int cellX,cellZ;
        boolean valid;
        double centerX,centerZ,sinkX,sinkZ;
    }
    private SiteGeometry siteGeometry(int cellX,int cellZ) {
        SiteGeometry site=siteGeometryCache.get();
        if(site.valid && site.cellX==cellX && site.cellZ==cellZ)return site;
        long hash=MountainReliefMath.siteHash(cellX,cellZ);
        double centerX=cellX*(double)CELL+CELL*0.5
                +MountainReliefMath.centerOffsetX(hash);
        double centerZ=cellZ*(double)CELL+CELL*0.5
                +MountainReliefMath.centerOffsetZ(hash);
        long sinkHash=MountainReliefMath.mix(hash ^ 0x6a09e667f3bcc909L);
        double sinkX=centerX+MountainReliefMath.centerOffsetX(sinkHash)*1.8;
        double sinkZ=centerZ+MountainReliefMath.centerOffsetZ(sinkHash)*1.8;
        site.centerX=centerX;
        site.centerZ=centerZ;
        site.sinkX=sinkX;
        site.sinkZ=sinkZ;
        site.cellX=cellX;
        site.cellZ=cellZ;
        site.valid=true;
        return site;
    }

    private final ThreadLocal<VolcanicShapeCache> volcanicShapeCache =
            ThreadLocal.withInitial(VolcanicShapeCache::new);

    private static final class VolcanicShapeCache {
        private final long[] keys = new long[VOLCANIC_CACHE_SLOTS];
        private final double[] shapes = new double[VOLCANIC_CACHE_SLOTS];
        private final boolean[] occupied = new boolean[VOLCANIC_CACHE_SLOTS];
        // Most NoiseChunk requests revisit one X/Z for many Y samples. Check
        // this before hashing/probing the direct-mapped table.
        private boolean lastValid;
        private long lastKey;
        private double lastShape;
    }

    private static final int HORIZONTAL_CACHE_SLOTS = 1024;
    private static final class HorizontalShapeCache {
        private final long[] xBits = new long[HORIZONTAL_CACHE_SLOTS];
        private final long[] zBits = new long[HORIZONTAL_CACHE_SLOTS];
        private final double[] first = new double[HORIZONTAL_CACHE_SLOTS];
        private final double[] second = new double[HORIZONTAL_CACHE_SLOTS];
        private final boolean[] occupied = new boolean[HORIZONTAL_CACHE_SLOTS];
        private boolean lastValid;
        private long lastXBits,lastZBits;
        private double lastFirst,lastSecond;
    }
    private final ThreadLocal<HorizontalShapeCache> horizontalShapeCache =
            ThreadLocal.withInitial(HorizontalShapeCache::new);

    private static final int KARST_CACHE_SLOTS = 1024;
    private static final class KarstShapeCache {
        private final long[] keys = new long[KARST_CACHE_SLOTS];
        private final double[] towers = new double[KARST_CACHE_SLOTS];
        private final double[] sinks = new double[KARST_CACHE_SLOTS];
        private final boolean[] occupied = new boolean[KARST_CACHE_SLOTS];
        private boolean lastValid;
        private long lastKey;
        private double lastTower;
        private double lastSink;
    }
    private final ThreadLocal<KarstShapeCache> karstShapeCache =
            ThreadLocal.withInitial(KarstShapeCache::new);

    TerrainArchetypeMath(String family, double macroGain, double mesoGain,
            double microGain, double reliefGain, double ridgeSharpness,
            double cliffIndex, double valleyIndex, double ridgeIndex,
            double anisotropyGain, double duneAxisRadians,
            double dominantWavelengthM) {
        this.family = family;
        this.macroGain = macroGain;
        this.mesoGain = mesoGain;
        this.microGain = microGain;
        this.reliefGain = reliefGain;
        this.ridgeSharpness = ridgeSharpness;
        this.cliffIndex = cliffIndex;
        this.valleyIndex = valleyIndex;
        this.ridgeIndex = ridgeIndex;
        this.anisotropyGain = anisotropyGain;
        this.duneAxisRadians = duneAxisRadians;
        this.dominantWavelengthM = dominantWavelengthM;
        this.axisCos = Math.cos(duneAxisRadians);
        this.axisSin = Math.sin(duneAxisRadians);
        this.glacialWidth = fittedScale(0.035, 180.0, 1600.0);
        this.volcanicRadius = fittedScale(0.025, 250.0, 800.0);
        this.karstTowerRadius = fittedScale(0.025, 80.0, 450.0);
        this.karstSinkRadius = fittedScale(0.012, 40.0, 200.0);
    }

    double compute(double original, double continent, double rawSignal,
            double x, double z) {
        double landGate = smoothstep(-0.10, 0.22, continent);
        if (landGate <= 0.0) return original;

        double signal = unitNoise(rawSignal);
        // Keep each family's trigonometry and geometric work inside that
        // family's branch. Density functions are sampled millions of times
        // during chunk generation; computing canyon waves for a volcano (or
        // cosine/sine per sample) was wasted CPU work.
        return switch (family) {
            case "alpine" -> alpine(original, landGate, signal, x, z);
            case "canyon" -> canyon(original, landGate, signal, x, z);
            case "glacial" -> glacialValley(original, landGate, x, z);
            case "volcanic" -> volcanicShield(original, landGate, x, z);
            case "karst" -> karstTowers(original, landGate, x, z, signal);
            case "dune" -> duneRidges(original, landGate, x, z, signal);
            default -> original;
        };
    }

    /**
     * Exact per-worker cache for the horizontal part of the three measured
     * fitted kernels that benefit from reuse. Their ridge/valley geometry is invariant over Y, while
     * the original density, land gate, highland gate and selector may vary.
     * Full double-bit coordinate keys preserve the helper's package-private
     * API and prevent aliases even if a future caller supplies fractional X/Z.
     */
    private void horizontalShapesAt(HorizontalShapeCache cache,double x,double z) {
        long xBits=Double.doubleToRawLongBits(x);
        long zBits=Double.doubleToRawLongBits(z);
        if(cache.lastValid && cache.lastXBits==xBits && cache.lastZBits==zBits)return;
        int hash=(int)(xBits^(xBits>>>32)^Long.rotateLeft(zBits,13)^(zBits>>>32));
        int slot=(hash^(hash>>>16))&(HORIZONTAL_CACHE_SLOTS-1);
        if(cache.occupied[slot] && cache.xBits[slot]==xBits
                && cache.zBits[slot]==zBits) {
            cache.lastXBits=xBits;
            cache.lastZBits=zBits;
            cache.lastFirst=cache.first[slot];
            cache.lastSecond=cache.second[slot];
            cache.lastValid=true;
            return;
        }

        double first=0.0,second=0.0;
        switch(family) {
            case "alpine" -> {
                double across=orientedAcross(x,z);
                double phase=TAU*across/dominantWavelengthM;
                first=0.85+0.15*Math.pow(
                        Math.max(0.0,Math.cos(phase)),2.0);
            }
            case "canyon" -> {
                double across=orientedAcross(x,z);
                double valleyDistance=Math.abs(
                        Math.sin(Math.PI*across/dominantWavelengthM));
                first=1.0-smoothstep(0.0,0.18,valleyDistance);
                second=smoothstep(0.18,0.25,valleyDistance)
                        *(1.0-smoothstep(0.25,0.42,valleyDistance));
            }
            case "glacial" -> {
                int cellX=Math.floorDiv((int)x,CELL);
                double centerX=cellX*(double)CELL+CELL*0.5
                        +glacialWidth*0.22*Math.sin(
                                TAU*z/dominantWavelengthM+cellX*1.17);
                double across=Math.abs(x-centerX);
                first=1.0-smoothstep(glacialWidth*0.28,glacialWidth,across);
            }
            default -> { }
        }
        cache.xBits[slot]=xBits;
        cache.zBits[slot]=zBits;
        cache.first[slot]=first;
        cache.second[slot]=second;
        cache.occupied[slot]=true;
        cache.lastXBits=xBits;
        cache.lastZBits=zBits;
        cache.lastFirst=first;
        cache.lastSecond=second;
        cache.lastValid=true;
    }

    private double alpine(double original, double landGate, double signal, double x, double z) {
        double highlandGate = smoothstep(0.65, 1.7, original);
        // The dominant orientation and wavelength are fitted from each real
        // DEM crop. Keep these broad signals subtle so the fitted scale guides
        // morphology without turning the entire world into repeated stripes.
        HorizontalShapeCache cache=horizontalShapeCache.get();
        horizontalShapesAt(cache,x,z);
        double ridgeBands=cache.lastFirst;
        return original + landGate * highlandGate * reliefGain
                * 0.22 * macroGain * ridgeSharpness * ridgeBands
                * (1.0 - Math.abs(signal));
    }

    private double canyon(double original, double landGate, double signal, double x, double z) {
        HorizontalShapeCache cache=horizontalShapeCache.get();
        horizontalShapesAt(cache,x,z);
        double canyonTrough=cache.lastFirst;
        double canyonShoulder=cache.lastSecond;
        return original - landGate * reliefGain
                * (0.28 * mesoGain * cliffIndex * Math.abs(signal)
                + 0.10 * valleyIndex * canyonTrough)
                + landGate * reliefGain * 0.025 * cliffIndex * canyonShoulder;
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        if (edge0 == edge1) return x < edge0 ? 0.0 : 1.0;
        double t = Math.max(0.0, Math.min(1.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double unitNoise(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }

    private static double distance(double x1, double z1, double x2, double z2) {
        return Math.hypot(x1 - x2, z1 - z2);
    }

    private double orientedAcross(double x, double z) {
        return x * axisCos + z * axisSin;
    }

    private double fittedScale(double fraction, double min, double max) {
        return Math.max(min, Math.min(max, dominantWavelengthM * fraction));
    }

    private double glacialValley(double original, double gate, double x, double z) {
        HorizontalShapeCache cache=horizontalShapeCache.get();
        horizontalShapesAt(cache,x,z);
        // Flat-bottomed center followed by a broad U-shaped valley wall;
        // width follows the measured wavelength of the glacial-valley crop.
        double trough=cache.lastFirst;
        return original - gate * reliefGain * 0.44 * Math.max(valleyIndex, 0.12) * trough;
    }

    private double volcanicShield(double original, double gate, double x, double z) {
        int blockX = (int) x;
        int blockZ = (int) z;
        double shape = volcanicShapeAt(blockX, blockZ);
        if (shape == 0.0) return original;
        return original + gate * reliefGain * 0.55 * Math.max(macroGain, 0.12) * shape;
    }

    private double volcanicShapeAt(int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        VolcanicShapeCache cache = volcanicShapeCache.get();
        if (cache.lastValid && cache.lastKey == key) return cache.lastShape;
        int hash = x * 0x9e3779b9 ^ Integer.rotateLeft(z * 0x85ebca6b, 13);
        int slot = (hash ^ (hash >>> 16)) & (VOLCANIC_CACHE_SLOTS - 1);
        if (cache.occupied[slot] && cache.keys[slot] == key) {
            cache.lastValid = true;
            cache.lastKey = key;
            cache.lastShape = cache.shapes[slot];
            return cache.lastShape;
        }

        int cellX = Math.floorDiv(x, CELL);
        int cellZ = Math.floorDiv(z, CELL);
        SiteGeometry site=siteGeometry(cellX,cellZ);
        double radius = volcanicRadius;
        double shape;
        // A point outside the enclosing square cannot lie inside the
        // circular volcano footprint. This exact zero-support rejection
        // avoids Math.hypot and expensive pow/exp on most columns.
        if (Math.abs(x-site.centerX) >= radius
                || Math.abs(z-site.centerZ) >= radius) {
            shape = 0.0;
        } else {
            double radial = distance(x, z, site.centerX, site.centerZ) / radius;
            if (radial >= 1.0) {
                shape = 0.0;
            } else {
                double shield = Math.pow(Math.max(0.0, 1.0 - radial), 1.35);
                double caldera = 0.16 * Math.exp(-Math.pow(radial / 0.12, 2.0));
                shape = shield - caldera;
            }
        }

        cache.keys[slot] = key;
        cache.shapes[slot] = shape;
        cache.occupied[slot] = true;
        cache.lastValid = true;
        cache.lastKey = key;
        cache.lastShape = shape;
        return shape;
    }

    private void karstShapesAt(KarstShapeCache cache, int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        if (cache.lastValid && cache.lastKey == key) return;
        int hash = x * 0x9e3779b9 ^ Integer.rotateLeft(z * 0x85ebca6b, 13);
        int slot = (hash ^ (hash >>> 16)) & (KARST_CACHE_SLOTS - 1);
        if (cache.occupied[slot] && cache.keys[slot] == key) {
            cache.lastValid = true;
            cache.lastKey = key;
            cache.lastTower = cache.towers[slot];
            cache.lastSink = cache.sinks[slot];
            return;
        }

        int cellX = Math.floorDiv(x, CELL);
        int cellZ = Math.floorDiv(z, CELL);
        SiteGeometry site = siteGeometry(cellX, cellZ);
        double towerRadius = karstTowerRadius;
        double tower = 0.0;
        if (Math.abs(x-site.centerX) < towerRadius
                && Math.abs(z-site.centerZ) < towerRadius) {
            double radial = distance(x, z, site.centerX, site.centerZ);
            tower = 1.0 - smoothstep(towerRadius * 0.55, towerRadius, radial);
        }
        double sinkRadius = karstSinkRadius;
        double sink = 0.0;
        if (Math.abs(x-site.sinkX) < sinkRadius
                && Math.abs(z-site.sinkZ) < sinkRadius) {
            sink = 1.0 - smoothstep(sinkRadius * 0.45, sinkRadius,
                    distance(x, z, site.sinkX, site.sinkZ));
        }
        cache.keys[slot] = key;
        cache.towers[slot] = tower;
        cache.sinks[slot] = sink;
        cache.occupied[slot] = true;
        cache.lastValid = true;
        cache.lastKey = key;
        cache.lastTower = tower;
        cache.lastSink = sink;
    }

    private double karstTowers(double original, double gate, double x, double z, double signal) {
        KarstShapeCache cache = karstShapeCache.get();
        karstShapesAt(cache, (int)x, (int)z);
        double noiseTower = Math.max(0.0, signal) * 0.08 * ridgeGain();
        return original + gate * reliefGain * (0.36 * Math.max(ridgeIndex, 0.12) * cache.lastTower
                + noiseTower - 0.22 * Math.max(valleyIndex, 0.12) * cache.lastSink);
    }

    private double duneRidges(double original, double gate, double x, double z, double signal) {
        // The Dune phase is only one multiply-add and a scale. Paired CI
        // benchmarks showed the cache lookup costs more than recomputing it,
        // so keep this kernel direct while the heavier three kernels stay memoized.
        double phase = TAU*orientedAcross(x,z)/dominantWavelengthM + signal * 0.12;
        double wave = Math.sin(phase) + 0.28 * Math.sin(2.0 * phase + 0.6);
        // The fitted dune wavelength is a macro-scale landform. Use the
        // measured macro gain for ridge amplitude; the old meso gain left the
        // fitted relief below half a block for the supplied DEM profile.
        return original + gate * reliefGain * 0.10 * Math.max(macroGain, 0.12)
                * anisotropyGain * wave;
    }

    private double ridgeGain() {
        return Math.max(0.12, macroGain + 0.5 * microGain);
    }
}

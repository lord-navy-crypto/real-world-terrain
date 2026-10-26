package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Procedural morphology kernel whose compact coefficients are fitted offline
 * from a verified real-world DEM. The source raster is never loaded at runtime.
 *
 * <p>All spatial features use absolute world coordinates and bounded support,
 * so they are deterministic across chunk order and do not read neighboring
 * chunks or blocks. Parameters are normalized fit statistics, not absolute
 * elevations; vertical scale remains the responsibility of the base noise graph.
 */
public final class TerrainArchetypeDensity implements DensityFunction.SimpleFunction {
    private static final int CELL = 2048;
    private static final double TAU = Math.PI * 2.0;

    private final DensityFunction base;
    private final DensityFunction continents;
    private final DensityFunction selector;
    private final String family;
    private final double macroGain;
    private final double mesoGain;
    private final double microGain;
    private final double ridgeSharpness;
    private final double cliffIndex;
    private final double valleyIndex;
    private final double ridgeIndex;
    private final double anisotropyGain;
    private final double duneAxisRadians;
    private final double dominantWavelengthM;

    public TerrainArchetypeDensity(
            DensityFunction base,
            DensityFunction continents,
            DensityFunction selector,
            String family,
            double macroGain,
            double mesoGain,
            double microGain,
            double ridgeSharpness,
            double cliffIndex,
            double valleyIndex,
            double ridgeIndex,
            double anisotropyGain,
            double duneAxisRadians,
            double dominantWavelengthM) {
        if (!isSupportedFamily(family)) {
            throw new IllegalArgumentException("Unsupported terrain archetype family: " + family);
        }
        this.base = base;
        this.continents = continents;
        this.selector = selector;
        this.family = family;
        this.macroGain = macroGain;
        this.mesoGain = mesoGain;
        this.microGain = microGain;
        this.ridgeSharpness = ridgeSharpness;
        this.cliffIndex = cliffIndex;
        this.valleyIndex = valleyIndex;
        this.ridgeIndex = ridgeIndex;
        this.anisotropyGain = anisotropyGain;
        this.duneAxisRadians = duneAxisRadians;
        this.dominantWavelengthM = dominantWavelengthM;
    }

    private static boolean isSupportedFamily(String value) {
        return "alpine".equals(value) || "canyon".equals(value)
                || "glacial".equals(value) || "volcanic".equals(value)
                || "karst".equals(value) || "dune".equals(value);
    }

    public DensityFunction base() { return base; }
    public DensityFunction continents() { return continents; }
    public DensityFunction selector() { return selector; }
    public String family() { return family; }
    public double macroGain() { return macroGain; }
    public double mesoGain() { return mesoGain; }
    public double microGain() { return microGain; }
    public double ridgeSharpness() { return ridgeSharpness; }
    public double cliffIndex() { return cliffIndex; }
    public double valleyIndex() { return valleyIndex; }
    public double ridgeIndex() { return ridgeIndex; }
    public double anisotropyGain() { return anisotropyGain; }
    public double duneAxisRadians() { return duneAxisRadians; }
    public double dominantWavelengthM() { return dominantWavelengthM; }

    public static final MapCodec<TerrainArchetypeDensity> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(TerrainArchetypeDensity::base),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents").forGetter(TerrainArchetypeDensity::continents),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("selector").forGetter(TerrainArchetypeDensity::selector),
                    Codec.STRING.fieldOf("family").forGetter(TerrainArchetypeDensity::family),
                    Codec.doubleRange(0.0, 1.0).fieldOf("macro_gain").forGetter(TerrainArchetypeDensity::macroGain),
                    Codec.doubleRange(0.0, 1.0).fieldOf("meso_gain").forGetter(TerrainArchetypeDensity::mesoGain),
                    Codec.doubleRange(0.0, 1.0).fieldOf("micro_gain").forGetter(TerrainArchetypeDensity::microGain),
                    Codec.doubleRange(0.0, 1.0).fieldOf("ridge_sharpness").forGetter(TerrainArchetypeDensity::ridgeSharpness),
                    Codec.doubleRange(0.0, 1.0).fieldOf("cliff_index").forGetter(TerrainArchetypeDensity::cliffIndex),
                    Codec.doubleRange(0.0, 1.0).fieldOf("valley_index").forGetter(TerrainArchetypeDensity::valleyIndex),
                    Codec.doubleRange(0.0, 1.0).fieldOf("ridge_index").forGetter(TerrainArchetypeDensity::ridgeIndex),
                    Codec.doubleRange(0.5, 2.0).fieldOf("anisotropy_gain").forGetter(TerrainArchetypeDensity::anisotropyGain),
                    Codec.doubleRange(-Math.PI, Math.PI).fieldOf("dune_axis_radians").forGetter(TerrainArchetypeDensity::duneAxisRadians),
                    Codec.doubleRange(1.0, 1000000.0).fieldOf("dominant_wavelength_m").forGetter(TerrainArchetypeDensity::dominantWavelengthM)
            ).apply(i, TerrainArchetypeDensity::new));
    private static final KeyDispatchDataCodec<TerrainArchetypeDensity> DISPATCH =
            KeyDispatchDataCodec.of(MAP_CODEC);

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

    @Override
    public double compute(DensityFunction.FunctionContext ctx) {
        double original = base.compute(ctx);
        double continent = continents.compute(ctx);
        double landGate = smoothstep(-0.10, 0.22, continent);
        if (landGate <= 0.0) return original;

        double signal = unitNoise(selector.compute(ctx));
        double x = ctx.blockX();
        double z = ctx.blockZ();
        double highlandGate = smoothstep(0.65, 1.7, original);

        return switch (family) {
            case "alpine" -> original + landGate * highlandGate
                    * 0.22 * macroGain * ridgeSharpness * (1.0 - Math.abs(signal));
            case "canyon" -> original - landGate
                    * 0.28 * mesoGain * cliffIndex * Math.abs(signal);
            case "glacial" -> glacialValley(original, landGate, x, z);
            case "volcanic" -> volcanicShield(original, landGate, x, z);
            case "karst" -> karstTowers(original, landGate, x, z, signal);
            case "dune" -> duneRidges(original, landGate, x, z, signal);
            default -> original;
        };
    }

    private double glacialValley(double original, double gate, double x, double z) {
        int cellX = Math.floorDiv((int)x, CELL);
        double centerX = cellX * (double) CELL + CELL * 0.5
                + 130.0 * Math.sin(z * 0.00055 + cellX * 1.17);
        double width = 260.0 + 280.0 * valleyIndex;
        double across = Math.abs(x - centerX);
        // Flat-bottomed center followed by a broad U-shaped valley wall.
        double trough = 1.0 - smoothstep(width * 0.28, width, across);
        return original - gate * 0.44 * Math.max(valleyIndex, 0.12) * trough;
    }

    private double volcanicShield(double original, double gate, double x, double z) {
        int cellX = Math.floorDiv((int)x, CELL);
        int cellZ = Math.floorDiv((int)z, CELL);
        long hash = MountainReliefMath.siteHash(cellX, cellZ);
        double centerX = cellX * (double) CELL + CELL * 0.5
                + MountainReliefMath.centerOffsetX(hash);
        double centerZ = cellZ * (double) CELL + CELL * 0.5
                + MountainReliefMath.centerOffsetZ(hash);
        double radius = 280.0 + Math.floorMod(hash >>> 41, 281L);
        double radial = distance(x, z, centerX, centerZ) / radius;
        if (radial >= 1.0) return original;
        double shield = Math.pow(Math.max(0.0, 1.0 - radial), 1.35);
        double caldera = 0.16 * Math.exp(-Math.pow(radial / 0.12, 2.0));
        return original + gate * 0.55 * Math.max(macroGain, 0.12) * (shield - caldera);
    }

    private double karstTowers(double original, double gate, double x, double z, double signal) {
        int cellX = Math.floorDiv((int)x, CELL);
        int cellZ = Math.floorDiv((int)z, CELL);
        long hash = MountainReliefMath.siteHash(cellX, cellZ);
        double centerX = cellX * (double) CELL + CELL * 0.5
                + MountainReliefMath.centerOffsetX(hash);
        double centerZ = cellZ * (double) CELL + CELL * 0.5
                + MountainReliefMath.centerOffsetZ(hash);
        double radial = distance(x, z, centerX, centerZ);
        double towerRadius = 230.0 + Math.floorMod(hash >>> 37, 220L);
        double tower = 1.0 - smoothstep(towerRadius * 0.55, towerRadius, radial);
        long sinkHash = MountainReliefMath.mix(hash ^ 0x6a09e667f3bcc909L);
        double sinkX = centerX + MountainReliefMath.centerOffsetX(sinkHash) * 1.8;
        double sinkZ = centerZ + MountainReliefMath.centerOffsetZ(sinkHash) * 1.8;
        double sinkRadius = 95.0 + Math.floorMod(sinkHash >>> 33, 90L);
        double sink = 1.0 - smoothstep(sinkRadius * 0.45, sinkRadius,
                distance(x, z, sinkX, sinkZ));
        double noiseTower = Math.max(0.0, signal) * 0.08 * ridgeGain();
        return original + gate * (0.36 * Math.max(ridgeIndex, 0.12) * tower
                + noiseTower - 0.22 * Math.max(valleyIndex, 0.12) * sink);
    }

    private double duneRidges(double original, double gate, double x, double z, double signal) {
        double across = x * Math.cos(duneAxisRadians) + z * Math.sin(duneAxisRadians);
        double phase = TAU * across / dominantWavelengthM + signal * 0.12;
        double wave = Math.sin(phase) + 0.28 * Math.sin(2.0 * phase + 0.6);
        return original + gate * 0.10 * Math.max(mesoGain, 0.12)
                * anisotropyGain * wave;
    }

    private double ridgeGain() {
        return Math.max(0.12, macroGain + 0.5 * microGain);
    }

    @Override
    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new TerrainArchetypeDensity(
                base.mapAll(visitor), continents.mapAll(visitor), selector.mapAll(visitor),
                family, macroGain, mesoGain, microGain, ridgeSharpness, cliffIndex,
                valleyIndex, ridgeIndex, anisotropyGain, duneAxisRadians, dominantWavelengthM));
    }

    @Override public double minValue() { return base.minValue() - 1.0; }
    @Override public double maxValue() { return base.maxValue() + 1.0; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return DISPATCH; }
}

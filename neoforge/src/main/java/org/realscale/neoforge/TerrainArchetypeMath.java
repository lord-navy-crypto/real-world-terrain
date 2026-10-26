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
    }

    double compute(double original, double continent, double rawSignal,
            double x, double z) {
        double landGate = smoothstep(-0.10, 0.22, continent);
        if (landGate <= 0.0) return original;

        double signal = unitNoise(rawSignal);
        double highlandGate = smoothstep(0.65, 1.7, original);
        // The dominant orientation and wavelength are fitted from each real
        // DEM crop. Keep these broad signals subtle so the fitted scale guides
        // morphology without turning the entire world into repeated stripes.
        double across = orientedAcross(x, z);
        double phase = TAU * across / dominantWavelengthM;
        double ridgeBands = 0.85 + 0.15 * Math.pow(Math.max(0.0, Math.cos(phase)), 2.0);
        double valleyDistance = Math.abs(Math.sin(Math.PI * across / dominantWavelengthM));
        double canyonTrough = 1.0 - smoothstep(0.0, 0.18, valleyDistance);
        double canyonShoulder = smoothstep(0.18, 0.25, valleyDistance)
                * (1.0 - smoothstep(0.25, 0.42, valleyDistance));

        return switch (family) {
            case "alpine" -> original + landGate * highlandGate * reliefGain
                    * 0.22 * macroGain * ridgeSharpness * ridgeBands
                    * (1.0 - Math.abs(signal));
            case "canyon" -> original - landGate * reliefGain
                    * (0.28 * mesoGain * cliffIndex * Math.abs(signal)
                    + 0.10 * valleyIndex * canyonTrough)
                    + landGate * reliefGain * 0.025 * cliffIndex * canyonShoulder;
            case "glacial" -> glacialValley(original, landGate, x, z);
            case "volcanic" -> volcanicShield(original, landGate, x, z);
            case "karst" -> karstTowers(original, landGate, x, z, signal);
            case "dune" -> duneRidges(original, landGate, x, z, signal);
            default -> original;
        };
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
        return x * Math.cos(duneAxisRadians) + z * Math.sin(duneAxisRadians);
    }

    private double fittedScale(double fraction, double min, double max) {
        return Math.max(min, Math.min(max, dominantWavelengthM * fraction));
    }

    private double glacialValley(double original, double gate, double x, double z) {
        int cellX = Math.floorDiv((int)x, CELL);
        double width = fittedScale(0.035, 180.0, 1600.0);
        double centerX = cellX * (double) CELL + CELL * 0.5
                + width * 0.22 * Math.sin(TAU * z / dominantWavelengthM + cellX * 1.17);
        double across = Math.abs(x - centerX);
        // Flat-bottomed center followed by a broad U-shaped valley wall;
        // width follows the measured wavelength of the glacial-valley crop.
        double trough = 1.0 - smoothstep(width * 0.28, width, across);
        return original - gate * reliefGain * 0.44 * Math.max(valleyIndex, 0.12) * trough;
    }

    private double volcanicShield(double original, double gate, double x, double z) {
        int cellX = Math.floorDiv((int)x, CELL);
        int cellZ = Math.floorDiv((int)z, CELL);
        long hash = MountainReliefMath.siteHash(cellX, cellZ);
        double centerX = cellX * (double) CELL + CELL * 0.5
                + MountainReliefMath.centerOffsetX(hash);
        double centerZ = cellZ * (double) CELL + CELL * 0.5
                + MountainReliefMath.centerOffsetZ(hash);
        double radius = fittedScale(0.025, 250.0, 800.0);
        double radial = distance(x, z, centerX, centerZ) / radius;
        if (radial >= 1.0) return original;
        double shield = Math.pow(Math.max(0.0, 1.0 - radial), 1.35);
        double caldera = 0.16 * Math.exp(-Math.pow(radial / 0.12, 2.0));
        return original + gate * reliefGain * 0.55 * Math.max(macroGain, 0.12) * (shield - caldera);
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
        double towerRadius = fittedScale(0.025, 80.0, 450.0);
        double tower = 1.0 - smoothstep(towerRadius * 0.55, towerRadius, radial);
        long sinkHash = MountainReliefMath.mix(hash ^ 0x6a09e667f3bcc909L);
        double sinkX = centerX + MountainReliefMath.centerOffsetX(sinkHash) * 1.8;
        double sinkZ = centerZ + MountainReliefMath.centerOffsetZ(sinkHash) * 1.8;
        double sinkRadius = fittedScale(0.012, 40.0, 200.0);
        double sink = 1.0 - smoothstep(sinkRadius * 0.45, sinkRadius,
                distance(x, z, sinkX, sinkZ));
        double noiseTower = Math.max(0.0, signal) * 0.08 * ridgeGain();
        return original + gate * reliefGain * (0.36 * Math.max(ridgeIndex, 0.12) * tower
                + noiseTower - 0.22 * Math.max(valleyIndex, 0.12) * sink);
    }

    private double duneRidges(double original, double gate, double x, double z, double signal) {
        double across = orientedAcross(x, z);
        double phase = TAU * across / dominantWavelengthM + signal * 0.12;
        double wave = Math.sin(phase) + 0.28 * Math.sin(2.0 * phase + 0.6);
        return original + gate * reliefGain * 0.10 * Math.max(mesoGain, 0.12)
                * anisotropyGain * wave;
    }

    private double ridgeGain() {
        return Math.max(0.12, macroGain + 0.5 * microGain);
    }
}

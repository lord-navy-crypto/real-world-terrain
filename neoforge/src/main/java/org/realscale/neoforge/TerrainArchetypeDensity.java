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
    private final DensityFunction base;
    private final DensityFunction continents;
    private final DensityFunction selector;
    private final DensityFunction regionSelector;
    private final DensityFunction temperature;
    private final DensityFunction humidity;
    private final String family;
    private final boolean alpineFamily;
    private final boolean selectorFreeFamily;
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
    private final TerrainArchetypeMath morphology;

    public TerrainArchetypeDensity(
            DensityFunction base,
            DensityFunction continents,
            DensityFunction selector,
            DensityFunction regionSelector,
            String family,
            double macroGain,
            double mesoGain,
            double microGain,
            double reliefGain,
            double ridgeSharpness,
            double cliffIndex,
            double valleyIndex,
            double ridgeIndex,
            double anisotropyGain,
            double duneAxisRadians,
            double dominantWavelengthM) {
        this(base,continents,selector,regionSelector,family,macroGain,mesoGain,
                microGain,reliefGain,ridgeSharpness,cliffIndex,valleyIndex,
                ridgeIndex,anisotropyGain,duneAxisRadians,dominantWavelengthM,
                null,null);
    }

    public TerrainArchetypeDensity(
            DensityFunction base,
            DensityFunction continents,
            DensityFunction selector,
            DensityFunction regionSelector,
            String family,
            double macroGain,
            double mesoGain,
            double microGain,
            double reliefGain,
            double ridgeSharpness,
            double cliffIndex,
            double valleyIndex,
            double ridgeIndex,
            double anisotropyGain,
            double duneAxisRadians,
            double dominantWavelengthM,
            DensityFunction temperature,
            DensityFunction humidity) {
        if (!isSupportedFamily(family)) {
            throw new IllegalArgumentException("Unsupported terrain archetype family: " + family);
        }
        this.base = base;
        this.continents = continents;
        this.selector = selector;
        this.regionSelector = regionSelector == null ? selector : regionSelector;
        this.temperature = temperature;
        this.humidity = humidity;
        this.family = family;
        this.alpineFamily = "alpine".equals(family);
        this.selectorFreeFamily = "glacial".equals(family) || "volcanic".equals(family);
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
        this.morphology = new TerrainArchetypeMath(
                family, macroGain, mesoGain, microGain, reliefGain, ridgeSharpness,
                cliffIndex, valleyIndex, ridgeIndex, anisotropyGain,
                duneAxisRadians, dominantWavelengthM);
    }

    private static boolean isSupportedFamily(String value) {
        return "alpine".equals(value) || "canyon".equals(value)
                || "glacial".equals(value) || "volcanic".equals(value)
                || "karst".equals(value) || "dune".equals(value)
                || "natural_automate".equals(value);
    }

    public DensityFunction base() { return base; }
    public DensityFunction continents() { return continents; }
    public DensityFunction selector() { return selector; }
    public DensityFunction regionSelector() { return regionSelector; }
    public String family() { return family; }
    public double macroGain() { return macroGain; }
    public double mesoGain() { return mesoGain; }
    public double microGain() { return microGain; }
    public double reliefGain() { return reliefGain; }
    public double ridgeSharpness() { return ridgeSharpness; }
    public double cliffIndex() { return cliffIndex; }
    public double valleyIndex() { return valleyIndex; }
    public double ridgeIndex() { return ridgeIndex; }
    public double anisotropyGain() { return anisotropyGain; }
    public double duneAxisRadians() { return duneAxisRadians; }
    public double dominantWavelengthM() { return dominantWavelengthM; }

    private record EnvironmentInputs(DensityFunction regionSelector,
            DensityFunction temperature,DensityFunction humidity) {
        private static final Codec<EnvironmentInputs> CODEC =
                RecordCodecBuilder.create(i -> i.group(
                        DensityFunction.HOLDER_HELPER_CODEC.optionalFieldOf("region_selector")
                                .forGetter(e -> java.util.Optional.ofNullable(e.regionSelector)),
                        DensityFunction.HOLDER_HELPER_CODEC.optionalFieldOf("temperature")
                                .forGetter(e -> java.util.Optional.ofNullable(e.temperature)),
                        DensityFunction.HOLDER_HELPER_CODEC.optionalFieldOf("humidity")
                                .forGetter(e -> java.util.Optional.ofNullable(e.humidity))
                ).apply(i,(region,temperature,humidity) ->
                        new EnvironmentInputs(region.orElse(null),
                                temperature.orElse(null),humidity.orElse(null))));
    }

    private java.util.Optional<EnvironmentInputs> environmentInputs() {
        if(regionSelector==selector && temperature==null && humidity==null)
            return java.util.Optional.empty();
        return java.util.Optional.of(new EnvironmentInputs(
                regionSelector==selector?null:regionSelector,temperature,humidity));
    }

    private static TerrainArchetypeDensity fromCodec(
            DensityFunction base,DensityFunction continents,DensityFunction selector,
            java.util.Optional<EnvironmentInputs> environment,String family,
            double macroGain,double mesoGain,double microGain,double reliefGain,
            double ridgeSharpness,double cliffIndex,double valleyIndex,
            double ridgeIndex,double anisotropyGain,double duneAxisRadians,
            double dominantWavelengthM) {
        EnvironmentInputs inputs=environment.orElse(null);
        return new TerrainArchetypeDensity(base,continents,selector,
                inputs==null?null:inputs.regionSelector(),family,macroGain,
                mesoGain,microGain,reliefGain,ridgeSharpness,cliffIndex,
                valleyIndex,ridgeIndex,anisotropyGain,duneAxisRadians,
                dominantWavelengthM,inputs==null?null:inputs.temperature(),
                inputs==null?null:inputs.humidity());
    }

    public static final MapCodec<TerrainArchetypeDensity> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(TerrainArchetypeDensity::base),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents").forGetter(TerrainArchetypeDensity::continents),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("selector").forGetter(TerrainArchetypeDensity::selector),
                    EnvironmentInputs.CODEC.optionalFieldOf("environment")
                            .forGetter(TerrainArchetypeDensity::environmentInputs),
                    Codec.STRING.fieldOf("family").forGetter(TerrainArchetypeDensity::family),
                    Codec.doubleRange(0.0, 1.0).fieldOf("macro_gain").forGetter(TerrainArchetypeDensity::macroGain),
                    Codec.doubleRange(0.0, 1.0).fieldOf("meso_gain").forGetter(TerrainArchetypeDensity::mesoGain),
                    Codec.doubleRange(0.0, 1.0).fieldOf("micro_gain").forGetter(TerrainArchetypeDensity::microGain),
                    Codec.doubleRange(0.0, 1.0).fieldOf("relief_gain").forGetter(TerrainArchetypeDensity::reliefGain),
                    Codec.doubleRange(0.0, 1.0).fieldOf("ridge_sharpness").forGetter(TerrainArchetypeDensity::ridgeSharpness),
                    Codec.doubleRange(0.0, 1.0).fieldOf("cliff_index").forGetter(TerrainArchetypeDensity::cliffIndex),
                    Codec.doubleRange(0.0, 1.0).fieldOf("valley_index").forGetter(TerrainArchetypeDensity::valleyIndex),
                    Codec.doubleRange(0.0, 1.0).fieldOf("ridge_index").forGetter(TerrainArchetypeDensity::ridgeIndex),
                    Codec.doubleRange(0.5, 2.0).fieldOf("anisotropy_gain").forGetter(TerrainArchetypeDensity::anisotropyGain),
                    Codec.doubleRange(-Math.PI, Math.PI).fieldOf("dune_axis_radians").forGetter(TerrainArchetypeDensity::duneAxisRadians),
                    Codec.doubleRange(1.0, 1000000.0).fieldOf("dominant_wavelength_m").forGetter(TerrainArchetypeDensity::dominantWavelengthM)
            ).apply(i, TerrainArchetypeDensity::fromCodec));
    private static final KeyDispatchDataCodec<TerrainArchetypeDensity> DISPATCH =
            KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public double compute(DensityFunction.FunctionContext ctx) {
        if(!DensityPerfSampler.ENABLED)return computeUnprofiled(ctx);
        long started=DensityPerfSampler.begin(DensityPerfSampler.Stage.TERRAIN_ARCHETYPE);
        try{return computeUnprofiled(ctx);}
        finally{DensityPerfSampler.finish(DensityPerfSampler.Stage.TERRAIN_ARCHETYPE,started);}
    }

    private double computeUnprofiled(DensityFunction.FunctionContext ctx) {
        double original = base.compute(ctx);
        // Alpine relief is mathematically zero for all continental/selector
        // inputs at or below 0.65. Run this independent exclusion BEFORE
        // sampling continental noise, which is costly in lowland columns.
        if (alpineFamily && original <= 0.65) return original;
        double continent = continents.compute(ctx);
        // The land gate is exactly zero below this boundary. Avoid
        // unnecessary selector noise work in ocean columns.
        if (continent <= -0.10) return original;
        // Glacier and volcano kernels never consume selector input. The flags
        // are computed once per codec instance instead of comparing strings
        // for every sampled block.
        double signal = selectorFreeFamily ? 0.0 : selector.compute(ctx);
        boolean naturalAutomate = "natural_automate".equals(family);
        double regionSignal = naturalAutomate
                ? regionSelector.compute(ctx) : signal;
        double temperatureSignal = naturalAutomate && temperature != null
                ? temperature.compute(ctx) : 0.0;
        double humiditySignal = naturalAutomate && humidity != null
                ? humidity.compute(ctx) : 0.0;
        if(naturalAutomate && DensityPerfSampler.ENABLED) {
            long blendStarted=DensityPerfSampler.begin(
                    DensityPerfSampler.Stage.NATURAL_AUTOMATE_BLEND);
            try {
                return morphology.compute(original,continent,signal,regionSignal,
                        temperatureSignal,humiditySignal,ctx.blockX(),ctx.blockZ());
            } finally {
                DensityPerfSampler.finish(
                        DensityPerfSampler.Stage.NATURAL_AUTOMATE_BLEND,blendStarted);
            }
        }
        return morphology.compute(original,continent,signal,regionSignal,
                temperatureSignal,humiditySignal,ctx.blockX(),ctx.blockZ());
    }

    @Override
    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        DensityFunction mappedSelector=selector.mapAll(visitor);
        DensityFunction mappedRegionSelector=regionSelector==selector
                ?mappedSelector:regionSelector.mapAll(visitor);
        return visitor.apply(new TerrainArchetypeDensity(
                base.mapAll(visitor),continents.mapAll(visitor),mappedSelector,
                mappedRegionSelector,family,macroGain,mesoGain,microGain,
                reliefGain,ridgeSharpness,cliffIndex,valleyIndex,ridgeIndex,
                anisotropyGain,duneAxisRadians,dominantWavelengthM,
                temperature==null?null:temperature.mapAll(visitor),
                humidity==null?null:humidity.mapAll(visitor)));
    }

    @Override public double minValue() { return base.minValue() - 1.0; }
    @Override public double maxValue() { return base.maxValue() + 1.0; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return DISPATCH; }
}

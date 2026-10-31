package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Zonal geographic temperature field for a planar/equirectangular-style world map.
 *
 * Z is the sole geographic climate coordinate: negative Z is north and
 * positive Z is south. The enclosing horizontal_scale density node converts
 * Minecraft X/Z blocks to source metres at 20 metres per horizontal block.
 * The latitude field remains periodic: equator -> north pole -> opposite
 * equator -> south pole -> original equator. The optional legacy
 * prime_meridian_x field is decoded for older datapacks and ignored.
 */
public record LatitudeTemperatureDensity(
        DensityFunction base,
        int equatorZ,
        int poleDistance,
        double strength,
        boolean balanced,
        double altitudeCooling,
        boolean clampAtPoles,
        int primeMeridianX)
        implements DensityFunction.SimpleFunction {

    public static final MapCodec<LatitudeTemperatureDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base")
                .forGetter(LatitudeTemperatureDensity::base),
            Codec.intRange(-600_000_000,600_000_000).fieldOf("equator_z")
                .forGetter(LatitudeTemperatureDensity::equatorZ),
            Codec.intRange(1024,10_001_966).fieldOf("pole_distance")
                .forGetter(LatitudeTemperatureDensity::poleDistance),
            Codec.doubleRange(0.0,1.0).fieldOf("strength")
                .forGetter(LatitudeTemperatureDensity::strength),
            Codec.BOOL.optionalFieldOf("balanced",false)
                .forGetter(LatitudeTemperatureDensity::balanced),
            Codec.doubleRange(0.0,0.9).optionalFieldOf("altitude_cooling",0.0)
                .forGetter(LatitudeTemperatureDensity::altitudeCooling),
            Codec.BOOL.optionalFieldOf("clamp_at_poles",false)
                .forGetter(LatitudeTemperatureDensity::clampAtPoles),
            Codec.intRange(-29_000_000,29_000_000).optionalFieldOf("prime_meridian_x",0)
                .forGetter(LatitudeTemperatureDensity::primeMeridianX)
        ).apply(i,LatitudeTemperatureDensity::new));

    private static final KeyDispatchDataCodec<LatitudeTemperatureDensity> DISPATCH =
        KeyDispatchDataCodec.of(MAP_CODEC);

    /** Latitude in degrees at a world Z coordinate. */
    public double latitudeAtZ(int z) {
        return clampAtPoles
            ? GeoMath.clampedLatitudeDeg(z,equatorZ,poleDistance)
            : GeoMath.latitudeDeg(z,equatorZ,poleDistance);
    }

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        double latitude=latitudeAtZ(ctx.blockZ());
        double original=base.compute(ctx);
        return balanced
            ? GeoMath.balancedLatitudeTemperature(
                original,latitude,strength,altitudeCooling,ctx.blockY())
            : GeoMath.latitudeTemperature(original,latitude,strength);
    }

    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new LatitudeTemperatureDensity(
            base.mapAll(visitor),equatorZ,poleDistance,strength,
            balanced,altitudeCooling,clampAtPoles,primeMeridianX));
    }

    @Override public double minValue() {
        return base.minValue()-.68*strength;
    }

    @Override public double maxValue() {
        return balanced
            ? base.maxValue()+.35*strength+altitudeCooling*.75
            : base.maxValue()+.22*strength;
    }

    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return DISPATCH;
    }
}

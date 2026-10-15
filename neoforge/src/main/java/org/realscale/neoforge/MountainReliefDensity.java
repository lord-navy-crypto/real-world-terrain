package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Mountain-scale basin and plateau relief, applied to HORIZONTAL height
 * signals before the world's vertical density gradient is added.
 *
 * <p>Never edits sea levels, fluids, caves, Minecraft blocks or dimensions.
 * Samples a seed-resolved biome/erosion signal once per active 2048x2048
 * regional site, using a bounded per-density-worker thread-local cache.
 * Never samples another chunk's interpolated height, and never depends on
 * what neighboring chunk happened to generate first.
 */
public final class MountainReliefDensity implements DensityFunction.SimpleFunction {
    private final DensityFunction base, continents, selector;
    private final double basinDepth, plateauRise;
    private final ThreadLocal<Site> current = ThreadLocal.withInitial(Site::new);

    private static final class Site {
        int gx = Integer.MIN_VALUE, gz = Integer.MIN_VALUE, type;
        double centerX, centerZ, radius, anchorHeight;
    }

    public MountainReliefDensity(DensityFunction base, DensityFunction continents,
                                 DensityFunction selector, double basinDepth,
                                 double plateauRise) {
        this.base = base;
        this.continents = continents;
        this.selector = selector;
        this.basinDepth = basinDepth;
        this.plateauRise = plateauRise;
    }

    public DensityFunction base() { return base; }
    public DensityFunction continents() { return continents; }
    public DensityFunction selector() { return selector; }
    public double basinDepth() { return basinDepth; }
    public double plateauRise() { return plateauRise; }

    public static final MapCodec<MountainReliefDensity> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(MountainReliefDensity::base),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents").forGetter(MountainReliefDensity::continents),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("selector").forGetter(MountainReliefDensity::selector),
                    Codec.doubleRange(0.0, 1.9).fieldOf("basin_depth").forGetter(MountainReliefDensity::basinDepth),
                    Codec.doubleRange(0.0, 1.3).fieldOf("plateau_rise").forGetter(MountainReliefDensity::plateauRise)
            ).apply(i, MountainReliefDensity::new));
    private static final KeyDispatchDataCodec<MountainReliefDensity> DISPATCH =
            KeyDispatchDataCodec.of(MAP_CODEC);

    private Site site(DensityFunction.FunctionContext ctx) {
        int gx = Math.floorDiv(ctx.blockX(), MountainReliefMath.CELL);
        int gz = Math.floorDiv(ctx.blockZ(), MountainReliefMath.CELL);
        Site site = current.get();
        if (site.gx == gx && site.gz == gz) return site;
        long h = MountainReliefMath.siteHash(gx, gz);
        long x = (long)gx * MountainReliefMath.CELL
                + MountainReliefMath.CELL / 2
                + MountainReliefMath.centerOffsetX(h);
        long z = (long)gz * MountainReliefMath.CELL
                + MountainReliefMath.CELL / 2
                + MountainReliefMath.centerOffsetZ(h);
        // All legal Minecraft coordinates are in signed 30M range. The
        // cast remains within int near playable world limits.
        int centerX = Math.toIntExact(x);
        int centerZ = Math.toIntExact(z);
        var anchor = new DensityFunction.SinglePointContext(centerX, ctx.blockY(), centerZ);
        double regionalSignal = selector.compute(anchor);
        site.type = MountainReliefMath.kind(h, regionalSignal);
        site.centerX = centerX;
        site.centerZ = centerZ;
        site.radius = MountainReliefMath.radius(h);
        site.anchorHeight = site.type == MountainReliefMath.PLATEAU
                ? MountainReliefMath.plateauLevel(h, regionalSignal) : 0;
        site.gx = gx;
        site.gz = gz;
        return site;
    }

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        double original = base.compute(ctx);
        // Most coastal and lowland voxels use their original, untouched height
        // signals. Gating before site lookups avoids unnecessary sampling.
        double continent = continents.compute(ctx);
        if (MountainReliefMath.inlandUplandGate(continent, original) <= 0.0)
            return original;
        Site s = site(ctx);
        if (s.type == MountainReliefMath.NONE) return original;
        double dx = ctx.blockX() - s.centerX;
        double dz = ctx.blockZ() - s.centerZ;
        double radiusFraction = Math.hypot(dx, dz) / s.radius;
        return MountainReliefMath.relief(original, s.anchorHeight,
                continent, s.type, radiusFraction, basinDepth, plateauRise);
    }

    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new MountainReliefDensity(base.mapAll(visitor),
                continents.mapAll(visitor), selector.mapAll(visitor),
                basinDepth, plateauRise));
    }
    @Override public double minValue() { return base.minValue() - basinDepth; }
    @Override public double maxValue() {
        return base.maxValue() + Math.max(plateauRise, basinDepth * 0.15);
    }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return DISPATCH; }
}

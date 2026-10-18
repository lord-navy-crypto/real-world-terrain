package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * "Special Chongqing" is a genuine DEM-based floating LAND SHELL.
 * There are no underground caverns, fake terrain beyond the pilot DEM,
 * bedrock foundation, copied Chunky engine or random geography.
 *
 * Native noise-based chunk generation paints vanilla blocks afterward;
 * this function only determines where a 40m shell of stone exists.
 * Sea/river polygons, bridges and buildings require separate REAL GIS data.
 */
public record ChongqingFloatingDensity(int shellBlocks,int verticalOffset)
        implements DensityFunction.SimpleFunction {
    public static final MapCodec<ChongqingFloatingDensity> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.intRange(16,96).fieldOf("shell_blocks")
                            .forGetter(ChongqingFloatingDensity::shellBlocks),
                    Codec.intRange(-200,350).fieldOf("vertical_offset")
                            .forGetter(ChongqingFloatingDensity::verticalOffset)
            ).apply(i,ChongqingFloatingDensity::new));
    private static final KeyDispatchDataCodec<ChongqingFloatingDensity> DISPATCH =
            KeyDispatchDataCodec.of(MAP_CODEC);
    private static class Tile {static final ChongqingDemTile DATA=ChongqingDemTile.load();}
    private static final class LastColumn {
        int x,z;
        boolean valid;
        double height;
        double load(int x,int z) {
            if(!valid || this.x!=x || this.z!=z) {
                this.x=x;this.z=z;
                height=Tile.DATA.metresAt(x,z);
                valid=true;
            }
            return height;
        }
    }
    private static final ThreadLocal<LastColumn> COLUMN =
            ThreadLocal.withInitial(LastColumn::new);

    @Override public double compute(DensityFunction.FunctionContext context) {
        double realMetres=COLUMN.get().load(context.blockX(),context.blockZ());
        if(!Double.isFinite(realMetres))return -8.0; // No real DEM = VOID.
        double surface=realMetres+verticalOffset; // No vertical exaggeration.
        double bottom=surface-shellBlocks;
        double y=context.blockY();
        // Solid iff bottom < y < genuine source height + constant offset.
        // This explicitly removes ALL expensive subterranean geology.
        return Math.min((surface-y)/8.0,(y-bottom)/8.0);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(this);
    }
    @Override public double minValue() { return -256.0; }
    @Override public double maxValue() { return 256.0; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return DISPATCH;
    }
}

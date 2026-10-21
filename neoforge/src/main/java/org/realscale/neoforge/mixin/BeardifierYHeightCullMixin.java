package org.realscale.neoforge.mixin;

import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.lang.reflect.Method;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import org.realscale.neoforge.BeardifierYExtent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EXPERIMENTAL: installed only when the mixin config plugin sees
 * -Drealscale.experimental.beardifier.ycull=true at JVM launch.
 * The default and production main branch have NO such mixin.
 */
@Mixin(value=Beardifier.class,remap=false)
public abstract class BeardifierYHeightCullMixin {
    @Shadow @Final protected ObjectListIterator<?> pieceIterator;
    @Shadow @Final protected ObjectListIterator<JigsawJunction> junctionIterator;
    @Unique private BeardifierYExtent realscale$extent;

    @Inject(method="<init>",at=@At("TAIL"),remap=false)
    private void realscale$bounds(ObjectListIterator<?> pieces,
            ObjectListIterator<JigsawJunction> junctions,CallbackInfo ci) {
        BeardifierYExtent envelope=new BeardifierYExtent();
        realscale$extent=envelope;
        // A nonzero initial position indicates a foreign/custom iterator.
        // Never risk consuming or reordering it.
        if(pieceIterator.nextIndex()!=0 || junctionIterator.nextIndex()!=0) {
            envelope.disable();
            return;
        }
        try {
            int entries=0;
            while(pieceIterator.hasNext()) {
                if(++entries>4096){envelope.disable();break;}
                Object rigid=pieceIterator.next();
                // Rigid is a private vanilla record. Resolve its accessors
                // ONCE per chunk, never in the millions of compute() calls.
                Class<?> type=rigid.getClass();
                Method boxMethod=null,deltaMethod=null;
                for(Method method:type.getDeclaredMethods()) {
                    if(method.getParameterCount()!=0)continue;
                    if(method.getReturnType()==BoundingBox.class)boxMethod=method;
                    else if(method.getName().toLowerCase().contains("groundleveldelta")
                            && method.getReturnType()==int.class)
                        deltaMethod=method;
                }
                if(boxMethod==null||deltaMethod==null
                        || !boxMethod.trySetAccessible()
                        || !deltaMethod.trySetAccessible()) {
                    envelope.disable();break;
                }
                BoundingBox box=(BoundingBox)boxMethod.invoke(rigid);
                int delta=(Integer)deltaMethod.invoke(rigid);
                envelope.includePiece(box.minY(),box.maxY(),delta);
            }
            entries=0;
            while(junctionIterator.hasNext()) {
                if(++entries>4096){envelope.disable();break;}
                envelope.includeJunction(junctionIterator.next().getSourceGroundY());
            }
        } catch(ReflectiveOperationException | RuntimeException error) {
            envelope.disable(); // Compatibility fallback: vanilla everywhere.
        } finally {
            pieceIterator.back(Integer.MAX_VALUE);
            junctionIterator.back(Integer.MAX_VALUE);
        }
    }

    @Inject(method="compute",at=@At("HEAD"),cancellable=true,remap=false)
    private void realscale$skipUnaffectedY(DensityFunction.FunctionContext ctx,
            CallbackInfoReturnable<Double> cir) {
        BeardifierYExtent e=realscale$extent;
        if(e!=null && e.canSkip(ctx.blockY()))cir.setReturnValue(0.0);
    }
}

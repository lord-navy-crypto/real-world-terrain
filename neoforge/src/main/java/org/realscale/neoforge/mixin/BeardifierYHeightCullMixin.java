package org.realscale.neoforge.mixin;

import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.lang.reflect.Method;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import org.realscale.neoforge.BeardifierYExtent;
import org.realscale.neoforge.BeardifierXZExtent;
import org.realscale.neoforge.BeardifierDirectParity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Experimental opt-in vanilla Beardifier Y cull with OPTIONAL new XZ extent.
 * The main branch is unchanged; neither flag defaults to true.
 */
@Mixin(value=Beardifier.class,remap=false)
public abstract class BeardifierYHeightCullMixin {
    @Shadow @Final protected ObjectListIterator<?> pieceIterator;
    @Shadow @Final protected ObjectListIterator<JigsawJunction> junctionIterator;
    @Unique private static final boolean realscale$XZ_ENABLED =
            Boolean.getBoolean("realscale.experimental.beardifier.xzcull");
    @Unique private BeardifierYExtent realscale$extent;
    @Unique private BeardifierXZExtent realscale$horizontal;

    @Inject(method="<init>",at=@At("TAIL"),remap=false)
    private void realscale$bounds(ObjectListIterator<?> pieces,
            ObjectListIterator<JigsawJunction> junctions,CallbackInfo ci) {
        BeardifierYExtent envelope=new BeardifierYExtent();
        BeardifierXZExtent horizontal=new BeardifierXZExtent();
        realscale$extent=envelope;
        realscale$horizontal=horizontal;
        if(pieceIterator.nextIndex()!=0 || junctionIterator.nextIndex()!=0){
            envelope.disable();horizontal.disable();return;
        }
        try {
            int entries=0;
            while(pieceIterator.hasNext()){
                if(++entries>4096){envelope.disable();horizontal.disable();break;}
                Object rigid=pieceIterator.next();
                Class<?> type=rigid.getClass();
                Method boxMethod=null,deltaMethod=null;
                for(Method m:type.getDeclaredMethods()){
                    if(m.getParameterCount()!=0)continue;
                    if(m.getReturnType()==BoundingBox.class)boxMethod=m;
                    else if(m.getName().toLowerCase().contains("groundleveldelta")
                            && m.getReturnType()==int.class)deltaMethod=m;
                }
                if(boxMethod==null||deltaMethod==null||
                        !boxMethod.trySetAccessible()||!deltaMethod.trySetAccessible()){
                    envelope.disable();horizontal.disable();break;
                }
                BoundingBox box=(BoundingBox)boxMethod.invoke(rigid);
                int delta=(Integer)deltaMethod.invoke(rigid);
                envelope.includePiece(box.minY(),box.maxY(),delta);
                horizontal.includePiece(box.minX(),box.maxX(),
                        box.minZ(),box.maxZ());
            }
            entries=0;
            while(junctionIterator.hasNext()){
                if(++entries>4096){envelope.disable();horizontal.disable();break;}
                JigsawJunction j=junctionIterator.next();
                envelope.includeJunction(j.getSourceGroundY());
                horizontal.includeJunction(j.getSourceX(),j.getSourceZ());
            }
        }catch(ReflectiveOperationException|RuntimeException ex){
            envelope.disable();horizontal.disable();
        }finally{
            pieceIterator.back(Integer.MAX_VALUE);
            junctionIterator.back(Integer.MAX_VALUE);
        }
    }

    @Inject(method="compute",at=@At("HEAD"),cancellable=true,remap=false)
    private void realscale$skipUnaffected(DensityFunction.FunctionContext ctx,
            CallbackInfoReturnable<Double> cir){
        if(BeardifierDirectParity.replaying())return;
        BeardifierYExtent e=realscale$extent;
        boolean outsideY=e!=null&&e.canSkip(ctx.blockY());
        BeardifierXZExtent h=realscale$horizontal;
        boolean outsideXZ=!outsideY&&realscale$XZ_ENABLED&&h!=null
                &&h.canSkip(ctx.blockX(),ctx.blockZ());
        if(outsideY||outsideXZ){
            if(BeardifierDirectParity.shouldVerifySkip(outsideXZ)){
                BeardifierDirectParity.setReplaying(true);
                double vanilla;
                try{
                    vanilla=((Beardifier)(Object)this).compute(ctx);
                }finally{
                    BeardifierDirectParity.setReplaying(false);
                }
                BeardifierDirectParity.assertZero(vanilla,
                        ctx.blockX(),ctx.blockY(),ctx.blockZ(),outsideXZ);
            }
            cir.setReturnValue(0.0);
        }
    }
}

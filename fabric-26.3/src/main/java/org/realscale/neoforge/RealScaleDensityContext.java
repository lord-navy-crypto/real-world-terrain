package org.realscale.neoforge;
import java.util.Map;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
final class RealScaleDensityContext {
 private final SamplerContext context; private final int x,y,z; private final Map<DensityFunction,DensitySampler> samplers;
 RealScaleDensityContext(SamplerContext context,int x,int y,int z,Map<DensityFunction,DensitySampler> samplers){this.context=context;this.x=x;this.y=y;this.z=z;this.samplers=samplers;}
 int blockX(){return x;} int blockY(){return y;} int blockZ(){return z;}
 double compute(DensityFunction function){DensitySampler sampler=samplers.get(function);if(sampler==null)throw new IllegalStateException("Uncompiled RealScale density child");return sampler.sampleValue(context,x,y,z);}
 RealScaleDensityContext at(int x,int y,int z){return new RealScaleDensityContext(context,x,y,z,samplers);}
}
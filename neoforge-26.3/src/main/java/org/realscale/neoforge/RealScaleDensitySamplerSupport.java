package org.realscale.neoforge;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
/** 26.3 bridge: compile children once per dimension; naive volume fill is a correctness baseline pending batching. */
final class RealScaleDensitySamplerSupport {
 @FunctionalInterface interface SampleFunction { double sample(RealScaleDensityContext context); }
 private RealScaleDensitySamplerSupport(){}
 static DensitySampler compile(DensityFunction.CompileContext compileContext,DensityFunction[] children,SampleFunction function){
  Map<DensityFunction,DensitySampler> compiled=new IdentityHashMap<>();
  for(DensityFunction child:children)if(child!=null&&!compiled.containsKey(child))compiled.put(child,child.compileSampler(compileContext));
  return new DensitySampler(){
   @Override public void sampleVolume(SamplerContext context,DensityBuffer outputBuffer,DensityVolume volume){DensitySampler.sampleVolumeNaive(context,outputBuffer,volume,this);}
   @Override public float sampleValue(SamplerContext context,int blockX,int blockY,int blockZ){return (float)function.sample(new RealScaleDensityContext(context,blockX,blockY,blockZ,compiled));}
  };
 }
}
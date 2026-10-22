package org.realscale.neoforge.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Profile/cache Mixin disabled unless user enables a named JVM opt-in. */
public final class ExperimentalPerlinMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage){}
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String target,String mixin){
        boolean enabled=mixin.endsWith(".PerlinExactMemoMixin")&&
                Boolean.getBoolean("realscale.experimental.perlin.instrument");
        System.err.println("REALSCALE_PERLIN_EXACT_MIXIN_DECISION "+
                mixin+" enabled="+enabled);
        return enabled;
    }
    @Override public void acceptTargets(Set<String> mine,Set<String> other){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String target,ClassNode node,String mixin,
            IMixinInfo info){}
    @Override public void postApply(String target,ClassNode node,String mixin,
            IMixinInfo info){}
}

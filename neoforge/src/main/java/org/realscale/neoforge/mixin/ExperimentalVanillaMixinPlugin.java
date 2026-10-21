package org.realscale.neoforge.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Opt-in at process startup; disabled means the mixin is NOT installed. */
public final class ExperimentalVanillaMixinPlugin implements IMixinConfigPlugin {
    private static final String FLAG="realscale.experimental.beardifier.ycull";
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String target,String mixin) {
        return mixin.endsWith(".BeardifierYHeightCullMixin")
                && Boolean.getBoolean(FLAG);
    }
    @Override public void acceptTargets(Set<String> mine,Set<String> other) {}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String target,ClassNode node,String mixin,
            IMixinInfo info) {}
    @Override public void postApply(String target,ClassNode node,String mixin,
            IMixinInfo info) {}
}

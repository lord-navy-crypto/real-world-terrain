package org.realscale.neoforge.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class AquiferReadOnlyAuditMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String p){}
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String target,String mixin){
        boolean enabled=mixin.endsWith(".AquiferReadOnlyAuditMixin")
                &&Boolean.parseBoolean(System.getProperty("realscale.experimental.aquifer.audit","true"));
        System.err.println("REALSCALE_AQUIFER_READONLY_MIXIN_DECISION "+
                mixin+" enabled="+enabled);
        return enabled;
    }
    @Override public void acceptTargets(Set<String> own,Set<String> others){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String target,ClassNode clazz,
            String mixin,IMixinInfo info){}
    @Override public void postApply(String target,ClassNode clazz,
            String mixin,IMixinInfo info){}
}

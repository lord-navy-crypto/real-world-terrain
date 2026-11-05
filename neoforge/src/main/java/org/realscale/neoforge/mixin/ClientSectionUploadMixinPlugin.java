package org.realscale.neoforge.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** TEST-only client upload override defaults OFF for DH/Sodium/Iris compatibility. */
public final class ClientSectionUploadMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage){}
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String target,String mixin){
        boolean enabled=mixin.endsWith(".VanillaSectionUploadBudgetMixin")
                &&Boolean.parseBoolean(System.getProperty("realscale.experimental.client.uploadbudget","false"));
        System.err.println("REALSCALE_CLIENT_UPLOAD_MIXIN_DECISION "+mixin+
                " enabled="+enabled);
        return enabled;
    }
    @Override public void acceptTargets(Set<String> mine,Set<String> other){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}

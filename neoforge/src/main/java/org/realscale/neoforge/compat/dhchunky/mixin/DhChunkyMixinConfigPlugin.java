package org.realscale.neoforge.compat.dhchunky.mixin;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.neoforged.fml.ModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Apply the DH/Chunky lifecycle patch only to the exact pair it was tested
 * against. Other versions, or either mod being absent, leave RealScale alone.
 */
public final class DhChunkyMixinConfigPlugin implements IMixinConfigPlugin {
    private static final AtomicBoolean realscale$reportedSkip = new AtomicBoolean();

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            boolean supported = hasVersion("distanthorizons", "3.3.3")
                    && hasVersion("chunky", "1.4.23");
            if (!supported && realscale$reportedSkip.compareAndSet(false, true)) {
                System.err.println(
                    "REALSCALE_DH_CHUNKY_GUARD_SKIPPED: embedded guard targets only "
                    + "Distant Horizons 3.3.3 + Chunky 1.4.23; RealScale continues normally");
            }
            return supported;
        } catch (RuntimeException ex) {
            if (realscale$reportedSkip.compareAndSet(false, true)) {
                System.err.println(
                    "REALSCALE_DH_CHUNKY_GUARD_SKIPPED: optional mod versions could not be "
                    + "checked safely; RealScale continues without the guard");
            }
            return false;
        }
    }

    private static boolean hasVersion(String modId, String expectedPrefix) {
        return ModList.get().getModContainerById(modId)
            .map(container -> {
                String actual = container.getModInfo().getVersion().toString();
                return actual.equals(expectedPrefix)
                    || actual.startsWith(expectedPrefix + "-")
                    || actual.startsWith(expectedPrefix + "+")
                    || actual.startsWith(expectedPrefix + ".");
            })
            .orElse(false);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(
            String targetClassName, ClassNode targetClass,
            String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(
            String targetClassName, ClassNode targetClass,
            String mixinClassName, IMixinInfo mixinInfo) {}
}

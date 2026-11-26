package org.realscale.neoforge.compat.dhchunky.mixin;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Apply the DH/Chunky lifecycle patch only to the exact pair it was tested
 * against. Mixin config plugins run before ModLoader constructs ModList, so
 * use FMLLoader's already-discovered LoadingModList instead of the later runtime registry.
 */
public final class DhChunkyMixinConfigPlugin implements IMixinConfigPlugin {
    private static final AtomicBoolean realscale$reportedSkip = new AtomicBoolean();

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean supported = hasVersion("distanthorizons", "3.3.3")
                && hasVersion("chunky", "1.4.23");
        if (!supported && realscale$reportedSkip.compareAndSet(false, true)) {
            System.err.println(
                "REALSCALE_DH_CHUNKY_GUARD_SKIPPED: embedded guard targets only "
                + "Distant Horizons 3.3.3 + Chunky 1.4.23; RealScale continues normally");
        }
        return supported;
    }

    private static boolean hasVersion(String modId, String expectedPrefix) {
        LoadingModList loadingModList = FMLLoader.getLoadingModList();
        if (loadingModList == null) {
            throw new IllegalStateException(
                "NeoForge LoadingModList is unavailable during DH-Chunky Mixin selection");
        }

        return loadingModList.getMods().stream()
            .filter(modInfo -> modId.equals(modInfo.getModId()))
            .map(ModInfo::getVersion)
            .map(Object::toString)
            .anyMatch(actual -> actual.equals(expectedPrefix)
                || actual.startsWith(expectedPrefix + "-")
                || actual.startsWith(expectedPrefix + "+")
                || actual.startsWith(expectedPrefix + "."));
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

package org.realscale.compat.dhchunky;

import net.neoforged.fml.common.Mod;

/**
 * Independent compatibility addon. Never modifies RealScale world generation.
 * Requires the exact DH/Chunky versions in neoforge.mods.toml.
 */
@Mod("dhchunkyguard")
public final class DhChunkyGuardMod {
    public DhChunkyGuardMod() {
        System.err.println("REALSCALE_DH_CHUNKY_GUARD_MOD_LOADED (experimental)");
    }
}

package org.realscale.neoforge.lotm;

import net.minecraft.world.entity.player.Player;

/** The four inaugural Sequence 9 pathways. IDs are deliberately stable for saved worlds. */
public enum LotmPath {
    SEER("seer"), MYSTERY_PRYER("mystery_pryer"), SLEEPLESS("sleepless"), CORPSE_COLLECTOR("corpse_collector");
    public static final String NBT_PATH = "realscale_lotm_path";
    public static final String NBT_SEQUENCE = "realscale_lotm_sequence";
    private final String id;
    LotmPath(String id) { this.id = id; }
    public String id() { return id; }
    public static LotmPath of(Player player) {
        String value = player.getPersistentData().getString(NBT_PATH);
        for (LotmPath path : values()) if (path.id.equals(value)) return path;
        return null;
    }
}

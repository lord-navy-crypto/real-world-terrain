package org.realscale.neoforge.lotm;

/** Pure, deterministic limits shared by game rules and JUnit smoke tests. */
public final class LotmBalance {
    public static final int MAX_SPIRIT = 100;
    public static final int MAX_PRACTICE = 100;
    private LotmBalance() {}

    public static int clamp(int value, int maximum) {
        return Math.max(0, Math.min(maximum, value));
    }

    public static int regenerate(int current, int amount) {
        return clamp(current + Math.max(amount, 0), MAX_SPIRIT);
    }

    public static int practice(int current, int amount) {
        return clamp(current + Math.max(amount, 0), MAX_PRACTICE);
    }

    public static int focusCost(String pathway) {
        return switch (pathway) {
            case "seer" -> 14;
            case "mystery_pryer" -> 18;
            case "sleepless" -> 10;
            case "corpse_collector" -> 12;
            default -> MAX_SPIRIT;
        };
    }

    public static final int DIVINATION_COST = 18;

    /** Omen orientation is deliberately coarse: no omniscience at Sequence 9. */
    public static String bearing(int dx, int dz, int dy) {
        if (Math.abs(dy) > 5 && Math.abs(dy) > Math.max(Math.abs(dx), Math.abs(dz))) {
            return dy > 0 ? "up" : "down";
        }
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? "east" : "west";
        if (dz != 0) return dz > 0 ? "south" : "north";
        return "near";
    }
}

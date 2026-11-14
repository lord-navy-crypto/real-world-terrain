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

    /** Server-validated costs for the two configurable pathway skill keys. */
    public static int skillCost(String pathway, int slot, int sequence) {
        return skillCost(pathway, slot, sequence, false);
    }

    /** The Pendulum specializes Seer divination; it never discounts other pathways. */
    public static int skillCost(String pathway, int slot, int sequence, boolean hasPendulum) {
        if (sequence < 8 || sequence > 9) return MAX_SPIRIT;
        int base = switch (slot) {
            case 1 -> focusCost(pathway);
            case 2 -> switch (pathway) {
                case "seer" -> DIVINATION_COST;
                case "mystery_pryer" -> 16;
                case "sleepless" -> 12;
                case "corpse_collector" -> 14;
                default -> MAX_SPIRIT;
            };
            default -> MAX_SPIRIT;
        };
        if (base >= MAX_SPIRIT) return MAX_SPIRIT;
        int rankCost = base + (9 - sequence) * (slot == 1 ? 5 : 4);
        int discount = hasPendulum && "seer".equals(pathway) && slot == 2 ? 4 : 0;
        return Math.max(1, rankCost - discount);
    }

    /** A worn Spirit Focus adds one point to each normal recovery interval. */
    public static int spiritRecovery(int configuredAmount, boolean hasSpiritFocus) {
        return Math.max(0, configuredAmount) + (hasSpiritFocus ? 1 : 0);
    }

    /** The Pendulum modestly extends the Seer's coarse local omen survey. */
    public static int divinationRadius(int sequence, boolean hasPendulum) {
        if (sequence < 8 || sequence > 9) return 0;
        return (sequence == 8 ? 12 : 10) + (hasPendulum ? 2 : 0);
    }

    public static int skillCooldownTicks(int slot, int sequence) {
        if (sequence < 8 || sequence > 9) return Integer.MAX_VALUE;
        return switch (slot) {
            case 1 -> sequence == 8 ? 135 : 160;
            case 2 -> sequence == 8 ? 180 : 220;
            default -> Integer.MAX_VALUE;
        };
    }

    /** Only Sequence 9 and 8 recipes are active in this release. */
    public static int alchemyTicks(int sequence, int sequence9, int sequence8) {
        return switch (sequence) {
            case 9 -> Math.max(1, sequence9);
            case 8 -> Math.max(1, sequence8);
            default -> 0;
        };
    }

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

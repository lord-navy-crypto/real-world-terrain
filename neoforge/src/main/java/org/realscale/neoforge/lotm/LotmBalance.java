package org.realscale.neoforge.lotm;

/** Pure, deterministic limits shared by game rules and JUnit smoke tests. */
public final class LotmBalance {
    public static final int MAX_SPIRIT = 100;
    public static final int MAX_PRACTICE = 100;
    /** Persistent sanity ceiling; gameplay surcharge saturates much earlier. */
    public static final int MAX_INSTABILITY = 100;
    private LotmBalance() {}

    public static int clamp(int value, int maximum) {
        return Math.max(0, Math.min(maximum, value));
    }

    private static int boundedAdd(int current, int amount, int maximum) {
        long total = (long) Math.max(0, current) + Math.max(0, amount);
        return (int) Math.min(Math.max(0, maximum), total);
    }

    public static int regenerate(int current, int amount) {
        return boundedAdd(current, amount, MAX_SPIRIT);
    }

    public static int practice(int current, int amount) {
        return boundedAdd(current, amount, MAX_PRACTICE);
    }

    /** Normalize old or malformed persistent values before they reach gameplay. */
    public static int clampInstability(int value) {
        return clamp(value, MAX_INSTABILITY);
    }

    /** Saturating addition avoids integer overflow in long-lived or edited saves. */
    public static int addInstability(int current, int amount) {
        long total = (long) clampInstability(current) + Math.max(0, amount);
        return (int) Math.min(MAX_INSTABILITY, total);
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
        return skillCost(pathway, slot, sequence, hasPendulum, 0);
    }

    /** Direct-characteristic instability raises the cost of exerting pathway abilities. */
    public static int skillCost(String pathway, int slot, int sequence,
            boolean hasPendulum, int instability) {
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
        return Math.max(1, rankCost - discount + instabilitySurcharge(instability));
    }

    /** One direct characteristic leaves a mild trace; the surcharge is capped and reversible. */
    public static int instabilitySurcharge(int instability) {
        return Math.min(8, clampInstability(instability) * 2);
    }

    /** A worn Spirit Focus adds one point to each normal recovery interval. */
    public static int spiritRecovery(int configuredAmount, boolean hasSpiritFocus) {
        long recovery = (long) Math.max(0, configuredAmount) + (hasSpiritFocus ? 1 : 0);
        return (int) Math.min(Integer.MAX_VALUE, recovery);
    }

    /** The Pendulum modestly extends the Seer's coarse local omen survey. */
    public static int divinationRadius(int sequence, boolean hasPendulum) {
        if (sequence < 8 || sequence > 9) return 0;
        return (sequence == 8 ? 12 : 10) + (hasPendulum ? 2 : 0);
    }

    /** Skill manuals improve cost efficiency without bypassing sequence or pathway checks. */
    public static int clampSkillMastery(int mastery) {
        return clamp(mastery, 2);
    }

    public static int skillCostWithMastery(int baseCost, int mastery, int costPercent) {
        int discount = Math.min(2, clampSkillMastery(mastery));
        int discounted = Math.max(1, Math.max(1, baseCost) - discount);
        int percent = costPercent <= 0 ? 100 : Math.max(25, Math.min(200, costPercent));
        return Math.max(1, (discounted * percent + 99) / 100);
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

    /**
     * Output safety for the alchemy machine. Invalid or overstacked recipe results
     * are rejected even when the output slot is empty; long arithmetic prevents overflow.
     */
    public static boolean canFitAlchemyOutput(boolean outputEmpty, boolean sameItemAndComponents,
            int outputCount, int outputMax, int resultCount, int resultMax) {
        if (resultCount <= 0 || resultMax <= 0 || resultCount > resultMax) return false;
        if (outputEmpty) return true;
        if (!sameItemAndComponents || outputCount <= 0 || outputMax <= 0) return false;
        long combined = (long) outputCount + resultCount;
        return combined <= Math.min(outputMax, resultMax);
    }

    /** Omen orientation is deliberately coarse: no omniscience at Sequence 9. */
    public static String bearing(int dx, int dz, int dy) {
        // Cast before abs: Math.abs(Integer.MIN_VALUE) would remain negative.
        long horizontalX = Math.abs((long) dx);
        long horizontalZ = Math.abs((long) dz);
        long vertical = Math.abs((long) dy);
        if (vertical > 5 && vertical > Math.max(horizontalX, horizontalZ)) {
            return dy > 0 ? "up" : "down";
        }
        if (horizontalX > horizontalZ) return dx > 0 ? "east" : "west";
        if (dz != 0) return dz > 0 ? "south" : "north";
        return "near";
    }
}

package org.realscale.neoforge.lotm;

/** Pure balance rules for the separate outer-deity boon progression. */
public final class LotmBoonBalance {
    public static final int MAX_TAINT = 100;
    public static final int ACTIVE_TAINT_LIMIT = 96;
    public static final int UPGRADE_TAINT_LIMIT = 40;
    public static final int UPGRADE_DISCOVERIES_REQUIRED = 3;
    public static final int UPGRADE_MIN_SANITY = 50;
    public static final int INSIGHT_COOLDOWN_TICKS = 200;

    private LotmBoonBalance() {}

    public static int clampTaint(int value) {
        return Math.max(0, Math.min(MAX_TAINT, value));
    }

    public static int addTaint(int current, int amount) {
        long next = (long) clampTaint(current) + Math.max(0, amount);
        return (int) Math.min(MAX_TAINT, next);
    }

    public static boolean canActivate(int sequence, int taint, boolean revoked, int sanity) {
        return (sequence == 9 || sequence == 8)
                && !revoked
                && clampTaint(taint) < ACTIVE_TAINT_LIMIT
                && LotmBalance.clampSanity(sanity) >= 10;
    }

    public static int insightTaintCost(int sequence) {
        return sequence == 8 ? 8 : sequence == 9 ? 5 : 0;
    }

    public static int insightSanityCost(int sequence) {
        return sequence == 8 ? 3 : sequence == 9 ? 2 : 0;
    }

    public static boolean canAdvanceToSequence8(int discoveries, int sanity, int taint) {
        return discoveries >= UPGRADE_DISCOVERIES_REQUIRED
                && LotmBalance.clampSanity(sanity) >= UPGRADE_MIN_SANITY
                && clampTaint(taint) <= UPGRADE_TAINT_LIMIT;
    }
}

package org.realscale.neoforge.lotm;

/** Server-authoritative per-sequence chance for signature occult materials. */
public final class LotmDropBalance {
    private LotmDropBalance() {}

    public static int effectiveChance(int configuredPercent, int creatureSequence) {
        int base = Math.max(0, Math.min(100, configuredPercent));
        if (creatureSequence == 8) {
            // S8 signatures are less common without making progression depend on huge grinds.
            return (base * 65 + 50) / 100;
        }
        return base;
    }

    public static boolean roll(int configuredPercent, int creatureSequence, int randomValue) {
        return randomValue >= 0 && randomValue < 100
                && randomValue < effectiveChance(configuredPercent, creatureSequence);
    }
}

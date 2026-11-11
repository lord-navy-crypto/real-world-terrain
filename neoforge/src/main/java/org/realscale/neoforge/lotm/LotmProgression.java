package org.realscale.neoforge.lotm;

/** Pure rules shared by potion/characteristic items and deterministic tests. */
public final class LotmProgression {
    private LotmProgression() {}

    public static String validate(String currentPath, int currentSequence,
            String targetPath, int targetSequence, int practice,
            boolean characteristic, int potionThreshold, int characteristicThreshold) {
        if (targetSequence < 7 || targetSequence > 9) return "outside_scope";
        if (currentPath == null || currentPath.isEmpty()) {
            return targetSequence == 9 ? "ready" : "sequence";
        }
        if (!currentPath.equals(targetPath)) return "path";
        if (currentSequence <= 7) return "outside_scope";
        if (targetSequence != currentSequence - 1) return "sequence";
        int required = characteristic ? characteristicThreshold : potionThreshold;
        return practice >= required ? "ready" : "digestion";
    }

    public static int nextSequence(int current) { return Math.max(7, current - 1); }
}

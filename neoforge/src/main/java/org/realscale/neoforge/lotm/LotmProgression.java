package org.realscale.neoforge.lotm;

/** Pure progression rules for the currently supported Sequence 9 -> 8 scope. */
public final class LotmProgression {
    private LotmProgression() {}
    public static String validate(String currentPath, int currentSequence,
            String targetPath, int targetSequence, int practice,
            boolean characteristic, int potionThreshold, int characteristicThreshold) {
        if (targetSequence < 8 || targetSequence > 9) return "outside_scope";
        if (currentPath == null || currentPath.isEmpty())
            return targetSequence == 9 ? "ready" : "sequence";
        if (!currentPath.equals(targetPath)) return "path";
        if (currentSequence <= 8 || currentSequence > 9) return "outside_scope";
        if (targetSequence != currentSequence - 1) return "sequence";
        int required = characteristic ? characteristicThreshold : potionThreshold;
        return practice >= required ? "ready" : "digestion";
    }
    public static int nextSequence(int current) { return Math.max(8, current - 1); }
}

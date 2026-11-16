package org.realscale.neoforge.lotm;

/**
 * Small, deterministic one-to-one matcher for up to four alchemy inputs.
 * Rows are occupied slots; columns are recipe ingredients.
 */
final class LotmIngredientMatching {
    private LotmIngredientMatching() {}

    static boolean canMatch(boolean[][] compatible) {
        if (compatible == null || compatible.length < 1 || compatible.length > 4) return false;
        int size = compatible.length;
        for (boolean[] row : compatible) {
            if (row == null || row.length != size) return false;
        }
        return assign(compatible, 0, 0);
    }

    private static boolean assign(boolean[][] compatible, int slot, int usedMask) {
        if (slot == compatible.length) return true;
        for (int ingredient = 0; ingredient < compatible.length; ingredient++) {
            int bit = 1 << ingredient;
            if ((usedMask & bit) == 0 && compatible[slot][ingredient]
                    && assign(compatible, slot + 1, usedMask | bit)) return true;
        }
        return false;
    }
}

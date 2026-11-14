package org.realscale.neoforge.lotm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LotmIngredientMatchingTest {
    @Test
    void solvesOverlappingBroadAndSpecificIngredientsWithoutDependingOnSlotOrder() {
        assertTrue(LotmIngredientMatching.canMatch(new boolean[][] {
                {true, true},
                {true, false}
        }));
        assertTrue(LotmIngredientMatching.canMatch(new boolean[][] {
                {true, false},
                {true, true}
        }));
    }

    @Test
    void preservesDuplicateIngredientMultiplicity() {
        assertFalse(LotmIngredientMatching.canMatch(new boolean[][] {
                {true, false},
                {true, false}
        }));
        assertTrue(LotmIngredientMatching.canMatch(new boolean[][] {
                {true, true},
                {true, true}
        }));
    }

    @Test
    void rejectsImpossibleOrMalformedAssignments() {
        assertFalse(LotmIngredientMatching.canMatch(null));
        assertFalse(LotmIngredientMatching.canMatch(new boolean[0][]));
        assertFalse(LotmIngredientMatching.canMatch(new boolean[][] {{true, false}}));
        assertFalse(LotmIngredientMatching.canMatch(new boolean[][] {{true}, {false}}));
        assertFalse(LotmIngredientMatching.canMatch(new boolean[5][5]));
    }

    @Test
    void handlesFourIngredientRecipesAndBacktracking() {
        assertTrue(LotmIngredientMatching.canMatch(new boolean[][] {
                {true, true, false, false},
                {true, false, false, false},
                {false, true, true, false},
                {false, false, true, true}
        }));
        assertFalse(LotmIngredientMatching.canMatch(new boolean[][] {
                {true, false, false, false},
                {true, false, false, false},
                {false, false, true, false},
                {false, false, false, true}
        }));
    }
}

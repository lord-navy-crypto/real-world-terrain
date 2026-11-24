package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ClipmapMorphMathTest {
    @Test
    void everyOuterEdgeVertexMatchesCoarseInterpolation() {
        for (int cell = 0; cell <= 128; cell++) {
            assertEquals(1.0, ClipmapMorphMath.edgeWeight(0, cell, 128, 8));
            assertEquals(1.0, ClipmapMorphMath.edgeWeight(128, cell, 128, 8));
            assertEquals(1.0, ClipmapMorphMath.edgeWeight(cell, 0, 128, 8));
            assertEquals(1.0, ClipmapMorphMath.edgeWeight(cell, 128, 128, 8));
        }
        double parent = ClipmapMorphMath.parentHeight(0, 1, 10, 25, 30, 55);
        assertEquals(20.0, parent);
        assertEquals(parent, ClipmapMorphMath.morphHeight(200, parent, 1));
    }

    @Test
    void coarseLatticeHeightsAreExactAndBlendDiesInside() {
        assertEquals(10.0, ClipmapMorphMath.parentHeight(0, 0, 10, 25, 30, 55));
        assertEquals(25.0, ClipmapMorphMath.parentHeight(1, 0, 10, 25, 30, 55) * 2 - 10);
        assertEquals(30.0, ClipmapMorphMath.parentHeight(0, 1, 10, 25, 30, 55) * 2 - 10);
        assertEquals(27.5, ClipmapMorphMath.parentHeight(1, 1, 10, 25, 30, 55));
        assertEquals(0.5, ClipmapMorphMath.edgeWeight(4, 64, 128, 8));
        assertEquals(0.0, ClipmapMorphMath.edgeWeight(8, 64, 128, 8));
        assertEquals(15.0, ClipmapMorphMath.morphHeight(10, 20, 0.5));
    }

    @Test
    void parentInterpolationMatchesTheRingMeshDiagonal() {
        assertEquals(27.5, ClipmapMorphMath.parentHeight(1, 1, 10, 25, 30, 55));
        assertEquals(20.0, ClipmapMorphMath.parentHeight(0, 1, 10, 25, 30, 55));
        assertEquals(17.5, ClipmapMorphMath.parentHeight(1, 0, 10, 25, 30, 55));
    }

    @Test
    void badMorphInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapMorphMath.edgeWeight(0, 0, 128, 64));
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapMorphMath.morphHeight(Double.NaN, 3, 0.5));
    }
}

package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ClipmapRingMeshTest {
    @Test
    void surfaceAndSkirtShareTheSameGridCoordinates() {
        float[] vertices = ClipmapRingMesh.vertices(128);
        int base = 129 * 129;
        assertEquals(base * 2 * 3, vertices.length);
        assertEquals(0f, vertices[2]);
        assertEquals(0f, vertices[base * 3]);
        assertEquals(0f, vertices[base * 3 + 1]);
        assertEquals(1f, vertices[base * 3 + 2]);
        assertEquals(128f, vertices[(base - 1) * 3]);
        assertEquals(128f, vertices[(base - 1) * 3 + 1]);
    }

    @Test
    void closesBothPerimetersWithoutAddingGeometryInsideTheHole() {
        int cells = 128;
        int innerHalf = 4;
        int holeWidth = innerHalf * 2;
        int quads = cells * cells - holeWidth * holeWidth;
        int skirts = 4 * cells + 4 * holeWidth;
        int base = (cells + 1) * (cells + 1);
        int[] indices = ClipmapRingMesh.indices(cells, innerHalf);
        assertEquals(6 * (quads + skirts), indices.length);
        for (int i = 0; i < quads * 6; i++) {
            assertTrue(indices[i] >= 0 && indices[i] < base);
        }
        for (int i = quads * 6; i < indices.length; i += 6) {
            assertTrue(indices[i] < base);
            assertTrue(indices[i + 1] < base);
            assertEquals(indices[i] + base, indices[i + 2]);
            assertEquals(indices[i + 1] + base, indices[i + 4]);
        }
        for (int index : indices) assertTrue(index >= 0 && index < base * 2);
    }

    @Test
    void coarserLevelsAlsoHaveInnerAndOuterSkirts() {
        int[] indices = ClipmapRingMesh.indices(128, 32);
        assertEquals(6 * (128 * 128 - 64 * 64 + 4 * 128 + 4 * 64),
                indices.length);
    }

    @Test
    void irisSurfaceTopologyHasNoArtificialSkirtWallsAndSupportsZeroHole() {
        int cells = 256;
        int innerHalf = 64;
        int quads = cells * cells - (innerHalf * 2) * (innerHalf * 2);
        int[] indices = ClipmapRingMesh.surfaceIndices(cells, innerHalf);
        assertEquals(6 * quads, indices.length);
        int base = (cells + 1) * (cells + 1);
        for (int index : indices) {
            assertTrue(index >= 0 && index < base,
                    "surface-only topology must not reference skirt vertices");
        }

        int[] fullSurface = ClipmapRingMesh.surfaceIndices(cells, 0);
        assertEquals(6 * cells * cells, fullSurface.length);
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapRingMesh.surfaceIndices(cells, cells / 2));
    }

    @Test
    void invalidGridInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ClipmapRingMesh.vertices(63));
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapRingMesh.indices(128, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapRingMesh.indices(128, 64));
    }
}

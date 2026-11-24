package org.realscale.neoforge.client.clipmap;

/**
 * CPU-only regular ring topology with downward inner/outer seam skirts.
 * (gridX, gridZ, skirtFlag) shares one height texel for both vertex layers.
 */
public final class ClipmapRingMesh {
    private ClipmapRingMesh() {}

    public static float[] vertices(int cells) {
        validateCells(cells);
        int side = cells + 1;
        int baseCount = Math.multiplyExact(side, side);
        float[] vertices = new float[Math.multiplyExact(baseCount, 6)];
        for (int layer = 0; layer < 2; layer++) {
            for (int z = 0; z <= cells; z++) {
                for (int x = 0; x <= cells; x++) {
                    int index = (layer * baseCount + z * side + x) * 3;
                    vertices[index] = x;
                    vertices[index + 1] = z;
                    vertices[index + 2] = layer;
                }
            }
        }
        return vertices;
    }

    /**
     * Surface-only ring topology. Adjacent LODs are stitched by parent-height
     * morphing, so full-depth inner and outer skirts are not needed on the
     * Iris terrain path. Avoiding those artificial walls keeps distant
     * mountain silhouettes continuous.
     */
    public static int[] surfaceIndices(int cells, int innerHalfCells) {
        validateCells(cells);
        if (innerHalfCells < 0 || innerHalfCells >= cells / 2) {
            throw new IllegalArgumentException("inner half cells must be 0..(cells/2-1)");
        }
        int side = cells + 1;
        int min = cells / 2 - innerHalfCells;
        int max = cells / 2 + innerHalfCells;
        int holeWidth = max - min;
        int quads = cells * cells - holeWidth * holeWidth;
        int[] indices = new int[Math.multiplyExact(6, quads)];
        int p = 0;
        for (int z = 0; z < cells; z++) {
            for (int x = 0; x < cells; x++) {
                if (x >= min && x < max && z >= min && z < max) continue;
                int a = z * side + x;
                int b = (z + 1) * side + x;
                int c = a + 1;
                int d = b + 1;
                indices[p++] = a;
                indices[p++] = b;
                indices[p++] = c;
                indices[p++] = c;
                indices[p++] = b;
                indices[p++] = d;
            }
        }
        if (p != indices.length) throw new IllegalStateException("surface index count mismatch");
        return indices;
    }

    public static int[] indices(int cells, int innerHalfCells) {
        validateCells(cells);
        if (innerHalfCells < 1 || innerHalfCells >= cells / 2) {
            throw new IllegalArgumentException("inner half cells must be 1..(cells/2-1)");
        }
        int side = cells + 1;
        int baseCount = Math.multiplyExact(side, side);
        int min = cells / 2 - innerHalfCells;
        int max = cells / 2 + innerHalfCells;
        int holeWidth = max - min;
        int quads = cells * cells - holeWidth * holeWidth;
        int edges = 4 * cells + 4 * holeWidth;
        int[] indices = new int[Math.multiplyExact(6, quads + edges)];
        int p = 0;
        for (int z = 0; z < cells; z++) {
            for (int x = 0; x < cells; x++) {
                if (x >= min && x < max && z >= min && z < max) continue;
                int a = z * side + x;
                int b = (z + 1) * side + x;
                int c = a + 1;
                int d = b + 1;
                indices[p++] = a;
                indices[p++] = b;
                indices[p++] = c;
                indices[p++] = c;
                indices[p++] = b;
                indices[p++] = d;
            }
        }
        for (int i = 0; i < cells; i++) {
            p = skirt(indices, p, i, i + 1, baseCount);
            p = skirt(indices, p, cells * side + i, cells * side + i + 1, baseCount);
            p = skirt(indices, p, i * side, (i + 1) * side, baseCount);
            p = skirt(indices, p, i * side + cells, (i + 1) * side + cells, baseCount);
        }
        for (int i = min; i < max; i++) {
            p = skirt(indices, p, min * side + i, min * side + i + 1, baseCount);
            p = skirt(indices, p, max * side + i, max * side + i + 1, baseCount);
            p = skirt(indices, p, i * side + min, (i + 1) * side + min, baseCount);
            p = skirt(indices, p, i * side + max, (i + 1) * side + max, baseCount);
        }
        if (p != indices.length) throw new IllegalStateException("skirt index count mismatch");
        return indices;
    }

    private static int skirt(int[] indices, int p, int a, int b, int bottomOffset) {
        indices[p++] = a;
        indices[p++] = b;
        indices[p++] = a + bottomOffset;
        indices[p++] = b;
        indices[p++] = b + bottomOffset;
        indices[p++] = a + bottomOffset;
        return p;
    }

    private static void validateCells(int cells) {
        if (cells < 16 || cells > 256 || (cells & (cells - 1)) != 0) {
            throw new IllegalArgumentException("cells must be a power of two, 16..256");
        }
    }
}

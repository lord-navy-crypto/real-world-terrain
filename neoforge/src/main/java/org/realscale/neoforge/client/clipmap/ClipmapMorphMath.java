package org.realscale.neoforge.client.clipmap;

/** CPU reference for the 2:1 outer ring geomorph in the vertex shader. */
public final class ClipmapMorphMath {
    private ClipmapMorphMath() {}

    public static double edgeWeight(int x, int z, int gridCells, int bandCells) {
        if (gridCells < 16 || bandCells < 1 || bandCells >= gridCells / 2
                || x < 0 || z < 0 || x > gridCells || z > gridCells) {
            throw new IllegalArgumentException("invalid geomorph grid");
        }
        int edge = Math.min(Math.min(x, z),
                Math.min(gridCells - x, gridCells - z));
        double t = Math.max(0.0, Math.min(1.0, edge / (double) bandCells));
        return 1.0 - t * t * (3.0 - 2.0 * t);
    }

    public static double parentHeight(int x, int z,
            double h00, double h10, double h01, double h11) {
        if (x < 0 || z < 0) throw new IllegalArgumentException("negative grid position");
        double fx = (x & 1) * 0.5;
        double fz = (z & 1) * 0.5;
        if (fx + fz <= 1.0) {
            return h00 + fx * (h10 - h00) + fz * (h01 - h00);
        }
        return h11 + (1.0 - fx) * (h01 - h11)
                + (1.0 - fz) * (h10 - h11);
    }

    public static double morphHeight(double fine, double parent, double weight) {
        if (!Double.isFinite(fine) || !Double.isFinite(parent)
                || !Double.isFinite(weight) || weight < 0.0 || weight > 1.0) {
            throw new IllegalArgumentException("invalid geomorph blend");
        }
        return fine + (parent - fine) * weight;
    }
}

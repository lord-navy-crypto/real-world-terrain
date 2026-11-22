package org.realscale.neoforge.client.clipmap;

/**
 * Compact exterior-only proxy for a distant visible feature.
 * Coordinates are world-space block coordinates; no interior block data is kept.
 */
public record VisibleSurfaceProxy(
        int x, int y, int z,
        float radius, float height,
        int rgb, int type) {
    public static final int TREE_CANOPY = 1;
    public static final int FLOATING_ISLAND = 2;
    public static final int BUILDING_EXTERIOR = 3;
    public static final int CAVE_MOUTH = 4;

    public VisibleSurfaceProxy {
        if (!Float.isFinite(radius) || !Float.isFinite(height)
                || radius <= 0.0f || height <= 0.0f) {
            throw new IllegalArgumentException("Proxy dimensions must be finite and positive");
        }
        if (type < TREE_CANOPY || type > CAVE_MOUTH) {
            throw new IllegalArgumentException("Unknown visible-surface proxy type: " + type);
        }
        rgb &= 0x00FFFFFF;
    }
}

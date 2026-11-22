package org.realscale.neoforge.client.clipmap;

import net.minecraft.world.phys.AABB;
import java.util.Objects;

/**
 * Compact exterior-only proxy for a distant visible feature.
 * Coordinates are world-space block coordinates; no interior block data is kept.
 *
 * <p>The immutable world-space bounds are computed once when the proxy is
 * decoded/generated, rather than allocating an AABB for every visible proxy
 * on every rendered frame.
 */
public final class VisibleSurfaceProxy {
    public static final int TREE_CANOPY = 1;
    public static final int FLOATING_ISLAND = 2;
    public static final int BUILDING_EXTERIOR = 3;
    public static final int CAVE_MOUTH = 4;

    private final int x;
    private final int y;
    private final int z;
    private final float radius;
    private final float height;
    private final int rgb;
    private final int type;
    private final AABB bounds;

    public VisibleSurfaceProxy(int x, int y, int z,
            float radius, float height, int rgb, int type) {
        if (!Float.isFinite(radius) || !Float.isFinite(height)
                || radius <= 0.0f || height <= 0.0f) {
            throw new IllegalArgumentException("Proxy dimensions must be finite and positive");
        }
        if (type < TREE_CANOPY || type > CAVE_MOUTH) {
            throw new IllegalArgumentException("Unknown visible-surface proxy type: " + type);
        }
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.height = height;
        this.rgb = rgb & 0x00FFFFFF;
        this.type = type;
        this.bounds = new AABB(
                x - (double) radius, y, z - (double) radius,
                x + (double) radius, y + (double) height, z + (double) radius);
    }

    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public float radius() { return radius; }
    public float height() { return height; }
    public int rgb() { return rgb; }
    public int type() { return type; }
    public AABB bounds() { return bounds; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof VisibleSurfaceProxy proxy)) return false;
        return x == proxy.x && y == proxy.y && z == proxy.z
                && Float.compare(radius, proxy.radius) == 0
                && Float.compare(height, proxy.height) == 0
                && rgb == proxy.rgb && type == proxy.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z, radius, height, rgb, type);
    }

    @Override
    public String toString() {
        return "VisibleSurfaceProxy[x=" + x + ", y=" + y + ", z=" + z
                + ", radius=" + radius + ", height=" + height
                + ", rgb=" + rgb + ", type=" + type + "]";
    }
}

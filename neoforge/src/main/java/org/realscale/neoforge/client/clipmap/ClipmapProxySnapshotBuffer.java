package org.realscale.neoforge.client.clipmap;

import java.util.Objects;

/**
 * Bounded one-frame staging area for clientbound exterior feature proxies.
 *
 * <p>Packet handling order is not an assumption: the proxy packet may arrive
 * before all six height levels. Keep only one staged set, never draw proxies
 * for a different dimension, request generation, or snapped centre.
 */
public final class ClipmapProxySnapshotBuffer<T> {
    private Slot<T> staged;
    private Slot<T> active;

    public void accept(long requestId, String dimension, long centerX, long centerZ,
            T value, ClipmapSnapshotAssembler.SnapshotKey heightKey) {
        if (requestId <= 0L || dimension == null || dimension.isBlank()) {
            throw new IllegalArgumentException("invalid proxy snapshot identifier");
        }
        Slot<T> next = new Slot<>(requestId, dimension, centerX, centerZ,
                Objects.requireNonNull(value, "value"));
        if (next.matches(heightKey)) {
            active = next;
            staged = null;
        } else {
            // A single bounded candidate prevents unbounded packet buildup.
            staged = next;
        }
    }

    public void onHeightSnapshot(ClipmapSnapshotAssembler.SnapshotKey key) {
        if (staged != null && staged.matches(key)) {
            active = staged;
            staged = null;
        } else if (active == null || !active.matches(key)) {
            active = null;
        }
        // Different request generations cannot ever become eligible later.
        if (staged != null && key != null
                && staged.requestId() != key.requestId()) {
            staged = null;
        }
    }

    public T activeFor(ClipmapSnapshotAssembler.SnapshotKey key) {
        return active != null && active.matches(key) ? active.value() : null;
    }

    public void clear() {
        staged = null;
        active = null;
    }

    private record Slot<T>(long requestId, String dimension,
            long centerX, long centerZ, T value) {
        private boolean matches(ClipmapSnapshotAssembler.SnapshotKey key) {
            return key != null && requestId == key.requestId()
                    && dimension.equals(key.dimension())
                    && centerX == key.centerX()
                    && centerZ == key.centerZ();
        }
    }
}

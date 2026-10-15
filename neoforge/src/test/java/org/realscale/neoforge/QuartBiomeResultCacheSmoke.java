package org.realscale.neoforge;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/** Executable, Minecraft-independent correctness gate for the ecotone result cache. */
public final class QuartBiomeResultCacheSmoke {
    private QuartBiomeResultCacheSmoke() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        QuartBiomeResultCache<String> cache = new QuartBiomeResultCache<>();
        require(cache.get(0L) == null, "Uninitialized key cannot yield a result");
        for (long coordinate : new long[]{
                0L, -1L, 1L, Long.MIN_VALUE, Long.MAX_VALUE,
                0x400000000000L, -0x400000000000L}) {
            String value = "biome/" + coordinate;
            cache.put(coordinate, value);
            require(value.equals(cache.get(coordinate)), "Coordinate mismatch " + coordinate);
            require(value.equals(cache.get(coordinate)), "Hot coordinate mismatch " + coordinate);
        }

        // Deliberate collisions must cause a miss, never an incorrect biome.
        long first = 0x1234567890ABCDEFL;
        long second = first + 1;
        while (QuartBiomeResultCache.slot(first) != QuartBiomeResultCache.slot(second))
            second++;
        cache.clear();
        cache.put(first, "forest");
        require("forest".equals(cache.get(first)), "First coordinate was not cached");
        cache.put(second, "plains");
        require("plains".equals(cache.get(second)), "Collision overwrite failed");
        // A separate one-entry hot cache may retain the previous key, but must
        // never return the colliding key's value as another biome.
        String prior = cache.get(first);
        require(prior == null || prior.equals("forest"), "Hash collision corrupted biome");

        cache.clear();
        require(cache.get(second) == null, "Sampler invalidation retained a result");
        cache.put(second, "ocean");
        require("ocean".equals(cache.get(second)), "Sampler reload failed");
        cache.put(second, "frozen_ocean");
        require("frozen_ocean".equals(cache.get(second)), "Updating same position failed");

        // A deterministic, stateless climate/oracle function must be returned
        // exactly as before, irrespective of cache hit rate or visit order.
        Random random = new Random(20261015L);
        long[] positions = new long[30000];
        for (int i = 0; i < positions.length; i++) {
            // Simulate repeated quart positions and distant signed coordinates.
            positions[i] = i % 5 == 0 ? (i % 71 - 35) : random.nextLong();
        }
        cache.clear();
        int hits = 0;
        for (int pass = 0; pass < 3; pass++) {
            for (long coordinate : positions) {
                String expected = "biome/" + Long.toUnsignedString(coordinate % 79);
                String actual = cache.get(coordinate);
                if (actual == null) {
                    cache.put(coordinate, expected);
                    actual = expected;
                } else hits++;
                require(expected.equals(actual), "Wrong cached biome at " + coordinate);
            }
        }
        require(hits > 100, "Repeated coordinates never reused cached results");

        // Every worldgen worker owns its own cache. Parallel invocations cannot
        // contaminate the results of other Climate.Sampler owners.
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] workers = new Thread[4];
        for (int i = 0; i < workers.length; i++) {
            final int index = i;
            workers[i] = new Thread(() -> {
                try {
                    QuartBiomeResultCache<String> local = new QuartBiomeResultCache<>();
                    for (int j = 0; j < 10000; j++) {
                        long coordinate = (j * 61L) ^ ((long) index << 40);
                        String expected = "worker" + index + "/" + coordinate;
                        local.put(coordinate, expected);
                        require(expected.equals(local.get(coordinate)),
                                "Worker-local result corrupted");
                        if (j % 1024 == 0) {
                            local.clear();
                            require(local.get(coordinate) == null,
                                    "Invalidated worker returned stale result");
                        }
                    }
                } catch (Throwable t) { failure.compareAndSet(null, t); }
            }, "ecotone-cache-test-" + i);
            workers[i].start();
        }
        for (Thread worker : workers) {
            try { worker.join(); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Worker test interrupted", e);
            }
        }
        if (failure.get() != null)
            throw new AssertionError("Parallel ecotone cache regression", failure.get());

        System.out.println("TVA_ECOTONE_RESULT_CACHE_PASS: bounded, signed, collision, "
                + "invalidated and worker-local output parity; " + hits + " repeat hits");
    }
}

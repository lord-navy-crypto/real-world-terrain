package org.realscale.neoforge;

/**
 * Server-thread-only generation and retry gate for asynchronous clipmap jobs.
 *
 * <p>A world/session reset invalidates every in-flight callback from the old
 * world. Queue pressure and transient sampling errors use a bounded tick
 * backoff instead of retrying and logging on every server tick.
 */
final class ClipmapRequestEpoch {
    private long generation;
    private long retryAfterTick;

    long advance() {
        generation++;
        retryAfterTick = 0L;
        return generation;
    }

    long current() {
        return generation;
    }

    boolean isCurrent(long token) {
        return token == generation;
    }

    boolean mayStart(long serverTick) {
        return serverTick >= retryAfterTick;
    }

    void defer(long serverTick, long delayTicks) {
        if (delayTicks < 1L) {
            throw new IllegalArgumentException("retry delay must be positive");
        }
        retryAfterTick = Math.max(retryAfterTick,
                Math.addExact(serverTick, delayTicks));
    }
}

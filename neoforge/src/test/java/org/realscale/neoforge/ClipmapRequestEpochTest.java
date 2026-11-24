package org.realscale.neoforge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ClipmapRequestEpochTest {
    @Test
    void worldResetInvalidatesOutstandingWorkerCallbacks() {
        ClipmapRequestEpoch epoch = new ClipmapRequestEpoch();
        long overworldRequest = epoch.advance();
        assertTrue(epoch.isCurrent(overworldRequest));

        long netherRequest = epoch.advance();
        assertFalse(epoch.isCurrent(overworldRequest));
        assertTrue(epoch.isCurrent(netherRequest));
    }

    @Test
    void queuePressureUsesBoundedRetryBackoff() {
        ClipmapRequestEpoch epoch = new ClipmapRequestEpoch();
        epoch.advance();
        assertTrue(epoch.mayStart(100));
        epoch.defer(100, 20);
        assertFalse(epoch.mayStart(119));
        assertTrue(epoch.mayStart(120));
    }

    @Test
    void newWorldEpochClearsOldRetryBackoff() {
        ClipmapRequestEpoch epoch = new ClipmapRequestEpoch();
        epoch.advance();
        epoch.defer(100, 40);
        assertFalse(epoch.mayStart(101));

        epoch.advance();
        assertTrue(epoch.mayStart(101));
    }

    @Test
    void rejectsZeroAndNegativeRetryDelays() {
        ClipmapRequestEpoch epoch = new ClipmapRequestEpoch();
        assertThrows(IllegalArgumentException.class, () -> epoch.defer(1, 0));
        assertThrows(IllegalArgumentException.class, () -> epoch.defer(1, -1));
    }
}

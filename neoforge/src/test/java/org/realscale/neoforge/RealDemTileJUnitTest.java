package org.realscale.neoforge;

import org.junit.jupiter.api.Test;

/**
 * JUnit 5 entry point for the original, SHA-pinned standalone DEM smoke test.
 *
 * Do not modify RealDemTileSmoke.java; qa/verify_repository.py compiles it
 * directly with javac, without dependencies or a Minecraft runtime.
 */
public final class RealDemTileJUnitTest {
    @Test
    void completeOriginalDemValidation() throws Exception {
        RealDemTileSmoke.main(new String[0]);
    }
}

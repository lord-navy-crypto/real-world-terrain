package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ClipmapEnvironmentTest {
    @Test
    void usesDimensionSeaLevelAndOverheadSunAtSolarNoon() {
        ClipmapEnvironment.State state = ClipmapEnvironment.from(32, 0.0f, 0.0f, 0.0f);

        assertEquals(32, state.seaLevel());
        assertEquals(0.0f, state.sunX(), 1.0e-6f);
        assertEquals(1.0f, state.sunY(), 1.0e-6f);
        assertEquals(0.0f, state.sunZ(), 1.0e-6f);
        assertEquals(0.50f, state.ambientLight(), 1.0e-6f);
        assertEquals(0.50f, state.directLight(), 1.0e-6f);
    }

    @Test
    void nightRemovesDirectSunlightButRetainsAmbientVisibility() {
        ClipmapEnvironment.State state = ClipmapEnvironment.from(63,
                (float) Math.PI, 0.0f, 0.0f);

        assertEquals(-1.0f, state.sunY(), 1.0e-6f);
        assertEquals(0.20f, state.ambientLight(), 1.0e-6f);
        assertEquals(0.0f, state.directLight(), 1.0e-6f);
    }

    @Test
    void rainAndThunderAttenuateSunAndSoftenAmbientLight() {
        ClipmapEnvironment.State clear = ClipmapEnvironment.from(63, 0.0f, 0.0f, 0.0f);
        ClipmapEnvironment.State rain = ClipmapEnvironment.from(63, 0.0f, 1.0f, 0.0f);
        ClipmapEnvironment.State storm = ClipmapEnvironment.from(63, 0.0f, 1.0f, 1.0f);

        assertTrue(clear.ambientLight() > rain.ambientLight());
        assertTrue(rain.ambientLight() > storm.ambientLight());
        assertTrue(clear.directLight() > rain.directLight());
        assertTrue(rain.directLight() > storm.directLight());
        assertEquals(0.0f, storm.directLight(), 1.0e-6f);
    }

    @Test
    void invalidWeatherInputsAreClampedAndInvalidSunFailsDark() {
        ClipmapEnvironment.State state = ClipmapEnvironment.from(63,
                Float.NaN, Float.NaN, Float.POSITIVE_INFINITY);

        assertEquals(-1.0f, state.sunY(), 1.0e-6f);
        assertEquals(0.20f, state.ambientLight(), 1.0e-6f);
        assertEquals(0.0f, state.directLight(), 1.0e-6f);
        assertTrue(Float.isFinite(state.sunX()));
    }
}

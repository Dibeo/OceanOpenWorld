package com.mygame.water;

import com.jme3.math.Vector2f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests deterministic wind evolution, normalized direction, amplitude bounds, and validation. */
final class WindModelTest {
    /** Verifies that equal seeds and elapsed times produce equal wind states. */
    @Test
    void sameSeedAndElapsedTimeProduceSameWind() {
        WindModel first = new WindModel(1234L, 0.1f, 0.6f, 2f);
        WindModel second = new WindModel(1234L, 0.1f, 0.6f, 2f);

        for (float tpf : new float[] {0f, 1f, 8f, 23f}) {
            first.update(tpf);
            second.update(tpf);
            assertEquals(first.getDirection(), second.getDirection());
            assertEquals(first.getWaveAmplitude(), second.getWaveAmplitude());
        }
    }

    /** Verifies that direction and wave amplitude evolve as time advances. */
    @Test
    void windDirectionAndWaveStrengthVaryOverTime() {
        WindModel model = new WindModel(1234L, 0.1f, 0.6f, 2f);
        Vector2f initialDirection = model.getDirection();
        float initialAmplitude = model.getWaveAmplitude();
        boolean directionChanged = false;
        boolean amplitudeChanged = false;

        for (int i = 0; i < 80; i++) {
            model.update(1f);
            directionChanged |= !initialDirection.equals(model.getDirection());
            amplitudeChanged |= initialAmplitude != model.getWaveAmplitude();
        }

        assertTrue(directionChanged, "Wind direction should evolve over time");
        assertTrue(amplitudeChanged, "Wind should modulate wave amplitude over time");
    }

    /** Verifies that direction remains normalized and amplitude remains within its configured range. */
    @Test
    void windDirectionIsNormalizedAndAmplitudeStaysWithinConfiguredRange() {
        float baseline = 2f;
        float variation = 0.4f;
        WindModel model = new WindModel(5678L, 0.05f, variation, baseline);

        for (int i = 0; i < 100; i++) {
            model.update(1f);
            assertEquals(1f, model.getDirection().length(), 1e-5f);
            assertTrue(model.getWaveAmplitude() >= baseline * (1f - variation));
            assertTrue(model.getWaveAmplitude() <= baseline * (1f + variation));
        }
    }

    /** Verifies that normalized wind strength remains in the inclusive unit interval. */
    @Test
    void normalizedWindStrengthRemainsWithinZeroAndOne() {
        WindModel model = new WindModel(5678L, 0.05f, 0.4f, 2f);

        for (int i = 0; i < 100; i++) {
            model.update(1f);
            assertTrue(model.getStrength() >= 0f);
            assertTrue(model.getStrength() <= 1f);
        }
    }

    /** Verifies that invalid settings and negative or non-finite time steps are rejected. */
    @Test
    void rejectsInvalidWindSettingsAndTimeSteps() {
        assertThrows(IllegalArgumentException.class,
                () -> new WindModel(1L, 0f, 0.5f, 1f));
        assertThrows(IllegalArgumentException.class,
                () -> new WindModel(1L, 0.1f, 1.1f, 1f));
        assertThrows(IllegalArgumentException.class,
                () -> new WindModel(1L, 0.1f, 0.5f, 1f).update(-1f));
        assertThrows(IllegalArgumentException.class,
                () -> new WindModel(1L, 0.1f, 0.5f, 1f).update(Float.NaN));
    }

    /** Verifies that a zero baseline amplitude remains zero regardless of wind strength. */
    @Test
    void zeroBaselineAmplitudeRemainsZero() {
        WindModel model = new WindModel(99L, 0.1f, 1f, 0f);

        for (int i = 0; i < 10; i++) {
            model.update(1f);
            assertEquals(0f, model.getWaveAmplitude());
        }
    }
}

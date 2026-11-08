package com.mygame.water;

import com.jme3.math.Vector2f;
import com.mygame.noise.PerlinNoise;

/** Smooth, deterministic wind variation used to drive water waves. */
public final class WindModel {
    private static final double DIRECTION_OFFSET = 23.17;
    private static final double STRENGTH_OFFSET = 71.83;

    private final PerlinNoise noise;
    private final double changeSpeed;
    private final float strengthVariation;
    private final float baseAmplitude;
    private final Vector2f direction = new Vector2f(1f, 0f);
    private double elapsedTime;
    private float waveAmplitude;
    private float strength;

    /**
     * Creates deterministic wind variation and initializes its current values at time zero.
     *
     * @param seed seed for the noise sequence
     * @param changeSpeed positive rate at which the noise is sampled
     * @param strengthVariation relative amplitude variation in {@code [0, 1]}
     * @param baseAmplitude baseline wave amplitude in world units; nonnegative
     * @throws IllegalArgumentException if a setting is non-finite or outside its allowed range
     */
    public WindModel(long seed, float changeSpeed, float strengthVariation, float baseAmplitude) {
        if (!Float.isFinite(changeSpeed) || changeSpeed <= 0f) {
            throw new IllegalArgumentException("Wind change speed must be finite and positive");
        }
        if (!Float.isFinite(strengthVariation) || strengthVariation < 0f || strengthVariation > 1f) {
            throw new IllegalArgumentException("Wind strength variation must be between 0 and 1");
        }
        if (!Float.isFinite(baseAmplitude) || baseAmplitude < 0f) {
            throw new IllegalArgumentException("Base wave amplitude must be finite and nonnegative");
        }

        noise = new PerlinNoise(seed);
        this.changeSpeed = changeSpeed;
        this.strengthVariation = strengthVariation;
        this.baseAmplitude = baseAmplitude;
        update(0f);
    }

    /**
     * Advances the deterministic wind state.
     *
     * @param tpf elapsed time in seconds; must be finite and nonnegative
     * @throws IllegalArgumentException if {@code tpf} is negative or non-finite
     */
    public void update(float tpf) {
        if (!Float.isFinite(tpf) || tpf < 0f) {
            throw new IllegalArgumentException("Time per frame must be finite and nonnegative");
        }
        elapsedTime += tpf;
        double sampleTime = elapsedTime * changeSpeed;

        double angle = noise.noise(sampleTime, DIRECTION_OFFSET) * Math.PI;
        direction.set((float) Math.cos(angle), (float) Math.sin(angle));

        double force = noise.noise(sampleTime, STRENGTH_OFFSET);
        strength = 0.5f + 0.5f * (float) force;
        waveAmplitude = baseAmplitude * (1f + strengthVariation * (float) force);
    }

    /**
     * Returns a copy of the current normalized wind direction in the XZ plane.
     *
     * @return unit-length direction vector
     */
    public Vector2f getDirection() {
        return direction.clone();
    }

    /**
     * Returns the current wave amplitude in world units.
     *
     * @return wave amplitude
     */
    public float getWaveAmplitude() {
        return waveAmplitude;
    }

    /**
     * Returns the normalized wind force, shared by visual effects such as foliage.
     *
     * @return normalized wind strength in [0, 1]
     */
    public float getStrength() {
        return strength;
    }
}

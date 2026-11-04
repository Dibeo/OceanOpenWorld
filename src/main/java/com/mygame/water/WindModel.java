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

    public Vector2f getDirection() {
        return direction.clone();
    }

    public float getWaveAmplitude() {
        return waveAmplitude;
    }

    /** Normalized wind force (0..1), shared by visual effects such as foliage. */
    public float getStrength() {
        return strength;
    }
}

package com.mygame.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests XML loading and validation of runtime configuration values. */
final class GameConfigTest {
    @TempDir
    Path tempDir;

    /** Verifies that the loader exposes the configured wind settings. */
    @Test
    void loadsWindSettings() throws IOException {
        GameConfig config = loadConfig(0.6f);

        assertEquals(0.015f, config.windChangeSpeed);
        assertEquals(0.6f, config.windStrengthVariation);
    }

    /** Verifies that wind-strength variation cannot exceed its documented maximum. */
    @Test
    void rejectsWindStrengthVariationAboveOne() throws IOException {
        Path configFile = writeConfig(1.01f);

        assertThrows(IllegalStateException.class, () -> GameConfig.load(configFile.toString()));
    }

    /** Verifies that invalid chunk geometry is rejected before use. */
    @Test
    void rejectsChunkSizeNotDivisibleBySubdivisions() throws IOException {
        Path configFile = writeConfig(0.6f);
        String xml = Files.readString(configFile).replace("subdivisions=\"32\"", "subdivisions=\"30\"");
        Files.writeString(configFile, xml);

        assertThrows(IllegalStateException.class, () -> GameConfig.load(configFile.toString()));
    }

    /** Verifies tree density cannot exceed its configured fraction range. */
    @Test
    void rejectsTreeDensityAboveOne() throws IOException {
        Path configFile = writeConfig(0.6f);
        String xml = Files.readString(configFile).replace("density=\"0.45\"", "density=\"1.01\"");
        Files.writeString(configFile, xml);

        assertThrows(IllegalStateException.class, () -> GameConfig.load(configFile.toString()));
    }

    /** Verifies tree detail distance stays within the documented practical range. */
    @Test
    void rejectsTreeDetailDistanceOutsideRange() throws IOException {
        Path configFile = writeConfig(0.6f);
        String xml = Files.readString(configFile).replace("detailDistance=\"180\"", "detailDistance=\"20\"");
        Files.writeString(configFile, xml);

        assertThrows(IllegalStateException.class, () -> GameConfig.load(configFile.toString()));
    }

    /** Verifies that a missing configuration file produces an explicit loading failure. */
    @Test
    void rejectsMissingConfigurationFile() {
        assertThrows(IllegalStateException.class,
                () -> GameConfig.load(tempDir.resolve("missing.xml").toString()));
    }

    /** Verifies that loading a valid file preserves representative world and graphics settings. */
    @Test
    void loadsRepresentativeRuntimeSettings() throws IOException {
        GameConfig config = GameConfig.load(writeConfig(0.6f).toString());

        assertEquals(42L, config.seed);
        assertEquals(64, config.chunkSize);
        assertEquals(32, config.subdivisions);
        assertEquals(640, config.width);
        assertEquals(1f, config.waterHeight);
        assertEquals(0.45f, config.treeDensity);
        assertEquals(180f, config.treeDetailDistance);
    }

    private GameConfig loadConfig(float variation) throws IOException {
        return GameConfig.load(writeConfig(variation).toString());
    }

    private Path writeConfig(float variation) throws IOException {
        String xml = """
                <gameConfig>
                  <world seed="42"/>
                  <terrain chunkSize="64" subdivisions="32" renderDistance="8" maxChunksPerFrame="1"/>
                  <camera speed="1" farPlane="2"/>
                  <graphics width="640" height="480" fpsLimit="60" vsync="false" gammaCorrection="false"/>
                  <water height="1" transparency="0" waveSpeed="0" waveScale="0" maxAmplitude="1"
                         windChangeSpeed="0.015" windStrengthVariation="%s" reflectionMapSize="1"/>
                  <lighting sunIntensity="1"/>
                  <grass density="1" distance="10" flowers="0"/>
                  <rocks density="0" sizeScale="1"/>
                  <trees density="0.45" detailDistance="180"/>
                  <clouds enabled="false" altitude="1" thickness="1" coverage="0" density="0.01"
                          windSpeed="0" tileMeters="1" textureSize="256" steps="8" lightSteps="1"/>
                </gameConfig>
                """.formatted(variation);
        Path file = tempDir.resolve("config.xml");
        Files.writeString(file, xml);
        return file;
    }
}

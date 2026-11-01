package com.mygame.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class GameConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsWindSettings() throws IOException {
        GameConfig config = loadConfig(0.6f);

        assertEquals(0.015f, config.windChangeSpeed);
        assertEquals(0.6f, config.windStrengthVariation);
    }

    @Test
    void rejectsWindStrengthVariationAboveOne() throws IOException {
        Path configFile = writeConfig(1.01f);

        assertThrows(IllegalStateException.class, () -> GameConfig.load(configFile.toString()));
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
                  <graphics width="1" height="1" fpsLimit="1" vsync="false" gammaCorrection="false"/>
                  <water height="0" transparency="0" waveSpeed="0" waveScale="0" maxAmplitude="1"
                         windChangeSpeed="0.015" windStrengthVariation="%s" reflectionMapSize="1"/>
                  <lighting sunIntensity="1"/>
                </gameConfig>
                """.formatted(variation);
        Path file = tempDir.resolve("config.xml");
        Files.writeString(file, xml);
        return file;
    }
}

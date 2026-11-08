# OpenWorld

OpenWorld is a Java-based open-world demo built with jMonkeyEngine 3. The project focuses on procedural terrain generation, atmospheric rendering, and large-scale exploration in a stylized environment. It is designed as a lightweight sandbox prototype for experimenting with terrain streaming, sky rendering, volumetric clouds, water animation, and camera movement.

## Features

- Procedural terrain generation with chunk-based streaming and configurable world parameters
- Infinite or large-area world layout with textured ground layers and grass blending
- Dynamic lighting with a sun direction and color system
- Sky rendering and atmospheric background scene setup
- Volumetric cloud layers driven by world seed and wind parameters
- Water surface simulation with reflective and animated water shading
- Browser-free desktop game startup using jMonkeyEngine's native windowing system
- XML-based configuration for world, graphics, terrain, water, and cloud tuning

## Project Goals

This project is intended to serve as a technical playground for:

- experimenting with real-time 3D terrain generation
- testing scene composition patterns in jMonkeyEngine
- tuning visuals for open-world landscapes without a large asset pipeline
- building a clean foundation for future content such as foliage, objects, or gameplay systems

## Technology Stack

- Java 25 toolchain
- Gradle build system
- jMonkeyEngine 3.9.0-stable
- Lemur UI library
- Minie physics library
- custom procedural noise and texture generation utilities

## Requirements

Before running the project, make sure you have:

- Java 25 or a compatible JDK configured for Gradle
- Git for cloning the repository
- A desktop environment capable of running a native Java/OpenGL application

## Quick Start

Clone the repository:

```bash
git clone https://github.com/Dibeo/OceanOpenWorld.git
cd OpenWorld
```

Run the application with the Gradle wrapper:

Linux/macOS:

```bash
./gradlew run
```

Windows:

```powershell
.\gradlew.bat run
```

Application logs are always written at DEBUG level or higher to `logs/openworld.log`
(with older logs rotated). Add `-l` or `--logs` to also show them in the console:

Linux/macOS:

```bash
./gradlew run --args="--logs"
```

Windows:

```powershell
.\gradlew.bat run --args="--logs"
```

To run the test suite:

```bash
./gradlew test
```

## Project Structure

```text
OpenWorld/
├── assets/                 # jMonkeyEngine assets and generated resource content
│   ├── MatDefs/            # Custom cloud, grass, and rock materials
│   ├── Shaders/            # GLSL programs used by the custom materials
│   └── textures.xml        # Procedural terrain texture definitions
├── gradle/                 # Gradle wrapper metadata
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/mygame/
│   │   │       ├── camera/           # Camera and fly-through logic
│   │   │       ├── cloud/            # Cloud generation and updates
│   │   │       ├── config/           # XML configuration loader
│   │   │       ├── lighting/         # Sun and light management
│   │   │       ├── noise/            # Procedural noise helpers
│   │   │       ├── scene/            # World assembly and update loop
│   │   │       ├── sky/              # Sky and atmosphere setup
│   │   │       ├── terrain/          # Terrain generator, chunks, and streaming
│   │   │       ├── texture/          # Procedural texture generation and texture definitions
│   │   │       ├── water/            # Water animation and rendering
│   │   │       └── Main.java          # Application entry point
│   │   └── resources/      # Application resources, including log4j2.xml
│   └── test/
├── build.gradle            # Gradle project configuration and dependencies
├── config.xml              # Main runtime configuration file
├── gradlew                 # Unix Gradle wrapper
├── gradlew.bat             # Windows Gradle wrapper
├── settings.gradle         # Gradle multi-project settings
└── README.md               # Project documentation
```

## Runtime Configuration

The application loads its settings from `config.xml` at startup. This file defines the world seed, terrain chunk size, camera speed and far plane, graphics settings, water settings, lighting intensity, and cloud parameters.

Key configuration sections include:

- `world` — seed and world identity
- `terrain` — chunk size, subdivisions, render distance, and frame budget
- `camera` — movement speed and camera distance
- `graphics` — resolution, VSync, gamma correction, and FPS cap
- `water` — wave behavior, transparency, wind, and reflection settings
- `lighting` — sun intensity
- `grass` — density and fade distance
- `clouds` — enabled state, altitude, coverage, density, and wind strength

Example:

```xml
<world seed="1" />

<terrain
    chunkSize="64"
    subdivisions="32"
    renderDistance="8"
    maxChunksPerFrame="8"
/>

<graphics
    width="1280"
    height="720"
    vsync="true"
    gammaCorrection="true"
    fpsLimit="60"
/>
```

## Controls

The project uses the default FlyByCamera behavior from jMonkeyEngine for exploration.

Typical controls are:

- Mouse drag or look input to rotate the camera
- WASD for lateral movement
- Q/E or equivalent vertical movement controls depending on the jMonkeyEngine camera configuration
- Shift-based speed adjustments when available through the engine camera settings

The camera is initialized with a fixed starting position and aimed at the world origin in `Main.java`.

## Main Architecture

The core runtime is assembled in `Main` and `WorldScene`:

- `Main` creates the application, loads the XML config, and starts the game loop
- `WorldScene` acts as the root scene assembly point and coordinates major systems
- `TerrainManager` builds and updates the procedural terrain and grass layers
- `LightingManager` manages the sun direction and light color
- `SkyManager` provides the skybox/atmospheric background
- `CloudManager` updates cloud noise and rendering
- `WaterManager` handles animated water and post-processing effects
- `GameConfig` validates and exposes all XML settings from `config.xml`

## Editable Configuration and Rendering Resources

The project keeps its tunable runtime and rendering inputs in source control:

- Edit the root `config.xml` to tune world seed, terrain streaming, camera, graphics, water,
  lighting, grass, rocks, and clouds. The XML comment at the file root documents required
  sections and attributes, units, accepted values, and validation rules. `GameConfig.load`
  validates the file at startup; settings have no implicit defaults.
- Edit `assets/textures.xml` to define seeded procedural surface maps. Each texture
  can provide diffuse and/or normal maps with ordered Perlin, ripple, and grain layers. Its
  XML comment documents defaults, units, value constraints, and how the texture library loads it.
- The editable material interfaces are `assets/MatDefs/{Clouds,Grass,Rock}/*.j3md`;
  matching GLSL programs are under `assets/Shaders/{Clouds,Grass,Rock}/`. Keep
  material parameter names and vertex layouts in sync with their Java producers and paired
  shaders when changing these files. The Gradle `:assets` project exposes this directory to
  jMonkeyEngine at runtime.
- Grass and rock masks/textures, along with cloud noise, are generated by Java code rather than
  stored as binary source assets. Terrain surface textures are generated from `textures.xml`
  and the current world seed.

Run `.\gradlew.bat test` after editing configuration parsing, texture definitions, or their
generators. Run `.\gradlew.bat run` to inspect visual changes in the desktop application.

## Build Notes

The build is configured in `build.gradle` and currently targets Java 25. The project includes dependencies for core jMonkeyEngine modules, desktop rendering, Lemur, Minie, and procedural generation support libraries.

The Gradle wrapper is included in the repository, so the project can be built without a separate local Gradle installation.

## License

This repository does not currently declare an explicit license file. If you plan to reuse or distribute the project, verify the intended licensing terms before publishing or extending it.

## Contributing

Contributions are welcome if you want to improve the terrain system, expand the world, tune the visuals, or add gameplay features. A good workflow is:

1. fork the repository
2. create a feature branch
3. implement and validate your changes
4. open a pull request with a clear description of the update
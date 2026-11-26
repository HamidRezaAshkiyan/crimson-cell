# AGENTS.md — Crimson Cell

## Overview
Native Android 2D retro pixel-art action roguelite (inspired by *Dead Cells*) written in Kotlin with a custom 60 FPS fixed-timestep engine, procedural pixel art sprites, a real-time 16-bit PCM chiptune audio synthesizer, 3 procedural biomes, 20 weapons & active skills, 10 enemy archetypes + 2 multi-phase bosses, meta-progression (Cell Forge), and first-class Android gamepad/controller support with automatic touch-overlay hiding.

## Tech Stack & Requirements
- **Language / Platform:** Kotlin on Android (`minSdk = 26` / Android 8.0+, `compileSdk = 35`, `targetSdk = 35`, `applicationId = com.crimsoncell.game`)
- **Build System:** Gradle `8.11.1` (`gradlew` / `gradlew.bat`), JDK 17 (`jvmTarget = 17`), Android SDK Platform 35 & Build Tools 35.0.0
- **Dependencies:** Minimal AndroidX only (`androidx.core:core-ktx:1.15.0`, `androidx.appcompat:appcompat:1.7.0`); `junit:junit:4.13.2` for JVM unit tests
- **No external game engine or asset packs:** Rendering (`Canvas`/`SurfaceView`), pixel sprites, physics, and 16-bit PCM audio synthesis are all implemented in pure Kotlin.

## Repository Structure
```text
crimson-cell/
├── settings.gradle.kts                         # Root project "CrimsonCell", includes :app
├── gradle.properties                           # JVM args, AndroidX, official Kotlin style
├── gradlew / gradlew.bat                       # Gradle wrapper
├── local.properties                            # Local Android SDK path (machine-specific; do not commit)
└── app/
    ├── build.gradle.kts                        # Android app config & dependencies
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── kotlin/com/crimsoncell/
        │   │   ├── MainActivity.kt             # Host Activity forwarding lifecycle & controller/key events
        │   │   ├── GameSurfaceView.kt          # SurfaceView + 60 FPS game loop thread & Canvas presentation
        │   │   ├── engine/GameEngine.kt        # Fixed-timestep simulation, hitstop, screen shake, camera, run state
        │   │   ├── entity/
        │   │   │   ├── Player.kt               # Coyote time, jump buffer, double jump, wall jump, dodge roll, dive slam, Rally HP, Verlet scarf
        │   │   │   └── ParticleSystem.kt       # Gore, sparks, crit numbers, afterimages
        │   │   ├── combat/
        │   │   │   ├── CombatSystem.kt         # Hitboxes, parry/reflect (200ms), crits, status effects (BLEED/BURN/FROZEN/POISON/STUN/ROOT)
        │   │   │   └── WeaponCatalog.kt        # 8 melee, 6 ranged/shield, 6 active skills, tiers I–V, affixes
        │   │   ├── enemy/
        │   │   │   ├── Enemy.kt                # 10 regular enemies + Iron Warden & Crimson Reaper bosses
        │   │   │   └── EnemyAI.kt              # Attack telegraphs (`!`), state machines, boss phases
        │   │   ├── world/
        │   │   │   ├── BiomeConfig.kt          # Prisoner's Depths, Toxic Sewers, Clockwork Spire palettes/pools
        │   │   │   ├── LevelGenerator.kt       # Procedural room-graph generator (arenas, chests, shrines, shop, elite, forge)
        │   │   │   └── LevelMap.kt             # Tile collision & drop-through platform queries
        │   │   ├── input/
        │   │   │   ├── InputState.kt           # Platform-agnostic button/axis snapshot
        │   │   │   ├── ControllerInputManager.kt # Xbox/PS/Backbone/Kishi/8BitDo HID, analog L2/R2, deadzones, rumble, keyboard
        │   │   │   └── TouchOverlay.kt         # Translucent retro touch controls (auto-hidden on gamepad connect)
        │   │   ├── render/
        │   │   │   ├── PixelRenderer.kt        # Nearest-neighbor low-res pixel framebuffer & parallax/HUD rendering
        │   │   │   └── PixelSprites.kt         # Procedural pixel-art sprite matrices
        │   │   ├── audio/ChiptuneAudioEngine.kt # Real-time 16-bit PCM AudioTrack synth (SFX + per-biome chiptune)
        │   │   └── progression/ProgressionStore.kt # Persistent Cell Forge upgrades & meta-progression
        │   └── res/                            # Launcher icons, strings, themes
        └── test/kotlin/com/crimsoncell/        # Pure JVM JUnit 4 tests (Combat, Input, LevelGen, Physics, Progression)
```

## Architecture & Key Flows
- **Game Loop (`MainActivity` → `GameSurfaceView` → `GameEngine`):** `MainActivity` routes gamepad/key events to `ControllerInputManager`; `GameSurfaceView` drives `GameEngine` at a fixed 60 FPS timestep (`InputState` → `Player` physics → `CombatSystem` → `EnemyAI` → `ParticleSystem` → `PixelRenderer`).
- **Controller-First Input:** `ControllerInputManager` supports hot-plug HID gamepads (with radial deadzones, analog triggers, and haptic rumble) plus keyboard fallback; active hardware controllers automatically hide `TouchOverlay`.
- **Headless Testability:** Core simulation packages (`engine`, `entity`, `combat`, `world`, `input`, `progression`) are decoupled from Android UI so they run fast under `./gradlew testDebugUnitTest` on the JVM.

## Development & Commands
```bash
# Run JVM unit tests
./gradlew testDebugUnitTest

# Build Debug APK (outputs app/build/outputs/apk/debug/app-debug.apk)
./gradlew assembleDebug

# Build Release APK (unsigned unless signing config is added)
./gradlew assembleRelease

# Clean build outputs
./gradlew clean
```

## Configuration & Environment
- `local.properties` defines `sdk.dir` pointing to the local Android SDK (must not be committed).
- Requires `JAVA_HOME` pointing to **JDK 17** and Android SDK Platform 35 / Build Tools 35.0.0.

## Conventions & Gotchas
- **Keep simulation pure Kotlin:** Do not leak `android.graphics.*` or `Context` into `combat/`, `world/`, `entity/`, or `progression/` so JVM unit tests (`testDebugUnitTest`) continue running without Robolectric or an emulator.
- **No external runtime assets:** Sprites and chiptune audio are generated procedurally in `PixelSprites.kt` and `ChiptuneAudioEngine.kt`.
- **Pixel-crisp rendering:** Disable bilinear filtering (`isFilterBitmap = false`, `isAntiAlias = false`) when scaling pixel art in `PixelRenderer.kt`.
- **Code style:** `kotlin.code.style=official` is enabled in `gradle.properties`.

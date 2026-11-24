# Crimson Cell

A fast-paced, retro pixel-art action roguelite for Android inspired by *Dead Cells*, built in native **Kotlin** with a custom 60 FPS 2D pixel engine, first-class **Android gamepad/controller support**, procedural pixel art, and a real-time 16-bit PCM chiptune audio synthesizer.

---

## Features

- **Snappy 60 FPS Roguelite Movement & Combat**
  - Fixed-timestep physics with coyote time, jump buffering, double jump, wall slide & wall jump, drop-through platforms, and mid-air **Dive Slam** shockwaves.
  - Invincible **Dodge Roll** (`B` / `Circle`) with i-frames, ghost afterimages, and attack-recovery cancel.
  - **Rally HP Mechanic**: taking damage leaves an orange recoverable bar — strike enemies quickly to heal it back.
  - Hitstop freeze-frames, directional screen shake, floating crit numbers, pixel gore/sparks, and Verlet trailing scarf physics.
- **First-Class Android Controller Support**
  - Works out of the box with **Xbox, PlayStation DualSense/DualShock, Backbone, Razer Kishi, 8BitDo**, and generic HID gamepads (plus keyboard support on emulators/Chromebooks).
  - Hot-plug detection automatically hides the translucent retro touch controls when a hardware controller is active.
  - Analog trigger support (`L2`/`R2`), radial deadzone filtering, and haptic rumble feedback.
- **20 Weapons & Active Skills**
  - **8 Melee**: *Rusty Blade*, *Twin Daggers*, *Broadsword*, *Assassin Dagger*, *Blood Sword*, *Frost Blade*, *Lightning Whip*, *Crimson Scythe*.
  - **6 Ranged & Shield**: *Quick Bow*, *Heavy Crossbow*, *Firebrands*, *Ice Bow*, *Throwing Knives*, *Spiked Shield* (with 200ms **Parry** stun & projectile reflection).
  - **6 Active Skills**: *Cluster Bomb*, *Frost Nova*, *Sinew Slicer Turret*, *Phaser*, *Flame Wave*, *Wolf Trap*.
  - Weapon tiers (`I`–`V`), procedural affixes, crit conditions, and status effects (`BLEED`, `BURN`, `FROZEN`, `POISON`, `STUN`, `ROOT`).
- **10 Enemy Archetypes + 2 Multi-Phase Bosses**
  - Every enemy displays a crisp yellow **`!`** attack telegraph so skilled players can dodge-roll or parry.
  - **Regular Enemies**: *Undead Runner*, *Bone Archer*, *Shieldbearer*, *Grenadier*, *Crimson Bat*, *Inquisitor*, *Dark Slasher*, *Toxic Bomber*, *Golem Bruiser*, *Phantom Assassin*.
  - **Bosses**: *Iron Warden* (Biome 2) and *Crimson Reaper* (Biome 3).
- **3 Procedural Biomes & Meta-Progression**
  - **Prisoner's Depths** → **Toxic Sewers** → **Clockwork Spire**, each with unique palettes, parallax layers, enemy pools, and procedural chiptune tracks.
  - Room-graph generator with combat arenas, treasure chests, scroll shrines (Brutality / Tactics / Survival), merchant shop, elite rooms, and the **Cell Forge** for permanent upgrades (*Health Flask+*, *Gold Reserve*, *Cell Forge Tier*, *Crimson Vigor*).

---

## Controller Layout

| Action | Xbox / Generic | PlayStation | Keyboard |
|---|---|---|---|
| Move / Aim | Left Stick / D-Pad | Left Stick / D-Pad | `WASD` / Arrows |
| Jump / Double Jump / Dive Slam (`Down + A`) | `A` | `Cross` | `Space` / `K` |
| Dodge Roll (i-frames) | `B` | `Circle` | `Shift` / `L` |
| Primary Weapon (Slot 1) | `X` | `Square` | `J` |
| Secondary Weapon / Shield Parry (Slot 2) | `Y` | `Triangle` | `U` |
| Skill 1 / Skill 2 | `LT` / `RT` | `L2` / `R2` | `Q` / `E` |
| Use Health Flask | `LB` | `L1` | `H` |
| Interact / Pickup / Shop / Portal | `RB` | `R1` | `F` |
| Pause / Settings | `Start` | `Options` | `Esc` / `P` |

---

## Download & Install

Grab the latest installable APK from the [Releases](../../releases) page, transfer it to your Android device (Android 8.0+ / API 26+), and install. Connect any Bluetooth or USB-C controller and press **A** or **Start** to jump straight into a run.

---

## Building From Source

Requirements: JDK 17 and Android SDK (Platform 35, Build Tools 35.0.0).

```bash
# Run JVM unit tests
./gradlew testDebugUnitTest

# Build Debug APK
./gradlew assembleDebug
```

The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

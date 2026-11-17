# Frosty Roll Rally: Developer Guide

## Build and run
```bash
./gradlew :app:assembleDebug        # debug build (mock ads + debug tools)
./gradlew :app:assembleRelease      # release build (R8 on, no mock ads); add your signingConfig first
./gradlew :app:testDebugUnitTest    # fast unit tests (handcrafted levels + 300 sampled levels)
```

**Full level validation** (all 5,000 levels). It rewrites `app/src/main/assets/levels/salts.bin`:
```bash
./gradlew :app:testDebugUnitTest --tests "*LevelValidationTest*" -DfullLevelValidation=true
```
Re-run it whenever you change simulation tuning (`game/sim`) or the generator (`level/LevelGenerator.kt`).

**Debug automation** (debug builds only). This jumps straight into a level, optionally driven by the AI autopilot:
```bash
adb shell am start -n com.ngstudio.snowrace/.MainActivity --ei autoLevel 7 --ez autopilot true
```

## Architecture
| Package | What lives there |
|---|---|
| `app/` | `App`, `AppContainer` (manual DI), `Stage` (controls the 3D layer and music) |
| `core/` | Math (`Mat4`, `Quat`), deterministic `Rng`, easing |
| `data/` | `SaveData` (single JSON save file), `SaveRepository` (atomic writes), `Catalog` (24 Snowpals, 20 balls, 12 trails), `GameRules` (economy, login calendar, wheel, missions, achievements, milestones) |
| `audio/` | `AudioEngine` (SoundPool SFX + gapless crossfading music streamer), `Haptics` |
| `ads/` | `AdProvider` interface, debug `MockAdProvider`, release `NoAdProvider`, `AdConfig` flags |
| `level/` | `LevelSpec`, `Handcrafted` (1 to 10), `LevelGenerator` (11 to 5,000), `LevelValidator`, `LevelRepository` (+ salts table) |
| `game/sim/` | Pure-Kotlin simulation: `RaceSim`, `SnowField` (deformable snow), `BridgeGrid` (shared crumbling bridges), `Hazards`, `AiBrain` (rivals + autopilot) |
| `game/render/` | OpenGL ES 3 engine: shaders, procedural meshes (`Props`, `CharacterRig`), `RaceScene`, `MenuScene` (home/shop/onboarding dioramas), shadows, particles |
| `game/RaceController` | Joins the simulation, input, HUD flows and race results |
| `ui/` | Jetpack Compose UI: theme, components, screens (splash, onboarding, home, levels, shop, rewards, profile, settings, legal, race) |

The 3D `GLSurfaceView` sits **behind** a transparent Compose layer. A single GL context serves every screen, so shaders compile only once. Opaque screens (Levels, Rewards, Settings, Legal) pause GL rendering to save battery.

## Regenerating assets
```bash
python3 -I tools/gen_art.py     # launcher icons, splash icon, logo, mascot, 44 UI icons, store art
python3 -I tools/gen_audio.py   # all SFX and music (WAV, 22.05 kHz mono)
```
Both are deterministic and need no downloads.

## Adding real ads later
1. Add your ad SDK and implement `AdProvider` (`showRewarded`).
2. Return it from `AdConfig.create()` for release, and set `AdConfig.rewardedUnlockEnabled = true` if locked levels should be unlockable by ads in release.
3. Add `INTERNET` permission, update `assets/legal/privacy.html`, and update the Play Console Data safety form.
4. Keep the fair-play rule: **never show ads during a race**.

## Release checklist
- Fill in `legal_studio_name`, `legal_contact_email` and `legal_effective_date` in `res/values/strings.xml`.
- Add a release `signingConfig` in `app/build.gradle.kts`.
- Play Console: content rating questionnaire (cartoon "bonk" with no violence, so Everyone), target audience (13+ recommended unless you plan Families-certified ads), Data safety (no data collected in this version).
- Store art lives in `store/` (512 icon, 1024×500 feature graphic).
- Run the full level validation and `./gradlew :app:assembleRelease`.

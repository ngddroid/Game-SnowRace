# Frosty Roll Rally - art manifest

All files below are generated procedurally by `tools/gen_art.py` (Python 3 + Pillow, no numpy, no external images). Re-run from the project root with:

```
python3 -I tools/gen_art.py            # everything
python3 -I tools/gen_art.py icons logo # selected groups
```

Groups: `launcher`, `splash`, `mascot`, `logo`, `icons`, `store`, `xml`, `manifest`. Output is deterministic (fixed seeds).

Technique: 4x supersampled layered masks, gradient shading, rim/bounce light, specular blobs, blurred drop shadows and navy (#1B2A4A) outlines made by Minkowski dilation, downscaled with LANCZOS. Fonts: Lilita One (display) from `app/src/main/res/font/lilita_one.ttf`.

Brand palette: night indigo #1E1B4B, deep indigo #2E2A7A, sky blue #5EC8FF, ice #DFF6FF, aurora mint #3DF5C8, berry pink #FF4D8D, gold #FFC83D, coral #FF7A45, navy outline #1B2A4A.


## Launcher icon (adaptive, API 26+) and legacy icons

| File | Size / mode | Notes |
|---|---|---|
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | xml | adaptive-icon: background, foreground and monochrome (themed icon) mipmaps |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | xml | adaptive-icon: background, foreground and monochrome (themed icon) mipmaps |
| `app/src/main/res/mipmap-mdpi/ic_launcher_foreground.png` | 108x108 RGBA | Pip pushing a giant glossy snowball, sparkles and speed lines; all key art inside the 66dp safe circle |
| `app/src/main/res/mipmap-mdpi/ic_launcher_background.png` | 108x108 RGB | opaque aurora night sky, snowflakes, snowy hills |
| `app/src/main/res/mipmap-mdpi/ic_launcher_monochrome.png` | 108x108 RGBA | white single-colour silhouette for Android 13 themed icons |
| `app/src/main/res/mipmap-mdpi/ic_launcher.png` | 48x48 RGBA | legacy rounded-square composite |
| `app/src/main/res/mipmap-mdpi/ic_launcher_round.png` | 48x48 RGBA | legacy circular composite |
| `app/src/main/res/mipmap-hdpi/ic_launcher_foreground.png` | 162x162 RGBA | Pip pushing a giant glossy snowball, sparkles and speed lines; all key art inside the 66dp safe circle |
| `app/src/main/res/mipmap-hdpi/ic_launcher_background.png` | 162x162 RGB | opaque aurora night sky, snowflakes, snowy hills |
| `app/src/main/res/mipmap-hdpi/ic_launcher_monochrome.png` | 162x162 RGBA | white single-colour silhouette for Android 13 themed icons |
| `app/src/main/res/mipmap-hdpi/ic_launcher.png` | 72x72 RGBA | legacy rounded-square composite |
| `app/src/main/res/mipmap-hdpi/ic_launcher_round.png` | 72x72 RGBA | legacy circular composite |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png` | 216x216 RGBA | Pip pushing a giant glossy snowball, sparkles and speed lines; all key art inside the 66dp safe circle |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_background.png` | 216x216 RGB | opaque aurora night sky, snowflakes, snowy hills |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_monochrome.png` | 216x216 RGBA | white single-colour silhouette for Android 13 themed icons |
| `app/src/main/res/mipmap-xhdpi/ic_launcher.png` | 96x96 RGBA | legacy rounded-square composite |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_round.png` | 96x96 RGBA | legacy circular composite |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png` | 324x324 RGBA | Pip pushing a giant glossy snowball, sparkles and speed lines; all key art inside the 66dp safe circle |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_background.png` | 324x324 RGB | opaque aurora night sky, snowflakes, snowy hills |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_monochrome.png` | 324x324 RGBA | white single-colour silhouette for Android 13 themed icons |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher.png` | 144x144 RGBA | legacy rounded-square composite |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png` | 144x144 RGBA | legacy circular composite |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png` | 432x432 RGBA | Pip pushing a giant glossy snowball, sparkles and speed lines; all key art inside the 66dp safe circle |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_background.png` | 432x432 RGB | opaque aurora night sky, snowflakes, snowy hills |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_monochrome.png` | 432x432 RGBA | white single-colour silhouette for Android 13 themed icons |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` | 192x192 RGBA | legacy rounded-square composite |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png` | 192x192 RGBA | legacy circular composite |

Removed template vectors: `app/src/main/res/drawable/ic_launcher_background.xml`, `app/src/main/res/drawable/ic_launcher_foreground.xml`.


## Brand art (`drawable-nodpi`)

| File | Size / mode | Notes |
|---|---|---|
| `app/src/main/res/drawable-nodpi/splash_icon.png` | 960x960 RGBA | Android 12 splash icon: Pip + snowball on a snow mound, inside the central 640 px circle |
| `app/src/main/res/drawable-nodpi/logo_frosty.png` | 1190x623 RGBA | game logo: snow-capped FROSTY + gold ROLL RALLY on a berry ribbon |
| `app/src/main/res/drawable-nodpi/mascot_pip.png` | 633x800 RGBA | full-body Pip waving: chibi kid in a puffy coral snowsuit, mint beanie, berry scarf, navy boots |

## UI icon set (256x256, transparent, `drawable-nodpi`)

Consistent style: top-left light, ~10 px navy outline at 256 px with a 6 px bottom extrusion, soft inner shading and specular highlights. Readable at 24-48 dp.

| File | Size / mode | Notes |
|---|---|---|
| `app/src/main/res/drawable-nodpi/ui_coin.png` | 256x256 RGBA | gold coin with embossed snowflake |
| `app/src/main/res/drawable-nodpi/ui_gem.png` | 256x256 RGBA | faceted berry-pink gem with aurora-mint glints |
| `app/src/main/res/drawable-nodpi/ui_star.png` | 256x256 RGBA | glossy gold star (earned) |
| `app/src/main/res/drawable-nodpi/ui_star_empty.png` | 256x256 RGBA | recessed slate-blue star slot (not earned) |
| `app/src/main/res/drawable-nodpi/ui_lock.png` | 256x256 RGBA | gold padlock with steel shackle |
| `app/src/main/res/drawable-nodpi/ui_gear.png` | 256x256 RGBA | settings gear |
| `app/src/main/res/drawable-nodpi/ui_home.png` | 256x256 RGBA | cozy cabin with snowy roof |
| `app/src/main/res/drawable-nodpi/ui_shop.png` | 256x256 RGBA | berry shopping bag |
| `app/src/main/res/drawable-nodpi/ui_gift.png` | 256x256 RGBA | mint gift box with berry bow |
| `app/src/main/res/drawable-nodpi/ui_map.png` | 256x256 RGBA | folded treasure map with path |
| `app/src/main/res/drawable-nodpi/ui_profile.png` | 256x256 RGBA | Pip head (beanie, brown bob) in an avatar badge |
| `app/src/main/res/drawable-nodpi/ui_trophy.png` | 256x256 RGBA | gold cup on plinth |
| `app/src/main/res/drawable-nodpi/ui_play.png` | 256x256 RGBA | white rounded play |
| `app/src/main/res/drawable-nodpi/ui_pause.png` | 256x256 RGBA | white pause bars |
| `app/src/main/res/drawable-nodpi/ui_close.png` | 256x256 RGBA | white X on berry button |
| `app/src/main/res/drawable-nodpi/ui_back.png` | 256x256 RGBA | white left arrow |
| `app/src/main/res/drawable-nodpi/ui_wheel.png` | 256x256 RGBA | prize wheel with pointer |
| `app/src/main/res/drawable-nodpi/ui_calendar.png` | 256x256 RGBA | daily-reward calendar |
| `app/src/main/res/drawable-nodpi/ui_mission.png` | 256x256 RGBA | checklist scroll |
| `app/src/main/res/drawable-nodpi/ui_chest.png` | 256x256 RGBA | treasure chest |
| `app/src/main/res/drawable-nodpi/ui_ad.png` | 256x256 RGBA | TV with play symbol (watch ad) |
| `app/src/main/res/drawable-nodpi/ui_music.png` | 256x256 RGBA | music notes |
| `app/src/main/res/drawable-nodpi/ui_sound.png` | 256x256 RGBA | speaker with waves |
| `app/src/main/res/drawable-nodpi/ui_vibrate.png` | 256x256 RGBA | phone with wiggle lines |
| `app/src/main/res/drawable-nodpi/ui_boost.png` | 256x256 RGBA | sled with speed lines |
| `app/src/main/res/drawable-nodpi/ui_shield.png` | 256x256 RGBA | ice shield with snowflake |
| `app/src/main/res/drawable-nodpi/ui_magnet.png` | 256x256 RGBA | magnet pulling snowballs |
| `app/src/main/res/drawable-nodpi/ui_mitten.png` | 256x256 RGBA | big mint mitten |
| `app/src/main/res/drawable-nodpi/ui_bridge.png` | 256x256 RGBA | snow-block bridge over water |
| `app/src/main/res/drawable-nodpi/ui_snowball.png` | 256x256 RGBA | glossy snowball |
| `app/src/main/res/drawable-nodpi/ui_check.png` | 256x256 RGBA | white check on mint |
| `app/src/main/res/drawable-nodpi/ui_info.png` | 256x256 RGBA | white i on sky blue |
| `app/src/main/res/drawable-nodpi/ui_restart.png` | 256x256 RGBA | circular restart arrow |
| `app/src/main/res/drawable-nodpi/ui_crown.png` | 256x256 RGBA | gold crown with jewels |
| `app/src/main/res/drawable-nodpi/ui_flag.png` | 256x256 RGBA | checkered finish flag |
| `app/src/main/res/drawable-nodpi/ui_doc.png` | 256x256 RGBA | document page (Terms) |
| `app/src/main/res/drawable-nodpi/ui_privacy.png` | 256x256 RGBA | shield with padlock (Privacy) |
| `app/src/main/res/drawable-nodpi/ui_graphics.png` | 256x256 RGBA | monitor with sparkle (graphics quality) |
| `app/src/main/res/drawable-nodpi/ui_controller.png` | 256x256 RGBA | gamepad |
| `app/src/main/res/drawable-nodpi/ui_edit.png` | 256x256 RGBA | pencil |
| `app/src/main/res/drawable-nodpi/ui_trash.png` | 256x256 RGBA | trash bin |
| `app/src/main/res/drawable-nodpi/ui_bonk.png` | 256x256 RGBA | cartoon bonk star burst |
| `app/src/main/res/drawable-nodpi/ui_snowflake.png` | 256x256 RGBA | six-armed snowflake |
| `app/src/main/res/drawable-nodpi/ui_hand.png` | 256x256 RGBA | pointing tutorial glove with tap ripples |

## Store art (`store/`)

| File | Size / mode | Notes |
|---|---|---|
| `store/play_icon_512.png` | 512x512 RGB | Play Store icon, full-bleed, opaque |
| `store/feature_graphic_1024x500.png` | 1024x500 RGB | aurora sky, mountains, race lane with flags, chibi kid racers (pink / mint / sky-blue snowsuits, varied skin tones), Pip with a giant snowball, logo |


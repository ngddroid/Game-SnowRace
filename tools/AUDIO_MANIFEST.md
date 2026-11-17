# Frosty Roll Rally - audio manifest

All audio is 100% original and procedurally synthesised by `tools/gen_audio.py`
(Python 3 standard library only, fixed seeds, bit-identical on every run).
Regenerate from the project root with:

    python3 -I tools/gen_audio.py

Format: WAV, PCM 16-bit, mono, 22050 Hz, in `app/src/main/res/raw/`.
One-shot SFX are peak-normalised to -1 dBFS and start/end on silence (>= 2 ms raised-cosine
fade-in, fade-out tail). Music is peak-normalised to -1.5 dBFS after a soft-knee clipper.
Loops (`*_loop`, `music_*`) are rendered circularly: note releases, echoes, reverb and filter
tails that ring past the loop end are folded back onto the start, so they repeat seamlessly
(set `MediaPlayer.isLooping = true` / SoundPool `loop = -1`). Relative loudness between
sounds should be balanced in-game via playback volume.

| File | Type | Duration (s) | Peak (dBFS) | Description |
|---|---|---:|---:|---|
| `sfx_ui_tap.wav` | one-shot | 0.070 | -1.0 | Soft bubbly pop/click for button taps (rising sine blip + tiny click). |
| `sfx_ui_back.wav` | one-shot | 0.080 | -1.0 | Lower, softer descending tap for back/cancel. |
| `sfx_ui_open.wav` | one-shot | 0.280 | -1.0 | Airy upward band-pass swoosh with an E6-B6 two-note chime. |
| `sfx_ui_close.wav` | one-shot | 0.220 | -1.0 | Downward swoosh ending in a soft closing tock. |
| `sfx_coin.wav` | one-shot | 0.300 | -1.0 | Bright two-note D6-A6 "ka-ching" with metallic ring and sparkles. |
| `sfx_coin_tick.wav` | one-shot | 0.040 | -1.0 | Tiny high E7 tick for coin count-up. |
| `sfx_star1.wav` | one-shot | 0.420 | -1.0 | Magical celesta chime G5->C6 with shimmer and twinkles (star 1). |
| `sfx_star2.wav` | one-shot | 0.420 | -1.0 | Same chime a step higher, C6->E6 (star 2). |
| `sfx_star3.wav` | one-shot | 0.420 | -1.0 | Highest chime E6->G6 plus a G7 sparkle (star 3). |
| `sfx_unlock.wav` | one-shot | 0.950 | -1.0 | Cheerful ascending C6-E6-G6-C7 bell jingle with sparkle tail. |
| `sfx_chest_open.wav` | one-shot | 1.250 | -1.0 | Wooden lid thump, then a shimmering rising C-major bell arpeggio with magic dust. |
| `sfx_wheel_tick.wav` | one-shot | 0.030 | -1.0 | Short wooden click for each prize-wheel peg. |
| `sfx_wheel_win.wav` | one-shot | 1.050 | -1.0 | Short brass fanfare (E5, G5, C-major chord) with timpani hit and bells. |
| `sfx_countdown.wav` | one-shot | 0.150 | -1.0 | Clean 880 Hz beep for 3-2-1. |
| `sfx_go.wav` | one-shot | 0.520 | -1.0 | Higher, brighter 1760 Hz chorused beep plus a rising whoosh. |
| `sfx_step_snow1.wav` | one-shot | 0.090 | -1.0 | Soft snow-crunch footstep (filtered noise grains + soft body), variation 1. |
| `sfx_step_snow2.wav` | one-shot | 0.090 | -1.0 | Snow-crunch footstep, slightly darker variation 2. |
| `sfx_roll_loop.wav` | loop | 1.200 | -1.0 | Seamless rolling-snowball loop: low rumble, packed-snow body, granular crunch with a rotation pulse. |
| `sfx_bridge_block.wav` | one-shot | 0.180 | -1.0 | Soft heavy packed-snow thump/plop (game pitches it up per block). |
| `sfx_splash.wav` | one-shot | 0.720 | -1.0 | Water splash: noise burst, spray and plunk with rising-pitch bubbles. |
| `sfx_bonk.wav` | one-shot | 0.320 | -1.0 | Cute cartoon bonk: woody knock + rubbery boing with quick pitch drop and wobble. |
| `sfx_boost.wav` | one-shot | 0.620 | -1.0 | Rising resonant whoosh with a climbing tone. |
| `sfx_shield.wav` | one-shot | 0.620 | -1.0 | Shimmering crystalline hum (detuned A-major partial cluster + tremolo hum). |
| `sfx_powerup.wav` | one-shot | 0.420 | -1.0 | Bright rising G-major arpeggio pickup with sparkle. |
| `sfx_gift.wav` | one-shot | 0.360 | -1.0 | Cute rising pop followed by three sparkling chimes. |
| `sfx_avalanche_loop.wav` | loop | 2.000 | -1.0 | Seamless deep avalanche rumble with tumbling snow chunks. |
| `sfx_crumble.wav` | one-shot | 0.820 | -1.0 | Crumbling snow/ice: dense cracks, debris and low rumble with icy tinks. |
| `sfx_win_fanfare.wav` | one-shot | 2.600 | -1.0 | Celebratory C-major brass fanfare (stab x3, F, G, C) with timpani, roll, crash and bells. |
| `sfx_lose.wav` | one-shot | 1.250 | -1.0 | Gentle comedic "aww": formant voice G4-Eb4-C4 with a droop and vibrato. |
| `sfx_error.wav` | one-shot | 0.260 | -1.0 | Soft low double buzz. |
| `sfx_whoosh.wav` | one-shot | 0.360 | -1.0 | Generic swoosh (band-pass sweep up then down). |
| `sfx_cheer.wav` | one-shot | 1.550 | -1.0 | Crowd celebration: applause texture, crowd wash and party-horn toots. |
| `sfx_pop.wav` | one-shot | 0.080 | -1.0 | Bubble pop. |
| `sfx_ice_crack.wav` | one-shot | 0.420 | -1.0 | Sharp ice crack with follow-up cracks, icy ring and a frozen-lake "pew". |
| `sfx_finale_step.wav` | one-shot | 0.260 | -1.0 | Bright marimba C5 note (game pitches it up per stair step). |
| `music_menu.wav` | music loop | 38.400 | -1.5 | Cozy winter theme, F major, 100 bpm, 16 bars: celesta lead, Karplus-Strong harp arpeggios, soft pad, sine bass, shaker, sleigh bells. |
| `music_race.wav` | music loop | 27.429 | -1.5 | Upbeat race theme, G major, 140 bpm, 16 bars: plucky lead hook, glockenspiel double, driving bass, four-on-the-floor kit, offbeat stabs, sleigh bells. |
| `music_boss.wav` | music loop | 25.600 | -1.5 | Tense-but-fun chase, E minor, 150 bpm, 16 bars: staccato saw-string stabs, galloping bass, minor riff, tom fills, sleigh bells. |

Music loop lengths are sample-exact: menu 64 beats x 13230 = 846720 samples (38.400 s),
race 64 x 9450 = 604800 samples (27.429 s), boss 64 x 8820 = 564480 samples (25.600 s).

Total: 38 files, 4.71 MB.

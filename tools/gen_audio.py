#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Frosty Roll Rally -- procedural audio generator.

Synthesises every sound effect and music loop used by the game from scratch,
using only the Python 3 standard library (no numpy/scipy, no samples, no
downloads, no borrowed melodies).  All randomness comes from fixed seeds, so
every run produces bit-identical files.

Usage (from the project root):
    python3 -I tools/gen_audio.py                 # generate all + verify + manifest
    python3 -I tools/gen_audio.py --verify        # only verify existing files
    python3 -I tools/gen_audio.py sfx_coin music_menu   # regenerate a subset

Output:
    app/src/main/res/raw/*.wav   PCM 16-bit, mono, 22050 Hz
    tools/AUDIO_MANIFEST.md      file list with durations and descriptions

Techniques: band-limited wavetables (harmonics below ~9.5 kHz only, so no
aliasing), FM and modal (decaying-partial) bells, Karplus-Strong plucks with
fractional-delay tuning, TPT state-variable and RBJ biquad filters, a
Freeverb-style comb/allpass reverb (block-vectorised), DC blocking, raised
cosine fades and a soft-knee clipper.  Loops are rendered *circularly*: every
tail that rings past the loop end (note releases, echoes, reverb, filter
ringing) is folded back onto the start, which is the exact periodic
steady-state, so loop points are click- and gap-free by construction.
"""

import array
import math
import os
import random
import sys
import time
import wave
import zlib
from itertools import accumulate, repeat
from operator import mul

SR = 22050
TAU = 2.0 * math.pi
PI = math.pi
sin = math.sin
cos = math.cos
exp = math.exp
tanh = math.tanh

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RAW_DIR = os.path.join(ROOT, 'app', 'src', 'main', 'res', 'raw')
MANIFEST_PATH = os.path.join(HERE, 'AUDIO_MANIFEST.md')

SFX_PEAK_DB = -1.0
MUSIC_PEAK_DB = -1.5


# ----------------------------------------------------------------------------
# basic helpers
# ----------------------------------------------------------------------------

def ns(t):
    """Seconds -> whole samples."""
    return int(round(t * SR))


def hz(m):
    """MIDI note number -> Hz (A4 = 69 = 440 Hz)."""
    return 440.0 * 2.0 ** ((m - 69) / 12.0)


def rng_for(name):
    """Deterministic RNG per sound (crc32, not the randomised str hash)."""
    return random.Random(zlib.crc32(name.encode('utf-8')))


def zeros(n):
    return [0.0] * n


def add_at(dst, src, off=0, gain=1.0):
    """Mix src*gain into dst starting at sample `off` (clipped to dst)."""
    if off < 0:
        src = src[-off:]
        off = 0
    end = min(len(dst), off + len(src))
    if end <= off:
        return dst
    if gain == 1.0:
        dst[off:end] = [a + b for a, b in zip(dst[off:end], src)]
    else:
        dst[off:end] = [a + gain * b for a, b in zip(dst[off:end], src)]
    return dst


def add_wrap(dst, src, off, gain=1.0):
    """Mix src into dst circularly (indices wrap modulo len(dst))."""
    L = len(dst)
    off %= L
    pos = 0
    while pos < len(src):
        chunk = src[pos:pos + (L - off)]
        add_at(dst, chunk, off, gain)
        pos += len(chunk)
        off = 0
    return dst


def vscale(x, g):
    return [v * g for v in x]


def vmul(a, b):
    return [p * q for p, q in zip(a, b)]


def pad_to(x, n):
    return x + [0.0] * (n - len(x)) if len(x) < n else x[:n]


def peak(x):
    return max(max(x), -min(x)) if x else 0.0


def rms(x):
    return math.sqrt(math.fsum(v * v for v in x) / len(x)) if x else 0.0


def sat(x, drive=1.5):
    """Gentle tanh saturation, unity small-signal gain."""
    k = 1.0 / drive
    return [k * tanh(drive * v) for v in x]


# ----------------------------------------------------------------------------
# envelopes
# ----------------------------------------------------------------------------

def env_exp(n, tau):
    """exp(-t/tau) for n samples (tau in seconds), via C-level accumulate."""
    if n <= 0:
        return []
    c = exp(-1.0 / (tau * SR))
    return list(accumulate(repeat(c, n - 1), mul, initial=1.0))


def ramp_up(n):
    """Raised cosine 0 -> 1 over n samples (first value exactly 0)."""
    if n <= 0:
        return []
    k = PI / n
    return [0.5 - 0.5 * cos(k * i) for i in range(n)]


def apply_attack(x, att):
    na = min(len(x), max(1, ns(att)))
    x[:na] = [v * r for v, r in zip(x[:na], ramp_up(na))]
    return x


def env_ad(n, att=0.002, tau=0.1, hold=0.0):
    """Raised-cosine attack, optional hold, exponential decay (n samples)."""
    na = min(n, max(1, ns(att)))
    nh = max(0, min(n - na, ns(hold)))
    out = ramp_up(na) + [1.0] * nh
    out += env_exp(n - len(out), tau)
    return out


def env_adsr(gate, a=0.005, d=0.1, s=0.7, r=0.15):
    """ADSR in seconds; returns gate + r seconds, ending at exactly 0."""
    ng = max(2, ns(gate))
    na = min(ng, max(1, ns(a)))
    out = ramp_up(na)
    m = ng - na
    if m > 0:
        out += [s + (1.0 - s) * e for e in env_exp(m, d)]
    lvl = out[-1]
    nr = max(2, ns(r))
    k = 1.0 / nr
    out += [lvl * exp(-4.0 * i * k) * (1.0 - i * k) for i in range(1, nr + 1)]
    return out


def env_hump(n, peak_at=0.5, p_up=2.0, p_dn=2.0):
    """Smooth 0 -> 1 -> 0 envelope peaking at fraction peak_at of n."""
    k = min(n - 1, max(1, int(n * peak_at)))
    m = n - k
    h = PI / 2.0
    return ([sin(h * i / k) ** p_up for i in range(k)] +
            [cos(h * (j + 1) / m) ** p_dn for j in range(m)])


def release_tail(x, start, length):
    """Fade x to zero from sample `start` over `length` samples, truncate."""
    start = max(0, min(start, len(x)))
    length = max(2, length)
    end = min(len(x), start + length)
    k = PI / length
    x[start:end] = [v * (0.5 + 0.5 * cos(k * i)) for i, v in enumerate(x[start:end])]
    del x[end:]
    return x


def end_fade(x, frac=0.12, min_s=0.004):
    """Short raised-cosine fade over the tail so truncated layers never click."""
    n = len(x)
    m = min(n, max(ns(min_s), int(n * frac)))
    if m > 1:
        k = PI / m
        x[n - m:] = [v * (0.5 + 0.5 * cos(k * (i + 1))) for i, v in enumerate(x[n - m:])]
    return x


# ----------------------------------------------------------------------------
# oscillators
# ----------------------------------------------------------------------------

def phases(freqs, ph0=0.0):
    """Running phase (radians) for a per-sample frequency list."""
    k = TAU / SR
    ph = list(accumulate([f * k for f in freqs], initial=ph0))
    ph.pop()
    return ph


def osc_sine(freqs, ph0=0.0):
    return [sin(p) for p in phases(freqs, ph0)]


def tone(n, f, ph0=0.0):
    w = TAU * f / SR
    return [sin(ph0 + w * i) for i in range(n)]


def glide(n, f0, f1, tau):
    """Exponential glide from f0 towards f1 with time constant tau (s)."""
    return [f1 + (f0 - f1) * e for e in env_exp(n, tau)]


def sweep_exp(n, f0, f1):
    """Log-linear sweep f0 -> f1 over n samples."""
    if n <= 1:
        return [f0] * n
    r = (f1 / f0) ** (1.0 / (n - 1))
    return list(accumulate(repeat(r, n - 1), mul, initial=f0))


WT_N = 2048
_WT_CACHE = {}


def _harm(shape, k):
    if shape == 'saw':
        return 1.0 / k
    if shape == 'square':
        return 1.0 / k if k % 2 else 0.0
    if shape == 'tri':
        return ((-1.0) ** ((k - 1) // 2)) / (k * k) if k % 2 else 0.0
    if shape == 'pulse':                    # 30 % duty pulse
        return math.sin(PI * k * 0.3) / k
    if shape == 'soft':                     # mellow saw for pads
        return 1.0 / k ** 1.7
    if shape == 'reed':                     # odd-heavy buzzy reed
        return (1.0 if k % 2 else 0.4) / k
    if shape == 'brass':                    # saw with a slight mid lift
        return (1.0 / k) * (1.0 + 0.6 * math.exp(-((k - 4.0) / 3.0) ** 2))
    raise ValueError(shape)


def wavetable(shape, f_max):
    """Band-limited single-cycle table: only harmonics that stay below
    ~9.5 kHz at f_max (Nyquist is 11.025 kHz) -> alias-free playback."""
    K = max(1, min(160, int(9500.0 / max(f_max, 1.0))))
    key = (shape, K)
    t = _WT_CACHE.get(key)
    if t is None:
        N = WT_N
        acc = [0.0] * N
        for k in range(1, K + 1):
            a = _harm(shape, k)
            if a == 0.0:
                continue
            w = TAU * k / N
            acc = [v + a * sin(w * j) for j, v in enumerate(acc)]
        pk = peak(acc) or 1.0
        T = [v / pk for v in acc]
        T.append(T[0])
        D = [T[i + 1] - T[i] for i in range(N)]
        t = (T, D)
        _WT_CACHE[key] = t
    return t


def osc_wt(table, freqs, ph0=0.0):
    """Linear-interpolated wavetable oscillator; freqs = per-sample Hz list."""
    T, D = table
    N = WT_N
    k = N / SR
    ph = list(accumulate([f * k for f in freqs], initial=ph0 * N))
    ph.pop()
    return [T[(i := int(q := p % N))] + (q - i) * D[i] for p in ph]


def osc_wt_const(table, n, f, ph0=0.0):
    T, D = table
    N = WT_N
    inc = f * N / SR
    base = ph0 * N
    return [T[(i := int(q := (base + inc * j) % N))] + (q - i) * D[i]
            for j in range(n)]


def white(n, rng):
    r = rng.random
    return [2.0 * r() - 1.0 for _ in range(n)]


# ----------------------------------------------------------------------------
# filters
# ----------------------------------------------------------------------------

def rbj(kind, f0, q=0.7071, gain_db=0.0):
    """RBJ audio-EQ-cookbook biquad coefficients (normalised)."""
    f0 = min(max(f0, 5.0), 0.47 * SR)
    w = TAU * f0 / SR
    cw = cos(w)
    sw = sin(w)
    al = sw / (2.0 * q)
    A = 10.0 ** (gain_db / 40.0)
    if kind == 'lp':
        b0 = b2 = (1 - cw) / 2
        b1 = 1 - cw
        a0, a1, a2 = 1 + al, -2 * cw, 1 - al
    elif kind == 'hp':
        b0 = b2 = (1 + cw) / 2
        b1 = -(1 + cw)
        a0, a1, a2 = 1 + al, -2 * cw, 1 - al
    elif kind == 'bp':                      # 0 dB peak gain
        b0, b1, b2 = al, 0.0, -al
        a0, a1, a2 = 1 + al, -2 * cw, 1 - al
    elif kind == 'peak':
        b0, b1, b2 = 1 + al * A, -2 * cw, 1 - al * A
        a0, a1, a2 = 1 + al / A, -2 * cw, 1 - al / A
    elif kind == 'hs':
        sq = 2 * math.sqrt(A) * al
        b0 = A * ((A + 1) + (A - 1) * cw + sq)
        b1 = -2 * A * ((A - 1) + (A + 1) * cw)
        b2 = A * ((A + 1) + (A - 1) * cw - sq)
        a0 = (A + 1) - (A - 1) * cw + sq
        a1 = 2 * ((A - 1) - (A + 1) * cw)
        a2 = (A + 1) - (A - 1) * cw - sq
    elif kind == 'ls':
        sq = 2 * math.sqrt(A) * al
        b0 = A * ((A + 1) - (A - 1) * cw + sq)
        b1 = 2 * A * ((A - 1) - (A + 1) * cw)
        b2 = A * ((A + 1) - (A - 1) * cw - sq)
        a0 = (A + 1) + (A - 1) * cw + sq
        a1 = -2 * ((A - 1) + (A + 1) * cw)
        a2 = (A + 1) + (A - 1) * cw - sq
    else:
        raise ValueError(kind)
    return (b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)


def biquad(x, c):
    """Transposed direct-form II biquad."""
    b0, b1, b2, a1, a2 = c
    y = [0.0] * len(x)
    s1 = s2 = 0.0
    for i, v in enumerate(x):
        o = b0 * v + s1
        s1 = b1 * v - a1 * o + s2
        s2 = b2 * v - a2 * o
        y[i] = o
    return y


def filt(x, kind, f0, q=0.7071, gain_db=0.0):
    return biquad(x, rbj(kind, f0, q, gain_db))


def svf(x, fc, q=0.707, mode='lp', block=16):
    """Zavalishin TPT state-variable filter (stable under fast modulation).
    fc: Hz (float) or per-sample list (re-read every `block` samples).
    mode 'bp' is normalised to 0 dB at the centre frequency."""
    n = len(x)
    y = [0.0] * n
    k = 1.0 / q
    ic1 = ic2 = 0.0
    m = {'lp': 0, 'bp': 1, 'hp': 2}[mode]
    is_list = isinstance(fc, list)
    if not is_list:
        block = max(1, n)
    lim = 0.45 * SR
    tan = math.tan
    pos = 0
    while pos < n:
        f = fc[pos] if is_list else fc
        f = 20.0 if f < 20.0 else (lim if f > lim else f)
        g = tan(PI * f / SR)
        a1 = 1.0 / (1.0 + g * (g + k))
        a2 = g * a1
        a3 = g * a2
        end = pos + block if pos + block < n else n
        for j in range(pos, end):
            v0 = x[j]
            v3 = v0 - ic2
            v1 = a1 * ic1 + a2 * v3
            v2 = ic2 + a2 * ic1 + a3 * v3
            ic1 = 2.0 * v1 - ic1
            ic2 = 2.0 * v2 - ic2
            if m == 0:
                y[j] = v2
            elif m == 1:
                y[j] = k * v1
            else:
                y[j] = v0 - k * v1 - v2
        pos = end
    return y


def onepole_lp(x, fc):
    a = exp(-TAU * fc / SR)
    b = 1.0 - a
    y = [0.0] * len(x)
    s = 0.0
    for i, v in enumerate(x):
        s = b * v + a * s
        y[i] = s
    return y


def dc_block(x, r=0.995):
    """y[n] = x[n] - x[n-1] + r*y[n-1]  (~18 Hz high-pass at 22.05 kHz)."""
    y = [0.0] * len(x)
    x1 = y1 = 0.0
    for i, v in enumerate(x):
        y1 = v - x1 + r * y1
        x1 = v
        y[i] = y1
    return y


# ----------------------------------------------------------------------------
# reverb (Freeverb topology, block-vectorised) and loop folding
# ----------------------------------------------------------------------------

def comb_damped(x, D, g, damp):
    """Feedback comb y[n] = x[n] + g*((1-d)*y[n-D] + d*y[n-D-1]).
    The 2-tap average in the loop damps highs on every pass (like Freeverb's
    one-pole damping).  Because the newest feedback sample is D samples old,
    whole blocks of up to D samples are computed with one comprehension."""
    n = len(x)
    pad = D + 1
    y = [0.0] * (pad + n)
    c0 = g * (1.0 - damp)
    c1 = g * damp
    pos = 0
    while pos < n:
        m = D if n - pos > D else n - pos
        b = pad + pos
        y[b:b + m] = [v + c0 * p + c1 * q for v, p, q in
                      zip(x[pos:pos + m], y[b - D:b - D + m], y[b - D - 1:b - D - 1 + m])]
        pos += m
    del y[:pad]
    return y


def allpass_fv(x, D, g=0.5):
    """Freeverb allpass: buf = x + g*buf[-D]; y = buf[-D] - x (block form)."""
    n = len(x)
    buf = [0.0] * (D + n)
    y = [0.0] * n
    pos = 0
    while pos < n:
        m = D if n - pos > D else n - pos
        xs = x[pos:pos + m]
        bd = buf[pos:pos + m]
        buf[D + pos:D + pos + m] = [v + g * d for v, d in zip(xs, bd)]
        y[pos:pos + m] = [d - v for v, d in zip(xs, bd)]
        pos += m
    return y


COMBS = (557, 593, 641, 677, 709, 743, 779, 811)    # Freeverb tunings / 2
ALLPASSES = (277, 221, 171, 113)


def reverb(x, room=0.86, damp=0.3, predelay=0.02):
    """Mono Freeverb-style reverb; output is len(x) + predelay samples."""
    src = [0.0] * ns(predelay) + x
    acc = None
    for D in COMBS:
        c = comb_damped(src, D, room, damp)
        acc = c if acc is None else [a + b for a, b in zip(acc, c)]
    acc = [v * 0.125 for v in acc]
    for D in ALLPASSES:
        acc = allpass_fv(acc, D, 0.5)
    return acc


def fold(buf, L):
    """Wrap everything past L back onto the start (circular / loop mixing)."""
    out = buf[:L]
    if len(out) < L:
        out += [0.0] * (L - len(out))
    pos = L
    while pos < len(buf):
        seg = buf[pos:pos + L]
        out[:len(seg)] = [a + b for a, b in zip(out, seg)]
        pos += L
    return out


def circular(x, fn, tail):
    """Exact periodic steady-state response of an LTI process `fn` to the
    periodic signal x: run on one period + silence, fold the tail back."""
    return fold(fn(x + [0.0] * tail), len(x))


# ----------------------------------------------------------------------------
# finishing / file IO
# ----------------------------------------------------------------------------

def fades(x, fin=0.002, fout=0.02):
    """Raised-cosine fade in/out; first and last samples become exactly 0."""
    n = len(x)
    a = min(n, max(2, ns(fin)))
    for i in range(a):
        x[i] *= 0.5 - 0.5 * cos(PI * i / a)
    b = min(n, max(2, ns(fout)))
    for i in range(b):
        x[n - 1 - i] *= 0.5 - 0.5 * cos(PI * i / b)
    return x


def normalize(x, peak_db):
    p = peak(x)
    if p <= 1e-12:
        return x
    g = 10.0 ** (peak_db / 20.0) / p
    return [v * g for v in x]


def soft_clip(x, knee=0.85):
    """Linear below the knee, tanh-shaped (C1-continuous) above it, |y| < 1."""
    r = 1.0 - knee
    nk = -knee
    return [v if nk <= v <= knee else
            (knee + r * tanh((v - knee) / r) if v > 0.0 else nk - r * tanh((nk - v) / r))
            for v in x]


def finish_sfx(x, fin=0.002, fout=0.02, lp=None):
    x = dc_block(x)
    if lp:
        x = filt(x, 'lp', lp)
    x = fades(x, fin, fout)
    return normalize(x, SFX_PEAK_DB)


def finish_loop(x):
    x = circular(x, dc_block, ns(0.4))
    return normalize(x, SFX_PEAK_DB)


def write_wav(path, x):
    data = array.array('h', [32767 if v >= 1.0 else (-32768 if v <= -1.0 else round(v * 32767.0))
                             for v in x])
    if sys.byteorder == 'big':
        data.byteswap()
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


def read_wav(path):
    with wave.open(path, 'rb') as w:
        params = (w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes())
        raw = w.readframes(w.getnframes())
    data = array.array('h')
    data.frombytes(raw)
    if sys.byteorder == 'big':
        data.byteswap()
    return params, data


# ----------------------------------------------------------------------------
# shared synthesis building blocks
# ----------------------------------------------------------------------------

def modal(f, dur, modes, att=0.002, rng=None, n=None):
    """Sum of exponentially decaying sine partials.
    modes = [(freq_ratio, amplitude, decay_tau_seconds), ...]."""
    n = n if n is not None else ns(dur)
    out = [0.0] * n
    for ratio, amp, tau in modes:
        fr = f * ratio
        if fr >= 0.45 * SR or amp == 0.0:
            continue
        w = TAU * fr / SR
        ph = rng.random() * TAU if rng else 0.0
        out = [o + amp * e * sin(ph + w * i)
               for i, o, e in zip(range(n), out, env_exp(n, tau))]
    if att > 0.0:
        apply_attack(out, att)
    return end_fade(out)


def fm_bell(f, dur, ratio=1.0, i0=1.5, i_tau=0.15, i_floor=0.2, tau=0.5, att=0.002):
    """Two-operator FM bell/celesta with decaying modulation index."""
    n = ns(dur)
    imax = max(0.0, (9500.0 - f) / (f * ratio) - 1.0)     # Carson's rule
    if i0 + i_floor > imax:
        s = imax / (i0 + i_floor)
        i0 *= s
        i_floor *= s
    wc = TAU * f / SR
    wm = wc * ratio
    out = [a * sin(wc * i + (i_floor + i0 * e) * sin(wm * i))
           for i, a, e in zip(range(n), env_exp(n, tau), env_exp(n, i_tau))]
    return end_fade(apply_attack(out, att))


GLOCK = [(1.0, 1.0, 0.30), (2.756, 0.32, 0.09), (5.404, 0.12, 0.035)]
CELESTA = [(1.0, 1.0, 0.45), (2.0, 0.10, 0.20), (4.0, 0.22, 0.07), (6.0, 0.05, 0.03)]


def chime(f, dur, tau_scale=1.0, modes=GLOCK, att=0.002, rng=None):
    return modal(f, dur, [(r, a, t * tau_scale) for r, a, t in modes], att=att, rng=rng)


def twinkles(buf, rng, count, t0, t1, f_lo, f_hi, amp=0.15, tau=0.035):
    """Sprinkle tiny high sine pings into buf between t0 and t1 seconds."""
    for _ in range(count):
        t = t0 + (t1 - t0) * rng.random()
        f = f_lo * (f_hi / f_lo) ** rng.random()
        p = modal(f, 0, [(1.0, 1.0, tau), (2.0, 0.2, tau * 0.5)], att=0.002,
                  n=ns(tau * 6.0))
        add_at(buf, p, ns(t), amp * (0.55 + 0.45 * rng.random()))
    return buf


def noise_band(n, rng, fc, q=0.8, mode='bp'):
    return svf(white(n, rng), fc, q, mode)


def click(n, rng, fc=3000.0, tau=0.0012, q=0.8):
    """Short filtered noise transient."""
    return svf(vmul(white(n, rng), env_exp(n, tau)), fc, q, 'bp')


def thump(dur, f0, f1, p_tau, a_tau, att=0.002):
    """Sine with an exponential pitch drop (kick/thud body)."""
    n = ns(dur)
    return end_fade(vmul(osc_sine(glide(n, f0, f1, p_tau)), env_ad(n, att, a_tau)))


def blip(dur, f0, f1, p_tau, a_tau, att=0.002, h2=0.0):
    """Pitch-swept sine blip (bubbles, pops); optional 2nd harmonic."""
    n = ns(dur)
    ph = phases(glide(n, f0, f1, p_tau))
    env = env_ad(n, att, a_tau)
    if h2:
        return end_fade([e * (sin(p) + h2 * sin(2.0 * p)) for p, e in zip(ph, env)])
    return end_fade([e * sin(p) for p, e in zip(ph, env)])


def grains(n, rng, count, t_lo, t_hi, shape=1.0, glen=(8, 40), amp_decay=True):
    """Sparse random noise grains (crunch / crackle excitation)."""
    out = [0.0] * n
    r = rng.random
    span = max(1e-6, t_hi - t_lo)
    for _ in range(count):
        u = r() ** shape
        t = t_lo + span * u
        i = ns(t)
        L = rng.randint(glen[0], glen[1])
        a = (0.25 + 0.75 * r()) * ((1.0 - 0.8 * u) if amp_decay else 1.0)
        k = 1.0 / L
        g = [a * (2.0 * r() - 1.0) * (1.0 - j * k) ** 2 for j in range(L)]
        add_at(out, g, i)
    return out


def brass(f, gate, rel=0.12, bright=1.0, vib=0.0, rng=None):
    """Brass-like voice: two detuned band-limited saws, a pitch scoop into the
    note, an envelope-tracking low-pass ("bwah") and optional late vibrato."""
    env = env_adsr(gate, 0.012, 0.09, 0.78, rel)
    n = len(env)
    scoop = env_exp(n, 0.018)
    freqs = [f * (1.0 - 0.02 * s) for s in scoop]          # ~35 cents below
    if vib:
        k = TAU * 5.6 / SR
        on, ramp = ns(0.15), ns(0.2)
        freqs = [v * (1.0 + vib * sin(k * i) * min(1.0, max(0.0, (i - on) / ramp)))
                 for i, v in enumerate(freqs)]
    table = wavetable('brass', f * 1.04)
    p0 = rng.random() if rng else 0.0
    a = osc_wt(table, freqs, p0)
    b = osc_wt(table, [v * 1.004 for v in freqs], (p0 + 0.37) % 1.0)
    fe = env_exp(n, 0.07)
    fc = [f * 1.2 + bright * (900.0 + 3600.0 * x + 1100.0 * e) for x, e in zip(fe, env)]
    s = svf([0.5 * (p + q) for p, q in zip(a, b)], fc, 0.8, 'lp')
    return vmul(s, env)


# ============================================================================
# SOUND EFFECTS
# ============================================================================

def sfx_ui_tap():
    rng = rng_for('sfx_ui_tap')
    out = blip(0.07, 560.0, 1300.0, 0.011, 0.016, att=0.002, h2=0.12)
    add_at(out, click(ns(0.01), rng, 3400.0, 0.0012, 0.9), 0, 0.30)
    add_at(out, blip(0.04, 300.0, 380.0, 0.01, 0.008), 0, 0.25)       # round body
    return finish_sfx(out, 0.002, 0.012)


def sfx_ui_back():
    rng = rng_for('sfx_ui_back')
    out = blip(0.08, 640.0, 360.0, 0.014, 0.02, att=0.002, h2=0.08)
    soft = onepole_lp(click(ns(0.012), rng, 1500.0, 0.0015, 0.7), 2500.0)
    add_at(out, soft, 0, 0.18)
    return finish_sfx(out, 0.002, 0.015)


def sfx_ui_open():
    rng = rng_for('sfx_ui_open')
    n = ns(0.28)
    m = ns(0.22)
    fc = sweep_exp(m, 700.0, 6000.0) + [6000.0] * (n - m)
    sw = vmul(svf(white(n, rng), fc, 1.3, 'bp'), env_hump(n, 0.45, 1.6, 2.2))
    out = vscale(sw, 0.55)
    add_at(out, chime(hz(88), 0.2, 0.5), ns(0.06), 0.42)      # E6
    add_at(out, chime(hz(95), 0.2, 0.55), ns(0.115), 0.40)    # B6
    return finish_sfx(out, 0.003, 0.05)


def sfx_ui_close():
    rng = rng_for('sfx_ui_close')
    n = ns(0.22)
    m = ns(0.18)
    fc = sweep_exp(m, 5200.0, 650.0) + [650.0] * (n - m)
    sw = vmul(svf(white(n, rng), fc, 1.2, 'bp'), env_hump(n, 0.3, 1.5, 2.0))
    out = vscale(sw, 0.6)
    add_at(out, blip(0.06, 520.0, 330.0, 0.012, 0.022), ns(0.125), 0.38)  # soft "tock"
    return finish_sfx(out, 0.003, 0.04)


def sfx_coin():
    rng = rng_for('sfx_coin')
    out = zeros(ns(0.30))
    n1 = modal(hz(86), 0.09, [(1, 1.0, 0.045), (2, 0.35, 0.025), (3, 0.16, 0.015)])   # D6
    n2 = modal(hz(93), 0.24, [(1, 1.0, 0.11), (2, 0.30, 0.05), (3, 0.12, 0.03),
                              (2.76, 0.16, 0.04), (5.40, 0.07, 0.02)])               # A6 "ching"
    add_at(out, n1, 0, 0.75)
    add_at(out, n2, ns(0.06), 0.9)
    add_at(out, click(ns(0.008), rng, 5000.0, 0.0008, 0.9), 0, 0.15)
    twinkles(out, rng, 5, 0.09, 0.22, 3000.0, 6500.0, amp=0.12, tau=0.022)
    return finish_sfx(out, 0.002, 0.04)


def sfx_coin_tick():
    out = modal(hz(100), 0.04, [(1, 1.0, 0.0065), (2, 0.18, 0.004)])   # E7 tick
    return finish_sfx(out, 0.002, 0.01)


def _star(level):
    rng = rng_for('sfx_star%d' % level)
    n = ns(0.42)
    out = zeros(n)
    base = {1: 84, 2: 88, 3: 91}[level]          # C6, E6, G6
    grace = {1: 79, 2: 84, 3: 88}[level]         # G5, C6, E6
    add_at(out, chime(hz(grace), 0.12, 0.35, CELESTA), 0, 0.45)
    add_at(out, chime(hz(base), 0.4, 0.7, CELESTA), ns(0.045), 0.70)
    add_at(out, chime(hz(base) * 1.0045, 0.4, 0.7, CELESTA), ns(0.045), 0.35)  # beating twin
    if level == 3:
        add_at(out, chime(hz(base + 12), 0.34, 0.6), ns(0.09), 0.30)
    twinkles(out, rng, 4 + level, 0.08, 0.33, hz(base) * 1.5, hz(base) * 3.2,
             amp=0.10 + 0.02 * level, tau=0.03)
    air = vmul(noise_band(n, rng, 7000.0, 2.0), env_hump(n, 0.35, 2.0, 2.0))
    add_at(out, air, 0, 0.05)
    return finish_sfx(out, 0.002, 0.08)


def sfx_star1():
    return _star(1)


def sfx_star2():
    return _star(2)


def sfx_star3():
    return _star(3)


def sfx_unlock():
    rng = rng_for('sfx_unlock')
    n = ns(0.95)
    out = zeros(n)
    for k, m in enumerate((84, 88, 91, 96)):           # C6 E6 G6 C7
        t = ns(0.085 * k)
        last = k == 3
        dur = 0.7 if last else 0.35
        add_at(out, fm_bell(hz(m), dur, 1.0, 1.4, 0.06, 0.15, tau=0.32 if last else 0.14), t, 0.55)
        add_at(out, chime(hz(m) * 2.0, dur * 0.6, 0.3), t, 0.10)
        add_at(out, fm_bell(hz(m - 12), dur, 1.0, 0.8, 0.08, 0.1, tau=0.3 if last else 0.12), t, 0.25)
    twinkles(out, rng, 9, 0.28, 0.78, 2500.0, 6000.0, amp=0.11, tau=0.035)
    air = vmul(noise_band(n, rng, 6500.0, 1.6), env_hump(n, 0.4, 1.5, 2.0))
    add_at(out, air, 0, 0.045)
    return finish_sfx(out, 0.002, 0.15)


def sfx_chest_open():
    rng = rng_for('sfx_chest_open')
    n = ns(1.25)
    out = zeros(n)
    add_at(out, thump(0.4, 150.0, 55.0, 0.04, 0.11), 0, 0.9)                     # lid thump
    add_at(out, modal(420.0, 0.12, [(1, 1.0, 0.025), (2.31, 0.5, 0.014), (3.9, 0.25, 0.008)]), 0, 0.35)
    lid = onepole_lp(vmul(white(ns(0.05), rng), env_exp(ns(0.05), 0.012)), 1800.0)
    add_at(out, lid, 0, 0.5)
    arp = (72, 76, 79, 84, 88, 91, 96)                  # C5 .. C7 rising arpeggio
    for k, m in enumerate(arp):
        t = ns(0.12 + 0.065 * k)
        last = k == len(arp) - 1
        add_at(out, fm_bell(hz(m), 0.9 if last else 0.5, 1.0, 1.2, 0.07, 0.15,
                            tau=0.45 if last else 0.22), t, 0.36 + 0.03 * k)
        add_at(out, chime(hz(m) * 2.0, 0.3, 0.4), t, 0.07)
    seg = n - ns(0.12)
    for m in (72, 76, 79):                              # warm triad swell
        add_at(out, vmul(tone(seg, hz(m), rng.random() * TAU), env_hump(seg, 0.35, 1.5, 1.5)),
               ns(0.12), 0.08)
    dn = n - ns(0.15)                                   # "magic dust" shimmer
    k1, k2 = TAU * 13.0 / SR, TAU * 7.3 / SR
    trem = [0.55 + 0.45 * sin(k1 * i) * sin(k2 * i + 1.0) for i in range(dn)]
    dust = vmul(vmul(noise_band(dn, rng, 6800.0, 1.4), trem), env_hump(dn, 0.45, 1.5, 2.0))
    add_at(out, dust, ns(0.15), 0.10)
    twinkles(out, rng, 12, 0.3, 1.0, 2500.0, 6500.0, amp=0.09, tau=0.04)
    return finish_sfx(out, 0.002, 0.2)


def sfx_wheel_tick():
    rng = rng_for('sfx_wheel_tick')
    out = modal(1850.0, 0.03, [(1, 1.0, 0.0045), (1.68, 0.5, 0.0035), (0.36, 0.55, 0.006)])
    m = ns(0.006)
    add_at(out, svf(vmul(white(m, rng), env_exp(m, 0.0008)), 2500.0, 0.7, 'hp'), 0, 0.35)
    return finish_sfx(out, 0.002, 0.008)


def sfx_wheel_win():
    rng = rng_for('sfx_wheel_win')
    n = ns(1.05)
    out = zeros(n)
    for t, g, ms in ((0.0, 0.085, (76,)), (0.11, 0.085, (79,)), (0.22, 0.55, (72, 76, 79, 84))):
        for m in ms:
            add_at(out, brass(hz(m), g, 0.14, 1.0, vib=0.004 if g > 0.3 else 0.0, rng=rng),
                   ns(t), 0.30)
    add_at(out, thump(0.45, 130.0, 72.0, 0.05, 0.14), ns(0.22), 0.35)
    for k, m in enumerate((96, 100, 103)):              # C7 E7 G7 sparkle
        add_at(out, chime(hz(m), 0.35, 0.6), ns(0.24 + 0.055 * k), 0.16)
    cn = ns(0.7)
    cym = vmul(noise_band(cn, rng, 5500.0, 0.6, 'hp'), env_ad(cn, 0.003, 0.22))
    add_at(out, cym, ns(0.22), 0.12)
    twinkles(out, rng, 6, 0.3, 0.85, 3000.0, 6500.0, 0.08)
    return finish_sfx(out, 0.002, 0.18)


def sfx_countdown():
    env = env_adsr(0.11, 0.003, 0.2, 0.9, 0.04)
    w = TAU * 880.0 / SR
    out = [e * (sin(w * i) + 0.12 * sin(2 * w * i) + 0.05 * sin(3 * w * i))
           for i, e in enumerate(env)]
    return finish_sfx(out, 0.002, 0.01)


def sfx_go():
    rng = rng_for('sfx_go')
    n = ns(0.52)
    out = zeros(n)
    env = env_adsr(0.33, 0.003, 0.25, 0.8, 0.17)
    w = TAU * 1760.0 / SR
    ph = [w * i for i in range(len(env))]
    add_at(out, [e * (sin(p) + 0.22 * sin(2 * p) + 0.08 * sin(3 * p)) for p, e in zip(ph, env)], 0, 0.5)
    add_at(out, [e * sin(p * 1.0017 + 0.5) for p, e in zip(ph, env)], 0, 0.25)   # chorus twin
    add_at(out, [e * sin(0.5 * p) for p, e in zip(ph, env)], 0, 0.30)            # octave body
    k = ns(0.42)
    fc = sweep_exp(k, 800.0, 6500.0) + [6500.0] * (n - k)
    add_at(out, vmul(svf(white(n, rng), fc, 1.1, 'bp'), env_hump(n, 0.5, 1.5, 2.0)), 0, 0.32)
    return finish_sfx(out, 0.002, 0.06)


def _snow_step(name, fc, n_grains, body_f, body_lvl):
    rng = rng_for(name)
    n = ns(0.09)
    exc = grains(n, rng, n_grains, 0.002, 0.065, shape=1.5, glen=(6, 30))
    crunch = svf(svf(exc, fc, 0.6, 'bp'), 700.0, 0.7, 'hp')
    out = vmul(crunch, env_ad(n, 0.002, 0.035))
    body = onepole_lp(onepole_lp(white(n, rng), body_f), body_f)
    add_at(out, vmul(body, env_ad(n, 0.003, 0.018)), 0, body_lvl)
    add_at(out, thump(0.05, 140.0, 90.0, 0.01, 0.015), 0, 0.12)
    return finish_sfx(out, 0.002, 0.015)


def sfx_step_snow1():
    return _snow_step('sfx_step_snow1', 2300.0, 40, 300.0, 0.8)


def sfx_step_snow2():
    return _snow_step('sfx_step_snow2', 1800.0, 48, 260.0, 0.9)


def sfx_roll_loop():
    """1.2 s seamless loop: everything is built on a circle (integer LFO
    cycles per loop, grains wrap around, filters run circularly)."""
    rng = rng_for('sfx_roll_loop')
    L = ns(1.2)
    k3, k5 = TAU * 3.0 / L, TAU * 5.0 / L
    rot = [0.78 + 0.16 * sin(k3 * i) + 0.06 * sin(k5 * i + 1.3) for i in range(L)]
    rum = circular(white(L, rng), lambda x: filt(filt(x, 'lp', 220.0, 0.8), 'hp', 55.0), ns(0.3))
    body = circular(white(L, rng), lambda x: svf(x, 520.0, 0.9, 'bp'), ns(0.2))
    exc = zeros(L)
    r = rng.random
    t = 0.0
    while True:
        t += -math.log(1.0 - r()) / 260.0               # Poisson grain clock
        if t >= 1.2:
            break
        i = ns(t)
        glen = rng.randint(6, 26)
        a = (0.2 + 0.8 * r() ** 2) * rot[i % L]
        add_wrap(exc, [a * (2.0 * r() - 1.0) * (1.0 - j / glen) ** 2 for j in range(glen)], i)
    crunch = circular(exc, lambda x: svf(svf(x, 2300.0, 0.7, 'bp'), 650.0, 0.7, 'hp'), ns(0.1))
    out = vmul(rum, rot)
    add_at(out, vmul(body, rot), 0, 0.55)
    add_at(out, crunch, 0, 2.6)
    out = circular(out, lambda x: filt(x, 'lp', 7000.0), ns(0.05))
    out = sat(normalize(out, 0.0), 2.2)        # memoryless -> loop stays seamless
    return finish_loop(out)


def sfx_bridge_block():
    rng = rng_for('sfx_bridge_block')
    n = ns(0.18)
    out = zeros(n)
    add_at(out, thump(0.18, 260.0, 110.0, 0.02, 0.045), 0, 0.55)
    add_at(out, blip(0.09, 520.0, 300.0, 0.02, 0.03, h2=0.2), 0, 0.7)      # "plop"
    m = ns(0.07)
    add_at(out, svf(vmul(white(m, rng), env_ad(m, 0.002, 0.016)), 1000.0, 1.0, 'bp'), 0, 1.1)
    add_at(out, svf(grains(m, rng, 18, 0.0, 0.045, 1.3, (5, 20)), 2600.0, 0.8, 'bp'), 0, 0.7)
    out = sat(normalize(out, 0.0), 1.3)
    return finish_sfx(out, 0.002, 0.03)


def sfx_splash():
    rng = rng_for('sfx_splash')
    n = ns(0.72)
    out = zeros(n)
    imp = filt(filt(vmul(white(n, rng), env_ad(n, 0.002, 0.09)), 'lp', 3800.0), 'hp', 260.0)
    add_at(out, imp, 0, 0.75)
    m = n - ns(0.02)
    spray = filt(vmul(white(m, rng), env_hump(m, 0.12, 1.2, 3.0)), 'hp', 2400.0)
    add_at(out, spray, ns(0.02), 0.22)
    add_at(out, thump(0.25, 190.0, 85.0, 0.05, 0.06), 0, 0.45)             # "plunk"
    for _ in range(24):                     # bubbles: rising damped sines (van den Doel)
        u = rng.random()
        t = 0.03 + 0.6 * u ** 1.8
        f0 = 350.0 * (1500.0 / 350.0) ** rng.random()
        d = 0.043 * f0 + 0.0014 * f0 ** 1.5
        xi = 0.12 + 0.12 * rng.random()
        k = ns(min(5.0 / d, 0.12))
        bub = vmul(osc_sine([f0 * (1.0 + xi * d * i / SR) for i in range(k)]),
                   env_ad(k, 0.001, 1.0 / d))
        add_at(out, end_fade(bub), ns(t), (0.55 - 0.4 * u) * (0.6 + 0.4 * rng.random()))
    return finish_sfx(out, 0.002, 0.12)


def sfx_bonk():
    n = ns(0.32)
    out = zeros(n)
    add_at(out, modal(900.0, 0.12, [(1, 1.0, 0.03), (2.17, 0.45, 0.015), (3.6, 0.2, 0.008)]), 0, 0.7)
    freqs = glide(n, 650.0, 170.0, 0.05)                    # rubbery pitch drop
    kv = TAU * 16.0 / SR
    freqs = [f * (1.0 + 0.035 * sin(kv * i) * (1.0 - o))   # wobble fades in
             for i, (f, o) in enumerate(zip(freqs, env_exp(n, 0.03)))]
    env = env_ad(n, 0.002, 0.11)
    boing = end_fade([e * (sin(p) + 0.4 * sin(2.0 * p) + 0.12 * sin(3.0 * p)) for p, e in zip(phases(freqs), env)])
    add_at(out, boing, 0, 0.9)
    add_at(out, thump(0.12, 130.0, 90.0, 0.03, 0.04), 0, 0.4)
    return finish_sfx(out, 0.002, 0.04)


def sfx_boost():
    rng = rng_for('sfx_boost')
    n = ns(0.62)
    m = ns(0.5)
    fc = sweep_exp(m, 350.0, 4900.0) + [4900.0] * (n - m)
    env = env_hump(n, 0.68, 1.6, 1.4)
    out = vscale(vmul(svf(white(n, rng), fc, 1.6, 'bp'), env), 0.8)
    add_at(out, vmul(svf(white(n, rng), [2.0 * f for f in fc], 3.0, 'bp'), env), 0, 0.25)
    tf = sweep_exp(m, 180.0, 810.0) + [810.0] * (n - m)
    add_at(out, vmul(svf(osc_wt(wavetable('saw', 820.0), tf), 2500.0, 0.7, 'lp'), env), 0, 0.22)
    return finish_sfx(out, 0.003, 0.05)


def sfx_shield():
    rng = rng_for('sfx_shield')
    env = env_adsr(0.42, 0.05, 0.3, 0.85, 0.2)
    n = len(env)
    out = zeros(n)
    for f, a in ((880.0, 0.5), (1318.5, 0.35), (1760.0, 0.3), (2217.5, 0.2), (2637.0, 0.15)):
        for det in (0.0, 3.3, -2.6):                         # detuned triplets shimmer
            add_at(out, tone(n, f + det, rng.random() * TAU), 0, a * 0.5)
    out = vmul(out, env)
    kt, w1, w2 = TAU * 9.0 / SR, TAU * 220.0 / SR, TAU * 440.0 / SR
    add_at(out, [e * (0.7 + 0.3 * sin(kt * i)) * (sin(w1 * i) + 0.5 * sin(w2 * i))
                 for i, e in enumerate(env)], 0, 0.3)
    k = ns(0.14)
    add_at(out, end_fade(vmul(osc_sine(sweep_exp(k, 1200.0, 4200.0)), env_ad(k, 0.002, 0.05))), 0, 0.25)
    twinkles(out, rng, 6, 0.05, 0.45, 3000.0, 7000.0, 0.07)
    return finish_sfx(out, 0.003, 0.06)


def sfx_powerup():
    rng = rng_for('sfx_powerup')
    out = zeros(ns(0.42))
    notes = (79, 83, 86, 91, 95, 98)                         # G5 B5 D6 G6 B6 D7
    for k, m in enumerate(notes):
        f = hz(m)
        last = k == len(notes) - 1
        q = ns(0.2 if last else 0.09)
        freqs = glide(q, f * 0.965, f, 0.012)
        env = env_ad(q, 0.002, 0.08 if last else 0.035)
        sq = svf(osc_wt(wavetable('square', f), freqs), min(9000.0, f * 4.0), 0.7, 'lp')
        s = [e * (0.6 * a + 0.4 * sin(p)) for a, e, p in zip(sq, env, phases(freqs))]
        add_at(out, end_fade(s), ns(0.042 * k), 0.5)
    add_at(out, chime(hz(103), 0.2, 0.5), ns(0.25), 0.18)
    twinkles(out, rng, 6, 0.22, 0.38, 3500.0, 7500.0, 0.08)
    return finish_sfx(out, 0.002, 0.05)


def sfx_gift():
    rng = rng_for('sfx_gift')
    out = zeros(ns(0.36))
    add_at(out, blip(0.08, 280.0, 950.0, 0.008, 0.022, h2=0.1), 0, 0.85)
    add_at(out, click(ns(0.01), rng, 2500.0, 0.001, 0.8), 0, 0.35)
    for k, (m, tau) in enumerate(((96, 0.08), (100, 0.08), (103, 0.14))):   # C7 E7 G7
        add_at(out, chime(hz(m), 0.25, tau / 0.3), ns(0.05 + 0.05 * k), 0.36)
    twinkles(out, rng, 5, 0.12, 0.3, 3500.0, 7500.0, 0.08)
    return finish_sfx(out, 0.002, 0.05)


def sfx_avalanche_loop():
    """2 s seamless deep rumble (circular construction, periodic LFOs)."""
    rng = rng_for('sfx_avalanche_loop')
    L = ns(2.0)
    comps = [(c, rng.random() * TAU, a) for c, a in ((1, 0.5), (2, 0.35), (3, 0.25), (5, 0.18), (7, 0.12))]
    raw = [0.0] * L
    for c, p, a in comps:
        w = TAU * c / L
        raw = [v + a * sin(w * i + p) for i, v in enumerate(raw)]
    lo, hi = min(raw), max(raw)
    mod = [0.55 + 0.45 * (v - lo) / (hi - lo) for v in raw]
    sub = circular(white(L, rng), lambda x: filt(filt(x, 'lp', 95.0, 0.7), 'lp', 95.0, 0.7), ns(0.5))
    mid = vmul(circular(white(L, rng), lambda x: svf(x, 230.0, 0.8, 'bp'), ns(0.3)), mod)
    chunks = zeros(L)
    for _ in range(16):                                      # tumbling snow chunks
        k = ns(0.06 + 0.08 * rng.random())
        g = end_fade(vmul(white(k, rng), env_ad(k, 0.004, k / SR / 3.5)))
        add_wrap(chunks, g, int(rng.random() * L), 0.4 + 0.6 * rng.random())
    chunks = circular(chunks, lambda x: filt(x, 'lp', 420.0, 0.8), ns(0.2))
    hiss = vmul(circular(white(L, rng), lambda x: filt(filt(x, 'hp', 2500.0), 'lp', 6000.0), ns(0.1)), mod)
    out = vscale(sub, 3.2)
    add_at(out, mid, 0, 1.3)
    add_at(out, chunks, 0, 1.4)
    add_at(out, hiss, 0, 0.05)
    out = sat(normalize(out, 0.0), 1.6)        # memoryless -> loop stays seamless
    return finish_loop(out)


def sfx_crumble():
    rng = rng_for('sfx_crumble')
    n = ns(0.82)
    out = zeros(n)
    r = rng.random
    for _ in range(38):                                       # cracks, denser early
        u = r()
        k = rng.randint(ns(0.002), ns(0.007))
        g = svf(vmul(white(k, rng), env_exp(k, k / SR / 3.0)), 1800.0 * 3.0 ** r(), 1.5 + 1.5 * r(), 'bp')
        add_at(out, end_fade(g), ns(0.62 * u ** 1.5), (1.0 - 0.75 * u) * (0.4 + 0.6 * r()))
    rum = vmul(onepole_lp(onepole_lp(white(n, rng), 380.0), 380.0), env_ad(n, 0.005, 0.25))
    add_at(out, rum, 0, 1.6)
    add_at(out, svf(grains(n, rng, 70, 0.0, 0.7, 1.4, (10, 60)), 1200.0, 0.8, 'bp'), 0, 0.5)
    for _ in range(4):                                        # icy tinks
        add_at(out, modal(2500.0 * 2.0 ** r(), 0.15, [(1, 1.0, 0.03 + 0.03 * r())]), ns(0.05 + 0.4 * r()), 0.15)
    return finish_sfx(out, 0.002, 0.15)


def sfx_win_fanfare():
    rng = rng_for('sfx_win_fanfare')
    n = ns(2.6)
    out = zeros(n)
    C, F, G, CC = (67, 72, 76), (69, 72, 77), (71, 74, 79), (72, 76, 79, 84)
    for t, g, chord in ((0.00, 0.11, C), (0.15, 0.11, C), (0.30, 0.11, C),
                        (0.45, 0.24, F), (0.72, 0.24, G), (1.00, 1.05, CC)):
        long_ = g > 0.5
        for m in chord:
            add_at(out, brass(hz(m), g, 0.18 if long_ else 0.08, 1.0,
                              vib=0.005 if long_ else 0.0, rng=rng), ns(t), 0.24)
    for t, g, m in ((0.0, 0.4, 48), (0.45, 0.24, 41), (0.72, 0.24, 43), (1.0, 1.05, 36)):
        add_at(out, brass(hz(m), g, 0.15, 0.5, rng=rng), ns(t), 0.22)         # bass
    for t, lvl in ((0.0, 0.24), (0.45, 0.27), (0.72, 0.27), (1.0, 0.42)):
        add_at(out, thump(0.5, 140.0, 75.0, 0.05, 0.16), ns(t), lvl)          # timpani-ish
    for k in range(10):                                                       # roll into the hit
        m = ns(0.05)
        hit = svf(vmul(white(m, rng), env_exp(m, 0.012)), 1800.0, 0.6, 'hp')
        add_at(out, end_fade(hit), ns(0.75 + 0.025 * k), 0.12 + 0.025 * k)
    cn = ns(1.5)
    crash = vmul(filt(white(cn, rng), 'hp', 4200.0), env_ad(cn, 0.003, 0.55))
    add_at(out, end_fade(crash), ns(1.0), 0.22)
    for k, m in enumerate((84, 88, 91, 96, 100)):                             # bell arpeggio
        add_at(out, chime(hz(m), 1.0, 1.1), ns(1.0 + 0.07 * k), 0.2)
    twinkles(out, rng, 14, 1.15, 2.2, 2500.0, 6500.0, 0.08, 0.05)
    return finish_sfx(out, 0.002, 0.4)


def sfx_lose():
    """Gentle comedic 'aww': formant-filtered voice G4 -> Eb4 -> C4 with a droop."""
    rng = rng_for('sfx_lose')
    n = ns(1.25)
    on2, on3 = ns(0.24), ns(0.48)
    tgt = []
    for i in range(n):
        t = i / SR
        if i < on2:
            m = 67.0
        elif i < on3:
            m = 63.0
        else:
            m = 60.0 - 1.2 * min(1.0, max(0.0, (t - 0.55) / 0.6))
        tgt.append(hz(m))
    a = exp(-1.0 / (0.025 * SR))                  # portamento between syllables
    s = tgt[0]
    f = []
    for v in tgt:
        s = v + (s - v) * a
        f.append(s)
    kv = TAU * 5.5 / SR
    f = [v * (1.0 + 0.012 * sin(kv * i) * min(1.0, max(0.0, (i / SR - 0.6) / 0.3)))
         for i, v in enumerate(f)]
    src = osc_wt(wavetable('saw', hz(67) * 1.03), f, rng.random())
    f2 = []                                       # F2 opens on each syllable: "w-aw"
    for i in range(n):
        o = 0 if i < on2 else (on2 if i < on3 else on3)
        f2.append(1150.0 - 420.0 * exp(-(i - o) / (0.06 * SR)))
    voice = svf(src, 650.0, 3.5, 'bp')
    add_at(voice, svf(src, f2, 4.5, 'bp'), 0, 0.7)
    add_at(voice, svf(src, 2600.0, 6.0, 'bp'), 0, 0.12)
    add_at(voice, onepole_lp(src, 900.0), 0, 0.25)
    env = env_adsr(1.0, 0.02, 0.6, 0.6, 0.25)
    art = [1.0 - 0.5 * exp(-((t - 0.24) / 0.025) ** 2) - 0.5 * exp(-((t - 0.48) / 0.025) ** 2)
           for t in (i / SR for i in range(len(env)))]
    out = vmul(vmul(pad_to(voice, len(env)), env), art)
    add_at(out, vmul(osc_sine(pad_to(f, len(env))), env), 0, 0.12)     # soft body
    return finish_sfx(out, 0.003, 0.08, lp=5000.0)


def sfx_error():
    out = zeros(ns(0.26))
    for t, f in ((0.0, 196.0), (0.13, 185.0)):
        env = env_adsr(0.085, 0.004, 0.1, 0.85, 0.025)
        m = len(env)
        src = svf(osc_wt_const(wavetable('reed', f), m, f), 1600.0, 0.7, 'lp')
        add_at(out, vmul(src, env), ns(t), 0.8)
        add_at(out, vmul(tone(m, f * 0.5), env), ns(t), 0.08)
    return finish_sfx(out, 0.002, 0.02)


def sfx_whoosh():
    rng = rng_for('sfx_whoosh')
    n = ns(0.36)
    k = ns(0.15)
    fc = sweep_exp(k, 500.0, 2600.0) + sweep_exp(n - k, 2600.0, 850.0)
    env = env_hump(n, 0.42, 1.8, 1.6)
    out = vmul(svf(white(n, rng), fc, 1.0, 'bp'), env)
    add_at(out, vmul(filt(white(n, rng), 'hp', 3500.0), env), 0, 0.12)
    return finish_sfx(out, 0.003, 0.04)


def sfx_cheer():
    rng = rng_for('sfx_cheer')
    n = ns(1.55)
    out = zeros(n)
    r = rng.random
    claps = zeros(n)
    t = 0.0
    while True:                                   # applause: dense random claps
        t += -math.log(1.0 - r()) / 140.0
        if t > 1.4:
            break
        k = ns(0.012)
        c = svf(vmul(white(k, rng), env_exp(k, 0.0025 + 0.002 * r())), 900.0 * 2.8 ** r(), 1.0 + r(), 'bp')
        add_at(claps, c, ns(t), 0.4 + 0.6 * r())
    a0, s1, s2 = ns(0.12), ns(0.8), ns(0.7)
    cenv = [min(1.0, i / a0) * (1.0 if i < s1 else max(0.0, 1.0 - (i - s1) / s2)) for i in range(n)]
    add_at(out, vmul(claps, cenv), 0, 1.0)
    wash = svf(white(n, rng), 700.0, 1.2, 'bp')   # crowd "yay" wash
    add_at(wash, svf(white(n, rng), 1600.0, 1.5, 'bp'), 0, 0.7)
    k1, k2 = TAU * 3.1 / SR, TAU * 4.7 / SR
    wm = [c * (0.75 + 0.15 * sin(k1 * i) + 0.1 * sin(k2 * i + 2.0)) for i, c in enumerate(cenv)]
    add_at(out, vmul(wash, wm), 0, 0.35)
    for t0, f, d in ((0.04, 523.25, 0.2), (0.33, 659.25, 0.18), (0.62, 783.99, 0.24)):
        env = env_adsr(d, 0.008, 0.1, 0.8, 0.04)  # party-horn toots (C5 E5 G5)
        m = len(env)
        kv = TAU * 13.0 / SR
        freqs = [v * (1.0 + 0.012 * sin(kv * i)) for i, v in enumerate(glide(m, f * 0.84, f, 0.025))]
        horn = svf(osc_wt(wavetable('reed', f * 1.02), freqs), 1500.0, 0.9, 'bp')
        add_at(out, vmul(horn, env), ns(t0), 0.5)
    twinkles(out, rng, 8, 0.1, 1.2, 2500.0, 6000.0, 0.06)
    return finish_sfx(out, 0.003, 0.15)


def sfx_pop():
    rng = rng_for('sfx_pop')
    out = blip(0.08, 380.0, 1200.0, 0.009, 0.018, att=0.002, h2=0.15)
    add_at(out, modal(220.0, 0.03, [(1, 1.0, 0.008)]), 0, 0.3)
    add_at(out, click(ns(0.01), rng, 2500.0, 0.001, 0.7), 0, 0.4)
    return finish_sfx(out, 0.002, 0.012)


def sfx_ice_crack():
    rng = rng_for('sfx_ice_crack')
    n = ns(0.42)
    out = zeros(n)
    m = ns(0.03)
    add_at(out, filt(vmul(white(m, rng), env_ad(m, 0.002, 0.003)), 'hp', 900.0), 0, 0.7)
    for t, a in ((0.035, 0.6), (0.07, 0.45), (0.11, 0.3), (0.16, 0.2)):     # follow-up cracks
        k = ns(0.012)
        c = svf(vmul(white(k, rng), env_ad(k, 0.0005, 0.0012)), 2000.0 + 3000.0 * rng.random(), 1.4, 'bp')
        add_at(out, end_fade(c), ns(t), a * 1.3)
    ring = modal(1.0, 0.4, [(2100.0, 0.25, 0.09), (3370.0, 0.2, 0.07), (5150.0, 0.12, 0.05),
                            (1350.0, 0.15, 0.12)])
    add_at(out, ring, 0, 1.3)
    k = ns(0.17)                                  # dispersive "pew" of lake ice
    add_at(out, end_fade(vmul(osc_sine(sweep_exp(k, 3800.0, 600.0)), env_ad(k, 0.002, 0.06))), ns(0.01), 0.25)
    add_at(out, thump(0.15, 110.0, 70.0, 0.02, 0.05), 0, 0.2)
    return finish_sfx(out, 0.002, 0.06)


def sfx_finale_step():
    rng = rng_for('sfx_finale_step')
    out = modal(hz(72), 0.26, [(1.0, 1.0, 0.11), (3.98, 0.5, 0.028), (9.9, 0.1, 0.01), (2.0, 0.06, 0.05)])
    m = ns(0.02)
    add_at(out, onepole_lp(vmul(white(m, rng), env_exp(m, 0.0012)), 3000.0), 0, 0.25)   # mallet
    return finish_sfx(out, 0.002, 0.04)


# ============================================================================
# MUSIC -- instruments (each renders one note; Voice caches identical notes)
# ============================================================================

class Voice:
    """Note renderer with a cache: identical (pitch, gate) notes render once."""

    def __init__(self, fn, **kw):
        self.fn = fn
        self.kw = kw
        self.cache = {}

    def __call__(self, midi, gate):
        key = (midi, round(gate, 4))
        b = self.cache.get(key)
        if b is None:
            b = self.fn(midi, gate, **self.kw)
            self.cache[key] = b
        return b


def ins_celesta(m, gate):
    """FM celesta / music-box bell with a bright 4th-partial 'ting'."""
    f = hz(m)
    ring = gate + 0.5
    y = fm_bell(f, ring, 1.0, 0.9, 0.16, 0.22, tau=0.75, att=0.003)
    add_at(y, modal(f, ring, [(4.0, 0.16, 0.06), (2.0, 0.05, 0.25)], att=0.003), 0)
    return release_tail(y, ns(gate + 0.05), ns(0.45))


def ins_harp(m, gate, ring=1.6):
    """Karplus-Strong pluck.  Loop = z^-D * g*[h0 + h1 z^-1 + h2 z^-2]
    (2-tap damping average merged with a linear fractional delay); the
    fraction is solved so the loop's phase delay at f0 gives exact tuning."""
    f = hz(m)
    rng = random.Random(1000 + m)
    P = SR / f
    D = int(P - 0.5)
    target = P - D
    w0 = TAU * f / SR

    def taps(x):
        return 0.5 * (1.0 - x), 0.5, 0.5 * x

    def pdelay(x):
        h0, h1, h2 = taps(x)
        return math.atan2(h1 * sin(w0) + h2 * sin(2 * w0),
                          h0 + h1 * cos(w0) + h2 * cos(2 * w0)) / w0

    lo, hi = 0.0, 1.0
    for _ in range(40):
        mid = 0.5 * (lo + hi)
        if pdelay(mid) < target:
            lo = mid
        else:
            hi = mid
    h0, h1, h2 = taps(0.5 * (lo + hi))
    mag = abs(complex(h0 + h1 * cos(w0) + h2 * cos(2 * w0),
                      -(h1 * sin(w0) + h2 * sin(2 * w0))))
    t60 = 2.4 * min(1.0, (300.0 / f) ** 0.35)
    g = min(0.9995, 10.0 ** (-3.0 * P / (t60 * SR)) / mag)
    c0, c1, c2 = g * h0, g * h1, g * h2
    n = ns(ring)
    L0 = int(P) + 1
    exc = white(L0, rng)
    for _ in range(2):                                   # softer, harp-like pluck
        exc = [0.5 * (a + b) for a, b in zip(exc, exc[1:] + exc[:1])]
    mu = sum(exc) / L0
    exc = [v - mu for v in exc]
    pk = peak(exc) or 1.0
    xin = [v / pk for v in exc] + [0.0] * (n - L0)
    pad = D + 2
    y = [0.0] * (pad + n)
    pos = 0
    while pos < n:
        mm = D if n - pos > D else n - pos
        b = pad + pos
        y[b:b + mm] = [v + c0 * p + c1 * q + c2 * r for v, p, q, r in
                       zip(xin[pos:pos + mm], y[b - D:b - D + mm],
                           y[b - D - 1:b - D - 1 + mm], y[b - D - 2:b - D - 2 + mm])]
        pos += mm
    out = onepole_lp(y[pad:], 4500.0)
    lvl = rms(out[:ns(0.2)]) or 1.0                     # even out random excitations
    out = [v * (0.3 / lvl) for v in out]
    apply_attack(out, 0.002)
    return end_fade(out, 0.2)


def ins_pad(m, gate, rel=0.9):
    """Soft detuned (3 x +-7 cent) mellow-saw pad."""
    f = hz(m)
    rng = random.Random(2000 + m)
    env = env_adsr(gate, 0.35, 0.6, 0.85, rel)
    n = len(env)
    table = wavetable('soft', f * 1.01)
    acc = zeros(n)
    for det in (-0.0042, 0.0, 0.0042):
        add_at(acc, osc_wt_const(table, n, f * (1.0 + det), rng.random()), 0, 1.0 / 3.0)
    return vmul(onepole_lp(acc, 2200.0), env)


def ins_sine_bass(m, gate):
    """Round sine bass; 2nd/3rd harmonics keep it audible on phone speakers."""
    f = hz(m)
    env = env_adsr(gate, 0.008, 0.3, 0.7, 0.09)
    w = TAU * f / SR
    return [e * (sin(w * i) + 0.3 * sin(2 * w * i) + 0.12 * sin(3 * w * i))
            for i, e in enumerate(env)]


def ins_pluck(m, gate, shape='pulse', bright=1.0, rel=0.07):
    """Bright plucky lead: 2 detuned band-limited oscillators + decaying LPF."""
    f = hz(m)
    env = env_adsr(gate, 0.003, 0.2, 0.45, rel)
    n = len(env)
    table = wavetable(shape, f * 1.01)
    a = osc_wt_const(table, n, f * 0.9985, 0.0)
    b = osc_wt_const(table, n, f * 1.0015, 0.33)
    fc = [f * 1.5 + bright * (900.0 + 5200.0 * e) for e in env_exp(n, 0.09)]
    return vmul(svf([0.5 * (p + q) for p, q in zip(a, b)], fc, 0.9, 'lp'), env)


def ins_glock(m, gate):
    f = hz(m)
    ring = min(gate + 0.3, 0.9)
    return chime(f, ring, 0.6, GLOCK, att=0.002)


def ins_bass(m, gate, bright=1.0):
    """Driving synth bass: saw+square through an enveloped LPF, plus sine sub."""
    f = hz(m)
    env = env_adsr(gate, 0.003, 0.12, 0.65, 0.035)
    n = len(env)
    saw = osc_wt_const(wavetable('saw', f), n, f, 0.0)
    sq = osc_wt_const(wavetable('square', f), n, f, 0.25)
    fc = [300.0 + 2.0 * f + bright * 2400.0 * e for e in env_exp(n, 0.06)]
    s = svf([0.6 * a + 0.4 * b for a, b in zip(saw, sq)], fc, 1.2, 'lp')
    w = TAU * f / SR
    return [e * (v + 0.5 * sin(w * i)) for i, (v, e) in enumerate(zip(s, env))]


def ins_stab(m, gate, shape='square', cutoff=2600.0, detune=0.0):
    """Short chord stab (offbeat organ-pluck, or detuned saw 'strings')."""
    f = hz(m)
    env = env_adsr(gate, 0.003, 0.08, 0.3, 0.05)
    n = len(env)
    table = wavetable(shape, f * 1.01)
    dets = (0.0,) if not detune else (-detune, 0.0, detune)
    acc = zeros(n)
    for k, d in enumerate(dets):
        add_at(acc, osc_wt_const(table, n, f * (1.0 + d), 0.21 * k), 0, 1.0 / len(dets))
    fc = [cutoff * (0.6 + 0.8 * e) for e in env_exp(n, 0.05)]
    return vmul(svf(acc, fc, 0.8, 'lp'), env)


# ---- drum kit ---------------------------------------------------------------

def drum_kick():
    n = ns(0.32)
    body = vmul(osc_sine(glide(n, 165.0, 52.0, 0.032)), env_ad(n, 0.0015, 0.16))
    m = ns(0.006)
    add_at(body, vmul(filt(white(m, random.Random(7)), 'hp', 2500.0), env_exp(m, 0.0012)), 0, 0.25)
    return end_fade(sat(body, 1.8))


def drum_snare(seed=11):
    rng = random.Random(seed)
    n = ns(0.28)
    out = vscale(vmul(osc_sine(glide(n, 230.0, 185.0, 0.01)), env_ad(n, 0.001, 0.045)), 0.5)
    add_at(out, vmul(osc_sine(glide(n, 400.0, 330.0, 0.01)), env_ad(n, 0.001, 0.03)), 0, 0.3)
    nz = filt(filt(white(n, rng), 'hp', 1200.0), 'lp', 9000.0)
    add_at(out, vmul(nz, env_ad(n, 0.001, 0.09)), 0, 0.85)
    return end_fade(out)


def drum_hat(seed, decay=0.012, length=0.06):
    rng = random.Random(seed)
    n = ns(length)
    nz = filt(filt(white(n, rng), 'hp', 6500.0, 0.8), 'hp', 6500.0, 0.8)
    return end_fade(vmul(nz, env_ad(n, 0.0008, decay)))


def drum_crash(seed=5):
    rng = random.Random(seed)
    n = ns(1.6)
    out = vmul(filt(white(n, rng), 'hp', 3800.0), env_ad(n, 0.002, 0.45))
    add_at(out, modal(1.0, 1.6, [(3150.0, 0.12, 0.5), (4370.0, 0.1, 0.4), (5530.0, 0.08, 0.35),
                                 (6720.0, 0.06, 0.3)], rng=rng), 0)
    return end_fade(out, 0.2)


def drum_tom(f0, f1, seed):
    rng = random.Random(seed)
    n = ns(0.35)
    body = vmul(osc_sine(glide(n, f0, f1, 0.06)), env_ad(n, 0.0015, 0.13))
    add_at(body, vmul(osc_sine(glide(n, f0 * 1.59, f1 * 1.59, 0.05)), env_ad(n, 0.0015, 0.05)), 0, 0.2)
    m = ns(0.02)
    add_at(body, filt(vmul(white(m, rng), env_exp(m, 0.003)), 'bp', 1500.0, 0.7), 0, 0.3)
    return end_fade(sat(body, 1.3))


def perc_shaker(seed, accent):
    rng = random.Random(seed)
    n = ns(0.07)
    return vmul(svf(white(n, rng), 5200.0, 1.1, 'bp'), env_hump(n, 0.25 if accent else 0.18, 1.5, 3.0))


def perc_sleigh(seed):
    """Sleigh-bell shake: a cluster of tiny inharmonic jingles + hiss."""
    rng = random.Random(seed)
    n = ns(0.22)
    out = zeros(n)
    for j in range(7):
        t = 0.03 * rng.random() ** 1.5 + (0.0 if j < 4 else 0.012 * j)
        base = 2500.0 * 2.0 ** rng.random()
        p = modal(base, 0.15, [(1.0, 1.0, 0.035), (1.47, 0.6, 0.03), (2.13, 0.4, 0.02)],
                  att=0.0008, rng=rng)
        add_at(out, p, ns(t), 0.3 + 0.7 * rng.random())
    add_at(out, vmul(filt(white(n, rng), 'hp', 5000.0), env_ad(n, 0.001, 0.04)), 0, 0.35)
    return end_fade(out)


# ============================================================================
# MUSIC -- theory helpers, track buffer and circular mixdown
# ============================================================================

PC = {'C': 0, 'C#': 1, 'Db': 1, 'D': 2, 'D#': 3, 'Eb': 3, 'E': 4, 'F': 5, 'F#': 6,
      'Gb': 6, 'G': 7, 'G#': 8, 'Ab': 8, 'A': 9, 'A#': 10, 'Bb': 10, 'B': 11}
QUAL = {'': (0, 4, 7), 'm': (0, 3, 7), 'maj7': (0, 4, 7, 11), 'm7': (0, 3, 7, 10),
        '7': (0, 4, 7, 10)}
DEBUG_STEMS = False


def note_num(s):
    """'F#5' -> MIDI number."""
    return 12 * (int(s[-1]) + 1) + PC[s[:-1]]


def parse_chord(s):
    root = s[:2] if len(s) > 1 and s[1] in '#b' else s[:1]
    return PC[root], QUAL[s[len(root):]]


def place(pc, lo):
    """Lowest MIDI note >= lo having pitch class pc."""
    return lo + (pc - lo) % 12


def parse_bar(s):
    """'C5:1 A4:.5 r:.5 ...' -> [(beat_in_bar, beats, midi|None)]; must fill 4 beats."""
    out = []
    t = 0.0
    for tok in s.split():
        name, d = tok.split(':')
        out.append((t, float(d), None if name == 'r' else note_num(name)))
        t += float(d)
    assert abs(t - 4.0) < 1e-9, s
    return out


def parse_prog(bars):
    """['Fmaj7', 'Am7:2 Dm7:2', ...] -> [(abs_beat, beats, root_pc, intervals)]."""
    out = []
    for b, s in enumerate(bars):
        t = 0.0
        for tok in s.split():
            ch, d = (tok.split(':') + ['4'])[:2]
            root, iv = parse_chord(ch)
            out.append((b * 4 + t, float(d), root, iv))
            t += float(d)
        assert abs(t - 4.0) < 1e-9, s
    return out


class Track:
    """Dry bus + reverb-send bus, one loop long plus a tail that later gets
    folded back onto the start (so releases/echoes wrap seamlessly)."""

    def __init__(self, bpm, bars=16, seed=1, tail=4.0):
        self.bpm = bpm
        self.spb = SR * 60.0 / bpm
        self.L = int(round(self.spb * 4 * bars))
        self.dry = zeros(self.L + ns(tail))
        self.send = zeros(self.L + ns(tail))
        self.rng = random.Random(seed)
        self.stems = {} if DEBUG_STEMS else None

    def sec(self, beats):
        return beats * 60.0 / self.bpm

    def add(self, buf, beat, gain, send=0.0, jitter=0.0, stem=None):
        p = int(round(beat * self.spb))
        if jitter:
            p = max(0, p + int(round((2.0 * self.rng.random() - 1.0) * jitter * SR)))
        add_at(self.dry, buf, p, gain)
        if send:
            add_at(self.send, buf, p, gain * send)
        if self.stems is not None and stem:
            st = self.stems.get(stem)
            if st is None:
                st = self.stems[stem] = zeros(len(self.dry))
            add_at(st, buf, p, gain)


def mixdown(tr, wet_ratio, room, damp, shelf_db=-3.0):
    """Fold buses -> circular reverb -> circular master EQ/DC block ->
    soft-knee clip -> normalise.  Every LTI stage is applied circularly, the
    clipper is memoryless, so the result is exactly periodic (seamless)."""
    L = tr.L
    dry = fold(tr.dry, L)
    send = fold(tr.send, L)
    tr.dry = tr.send = None
    wet = circular(send, lambda x: reverb(x, room, damp, 0.022), ns(4.5))
    rd, rw = rms(dry), rms(wet)
    if rw > 1e-9:
        wet = vscale(wet, wet_ratio * rd / rw)          # modest, calibrated wet level
    mix = [a + b for a, b in zip(dry, wet)]

    def master(x):
        x = filt(x, 'hs', 6500.0, 0.7071, shelf_db)      # tame the top
        x = filt(x, 'lp', 10000.0, 0.7071)
        x = filt(x, 'hp', 30.0, 0.7071)
        return dc_block(x)

    mix = circular(mix, master, ns(1.0))
    if tr.stems is not None:
        ref = rms(mix) or 1.0
        for k, st in sorted(tr.stems.items()):
            print('    stem %-7s %6.1f dB' % (k, 20.0 * math.log10(max(1e-9, rms(fold(st, L)) / ref))))
        print('    wet     %6.1f dB' % (20.0 * math.log10(max(1e-9, rms(wet) / ref))))
    mix = normalize(mix, 20.0 * math.log10(1.3))          # peaks 30 % over the knee range
    if tr.stems is not None:
        print('    soft-clipped samples: %.2f %%' % (100.0 * sum(1 for v in mix if abs(v) > 0.8) / len(mix)))
    return normalize(soft_clip(mix, 0.8), MUSIC_PEAK_DB)


# ---- music_menu: cozy winter theme, F major, 100 bpm, 16 bars ---------------

MENU_PROG = ['Fmaj7', 'Am7', 'Bbmaj7', 'C', 'Dm7', 'Bb', 'Gm7', 'C7',
             'Fmaj7', 'A7', 'Dm7', 'F7', 'Bbmaj7', 'C', 'Am7:2 Dm7:2', 'Gm7:2 C7:2']

MENU_MELODY = [                       # original tune (played an octave up)
    'C5:1 A4:.5 C5:.5 F5:1.5 E5:.5',
    'E5:1 D5:.5 C5:.5 E5:2',
    'D5:1 Bb4:.5 D5:.5 G5:1.5 F5:.5',
    'E5:1 F5:.5 E5:.5 D5:1 C5:1',
    'A4:1 F4:.5 A4:.5 D5:1.5 C5:.5',
    'D5:1 C5:.5 Bb4:.5 D5:2',
    'Bb4:1 G4:.5 Bb4:.5 F5:1.5 E5:.5',
    'E5:1.5 D5:.5 C5:1 Bb4:1',
    'A5:1.5 G5:.5 F5:1 C5:1',
    'E5:1 C#5:.5 E5:.5 A5:1.5 G5:.5',
    'F5:1.5 E5:.5 D5:1 A4:1',
    'C5:1 Eb5:1 D5:.5 C5:.5 A4:1',
    'D5:1 Bb4:.5 D5:.5 G5:1.5 F5:.5',
    'E5:1.5 F5:.5 G5:2',
    'A5:1 G5:.5 E5:.5 F5:1 D5:1',
    'Bb4:1 D5:.5 C5:.5 E5:1 G5:1',
]


def music_menu():
    tr = Track(100, 16, seed=101)
    lead, harp, pad, bass = Voice(ins_celesta), Voice(ins_harp), Voice(ins_pad), Voice(ins_sine_bass)
    prog = parse_prog(MENU_PROG)
    for b, bar in enumerate(MENU_MELODY):            # celesta lead + dotted-8th echoes
        for t, d, m in parse_bar(bar):
            if m is None:
                continue
            beat = b * 4 + t
            g = 0.42 * (1.0 if t in (0.0, 2.0) else 0.88)
            buf = lead(m + 12, tr.sec(d) * 0.92)
            tr.add(buf, beat, g, send=0.35, jitter=0.002, stem='lead')
            tr.add(buf, beat + 0.75, g * 0.30, send=0.6, stem='echo')
            tr.add(buf, beat + 1.5, g * 0.12, send=0.8, stem='echo')
    pattern = (0, 1, 2, 3, 4, 3, 2, 1)
    for beat0, beats, root, iv in prog:
        r = place(root, 50)                          # harp arpeggio, up-down 8ths
        voicing = (r, r + 7, r + 12, r + 12 + iv[1], r + 12 + (iv[3] if len(iv) > 3 else 7))
        for k in range(int(beats * 2)):
            tr.add(harp(voicing[pattern[k % 8]], 0.3), beat0 + 0.5 * k,
                   0.36 * (0.85 + 0.15 * tr.rng.random()), send=0.3, jitter=0.003, stem='harp')
        for m in sorted(place((root + i) % 12, 55) for i in iv):   # soft pad
            tr.add(pad(m, tr.sec(beats) * 0.98), beat0, 0.11, send=0.45, stem='pad')
        R = place(root, 40)                          # round sine bass
        fifth = R + 7 if R + 7 <= 55 else R - 5
        pat = ((0, 1.5, R), (1.5, 0.5, R), (2, 2, fifth)) if beats >= 4 else ((0, beats, R),)
        for t, d, m in pat:
            tr.add(bass(m, tr.sec(d) * 0.9), beat0 + t, 0.26, stem='bass')
    shakers = [perc_shaker(300 + k, k % 2 == 1) for k in range(4)]
    sleighs = [perc_sleigh(400 + k) for k in range(3)]
    vel8 = (0.35, 0.7, 0.4, 0.75, 0.35, 0.7, 0.4, 0.8)
    for b in range(16):
        for k in range(8):
            tr.add(shakers[(b * 8 + k) % 4], b * 4 + 0.5 * k, 0.35 * vel8[k], send=0.15,
                   jitter=0.004, stem='shaker')
        for beat in (1, 3):
            tr.add(sleighs[(b + beat) % 3], b * 4 + beat, 0.17, send=0.25, jitter=0.003, stem='sleigh')
        if b in (7, 15):
            for t in (3.5, 3.75):
                tr.add(sleighs[int(t * 4) % 3], b * 4 + t, 0.11, send=0.25, stem='sleigh')
    return mixdown(tr, 0.30, 0.88, 0.35, -3.5)


# ---- music_race: upbeat, G major, 140 bpm, 16 bars --------------------------

RACE_PROG = ['G', 'D', 'Em', 'C', 'G', 'D', 'C', 'D', 'Em', 'C', 'G', 'D', 'C', 'D', 'G', 'D']

RACE_MELODY = [                       # original hook
    'D5:.5 G5:.5 G5:.5 A5:.5 B5:.75 A5:.25 G5:1',
    'F#5:.5 A5:.5 A5:.5 F#5:.5 D5:1 r:.5 A4:.5',
    'B4:.5 E5:.5 E5:.5 F#5:.5 G5:.75 F#5:.25 E5:1',
    'G5:.5 E5:.5 C5:.5 E5:.5 D5:1.5 r:.5',
    'D5:.5 G5:.5 G5:.5 A5:.5 B5:.75 A5:.25 G5:1',
    'F#5:.5 A5:.5 A5:.5 F#5:.5 D5:1 r:.5 A4:.5',
    'E5:.5 G5:.5 G5:.5 A5:.5 G5:.75 E5:.25 C5:1',
    'A4:.5 D5:.5 F#5:.5 A5:.5 G5:.5 F#5:.5 E5:.5 D5:.5',
    'B5:1 G5:.5 E5:.5 B5:1 A5:.5 G5:.5',
    'G5:1 E5:.5 C5:.5 G5:1 E5:.5 D5:.5',
    'D5:1 B4:.5 D5:.5 G5:1 A5:.5 B5:.5',
    'A5:1.5 F#5:.5 D5:.5 E5:.5 F#5:.5 A5:.5',
    'G5:.5 E5:.5 G5:.5 A5:.5 G5:.5 E5:.5 C5:1',
    'F#5:.5 D5:.5 F#5:.5 A5:.5 F#5:.5 D5:.5 A4:1',
    'B4:.5 D5:.5 G5:.5 B5:.5 A5:.5 G5:.5 D5:1',
    'A5:.5 F#5:.5 E5:.5 D5:.5 E5:.5 F#5:.5 A5:1',
]


def music_race():
    tr = Track(140, 16, seed=202)
    lead = Voice(ins_pluck, shape='pulse', bright=1.0)
    bell, bass = Voice(ins_glock), Voice(ins_bass)
    stab = Voice(ins_stab, shape='square', cutoff=2600.0)
    prog = parse_prog(RACE_PROG)
    for b, bar in enumerate(RACE_MELODY):
        for t, d, m in parse_bar(bar):
            if m is None:
                continue
            beat = b * 4 + t
            buf = lead(m, tr.sec(d) * 0.85)
            tr.add(buf, beat, 0.8, send=0.22, stem='lead')
            tr.add(buf, beat + 0.75, 0.8 * 0.22, send=0.5, stem='echo')
            if b >= 8:                                   # glockenspiel double for lift
                tr.add(bell(m + 12, tr.sec(d)), beat, 0.17, send=0.3, stem='bell')
    pats = ((0, 0, 12, 0, 0, 0, 12, 0), (0, 12, 0, 12, 0, 12, 7, 12))
    for beat0, beats, root, iv in prog:
        R = place(root, 40)
        pat = pats[0] if beat0 < 32 else pats[1]
        for k in range(8):                               # driving 8th-note bass
            tr.add(bass(R + pat[k], tr.sec(0.5) * 0.8), beat0 + 0.5 * k, 0.25, stem='bass')
        notes = sorted(place((root + i) % 12, 55) for i in iv[:3])
        for k in range(4):                               # offbeat chord stabs
            for m in notes:
                tr.add(stab(m, tr.sec(0.35)), beat0 + k + 0.5, 0.1, send=0.15, stem='stab')
    kick, snare, crash = drum_kick(), drum_snare(), drum_crash()
    hats = [drum_hat(500 + k) for k in range(4)]
    ohat = drum_hat(510, 0.09, 0.35)
    sleighs = [perc_sleigh(600 + k) for k in range(4)]
    hv = (0.3, 0.15, 0.8, 0.15)
    for b in range(16):
        base = b * 4
        fill = b in (7, 15)
        for k in range(4):
            tr.add(kick, base + k, 0.6, stem='kick')
        for k in (1, 3):
            if not (fill and k == 3):
                tr.add(snare, base + k, 0.7, send=0.15, stem='snare')
        for s in range(16):
            if b % 2 == 1 and s == 14:
                tr.add(ohat, base + 3.5, 0.3, send=0.08, stem='hat')
            else:
                tr.add(hats[s % 4], base + 0.25 * s, 0.36 * hv[s % 4], send=0.05, stem='hat')
        for k in range(8):
            tr.add(sleighs[(b * 8 + k) % 4], base + 0.5 * k, 0.09 * (0.55 if k % 2 == 0 else 0.9),
                   send=0.12, stem='sleigh')
    for b in (0, 8):
        tr.add(crash, b * 4, 0.25, send=0.25, stem='crash')
    for t, v in ((2.0, 0.5), (2.5, 0.6), (3.0, 0.7), (3.25, 0.75), (3.5, 0.85), (3.75, 1.0)):
        tr.add(snare, 28 + t, 0.7 * v, send=0.15, stem='snare')
    for t, v in ((3.0, 0.6), (3.25, 0.75), (3.5, 0.85), (3.75, 1.0)):
        tr.add(snare, 60 + t, 0.7 * v, send=0.15, stem='snare')
    return mixdown(tr, 0.18, 0.84, 0.3, -2.5)


# ---- music_boss: tense-but-fun chase, E minor, 150 bpm, 16 bars -------------

BOSS_PROG = ['Em', 'Em', 'C', 'D', 'Em', 'Em', 'Am', 'B7', 'C', 'D', 'Em', 'Em', 'Am', 'B7', 'Em', 'B7']

BOSS_MELODY = [                       # original minor riff
    'E5:.5 B4:.5 E5:.5 G5:.5 F#5:.5 E5:.5 D#5:.5 E5:.5',
    'G5:.5 F#5:.5 E5:.5 B4:.5 C5:1 B4:1',
    'C5:.5 E5:.5 G5:.5 E5:.5 A5:.75 G5:.25 E5:1',
    'F#5:.5 A5:.5 F#5:.5 D5:.5 E5:.5 F#5:.5 A5:1',
    'E5:.5 B4:.5 E5:.5 G5:.5 F#5:.5 E5:.5 D#5:.5 E5:.5',
    'G5:.5 F#5:.5 E5:.5 B4:.5 C5:1 B4:1',
    'A5:.5 E5:.5 C5:.5 E5:.5 A5:.5 B5:.5 C6:1',
    'B5:.5 A5:.5 G5:.5 F#5:.5 D#5:1 B4:1',
    'G5:1.5 E5:.5 C5:1 E5:1',
    'F#5:1.5 D5:.5 A4:1 D5:1',
    'E5:.5 G5:.5 B5:.5 G5:.5 E5:.5 G5:.5 B5:1',
    'C6:1 B5:.5 A5:.5 G5:1 F#5:1',
    'E5:.5 A5:.5 C6:.5 A5:.5 E5:.5 C5:.5 A4:1',
    'D#5:.5 F#5:.5 A5:.5 F#5:.5 B5:1 A5:1',
    'G5:.5 F#5:.5 E5:.5 B4:.5 E5:1 G5:1',
    'F#5:.5 D#5:.5 B4:.5 D#5:.5 F#5:2',
]


def music_boss():
    tr = Track(150, 16, seed=303)
    lead = Voice(ins_pluck, shape='saw', bright=0.85)
    ost = Voice(ins_pluck, shape='pulse', bright=0.6, rel=0.04)
    bass = Voice(ins_bass, bright=1.2)
    stab = Voice(ins_stab, shape='saw', cutoff=2400.0, detune=0.005)
    prog = parse_prog(BOSS_PROG)
    for b, bar in enumerate(BOSS_MELODY):
        for t, d, m in parse_bar(bar):
            if m is None:
                continue
            beat = b * 4 + t
            buf = lead(m, tr.sec(d) * 0.8)
            tr.add(buf, beat, 0.7, send=0.2, stem='lead')
            tr.add(buf, beat + 0.75, 0.7 * 0.2, send=0.5, stem='echo')
    gallop = ((0, 0), (0.5, 0), (0.75, 0), (1, 0), (1.5, 0), (1.75, 0),
              (2, 12), (2.5, 0), (2.75, 0), (3, 0), (3.5, 7), (3.75, 12))
    for beat0, beats, root, iv in prog:
        notes = sorted(place((root + i) % 12, 59) for i in iv)
        for t in (0.0, 0.75, 1.5, 2.0, 2.75, 3.5):          # 3-3-2 staccato string stabs
            for m in notes:
                tr.add(stab(m, tr.sec(0.3)), beat0 + t, 0.42, send=0.18, stem='stab')
            tr.add(stab(place(root, 47), tr.sec(0.3)), beat0 + t, 0.24, send=0.1, stem='stab')
        R = place(root, 40)
        for t, iv_ in gallop:                                # galloping bass
            tr.add(bass(R + iv_, tr.sec(0.4 if t % 1.0 == 0.0 else 0.2)), beat0 + t, 0.24, stem='bass')
        if beat0 >= 32:                                      # pulsing ostinato, 2nd half
            hi = (place(root, 76), place((root + 7) % 12, 76))
            for k in range(8):
                tr.add(ost(hi[k % 2], tr.sec(0.3)), beat0 + 0.5 * k, 0.24, send=0.25, stem='ost')
    kick, snare, crash = drum_kick(), drum_snare(12), drum_crash(9)
    hats = [drum_hat(700 + k) for k in range(4)]
    toms = {'h': drum_tom(240.0, 180.0, 31), 'm': drum_tom(180.0, 135.0, 32), 'l': drum_tom(130.0, 98.0, 33)}
    sleighs = [perc_sleigh(800 + k) for k in range(3)]
    hv = (0.6, 0.25, 0.45, 0.25)
    fills = {3: 'short', 7: 'short', 11: 'short', 15: 'long'}
    for b in range(16):
        base = b * 4
        fill = fills.get(b)
        for k in range(4):
            tr.add(kick, base + k, 0.6, stem='kick')
        if b % 2 == 1 and not fill:
            tr.add(kick, base + 3.5, 0.4, stem='kick')
        for k in (1, 3):
            if not (fill and k == 3):
                tr.add(snare, base + k, 0.7, send=0.15, stem='snare')
        stop = 16 if not fill else (12 if fill == 'short' else 8)
        for s in range(stop):
            tr.add(hats[s % 4], base + 0.25 * s, 0.42 * hv[s % 4], send=0.05, stem='hat')
        if fill:
            seq = 'hhml' if fill == 'short' else 'hhmmmlll'
            for s, tom in zip(range(16 - len(seq), 16), seq):
                tr.add(toms[tom], base + 0.25 * s, 0.35, send=0.15, stem='tom')
        if b >= 8:
            for k in range(8):
                tr.add(sleighs[(b + k) % 3], base + 0.5 * k, 0.085 * (0.5 if k % 2 == 0 else 0.9),
                       send=0.12, stem='sleigh')
    for b in (0, 8):
        tr.add(crash, b * 4, 0.25, send=0.25, stem='crash')
    return mixdown(tr, 0.16, 0.83, 0.3, -2.5)


# ============================================================================
# registry, verification, manifest, CLI
# ============================================================================

# (name, generator, kind, nominal seconds from the brief, description)
SOUNDS = [
    ('sfx_ui_tap', sfx_ui_tap, 'sfx', 0.07, 'Soft bubbly pop/click for button taps (rising sine blip + tiny click).'),
    ('sfx_ui_back', sfx_ui_back, 'sfx', 0.08, 'Lower, softer descending tap for back/cancel.'),
    ('sfx_ui_open', sfx_ui_open, 'sfx', 0.25, 'Airy upward band-pass swoosh with an E6-B6 two-note chime.'),
    ('sfx_ui_close', sfx_ui_close, 'sfx', 0.20, 'Downward swoosh ending in a soft closing tock.'),
    ('sfx_coin', sfx_coin, 'sfx', 0.25, 'Bright two-note D6-A6 "ka-ching" with metallic ring and sparkles.'),
    ('sfx_coin_tick', sfx_coin_tick, 'sfx', 0.04, 'Tiny high E7 tick for coin count-up.'),
    ('sfx_star1', sfx_star1, 'sfx', 0.40, 'Magical celesta chime G5->C6 with shimmer and twinkles (star 1).'),
    ('sfx_star2', sfx_star2, 'sfx', 0.40, 'Same chime a step higher, C6->E6 (star 2).'),
    ('sfx_star3', sfx_star3, 'sfx', 0.40, 'Highest chime E6->G6 plus a G7 sparkle (star 3).'),
    ('sfx_unlock', sfx_unlock, 'sfx', 0.90, 'Cheerful ascending C6-E6-G6-C7 bell jingle with sparkle tail.'),
    ('sfx_chest_open', sfx_chest_open, 'sfx', 1.20, 'Wooden lid thump, then a shimmering rising C-major bell arpeggio with magic dust.'),
    ('sfx_wheel_tick', sfx_wheel_tick, 'sfx', 0.03, 'Short wooden click for each prize-wheel peg.'),
    ('sfx_wheel_win', sfx_wheel_win, 'sfx', 1.00, 'Short brass fanfare (E5, G5, C-major chord) with timpani hit and bells.'),
    ('sfx_countdown', sfx_countdown, 'sfx', 0.15, 'Clean 880 Hz beep for 3-2-1.'),
    ('sfx_go', sfx_go, 'sfx', 0.50, 'Higher, brighter 1760 Hz chorused beep plus a rising whoosh.'),
    ('sfx_step_snow1', sfx_step_snow1, 'sfx', 0.09, 'Soft snow-crunch footstep (filtered noise grains + soft body), variation 1.'),
    ('sfx_step_snow2', sfx_step_snow2, 'sfx', 0.09, 'Snow-crunch footstep, slightly darker variation 2.'),
    ('sfx_roll_loop', sfx_roll_loop, 'loop', 1.20, 'Seamless rolling-snowball loop: low rumble, packed-snow body, granular crunch with a rotation pulse.'),
    ('sfx_bridge_block', sfx_bridge_block, 'sfx', 0.18, 'Soft heavy packed-snow thump/plop (game pitches it up per block).'),
    ('sfx_splash', sfx_splash, 'sfx', 0.70, 'Water splash: noise burst, spray and plunk with rising-pitch bubbles.'),
    ('sfx_bonk', sfx_bonk, 'sfx', 0.30, 'Cute cartoon bonk: woody knock + rubbery boing with quick pitch drop and wobble.'),
    ('sfx_boost', sfx_boost, 'sfx', 0.60, 'Rising resonant whoosh with a climbing tone.'),
    ('sfx_shield', sfx_shield, 'sfx', 0.60, 'Shimmering crystalline hum (detuned A-major partial cluster + tremolo hum).'),
    ('sfx_powerup', sfx_powerup, 'sfx', 0.40, 'Bright rising G-major arpeggio pickup with sparkle.'),
    ('sfx_gift', sfx_gift, 'sfx', 0.35, 'Cute rising pop followed by three sparkling chimes.'),
    ('sfx_avalanche_loop', sfx_avalanche_loop, 'loop', 2.00, 'Seamless deep avalanche rumble with tumbling snow chunks.'),
    ('sfx_crumble', sfx_crumble, 'sfx', 0.80, 'Crumbling snow/ice: dense cracks, debris and low rumble with icy tinks.'),
    ('sfx_win_fanfare', sfx_win_fanfare, 'sfx', 2.50, 'Celebratory C-major brass fanfare (stab x3, F, G, C) with timpani, roll, crash and bells.'),
    ('sfx_lose', sfx_lose, 'sfx', 1.20, 'Gentle comedic "aww": formant voice G4-Eb4-C4 with a droop and vibrato.'),
    ('sfx_error', sfx_error, 'sfx', 0.25, 'Soft low double buzz.'),
    ('sfx_whoosh', sfx_whoosh, 'sfx', 0.35, 'Generic swoosh (band-pass sweep up then down).'),
    ('sfx_cheer', sfx_cheer, 'sfx', 1.50, 'Crowd celebration: applause texture, crowd wash and party-horn toots.'),
    ('sfx_pop', sfx_pop, 'sfx', 0.08, 'Bubble pop.'),
    ('sfx_ice_crack', sfx_ice_crack, 'sfx', 0.40, 'Sharp ice crack with follow-up cracks, icy ring and a frozen-lake "pew".'),
    ('sfx_finale_step', sfx_finale_step, 'sfx', 0.25, 'Bright marimba C5 note (game pitches it up per stair step).'),
    ('music_menu', music_menu, 'music', 38.4, 'Cozy winter theme, F major, 100 bpm, 16 bars: celesta lead, Karplus-Strong harp arpeggios, soft pad, sine bass, shaker, sleigh bells.'),
    ('music_race', music_race, 'music', 27.43, 'Upbeat race theme, G major, 140 bpm, 16 bars: plucky lead hook, glockenspiel double, driving bass, four-on-the-floor kit, offbeat stabs, sleigh bells.'),
    ('music_boss', music_boss, 'music', 25.6, 'Tense-but-fun chase, E minor, 150 bpm, 16 bars: staccato saw-string stabs, galloping bass, minor riff, tom fills, sleigh bells.'),
]


def generate(names):
    os.makedirs(RAW_DIR, exist_ok=True)
    for name, fn, kind, nominal, desc in SOUNDS:
        if names and name not in names:
            continue
        t0 = time.time()
        x = fn()
        write_wav(os.path.join(RAW_DIR, name + '.wav'), x)
        print('  %-20s %7.3f s   rendered in %.2fs' % (name, len(x) / SR, time.time() - t0))


def _db(v):
    return 20.0 * math.log10(v) if v > 0 else -999.0


def verify():
    """Re-open every WAV and check format, duration, peak, DC, clipping,
    clean starts/ends (one-shots) and seam continuity (loops)."""
    rows, ok_all = [], True
    print('\n%-20s %8s %7s %8s %7s %7s  %s' % ('file', 'dur(s)', 'peakdB', 'rms dB', 'zcr/s', 'DC', 'edges / seam'))
    for name, fn, kind, nominal, desc in SOUNDS:
        path = os.path.join(RAW_DIR, name + '.wav')
        if not os.path.exists(path):
            print('%-20s MISSING' % name)
            ok_all = False
            continue
        (ch, sw, fr, nf), d = read_wav(path)
        probs = []
        if (ch, sw, fr) != (1, 2, SR):
            probs.append('format %r' % ((ch, sw, fr),))
        dur = nf / float(fr)
        if abs(dur - nominal) > 0.25 * nominal + 0.02:
            probs.append('duration')
        x = [v / 32768.0 for v in d]
        pk = peak(x)
        target = MUSIC_PEAK_DB if kind == 'music' else SFX_PEAK_DB
        if abs(_db(pk) - target) > 0.25:
            probs.append('peak')
        dc = sum(x) / len(x)
        if abs(dc) > 0.002:
            probs.append('DC')
        clipped = sum(1 for v in d if v >= 32767 or v <= -32768)
        if clipped:
            probs.append('clipped x%d' % clipped)
        zcr = sum(1 for a, b in zip(x, x[1:]) if (a < 0.0) != (b < 0.0)) / dur
        if kind == 'sfx':
            edge = 'first=%d last=%d LSB' % (d[0], d[-1])
            if abs(d[0]) > 2 or abs(d[-1]) > 2:
                probs.append('edge click')
        else:
            diffs = sorted(abs(b - a) for a, b in zip(d, d[1:]))
            p99 = diffs[int(0.99 * (len(diffs) - 1))] or 1
            jump = abs(d[0] - d[-1])
            d2 = sorted(abs(c - 2 * b + a) for a, b, c in zip(d, d[1:], d[2:]))
            p99b = d2[int(0.99 * (len(d2) - 1))] or 1
            jump2 = max(abs(d[1] - 2 * d[0] + d[-1]), abs(d[0] - 2 * d[-1] + d[-2]))
            whole = rms(x) or 1e-9
            q = ns(0.25)
            hl, tl = _db(rms(x[:q]) / whole), _db(rms(x[-q:]) / whole)
            edge = ('seam step %d (p99 %d), 2nd-diff %d (p99 %d); head %+.1f / tail %+.1f dB'
                    % (jump, p99, jump2, p99b, hl, tl))
            if jump > p99 or jump2 > p99b or min(hl, tl) < -24.0:
                probs.append('seam')
        r = rms(x)
        status = 'OK' if not probs else 'FAIL: ' + ', '.join(probs)
        ok_all = ok_all and not probs
        print('%-20s %8.3f %7.2f %8.1f %7.0f %7.4f  %s  %s' % (name, dur, _db(pk), _db(r), zcr, dc, edge, status))
        rows.append((name, kind, dur, _db(pk), os.path.getsize(path), desc))
    total = sum(r[4] for r in rows)
    print('\n%d files, total %.2f MB (%d bytes)  ->  %s' % (len(rows), total / 1048576.0, total,
                                                            'ALL CHECKS PASSED' if ok_all else 'SOME CHECKS FAILED'))
    return ok_all, rows


def write_manifest(rows):
    total = sum(r[4] for r in rows)
    kinds = {'sfx': 'one-shot', 'loop': 'loop', 'music': 'music loop'}
    lines = [
        '# Frosty Roll Rally - audio manifest',
        '',
        'All audio is 100% original and procedurally synthesised by `tools/gen_audio.py`',
        '(Python 3 standard library only, fixed seeds, bit-identical on every run).',
        'Regenerate from the project root with:',
        '',
        '    python3 -I tools/gen_audio.py',
        '',
        'Format: WAV, PCM 16-bit, mono, 22050 Hz, in `app/src/main/res/raw/`.',
        'One-shot SFX are peak-normalised to -1 dBFS and start/end on silence (>= 2 ms raised-cosine',
        'fade-in, fade-out tail). Music is peak-normalised to -1.5 dBFS after a soft-knee clipper.',
        'Loops (`*_loop`, `music_*`) are rendered circularly: note releases, echoes, reverb and filter',
        'tails that ring past the loop end are folded back onto the start, so they repeat seamlessly',
        '(set `MediaPlayer.isLooping = true` / SoundPool `loop = -1`). Relative loudness between',
        'sounds should be balanced in-game via playback volume.',
        '',
        '| File | Type | Duration (s) | Peak (dBFS) | Description |',
        '|---|---|---:|---:|---|',
    ]
    for name, kind, dur, pk, size, desc in rows:
        lines.append('| `%s.wav` | %s | %.3f | %.1f | %s |' % (name, kinds[kind], dur, pk, desc))
    lines += [
        '',
        'Music loop lengths are sample-exact: menu 64 beats x 13230 = 846720 samples (38.400 s),',
        'race 64 x 9450 = 604800 samples (27.429 s), boss 64 x 8820 = 564480 samples (25.600 s).',
        '',
        'Total: %d files, %.2f MB.' % (len(rows), total / 1048576.0),
        '',
    ]
    with open(MANIFEST_PATH, 'w') as fh:
        fh.write('\n'.join(lines))


def main(argv):
    global DEBUG_STEMS
    flags = {a for a in argv if a.startswith('--')}
    names = [a for a in argv if not a.startswith('--')]
    known = {s[0] for s in SOUNDS}
    bad = [n for n in names if n not in known]
    if bad:
        print('unknown sound(s): %s' % ', '.join(bad))
        return 2
    DEBUG_STEMS = '--stems' in flags
    t0 = time.time()
    if '--verify' not in flags:
        print('Rendering to %s' % RAW_DIR)
        generate(names)
        print('generation time: %.1f s' % (time.time() - t0))
    ok, rows = verify()
    if len(rows) == len(SOUNDS):
        write_manifest(rows)
        print('manifest: %s' % MANIFEST_PATH)
    print('total time: %.1f s' % (time.time() - t0))
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))

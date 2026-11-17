#!/usr/bin/env python3
"""Frosty Roll Rally - procedural 2D art generator.

Everything (launcher icons, splash icon, logo, mascot, UI icon set, store art)
is drawn from code with Pillow only. No external images, no randomness without
a fixed seed, so every run produces identical files.

Run from the project root:

    python3 -I tools/gen_art.py            # everything
    python3 -I tools/gen_art.py icons logo # only some groups

Groups: launcher, splash, mascot, logo, icons, store, xml, manifest.

Technique: each asset is drawn at 4x supersampling (layered masks, gradients,
rim lights, specular blobs, blurred shadows, thick navy outlines made by an
exact Minkowski dilation) and downscaled with LANCZOS.
"""
import math
import os
import random
import sys

try:
    from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont
except ImportError:  # `python3 -I` drops the user site-packages dir, where Pillow may live.
    import site

    _user_site = site.getusersitepackages()
    if _user_site and _user_site not in sys.path:
        sys.path.append(_user_site)
    from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

_RS = getattr(Image, "Resampling", Image)
LANCZOS, BICUBIC, BILINEAR = _RS.LANCZOS, _RS.BICUBIC, _RS.BILINEAR

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
NODPI = os.path.join(RES, "drawable-nodpi")
STORE = os.path.join(ROOT, "store")
FONT_DISPLAY = os.path.join(RES, "font", "lilita_one.ttf")
FONT_BODY = os.path.join(RES, "font", "nunito.ttf")

SS = 4  # supersampling factor

# --------------------------------------------------------------------------
# Colour
# --------------------------------------------------------------------------


def hx(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def rgba(c):
    c = tuple(c)
    return c + (255,) * (4 - len(c))


def mix(c1, c2, t):
    c1, c2 = rgba(c1), rgba(c2)
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(4))


def lighten(c, t):
    return mix(c, (255, 255, 255, rgba(c)[3]), t)


def shade(c, t):
    """Darken toward a cool deep indigo (keeps shadows saturated, never grey)."""
    return mix(c, (34, 26, 82, rgba(c)[3]), t)


def alpha(c, a):
    c = rgba(c)
    return c[:3] + (int(a),)


NIGHT = hx("#1E1B4B")
DEEP = hx("#2E2A7A")
SKY = hx("#5EC8FF")
ICE = hx("#DFF6FF")
MINT = hx("#3DF5C8")
BERRY = hx("#FF4D8D")
GOLD = hx("#FFC83D")
CORAL = hx("#FF7A45")
NAVY = hx("#1B2A4A")
WHITE = hx("#FFFFFF")
SNOW1 = hx("#CFE6FF")
SNOW2 = hx("#A9CCF0")

GOLD_L = hx("#FFE68A")
GOLD_D = hx("#E8921C")
GOLD_DD = hx("#B5620F")
BERRY_L = hx("#FF8DB8")
BERRY_D = hx("#C8246A")
MINT_L = hx("#B6FFEC")
MINT_D = hx("#13B391")
SKY_L = hx("#B5E9FF")
SKY_D = hx("#2F8FE0")
SLATE = hx("#6A78A8")
SLATE_D = hx("#465285")
SLATE_L = hx("#94A2CC")
EYE_TOP, EYE_MID, EYE_LOW, EYE_REF = hx("#2E3470"), hx("#14173A"), hx("#1C2556"), hx("#3F57B8")
MOUTH = hx("#6A1636")

# Mono mode renders a single-colour silhouette (Android 13 themed icon): "ink" colours
# (outlines, eyes, mouth) become holes, every solid fill becomes white, soft effects vanish.
MONO = [False]
INK = {NAVY[:3], EYE_TOP[:3], EYE_MID[:3], EYE_LOW[:3], MOUTH[:3]}


def _ink(c):
    return rgba(c)[:3] in INK

# --------------------------------------------------------------------------
# Small math helpers
# --------------------------------------------------------------------------


def clamp01(v):
    return 0.0 if v < 0 else (1.0 if v > 1 else v)


def lerp(a, b, t):
    return a + (b - a) * t


def smooth(t):
    t = clamp01(t)
    return t * t * (3 - 2 * t)


def rot_pts(pts, cx, cy, deg):
    """Rotate points around (cx, cy); positive degrees turn clockwise on screen."""
    a = math.radians(deg)
    ca, sa = math.cos(a), math.sin(a)
    return [(cx + (x - cx) * ca - (y - cy) * sa, cy + (x - cx) * sa + (y - cy) * ca) for x, y in pts]


def ellipse_pts(cx, cy, rx, ry, ang=0.0, n=96):
    pts = [(cx + rx * math.cos(2 * math.pi * i / n), cy + ry * math.sin(2 * math.pi * i / n)) for i in range(n)]
    return rot_pts(pts, cx, cy, ang) if ang else pts


def rrect_pts(x0, y0, x1, y1, r, n=10):
    r = max(0.0, min(r, (x1 - x0) / 2.0, (y1 - y0) / 2.0))
    pts = []
    for cx, cy, a0 in ((x1 - r, y0 + r, -90), (x1 - r, y1 - r, 0), (x0 + r, y1 - r, 90), (x0 + r, y0 + r, 180)):
        for i in range(n + 1):
            a = math.radians(a0 + 90.0 * i / n)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def star_pts(cx, cy, ro, ri, n=5, rot=-90.0):
    pts = []
    for i in range(2 * n):
        r = ro if i % 2 == 0 else ri
        a = math.radians(rot + 180.0 * i / n)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def twinkle_pts(cx, cy, r, p=2.3, rot=0.0, n=72, squash=1.0):
    """Four-point sparkle with concave sides (super-astroid)."""
    pts = []
    for i in range(n):
        t = 2 * math.pi * i / n
        c, s = math.cos(t), math.sin(t)
        pts.append((cx + r * math.copysign(abs(c) ** p, c), cy + r * squash * math.copysign(abs(s) ** p, s)))
    return rot_pts(pts, cx, cy, rot) if rot else pts


def catmull(pts, closed=True, n=14):
    out = []
    P = list(pts)
    m = len(P)
    rng = range(m) if closed else range(m - 1)
    for i in rng:
        p0 = P[(i - 1) % m] if closed else P[max(i - 1, 0)]
        p1 = P[i]
        p2 = P[(i + 1) % m] if closed else P[min(i + 1, m - 1)]
        p3 = P[(i + 2) % m] if closed else P[min(i + 2, m - 1)]
        for k in range(n):
            t = k / float(n)
            t2, t3 = t * t, t * t * t
            out.append(tuple(
                0.5 * ((2 * p1[q]) + (-p0[q] + p2[q]) * t + (2 * p0[q] - 5 * p1[q] + 4 * p2[q] - p3[q]) * t2
                       + (-p0[q] + 3 * p1[q] - 3 * p2[q] + p3[q]) * t3) for q in (0, 1)))
    if not closed:
        out.append(tuple(P[-1]))
    return out


def bezier(p0, p1, p2, p3, n=24):
    out = []
    for i in range(n + 1):
        t = i / float(n)
        u = 1 - t
        out.append((u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0],
                    u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1]))
    return out


def arc_pts(cx, cy, r, a0, a1, n=48):
    return [(cx + r * math.cos(math.radians(lerp(a0, a1, i / float(n)))),
             cy + r * math.sin(math.radians(lerp(a0, a1, i / float(n))))) for i in range(n + 1)]


def taper_pts(center, wfn):
    """Closed polygon around a centre polyline; wfn(t) gives the full width at t in [0, 1]."""
    n = len(center)
    L = [0.0]
    for i in range(1, n):
        L.append(L[-1] + math.hypot(center[i][0] - center[i - 1][0], center[i][1] - center[i - 1][1]))
    total = L[-1] or 1.0
    left, right = [], []
    for i in range(n):
        a = center[max(i - 1, 0)]
        b = center[min(i + 1, n - 1)]
        dx, dy = b[0] - a[0], b[1] - a[1]
        ln = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / ln, dx / ln
        hw = wfn(L[i] / total) / 2.0
        left.append((center[i][0] + nx * hw, center[i][1] + ny * hw))
        right.append((center[i][0] - nx * hw, center[i][1] - ny * hw))
    return left + right[::-1]


# --------------------------------------------------------------------------
# Masks (mode "L")
# --------------------------------------------------------------------------


def mnew(size, v=0):
    return Image.new("L", size, v)


def m_poly(size, pts):
    m = mnew(size)
    if len(pts) >= 3:
        ImageDraw.Draw(m).polygon([(float(x), float(y)) for x, y in pts], fill=255)
    return m


def m_ellipse(size, box):
    m = mnew(size)
    ImageDraw.Draw(m).ellipse([float(v) for v in box], fill=255)
    return m


def m_circle(size, cx, cy, r):
    return m_ellipse(size, (cx - r, cy - r, cx + r, cy + r))


def m_ell(size, cx, cy, rx, ry, ang=0.0):
    if not ang:
        return m_ellipse(size, (cx - rx, cy - ry, cx + rx, cy + ry))
    return m_poly(size, ellipse_pts(cx, cy, rx, ry, ang, 120))


def m_rrect(size, box, r):
    m = mnew(size)
    x0, y0, x1, y1 = [float(v) for v in box]
    r = max(0.0, min(r, (x1 - x0) / 2.0 - 0.5, (y1 - y0) / 2.0 - 0.5))
    ImageDraw.Draw(m).rounded_rectangle((x0, y0, x1, y1), radius=r, fill=255)
    return m


def m_line(size, pts, w, cap=True):
    m = mnew(size)
    d = ImageDraw.Draw(m)
    pts = [(float(x), float(y)) for x, y in pts]
    if len(pts) >= 2:
        d.line(pts, fill=255, width=max(1, int(round(w))), joint="curve")
    if cap:
        r = w / 2.0
        for (x, y) in (pts[0], pts[-1]):
            d.ellipse((x - r, y - r, x + r, y + r), fill=255)
    return m


def m_union(*ms):
    out = ms[0]
    for m in ms[1:]:
        out = ImageChops.lighter(out, m)
    return out


def m_inter(a, b):
    return ImageChops.multiply(a, b)


def m_sub(a, b):
    return ImageChops.subtract(a, b)


def m_inv(a):
    return ImageChops.invert(a)


def m_shift(m, dx, dy, fill=0):
    out = Image.new("L", m.size, fill)
    out.paste(m, (int(round(dx)), int(round(dy))))
    return out


def m_blur(m, r):
    return m.filter(ImageFilter.GaussianBlur(r)) if r > 0.3 else m


def m_mul(m, k):
    if k >= 0.999:
        return m
    return m.point([int(v * k + 0.5) for v in range(256)])


def m_thresh(m, lo=100, hi=156):
    return m.point([0 if v <= lo else (255 if v >= hi else int(255 * (v - lo) / float(hi - lo))) for v in range(256)])


def _crop_box(m, pad):
    bb = m.getbbox()
    if bb is None:
        return None
    W, H = m.size
    return (max(0, bb[0] - pad), max(0, bb[1] - pad), min(W, bb[2] + pad), min(H, bb[3] + pad))


def _ring(m, s, fill=0):
    W, H = m.size
    n = max(8, int(math.ceil(2 * math.pi * s / 2.2)))
    seen = set()
    out = m
    for i in range(n):
        a = 2 * math.pi * i / n
        dx, dy = int(round(s * math.cos(a))), int(round(s * math.sin(a)))
        if (dx, dy) in seen or (dx == 0 and dy == 0):
            continue
        seen.add((dx, dy))
        sh = Image.new("L", (W, H), fill)
        sh.paste(m, (dx, dy))
        out = ImageChops.lighter(out, sh)
    return out


def m_dilate(m, r):
    """Exact-ish Minkowski dilation by a disc: two ring passes of radius r/2 (C_r/2 + C_r/2 = D_r)."""
    if r < 0.5:
        return m
    box = _crop_box(m, int(math.ceil(r)) + 2)
    if box is None:
        return m
    sub = m.crop(box)
    sub = _ring(sub, r) if r <= 3 else _ring(_ring(sub, r / 2.0), r / 2.0)
    out = mnew(m.size)
    out.paste(sub, box[:2])
    return out


def m_erode(m, r):
    if r < 0.5:
        return m
    box = _crop_box(m, int(math.ceil(r)) + 2)
    if box is None:
        return m
    sub = ImageChops.invert(m.crop(box))
    sub = _ring(sub, r, 255) if r <= 3 else _ring(_ring(sub, r / 2.0, 255), r / 2.0, 255)
    out = mnew(m.size)
    out.paste(ImageChops.invert(sub), box[:2])
    return out


def m_open(m, r):
    return m_dilate(m_erode(m, r), r)


def m_close(m, r):
    return m_erode(m_dilate(m, r), r)


def m_soften(m, r):
    """Round off corners/kinks: blur then re-threshold."""
    return m_thresh(m_blur(m, r), 110, 146)


def m_fluffy(size, cx, cy, r, bumps=11, seed=1, rot=0.0):
    rnd = random.Random(seed)
    m = m_circle(size, cx, cy, r * 0.8)
    for i in range(bumps):
        a = math.radians(rot + 360.0 * i / bumps + rnd.uniform(-6, 6))
        br = r * rnd.uniform(0.27, 0.33)
        m = m_union(m, m_circle(size, cx + (r - br) * math.cos(a), cy + (r - br) * math.sin(a), br))
    return m


def m_cloud(size, circles):
    m = mnew(size)
    d = ImageDraw.Draw(m)
    for (cx, cy, r) in circles:
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=255)
    return m


# --------------------------------------------------------------------------
# Painting
# --------------------------------------------------------------------------


def paint_fill(cv, mask, color, opacity=1.0):
    bb = mask.getbbox()
    if bb is None:
        return
    color = rgba(color)
    if MONO[0]:
        k = color[3] / 255.0 * opacity
        if _ink(color):
            if k >= 0.5:
                erase(cv, mask)
            return
        if k < 0.6:
            return
        color, opacity = WHITE, 1.0
    m = m_mul(mask.crop(bb), color[3] / 255.0 * opacity)
    layer = Image.new("RGBA", m.size, color[:3] + (255,))
    layer.putalpha(m)
    cv.alpha_composite(layer, dest=(bb[0], bb[1]))


def field_L(w, h, fn, N=64):
    gw, gh = max(2, min(N, w)), max(2, min(N, h))
    sx, sy = w / float(gw), h / float(gh)
    data = []
    for j in range(gh):
        y = (j + 0.5) * sy
        for i in range(gw):
            v = fn((i + 0.5) * sx, y)
            data.append(0 if v <= 0 else (255 if v >= 1 else int(v * 255 + 0.5)))
    g = Image.new("L", (gw, gh))
    g.putdata(data)
    if (gw, gh) != (w, h):
        g = g.resize((w, h), BICUBIC)
    return g


def ramp_luts(stops):
    st = sorted([(float(t), rgba(c)) for t, c in stops], key=lambda s: s[0])
    luts = [[], [], [], []]
    for i in range(256):
        t = i / 255.0
        c = st[-1][1]
        if t <= st[0][0]:
            c = st[0][1]
        else:
            for k in range(len(st) - 1):
                if st[k][0] <= t <= st[k + 1][0]:
                    u = (t - st[k][0]) / max(1e-9, st[k + 1][0] - st[k][0])
                    c = tuple(st[k][1][q] + (st[k + 1][1][q] - st[k][1][q]) * u for q in range(4))
                    break
        for q in range(4):
            luts[q].append(int(round(c[q])))
    return luts


def ramp_img(L, stops):
    lr, lg, lb, la = ramp_luts(stops)
    return Image.merge("RGBA", (L.point(lr), L.point(lg), L.point(lb), L.point(la)))


def lin_fn(p0, p1):
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    L2 = float(dx * dx + dy * dy) or 1.0
    return lambda x, y: ((x - p0[0]) * dx + (y - p0[1]) * dy) / L2


def rad_fn(c, rx, ry=None):
    ry = ry or rx
    return lambda x, y: math.hypot((x - c[0]) / rx, (y - c[1]) / ry)


def paint_grad(cv, mask, fn, stops, opacity=1.0, N=64):
    bb = mask.getbbox()
    if bb is None:
        return
    if MONO[0]:
        if opacity < 0.6:
            return
        lum = sum(0.299 * rgba(c)[0] + 0.587 * rgba(c)[1] + 0.114 * rgba(c)[2] for _, c in stops) / len(stops)
        if lum < 80:
            erase(cv, mask)
        else:
            paint_fill(cv, mask, WHITE)
        return
    x0, y0, x1, y1 = bb
    L = field_L(x1 - x0, y1 - y0, lambda x, y: fn(x + x0, y + y0), N)
    layer = ramp_img(L, stops)
    a = m_mul(ImageChops.multiply(layer.getchannel("A"), mask.crop(bb)), opacity)
    layer.putalpha(a)
    cv.alpha_composite(layer, dest=(x0, y0))


def paint_layer(cv, layer, mask=None, opacity=1.0, dest=(0, 0)):
    lay = layer.copy()
    a = lay.getchannel("A")
    if mask is not None:
        a = ImageChops.multiply(a, mask)
    lay.putalpha(m_mul(a, opacity))
    cv.alpha_composite(lay, dest=dest)


def erase(cv, mask):
    a = ImageChops.multiply(cv.getchannel("A"), ImageChops.invert(mask))
    cv.putalpha(a)


def glossy(cv, mask, base, light=None, dark=None, kind="sphere", lc=(0.34, 0.28), R=1.0,
           core=0.45, core_d=0.13, rim=0.0, rim_d=0.05, spec=0.75, spec_box=None, spec_ang=-28.0,
           spec_blur=0.03, bounce=0.0, bounce_c=None, stops=None, grad_fn=None):
    """Premium casual-game shading: gradient body, core shadow, rim light, bounce light, specular."""
    bb = mask.getbbox()
    if bb is None:
        return
    if MONO[0]:
        paint_fill(cv, mask, WHITE)
        return
    w, h = bb[2] - bb[0], bb[3] - bb[1]
    s = float(min(w, h))
    box = _crop_box(mask, int(max(w, h) * 0.2) + 4)
    ox, oy = box[0], box[1]
    sub = cv.crop(box)
    m = mask.crop(box)
    x0, y0, x1, y1 = bb[0] - ox, bb[1] - oy, bb[2] - ox, bb[3] - oy
    base = rgba(base)
    light = rgba(light) if light is not None else lighten(base, 0.42)
    dark = rgba(dark) if dark is not None else shade(base, 0.32)
    if grad_fn is not None:
        paint_grad(sub, m, lambda x, y: grad_fn(x + ox, y + oy), stops or [(0, light), (0.5, base), (1, dark)])
    elif kind == "sphere":
        cx, cy = x0 + lc[0] * w, y0 + lc[1] * h
        rr = R * max(w, h)
        paint_grad(sub, m, lambda x, y: math.hypot(x - cx, y - cy) / rr,
                   stops or [(0, light), (0.5, base), (1.0, dark)])
    elif kind == "vertical":
        paint_grad(sub, m, lambda x, y: (y - y0) / float(h), stops or [(0, light), (0.55, base), (1.0, dark)])
    elif kind == "diag":
        paint_grad(sub, m, lambda x, y: ((x - x0) / float(w) * 0.45 + (y - y0) / float(h) * 0.55),
                   stops or [(0, light), (0.5, base), (1.0, dark)])
    else:
        paint_fill(sub, m, base)
    if core > 0:
        d = core_d * s
        cr = m_sub(m, m_shift(m, -0.45 * d, -d))
        cr = m_inter(m_blur(cr, 0.45 * d), m)
        paint_fill(sub, cr, dark, core)
    if bounce > 0:
        d = 0.045 * s
        br = m_sub(m, m_shift(m, 0.2 * d, -d))
        br = m_inter(m_blur(br, 0.35 * d), m)
        paint_fill(sub, br, bounce_c or lighten(base, 0.35), bounce)
    if rim > 0:
        d = rim_d * s
        rm = m_sub(m, m_shift(m, 0.55 * d, d))
        rm = m_inter(m_blur(rm, 0.45 * d), m)
        paint_fill(sub, rm, WHITE, rim)
    if spec > 0:
        if spec_box is None:
            sb = (x0 + 0.16 * w, y0 + 0.08 * h, x0 + 0.48 * w, y0 + 0.28 * h)
        else:
            sb = (spec_box[0] - ox, spec_box[1] - oy, spec_box[2] - ox, spec_box[3] - oy)
        sm = m_ell(sub.size, (sb[0] + sb[2]) / 2.0, (sb[1] + sb[3]) / 2.0, (sb[2] - sb[0]) / 2.0,
                   (sb[3] - sb[1]) / 2.0, spec_ang)
        sm = m_inter(m_blur(sm, spec_blur * s), m)
        paint_fill(sub, sm, WHITE, spec)
    cv.paste(sub, (ox, oy))


def outline(cv, mask, w, color=NAVY, extrude=0.0):
    o = m_dilate(mask, w)
    if extrude:
        o = m_union(o, m_shift(o, 0, extrude))
    paint_fill(cv, o, color)
    return o


def soft_shadow(cv, mask, dx, dy, blur, color=NAVY, opacity=0.35):
    paint_fill(cv, m_blur(m_shift(mask, dx, dy), blur), color, opacity)


def downscale(img, size):
    return img.resize(size, LANCZOS)


def save_png(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    print("  wrote", os.path.relpath(path, ROOT), img.size, img.mode)


# --------------------------------------------------------------------------
# Decorations: sparkles, snowflakes, swooshes, snow puffs
# --------------------------------------------------------------------------


def draw_sparkle(cv, cx, cy, r, color=WHITE, ow=0.0, glow=0.35, rot=0.0, core=None):
    size = cv.size
    m = m_poly(size, twinkle_pts(cx, cy, r, 2.35, rot))
    if glow > 0:
        paint_fill(cv, m_blur(m_circle(size, cx, cy, r * 0.55), r * 0.35), color, glow)
    if ow > 0:
        paint_fill(cv, m_dilate(m, ow), NAVY)
    if core is None:
        paint_fill(cv, m, color)
    else:
        paint_grad(cv, m, rad_fn((cx, cy), r * 0.75), [(0, core), (1, color)])


def snowflake_mask(size, cx, cy, r, w, rot=0.0, branches=True):
    m = mnew(size)
    d = ImageDraw.Draw(m)
    for k in range(6):
        a = math.radians(rot + 60 * k - 90)
        ex, ey = cx + r * math.cos(a), cy + r * math.sin(a)
        d.line([(cx, cy), (ex, ey)], fill=255, width=max(1, int(round(w))))
        d.ellipse((ex - w / 2, ey - w / 2, ex + w / 2, ey + w / 2), fill=255)
        if branches:
            for t, bl in ((0.55, 0.32), (0.8, 0.2)):
                bx, by = cx + r * t * math.cos(a), cy + r * t * math.sin(a)
                for sgn in (-1, 1):
                    b = a + sgn * math.radians(45)
                    ex2, ey2 = bx + r * bl * math.cos(b), by + r * bl * math.sin(b)
                    d.line([(bx, by), (ex2, ey2)], fill=255, width=max(1, int(round(w * 0.85))))
                    d.ellipse((ex2 - w * 0.42, ey2 - w * 0.42, ex2 + w * 0.42, ey2 + w * 0.42), fill=255)
    d.ellipse((cx - w * 0.9, cy - w * 0.9, cx + w * 0.9, cy + w * 0.9), fill=255)
    return m


def swoosh_mask(size, pts, width, taper_in=0.25):
    def wfn(t):
        if t < taper_in:
            return width * smooth(t / taper_in) ** 0.8 + 0.5
        return width * (1 - smooth((t - taper_in) / (1 - taper_in))) ** 0.6 + 0.5
    return m_poly(size, taper_pts(catmull(pts, closed=False, n=12), wfn))


def draw_puff(cv, circles, ow):
    m = m_cloud(cv.size, circles)
    paint_fill(cv, m_dilate(m, ow), NAVY)
    glossy(cv, m, WHITE, light=WHITE, dark=hx("#B9D5F3"), kind="vertical", core=0.5, core_d=0.25, spec=0)
    return m


# --------------------------------------------------------------------------
# Pip - chibi kid in a puffy winter snowsuit (matches the in-game 3D character)
# --------------------------------------------------------------------------


def warm_shade(c, t):
    """Darken skin / hair toward a warm brown so shadows never go grey or purple."""
    return mix(c, (110, 46, 34, rgba(c)[3]), t)


PIP_PAL = dict(
    skin=hx("#FFDCC0"), skin_l=hx("#FFF2E6"), skin_d=hx("#F2B590"), skin_dd=hx("#D98A66"),
    hair=hx("#6B3E1F"), hair_l=hx("#A26A3C"), hair_d=hx("#43230E"),
    cheek=hx("#FF8FA8"),
    hat=MINT, hat_l=hx("#BDFFEE"), hat_d=hx("#10AE8C"),
    rim=WHITE, rim_d=hx("#BCD9F6"),
    pom=BERRY, pom_l=hx("#FFA6C6"), pom_d=hx("#C42367"),
    scarf=BERRY, scarf_l=hx("#FF8DB8"), scarf_d=hx("#C42367"), stripe=WHITE,
    suit=CORAL, suit_l=hx("#FFB386"), suit_d=hx("#E0532B"), suit_dd=hx("#B23C1E"),
    panel=hx("#FFD2B8"), panel_l=hx("#FFEEE4"), panel_d=hx("#EDA684"),
    pants=hx("#C75F36"), pants_l=hx("#E9895C"), pants_d=hx("#8C3B1C"),
    mitt=MINT, mitt_l=hx("#BDFFEE"), mitt_d=hx("#10AE8C"),
    boot=hx("#2A3B66"), boot_l=hx("#5A70AE"), boot_d=hx("#1B2A4A"), sole=hx("#7487BC"),
)


def kid_palette(suit, hat, pom, scarf, skin, hair, mitt=None, pants=None):
    """Colour variants for the other racers: snowsuit, hat and scarf colours, skin tone and hair."""
    pal = dict(PIP_PAL)
    pants = pants or shade(suit, 0.28)
    mitt = mitt or hat
    pal.update(
        skin=skin, skin_l=lighten(skin, 0.45), skin_d=warm_shade(skin, 0.16), skin_dd=warm_shade(skin, 0.32),
        hair=hair, hair_l=lighten(hair, 0.3), hair_d=warm_shade(hair, 0.35),
        suit=suit, suit_l=lighten(suit, 0.42), suit_d=shade(suit, 0.2), suit_dd=shade(suit, 0.38),
        panel=lighten(suit, 0.6), panel_l=lighten(suit, 0.82), panel_d=lighten(shade(suit, 0.12), 0.3),
        pants=pants, pants_l=lighten(pants, 0.3), pants_d=shade(pants, 0.3),
        hat=hat, hat_l=lighten(hat, 0.6), hat_d=shade(hat, 0.3),
        pom=pom, pom_l=lighten(pom, 0.5), pom_d=shade(pom, 0.3),
        scarf=scarf, scarf_l=lighten(scarf, 0.4), scarf_d=shade(scarf, 0.3),
        mitt=mitt, mitt_l=lighten(mitt, 0.6), mitt_d=shade(mitt, 0.3))
    return pal


def band_pts(yc, xe, thick, sag, n=48):
    top, bot = [], []
    for i in range(n + 1):
        x = -xe + 2 * xe * i / float(n)
        yy = yc + sag * (1 - (x / xe) ** 2)
        top.append((x, yy - thick / 2.0))
        bot.append((x, yy + thick / 2.0))
    return top + bot[::-1]


# Local units: 1.0 = height from the boot soles to the top of the beanie (pompom adds ~0.09).
KID_POSES = {
    # arms: (shoulder, elbow, hand, finger direction deg, thumb side); legs: (hip, ankle); boots: (x, tilt deg)
    "wave": dict(arms=[((-0.15, -0.515), (-0.29, -0.57), (-0.345, -0.735), -98, 1),
                       ((0.15, -0.515), (0.25, -0.445), (0.315, -0.365), 62, -1)],
                 legs=[((-0.085, -0.235), (-0.098, -0.085)), ((0.085, -0.235), (0.098, -0.085))],
                 boots=[(-0.105, 4.0), (0.105, -4.0)], knot=1, tail_rise=0.1, wave_lines=True),
    "idle": dict(arms=[((-0.15, -0.515), (-0.25, -0.445), (-0.315, -0.365), 118, 1),
                       ((0.15, -0.515), (0.25, -0.445), (0.315, -0.365), 62, -1)],
                 legs=[((-0.085, -0.235), (-0.098, -0.085)), ((0.085, -0.235), (0.098, -0.085))],
                 boots=[(-0.105, 4.0), (0.105, -4.0)], knot=1, tail_rise=0.05),
    "push": dict(arms=[((0.07, -0.48), (0.245, -0.42), (0.405, -0.4), -6, -1),
                       ((0.12, -0.525), (0.285, -0.515), (0.43, -0.52), -10, -1)],
                 legs=[((-0.07, -0.235), (-0.16, -0.085)), ((0.075, -0.235), (0.112, -0.085))],
                 boots=[(-0.178, 14.0), (0.125, -4.0)], knot=-1, tail_rise=0.02),
}

KID_TORSO = [(0.0, -0.582), (0.13, -0.578), (0.198, -0.52), (0.226, -0.425), (0.218, -0.32), (0.182, -0.24),
             (0.112, -0.203), (0.0, -0.196), (-0.112, -0.203), (-0.182, -0.24), (-0.218, -0.32), (-0.226, -0.425),
             (-0.198, -0.52), (-0.13, -0.578)]


class Rig:
    """Local coordinates: unit = character height H, origin at the ground under the feet, y up is negative."""

    def __init__(self, H, w_mul=1.5, h_mul=1.42, ground=0.16):
        self.H = float(H)
        W, HH = int(H * w_mul), int(H * h_mul)
        self.size = (W, HH)
        self.ox, self.oy = W / 2.0, HH - ground * H
        self.cv = Image.new("RGBA", self.size, (0, 0, 0, 0))

    def P(self, x, y):
        return (self.ox + x * self.H, self.oy + y * self.H)

    def pts(self, lst):
        return [self.P(x, y) for x, y in lst]

    def poly(self, lst):
        return m_poly(self.size, self.pts(lst))

    def spoly(self, lst, n=10):
        return m_poly(self.size, self.pts(catmull(lst, True, n)))

    def ell(self, x, y, rx, ry, ang=0.0):
        cx, cy = self.P(x, y)
        return m_ell(self.size, cx, cy, rx * self.H, ry * self.H, ang)

    def circ(self, x, y, r):
        cx, cy = self.P(x, y)
        return m_circle(self.size, cx, cy, r * self.H)

    def line(self, lst, w):
        return m_line(self.size, self.pts(lst), w * self.H)


def _mitten(R, hand, ddeg, side):
    hxl, hyl = hand
    a = math.radians(ddeg)
    dx, dy = math.cos(a), math.sin(a)
    px, py = -dy, dx
    main = R.ell(hxl + dx * 0.02, hyl + dy * 0.02, 0.064, 0.054, ddeg)
    thumb = R.ell(hxl + px * side * 0.048 - dx * 0.006, hyl + py * side * 0.048 - dy * 0.006, 0.034, 0.022,
                  ddeg - side * 48)
    return main, thumb


def _boot(R, x, ang):
    pts = [(x - 0.06, -0.108), (x + 0.06, -0.108), (x + 0.07, -0.06), (x + 0.086, -0.018), (x + 0.07, 0.004),
           (x - 0.07, 0.004), (x - 0.086, -0.018), (x - 0.07, -0.06)]
    pts = rot_pts(catmull(pts, True, 8), x, -0.05, ang)
    return R.poly(pts)


def _scarf_strip(R, center, w0, w1):
    return R.poly(taper_pts(center, lambda t: lerp(w0, w1, t)))


def draw_pip(H, pose="wave", pal=None, look=(0.0, 0.0), ow_k=0.026, iw_k=0.013, hat_tilt=-6.0,
             detail=True, mouth="open", tail_len=0.26):
    """Render Pip (or a palette variant): chibi kid, big head (~40% of height), puffy snowsuit.
    Returns (RGBA layer, ground anchor xy, info dict with hand positions and the silhouette mask)."""
    pal = pal or PIP_PAL
    R = Rig(H)
    S = R.size
    cv = R.cv
    H = R.H
    ow, iw = ow_k * H, iw_k * H
    cfg = KID_POSES[pose]
    ks = cfg["knot"]

    # ---------------- shapes ----------------
    head = R.ell(0.0, -0.752, 0.252, 0.228)
    back_hair = m_inter(R.ell(0.0, -0.75, 0.279, 0.242), R.poly([(-1, -0.618), (1, -0.618), (1, -2), (-1, -2)]))
    for sx in (-1, 1):
        back_hair = m_union(back_hair, R.ell(sx * 0.253, -0.633, 0.055, 0.031, sx * 16))
    back_hair = m_soften(back_hair, 0.006 * H)
    locks = []
    for sx in (-1, 1):
        locks.append(R.spoly([(sx * 0.274, -0.856), (sx * 0.178, -0.86), (sx * 0.183, -0.806), (sx * 0.2, -0.75),
                              (sx * 0.226, -0.695), (sx * 0.262, -0.682), (sx * 0.281, -0.746)], 8))

    yr = -0.862
    dome = R.poly(rot_pts(ellipse_pts(0.0, yr + 0.005, 0.262, 0.175, 0, 120), 0, yr, hat_tilt))
    dome = m_inter(dome, R.poly(rot_pts([(-1, yr + 0.03), (1, yr + 0.03), (1, -2.2), (-1, -2.2)], 0, yr, hat_tilt)))
    xe_r = 0.262
    rim = R.poly(rot_pts(band_pts(yr, xe_r, 0.08, 0.02), 0, yr, hat_tilt))
    for sx in (-1, 1):
        rim = m_union(rim, R.circ(*rot_pts([(sx * xe_r, yr)], 0, yr, hat_tilt)[0], 0.04))
    top_x, top_y = rot_pts([(0.0, yr + 0.005 - 0.175)], 0, yr, hat_tilt)[0]
    pom = m_fluffy(S, *R.P(top_x + 0.006, top_y - 0.03), 0.062 * H, bumps=10, seed=4)

    ys, xe_s = -0.552, 0.172
    band = R.poly(band_pts(ys, xe_s, 0.08, 0.016))
    for sx in (-1, 1):
        band = m_union(band, R.circ(sx * xe_s, ys, 0.04))
    kx, ky = ks * (xe_s - 0.022), ys + 0.012
    knot = R.ell(kx, ky, 0.046, 0.043, 20 * ks)
    tail_c = []
    rise = cfg.get("tail_rise", 0.05)
    for i in range(14):
        t = i / 13.0
        tail_c.append((ks * (xe_s - 0.01 + tail_len * t),
                       ky + 0.012 - rise * t + 0.026 * math.sin(math.pi * t * 1.7) * t))
    tail = _scarf_strip(R, tail_c, 0.074, 0.062)
    a_end = math.atan2(tail_c[-1][1] - tail_c[-3][1], tail_c[-1][0] - tail_c[-3][0])
    fringe = mnew(S)
    for j in range(4):
        off = -0.024 + 0.016 * j
        fx = tail_c[-1][0] - math.sin(a_end) * off
        fy = tail_c[-1][1] + math.cos(a_end) * off
        fringe = m_union(fringe, R.line([(fx, fy), (fx + math.cos(a_end) * 0.036, fy + math.sin(a_end) * 0.036)],
                                        0.012))
    hang_c = catmull([(kx, ky + 0.015), (kx + ks * 0.008, ky + 0.07), (kx + ks * 0.004, ky + 0.13),
                      (kx + ks * 0.014, ky + 0.168)], closed=False, n=6)
    hang = _scarf_strip(R, hang_c, 0.062, 0.056)
    hang_fr = mnew(S)
    hx_e, hy_e = hang_c[-1]
    for j in range(4):
        fx = hx_e - 0.022 + 0.0147 * j
        hang_fr = m_union(hang_fr, R.line([(fx, hy_e), (fx + ks * 0.003, hy_e + 0.032)], 0.012))

    torso = R.spoly(KID_TORSO, 10)
    arms = []
    for (sh, el, hd, ddeg, side) in cfg["arms"]:
        sleeve = R.line([sh, el, hd], 0.122)
        mm, th = _mitten(R, hd, ddeg, side)
        arms.append((sleeve, mm, th, (sh, el, hd)))
    legs = [R.line([hip, ank], 0.132) for (hip, ank) in cfg["legs"]]
    boots = [_boot(R, bx, ba) for (bx, ba) in cfg["boots"]]

    parts_all = [head, back_hair, dome, rim, pom, band, knot, tail, fringe, hang, hang_fr, torso] + locks + legs + boots
    for a_ in arms:
        parts_all += [a_[0], a_[1], a_[2]]
    sil = m_union(*parts_all)

    # ---------------- paint ----------------
    paint_fill(cv, m_dilate(sil, ow), NAVY)

    def line(m, k=1.0):
        paint_fill(cv, m_dilate(m, iw * k), NAVY)

    def stripe_strip(center, i0, i1, host):
        st = m_inter(_scarf_strip(R, center[i0:i1 + 1], 0.12, 0.12), host)
        glossy(cv, st, WHITE, WHITE, pal["rim_d"], kind="vertical", core=0.3, spec=0)

    # scarf tail (behind everything)
    line(fringe, 0.7)
    paint_fill(cv, fringe, pal["scarf_d"])
    line(tail)
    glossy(cv, tail, pal["scarf"], pal["scarf_l"], pal["scarf_d"], kind="vertical", core=0.35, spec=0)
    stripe_strip(tail_c, 3, 5, tail)
    stripe_strip(tail_c, 8, 10, tail)

    # arms (behind the torso)
    for sleeve, mm, th, (sh, el, hd) in arms:
        line(sleeve)
        glossy(cv, sleeve, pal["suit"], pal["suit_l"], pal["suit_d"], kind="sphere", core=0.4, spec=0)
        dx, dy = hd[0] - el[0], hd[1] - el[1]
        ln = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / ln, dx / ln
        cxl, cyl = el[0] + dx * 0.62, el[1] + dy * 0.62
        cuff = m_inter(R.line([(cxl - nx * 0.08, cyl - ny * 0.08), (cxl + nx * 0.08, cyl + ny * 0.08)], 0.032), sleeve)
        paint_fill(cv, cuff, pal["suit_d"], 0.85)
        paint_fill(cv, m_inter(m_sub(cuff, m_shift(cuff, 0, 0.008 * H)), sleeve), pal["suit_l"], 0.6)
        line(th, 0.8)
        glossy(cv, th, pal["mitt"], pal["mitt_l"], pal["mitt_d"], kind="sphere", core=0.35, spec=0)
        line(mm)
        glossy(cv, mm, pal["mitt"], pal["mitt_l"], pal["mitt_d"], kind="sphere", core=0.4, spec=0.55,
               spec_blur=0.06)

    # legs + boots
    for lg in legs:
        line(lg)
        glossy(cv, lg, pal["pants"], pal["pants_l"], pal["pants_d"], kind="sphere", lc=(0.3, 0.2), core=0.4, spec=0)
    for bt in boots:
        line(bt)
        glossy(cv, bt, pal["boot"], pal["boot_l"], pal["boot_d"], kind="sphere", lc=(0.35, 0.15), core=0.35,
               spec=0.45, spec_blur=0.08)
        bb_ = bt.getbbox()
        sole = m_inter(bt, m_shift(bt, 0, -0.024 * H))
        sole = m_sub(bt, sole)
        paint_fill(cv, sole, pal["sole"])
        paint_fill(cv, m_inter(m_sub(sole, m_shift(sole, 0, 0.008 * H)), sole), WHITE, 0.5)

    # torso: puffy snowsuit
    line(torso)
    glossy(cv, torso, pal["suit"], pal["suit_l"], pal["suit_d"], kind="sphere", lc=(0.32, 0.22), R=1.05, core=0.45,
           core_d=0.12, bounce=0.3, bounce_c=lighten(pal["suit"], 0.4), rim=0.18, rim_d=0.035, spec=0)
    seams = mnew(S)
    for yy in (-0.44, -0.325):
        seams = m_union(seams, R.line([(x, yy + 0.016 * (1 - (x / 0.23) ** 2)) for x in
                                       [-0.23 + 0.46 * i / 20.0 for i in range(21)]], 0.011))
    seams = m_inter(seams, torso)
    paint_fill(cv, m_inter(m_blur(m_shift(seams, 0, -0.012 * H), 0.006 * H), torso), pal["suit_l"], 0.55)
    paint_fill(cv, seams, pal["suit_dd"], 0.55)
    panel = m_inter(m_blur(R.poly(rrect_pts(-0.082, -0.54, 0.082, -0.245, 0.06)), 0.004 * H), torso)
    paint_fill(cv, m_inter(m_dilate(panel, 0.006 * H), torso), pal["suit_dd"], 0.35)
    paint_grad(cv, panel, lin_fn(R.P(0, -0.54), R.P(0, -0.245)), [(0, pal["panel_l"]), (0.5, pal["panel"]),
                                                                    (1, pal["panel_d"])])
    zipl = m_inter(R.line([(0.0, -0.53), (0.0, -0.255)], 0.009), panel)
    paint_fill(cv, zipl, pal["panel_d"], 0.9)
    pull = R.poly(rrect_pts(-0.015, -0.515, 0.015, -0.468, 0.008))
    paint_fill(cv, m_dilate(pull, 0.004 * H), NAVY)
    glossy(cv, pull, STEEL, STEEL_L, STEEL_D, kind="vertical", core=0.3, spec=0)
    gl = m_inter(m_blur(R.ell(-0.13, -0.47, 0.03, 0.06, 18), 0.012 * H), torso)
    paint_fill(cv, gl, WHITE, 0.4)
    # shadow cast by head + scarf on the suit
    shd = m_inter(m_blur(m_shift(m_union(band, head), 0, 0.028 * H), 0.02 * H), torso)
    paint_fill(cv, shd, pal["suit_dd"], 0.4)

    # back hair, head, face
    line(back_hair)
    glossy(cv, back_hair, pal["hair"], pal["hair_l"], pal["hair_d"], kind="sphere", lc=(0.3, 0.2), core=0.4, spec=0)
    line(head)
    glossy(cv, head, pal["skin"], pal["skin_l"], pal["skin_d"], kind="sphere", lc=(0.34, 0.3), R=1.0, core=0.35,
           core_d=0.1, spec=0)
    rs = m_inter(m_blur(m_shift(m_union(rim, *locks), 0, 0.022 * H), 0.016 * H), head)
    paint_fill(cv, rs, pal["skin_dd"], 0.35)
    lx, ly = look
    for sx in (-1, 1):
        ex, ey = sx * 0.093 + lx, -0.733 + ly
        eye = R.ell(ex, ey, 0.047, 0.061)
        paint_fill(cv, m_dilate(eye, iw * 0.3), NAVY)
        paint_grad(cv, eye, lin_fn(R.P(ex, ey - 0.061), R.P(ex, ey + 0.061)),
                   [(0, EYE_TOP), (0.45, EYE_MID), (0.8, EYE_LOW), (1, EYE_REF)])
        paint_fill(cv, R.ell(ex - 0.015, ey - 0.022, 0.0195, 0.0205), WHITE)
        paint_fill(cv, R.circ(ex + 0.017, ey + 0.023, 0.009), WHITE, 0.92)
        ch = m_inter(m_blur(R.ell(sx * 0.163, -0.668, 0.048, 0.029), 0.01 * H), head)
        paint_fill(cv, ch, pal["cheek"], 0.6)
        paint_fill(cv, m_inter(m_blur(R.ell(sx * 0.15 - 0.01, -0.676, 0.01, 0.006), 0.003 * H), head), WHITE, 0.55)
    nose = R.ell(0.0, -0.688, 0.012, 0.0075)
    paint_fill(cv, m_blur(nose, 0.002 * H), pal["skin_dd"], 0.55)
    paint_fill(cv, R.ell(-0.004, -0.691, 0.004, 0.0025), WHITE, 0.6)
    mw, md, my = 0.028, 0.03, -0.656
    if mouth == "open":
        mp = [(-mw + 2 * mw * i / 20.0, my + 0.006 * (1 - ((-mw + 2 * mw * i / 20.0) / mw) ** 2)) for i in range(21)]
        mp += [(mw * math.cos(math.pi * i / 40.0), my + md * math.sin(math.pi * i / 40.0)) for i in range(1, 40)]
        mth = R.poly(mp)
        paint_fill(cv, m_dilate(mth, iw * 0.5), NAVY)
        paint_fill(cv, mth, MOUTH)
        paint_fill(cv, m_inter(R.ell(0.002, my + md * 0.86, 0.018, 0.014), mth), hx("#FF7AA0"))
    else:
        paint_fill(cv, R.line(arc_pts(0, my - 0.02, 0.028, 25, 155, 20), 0.011), NAVY)
    for lk in locks:
        line(lk)
        glossy(cv, lk, pal["hair"], pal["hair_l"], pal["hair_d"], kind="vertical", core=0.35, spec=0)
    if detail:
        strands = mnew(S)
        for sx in (-1, 1):
            strands = m_union(strands, R.line(catmull([(sx * 0.213, -0.84), (sx * 0.228, -0.78), (sx * 0.25, -0.715)],
                                                      False, 6), 0.007))
            strands = m_union(strands, R.line(catmull([(sx * 0.262, -0.83), (sx * 0.27, -0.735), (sx * 0.262, -0.665)],
                                                      False, 6), 0.007))
        paint_fill(cv, m_inter(strands, m_union(back_hair, *locks)), pal["hair_d"], 0.55)
        hl = m_inter(m_blur(R.line([(-0.252, -0.82), (-0.264, -0.745)], 0.012), 0.003 * H), m_union(back_hair, *locks))
        paint_fill(cv, hl, pal["hair_l"], 0.7)

    # scarf band, hanging end, knot
    line(band)
    glossy(cv, band, pal["scarf"], pal["scarf_l"], pal["scarf_d"], kind="vertical", core=0.4, core_d=0.2, spec=0)
    for kk in range(-5, 6):
        phi = kk * 0.29
        if abs(phi) > 1.45:
            continue
        xa, xb = xe_s * math.sin(phi - 0.065), xe_s * math.sin(phi + 0.065)
        ya, yb = ys + 0.016 * (1 - (xa / xe_s) ** 2), ys + 0.016 * (1 - (xb / xe_s) ** 2)
        q = R.poly([(xa, ya - 0.07), (xb, yb - 0.07), (xb, yb + 0.07), (xa, ya + 0.07)])
        glossy(cv, m_inter(q, band), WHITE, WHITE, pal["rim_d"], kind="vertical", core=0.35, core_d=0.25, spec=0)
    paint_fill(cv, m_inter(m_blur(R.ell(-0.07, ys - 0.022, 0.1, 0.01, 2), 0.006 * H), band), WHITE, 0.4)
    line(hang_fr, 0.7)
    paint_fill(cv, hang_fr, pal["scarf_d"])
    line(hang)
    glossy(cv, hang, pal["scarf"], pal["scarf_l"], pal["scarf_d"], kind="vertical", core=0.4, spec=0)
    stripe_strip(hang_c, len(hang_c) // 2 - 2, len(hang_c) // 2 + 1, hang)
    line(knot)
    glossy(cv, knot, pal["scarf"], pal["scarf_l"], pal["scarf_d"], kind="sphere", core=0.45, spec=0.4,
           spec_blur=0.08)

    # beanie
    line(dome)
    glossy(cv, dome, pal["hat"], pal["hat_l"], pal["hat_d"], kind="sphere", lc=(0.3, 0.2), core=0.45, core_d=0.12,
           spec=0)
    if detail:
        rib = mnew(S)
        for kk in range(-3, 4):
            s_ = kk / 3.6
            pts = [(s_ * 0.262 * math.sqrt(max(0.0, 1 - (i / 16.0 * 0.93) ** 2)), yr - i / 16.0 * 0.93 * 0.175)
                   for i in range(17)]
            rib = m_union(rib, m_line(S, R.pts(rot_pts(pts, 0, yr, hat_tilt)), 0.009 * H))
        paint_fill(cv, m_inter(rib, dome), pal["hat_d"], 0.28)
    hgl = m_inter(m_blur(R.ell(*rot_pts([(-0.125, yr - 0.105)], 0, yr, hat_tilt)[0], 0.07, 0.032, -30 + hat_tilt),
                         0.014 * H), dome)
    paint_fill(cv, hgl, WHITE, 0.6)
    line(rim)
    glossy(cv, rim, WHITE, WHITE, pal["rim_d"], kind="vertical", core=0.5, core_d=0.22, spec=0)
    if detail:
        rr = mnew(S)
        for kk in range(-7, 8):
            phi = kk * 0.2
            x = xe_r * math.sin(phi)
            y = yr + 0.02 * (1 - (x / xe_r) ** 2)
            rr = m_union(rr, m_line(S, R.pts(rot_pts([(x, y - 0.03), (x, y + 0.03)], 0, yr, hat_tilt)), 0.008 * H))
        paint_fill(cv, m_inter(rr, rim), pal["rim_d"], 0.6)
    line(pom)
    glossy(cv, pom, pal["pom"], pal["pom_l"], pal["pom_d"], kind="sphere", lc=(0.32, 0.25), core=0.45, spec=0.6,
           spec_blur=0.08)

    if cfg.get("wave_lines"):
        hd = cfg["arms"][0][2]
        for (r_, a0, a1) in ((0.11, 196, 250), (0.16, 192, 246)):
            paint_fill(cv, R.line(arc_pts(hd[0] + 0.02, hd[1] + 0.015, r_, a0, a1, 16), 0.017), NAVY)

    info = dict(hands=[R.P(*a_[2]) for a_ in cfg["arms"]], H=H, sil=sil)
    return cv, (R.ox, R.oy), info


def place_layer(cv, layer, anchor, at, rot=0.0):
    """Paste a rig layer so that its anchor lands on `at`, optionally rotated about the anchor."""
    if rot:
        layer = layer.rotate(rot, resample=BICUBIC, center=anchor)
    dx, dy = int(round(at[0] - anchor[0])), int(round(at[1] - anchor[1]))
    tmp = Image.new("RGBA", cv.size, (0, 0, 0, 0))
    tmp.paste(layer, (dx, dy))
    cv.alpha_composite(tmp)


def rotate_point(p, center, deg_ccw):
    a = math.radians(deg_ccw)
    x, y = p[0] - center[0], p[1] - center[1]
    return (center[0] + x * math.cos(a) + y * math.sin(a), center[1] - x * math.sin(a) + y * math.cos(a))


# --------------------------------------------------------------------------
# Snowball
# --------------------------------------------------------------------------


def snowball_mask(size, cx, cy, r, seed=7, lump=1.0):
    rnd = random.Random(seed)
    ph = [rnd.uniform(0, 6.283) for _ in range(4)]
    pts = []
    for i in range(240):
        a = 2 * math.pi * i / 240
        k = 1 + lump * (0.017 * math.sin(3 * a + ph[0]) + 0.011 * math.sin(5 * a + ph[1]) +
                        0.007 * math.sin(9 * a + ph[2]) + (lump - 1) * 0.006 * math.sin(14 * a + ph[3]))
        pts.append((cx + r * k * math.cos(a), cy + r * k * math.sin(a)))
    return m_poly(size, pts)


def draw_snowball(cv, cx, cy, r, ow, seed=7, sparkles=True, outline_on=True, lumps=True, tone=1.0):
    size = cv.size
    ball = snowball_mask(size, cx, cy, r, seed)
    if outline_on:
        paint_fill(cv, m_dilate(ball, ow), NAVY)
    paint_grad(cv, ball, rad_fn((cx - 0.36 * r, cy - 0.42 * r), 1.95 * r / tone),
               [(0, WHITE), (0.3, hx("#F8FCFF")), (0.58, hx("#DCEEFF")), (0.82, hx("#B7D6F5")), (1, hx("#9BBDEA"))])
    rnd = random.Random(seed * 13 + 1)
    if lumps:
        # rolled-snow swirl layers
        for (ox_, oy_, rr, a0, a1, wk) in ((0.2, 0.18, 0.62, 150, 265, 0.035), (0.05, 0.3, 0.42, 165, 300, 0.03),
                                           (0.35, -0.05, 0.85, 100, 175, 0.03)):
            sw = m_line(size, arc_pts(cx + ox_ * r, cy + oy_ * r, rr * r, a0, a1, 40), wk * r)
            sw = m_inter(m_blur(sw, 0.012 * r), ball)
            paint_fill(cv, sw, hx("#A9CBEF"), 0.45)
            hl = m_inter(m_blur(m_shift(sw, -0.015 * r, -0.02 * r), 0.01 * r), ball)
            paint_fill(cv, m_sub(hl, sw), WHITE, 0.55)
        # clumps
        placed = 0
        tries = 0
        while placed < 13 and tries < 200:
            tries += 1
            a = rnd.uniform(0, 2 * math.pi)
            d = math.sqrt(rnd.uniform(0.05, 0.75)) * r
            px, py = cx + d * math.cos(a), cy + d * math.sin(a)
            rr = rnd.uniform(0.045, 0.085) * r
            lit = ((px - cx) * -0.6 + (py - cy) * -0.8) / r  # >0 toward the light
            cl = m_ell(size, px, py, rr * 1.25, rr, rnd.uniform(-30, 30))
            paint_fill(cv, m_inter(m_blur(m_shift(cl, 0.25 * rr, 0.3 * rr), 0.25 * rr), ball), hx("#97BBE6"),
                       0.38 if lit < 0.2 else 0.22)
            paint_fill(cv, m_inter(cl, ball), hx("#F3F9FF") if lit > 0 else hx("#DDEDFF"))
            paint_fill(cv, m_inter(m_blur(m_shift(cl, -0.25 * rr, -0.3 * rr), 0.2 * rr), m_inter(cl, ball)), WHITE,
                       0.9)
            placed += 1
    # core shadow + bounce + speculars
    cr = m_sub(ball, m_shift(ball, -0.1 * r, -0.16 * r))
    paint_fill(cv, m_inter(m_blur(cr, 0.08 * r), ball), hx("#7FA6DC"), 0.55)
    bn = m_sub(ball, m_shift(ball, 0.03 * r, -0.05 * r))
    paint_fill(cv, m_inter(m_blur(bn, 0.02 * r), ball), WHITE, 0.55)
    sp = m_inter(m_blur(m_ell(size, cx - 0.4 * r, cy - 0.5 * r, 0.27 * r, 0.15 * r, -38), 0.05 * r), ball)
    paint_fill(cv, sp, WHITE, 0.95)
    sp2 = m_inter(m_blur(m_circle(size, cx - 0.62 * r, cy - 0.18 * r, 0.05 * r), 0.015 * r), ball)
    paint_fill(cv, sp2, WHITE, 0.9)
    if sparkles:
        draw_sparkle(cv, cx - 0.12 * r, cy - 0.6 * r, 0.16 * r, WHITE, glow=0.5)
        draw_sparkle(cv, cx + 0.42 * r, cy - 0.38 * r, 0.09 * r, WHITE, glow=0.5)
    return ball


# --------------------------------------------------------------------------
# Sky / ground backdrops
# --------------------------------------------------------------------------


def dither(img, amount=2, seed=5):
    """Break gradient banding with a tiny deterministic noise (+-amount per channel)."""
    w, h = img.size
    span = 2 * amount + 1
    noise = Image.frombytes("L", (w, h), random.Random(seed).randbytes(w * h))
    noise = noise.point([128 - amount + (v % span) for v in range(256)])
    ch = list(img.split())
    for i in range(3):
        ch[i] = ImageChops.add(ch[i], noise, 1.0, -128)
    return Image.merge(img.mode, ch)


def draw_aurora(cv, y0, amp, thick, waves=1.3, phase=0.4, x_from=0.0, x_to=1.0, strength=1.0, seed=9, tilt=0.0):
    W, H = cv.size
    rnd = random.Random(seed)
    top, bot = [], []
    n = 60
    for i in range(n + 1):
        u = lerp(x_from, x_to, i / float(n))
        x = u * W
        yc = y0 * H + amp * H * math.sin(2 * math.pi * u * waves + phase) + tilt * H * (u - 0.5)
        th = thick * H * (0.75 + 0.35 * math.sin(2 * math.pi * u * 2.1 + 1.3))
        top.append((x, yc - th * 0.5))
        bot.append((x, yc + th * 0.5))
    band = m_poly((W, H), top + bot[::-1])
    soft = m_blur(band, thick * H * 0.38)
    # vertical curtain streaks: draw thin columns at low res then stretch vertically
    sh = max(4, H // 24)
    st = Image.new("L", (W, sh), 0)
    d = ImageDraw.Draw(st)
    x = 0
    while x < W:
        wcol = rnd.randint(max(1, W // 160), max(2, W // 60))
        d.rectangle((x, 0, x + wcol, sh), fill=rnd.randint(60, 255))
        x += wcol + rnd.randint(0, max(1, W // 120))
    st = st.resize((W, H), BICUBIC).filter(ImageFilter.GaussianBlur(max(1, W // 400)))
    curtain = m_inter(soft, st)
    glow = m_blur(band, thick * H * 0.9)
    paint_fill(cv, glow, MINT, 0.26 * strength)
    paint_fill(cv, soft, hx("#2EE6B8"), 0.42 * strength)
    paint_fill(cv, curtain, hx("#7DFFDD"), 0.7 * strength)
    pink = m_blur(m_shift(band, 0, -thick * H * 0.75), thick * H * 0.5)
    pink = m_sub(pink, m_mul(soft, 0.7))
    paint_fill(cv, pink, BERRY, 0.42 * strength)
    paint_fill(cv, m_inter(pink, st), hx("#FF7FB0"), 0.25 * strength)


def draw_stars(cv, n, seed, region, rmin, rmax, flakes=0, twinkles=0, flake_r=None):
    W, H = cv.size
    rnd = random.Random(seed)
    x0, y0, x1, y1 = region
    d_m = mnew((W, H))
    d = ImageDraw.Draw(d_m)
    for _ in range(n):
        x, y = rnd.uniform(x0, x1) * W, rnd.uniform(y0, y1) * H
        r = rnd.uniform(rmin, rmax) * W
        d.ellipse((x - r, y - r, x + r, y + r), fill=rnd.randint(110, 255))
    paint_fill(cv, d_m, WHITE)
    for _ in range(flakes):
        x, y = rnd.uniform(x0, x1) * W, rnd.uniform(y0, y1) * H
        r = (flake_r or rmax * 3.2) * W * rnd.uniform(0.75, 1.15)
        fm = snowflake_mask((W, H), x, y, r, max(1.0, r * 0.2), rnd.uniform(0, 30), branches=r > 10)
        paint_fill(cv, fm, WHITE, rnd.uniform(0.55, 0.85))
    for _ in range(twinkles):
        x, y = rnd.uniform(x0, x1) * W, rnd.uniform(y0, y1) * H
        r = rmax * 4.5 * W * rnd.uniform(0.7, 1.2)
        draw_sparkle(cv, x, y, r, WHITE, glow=0.45)


def hill_mask(size, pts_fn, n=80):
    W, H = size
    pts = [(W * i / float(n), pts_fn(i / float(n))) for i in range(n + 1)]
    return m_poly(size, pts + [(W, H + 10), (0, H + 10)])


def draw_snow_hill(cv, mask, top_y, bot_y, light=WHITE, mid=ICE, low=hx("#B3D3F4"), rim=True):
    H = cv.size[1]
    paint_grad(cv, mask, lambda x, y: (y - top_y) / float(max(1, bot_y - top_y)), [(0, light), (0.35, mid), (1, low)])
    if rim:
        e = m_sub(mask, m_shift(mask, 0, H * 0.012))
        paint_fill(cv, m_blur(e, H * 0.004), WHITE, 0.9)
        e2 = m_inter(m_sub(m_shift(mask, 0, H * 0.012), m_shift(mask, 0, H * 0.03)), mask)
        paint_fill(cv, m_blur(e2, H * 0.008), hx("#9EC3EE"), 0.35)


# --------------------------------------------------------------------------
# Hero scene: Pip pushing a giant snowball (launcher foreground, splash, store)
# --------------------------------------------------------------------------


def place_rig(cv, layer, anchor, info, at, rot, ow):
    """Composite a Pip layer. In mono mode first knock out his outline area so he stays separate."""
    if MONO[0]:
        ko = m_dilate(info["sil"], ow)
        if rot:
            ko = ko.rotate(rot, resample=BICUBIC, center=anchor)
        km = mnew(cv.size)
        km.paste(ko, (int(round(at[0] - anchor[0])), int(round(at[1] - anchor[1]))))
        erase(cv, km)
    place_layer(cv, layer, anchor, at, rot)


def rig_point(p, anchor, at, rot):
    q = rotate_point(p, anchor, rot) if rot else p
    return (q[0] - anchor[0] + at[0], q[1] - anchor[1] + at[1])


def hero_scene(size, cx, cy, Rs, mono=False, ground=False):
    MONO[0] = mono
    try:
        return _hero(size, cx, cy, Rs, ground)
    finally:
        MONO[0] = False


def push_ball_center(hands, br, ground_y):
    """Ball resting on the ground with Pip's mittens pressed on its left face."""
    hx_avg = sum(h[0] for h in hands) / float(len(hands))
    return hx_avg + 0.42 * br, ground_y - br


def _hero(size, cx, cy, Rs, ground):
    cv = Image.new("RGBA", size, (0, 0, 0, 0))

    def U(x, y):
        return (cx + x * Rs, cy + y * Rs)

    ow = 0.05 * Rs
    gy = 0.6
    H = 1.05 * Rs
    tilt = -8.0
    layer, anchor, info = draw_pip(H, "push", ow_k=ow / H, iw_k=0.5 * ow / H, look=(0.012, 0.0), tail_len=0.2)
    at = U(-0.40, gy + 0.01)
    hands = [rig_point(h, anchor, at, tilt) for h in info["hands"]]
    br = 0.52 * Rs
    bcx, bcy = push_ball_center(hands, br, U(0, gy + 0.01)[1])
    if ground:
        mx, my = U(-0.02, 0.725)
        mound = m_ell(size, mx, my, 0.66 * Rs, 0.14 * Rs)
        for (bx, by, r) in ((-0.42, 0.655, 0.1), (-0.08, 0.64, 0.12), (0.32, 0.645, 0.11), (0.56, 0.68, 0.07)):
            mound = m_union(mound, m_circle(size, *U(bx, by), r * Rs))
        mound = m_soften(mound, 0.03 * Rs)
        paint_fill(cv, m_dilate(mound, ow), NAVY)
        glossy(cv, mound, WHITE, WHITE, hx("#9DC1EC"), kind="vertical", core=0.6, core_d=0.3, spec=0,
               stops=[(0, WHITE), (0.45, ICE), (1, hx("#A9CCF0"))])
        rnd = random.Random(3)
        for _ in range(9):
            gx, gy_ = U(rnd.uniform(-0.55, 0.55), rnd.uniform(0.7, 0.78))
            paint_fill(cv, m_inter(m_circle(size, gx, gy_, rnd.uniform(0.008, 0.014) * Rs), mound), WHITE)
    sh = m_ell(size, (at[0] + bcx) / 2.0, U(0, gy + 0.012)[1], (bcx - at[0]) / 2.0 + 0.62 * br, 0.07 * Rs)
    paint_fill(cv, m_blur(sh, 0.035 * Rs), NAVY, 0.4)

    # speed lines trailing behind Pip (left of his head)
    for (y, x0, x1, w) in ((-0.3, -0.95, -0.68, 0.062), (-0.1, -0.98, -0.74, 0.055)):
        m = swoosh_mask(size, [U(x0, y + 0.02), U((x0 + x1) / 2, y + 0.004), U(x1, y - 0.004)], w * Rs, taper_in=0.75)
        paint_fill(cv, m_dilate(m, ow * 0.55), NAVY)
        paint_grad(cv, m, lin_fn(U(x0, y), U(x1, y)), [(0, SKY), (0.6, ICE), (1, WHITE)])

    draw_puff(cv, [(at[0] - 0.33 * H, at[1] - 0.04 * Rs, 0.065 * Rs), (at[0] - 0.24 * H, at[1] - 0.08 * Rs, 0.05 * Rs),
                   (at[0] - 0.42 * H, at[1] - 0.0 * Rs, 0.04 * Rs)], ow * 0.6)
    draw_snowball(cv, bcx, bcy, br, ow, seed=7, sparkles=True)
    draw_puff(cv, [(bcx + 0.62 * br, at[1] - 0.05 * Rs, 0.07 * Rs), (bcx + 0.8 * br, at[1] - 0.025 * Rs, 0.05 * Rs),
                   (bcx + 0.45 * br, at[1] - 0.0 * Rs, 0.05 * Rs)], ow * 0.6)
    place_rig(cv, layer, anchor, info, at, tilt, ow)

    draw_sparkle(cv, bcx + 0.6 * br, bcy - 1.12 * br, 0.135 * Rs, GOLD, ow=ow * 0.5, glow=0.4, core=hx("#FFF6C8"))
    draw_sparkle(cv, bcx + 1.08 * br, bcy - 0.58 * br, 0.065 * Rs, WHITE, ow=ow * 0.45, glow=0.4)
    draw_sparkle(cv, bcx - 0.08 * br, bcy - 1.42 * br, 0.06 * Rs, WHITE, ow=ow * 0.45, glow=0.4)
    return cv


def max_radius(img, cx, cy, thr=60):
    """Largest distance from (cx, cy) of any pixel with alpha > thr (binary search on circles)."""
    a = img.getchannel("A").point(lambda v: 255 if v > thr else 0)
    lo, hi = 0.0, float(max(img.size))
    for _ in range(18):
        mid = (lo + hi) / 2.0
        m = Image.new("L", img.size, 255)
        ImageDraw.Draw(m).ellipse((cx - mid, cy - mid, cx + mid, cy + mid), fill=0)
        if ImageChops.multiply(a, m).getbbox():
            lo = mid
        else:
            hi = mid
    return hi


def fitted_hero(N, limit, ground=False):
    """Render the hero scene, then re-render scaled so every visible pixel sits inside radius `limit`."""
    Rs = limit * 0.97
    img = hero_scene((N, N), N / 2.0, N / 2.0, Rs, ground=ground)
    r = max(max_radius(img, N / 2.0, N / 2.0, 60), max_radius(img, N / 2.0, N / 2.0, 8) - 0.012 * limit)
    if r > limit:
        Rs *= limit / r
        img = hero_scene((N, N), N / 2.0, N / 2.0, Rs, ground=ground)
    return img, Rs


# --------------------------------------------------------------------------
# Launcher icon
# --------------------------------------------------------------------------

DENSITIES = [("mdpi", 1.0), ("hdpi", 1.5), ("xhdpi", 2.0), ("xxhdpi", 3.0), ("xxxhdpi", 4.0)]


def icon_background(N):
    cv = Image.new("RGBA", (N, N), NIGHT)
    full = mnew((N, N), 255)
    paint_grad(cv, full, lambda x, y: y / float(N),
               [(0, hx("#17143F")), (0.3, NIGHT), (0.55, DEEP), (0.78, hx("#2F49AE")), (1, hx("#3B63C9"))], N=24)
    draw_aurora(cv, 0.37, 0.045, 0.15, waves=0.9, phase=0.9, strength=1.0, seed=9, tilt=-0.14)
    draw_stars(cv, 44, 11, (0.04, 0.04, 0.96, 0.64), 0.0016, 0.0040, flakes=5, twinkles=3, flake_r=0.016)

    def back_fn(u):
        return N * (0.705 - 0.075 * math.exp(-((u - 0.2) / 0.2) ** 2) - 0.055 * math.exp(-((u - 0.86) / 0.14) ** 2))

    back = hill_mask((N, N), back_fn)
    draw_snow_hill(cv, back, N * 0.62, N * 0.86, light=hx("#D5E9FF"), mid=hx("#A9C9F2"), low=hx("#7E9FE0"), rim=False)
    # tiny pines on the back hill
    for (u, hk) in ((0.80, 0.075), (0.865, 0.06), (0.17, 0.065)):
        bx, by = u * N, back_fn(u) + N * 0.012
        tree = m_poly((N, N), [(bx, by - hk * N), (bx + 0.032 * N, by), (bx - 0.032 * N, by)])
        tree = m_union(tree, m_poly((N, N), [(bx, by - hk * N * 1.25), (bx + 0.024 * N, by - hk * N * 0.45),
                                            (bx - 0.024 * N, by - hk * N * 0.45)]))
        paint_fill(cv, tree, hx("#3A4FA6"))
        cap = m_inter(tree, m_poly((N, N), [(bx - N, by - hk * N * 1.3), (bx + N, by - hk * N * 1.3),
                                            (bx + N, by - hk * N * 0.9), (bx - N, by - hk * N * 0.9)]))
        paint_fill(cv, cap, hx("#D5E9FF"))

    def front_fn(u):
        return N * (0.668 + 0.11 * ((u - 0.5) * 2) ** 2)

    front = hill_mask((N, N), front_fn)
    draw_snow_hill(cv, front, N * 0.66, N, light=WHITE, mid=ICE, low=hx("#A7C8F0"), rim=True)
    # soft sparkle glints on the snow
    rnd = random.Random(21)
    for _ in range(14):
        u = rnd.uniform(0.12, 0.88)
        y = front_fn(u) + rnd.uniform(0.02, 0.2) * N
        r = rnd.uniform(0.002, 0.004) * N
        paint_fill(cv, m_circle((N, N), u * N, y, r), WHITE, 0.9)
    return cv


def legacy_icon(comp, px, shape):
    N = comp.size[0]
    c0, c1 = int(round(N * 18 / 108.0)), int(round(N * 90 / 108.0))
    crop = comp.crop((c0, c0, c1, c1))
    M = crop.size[0]
    margin = M * 0.045
    if shape == "round":
        m = m_circle((M, M), M / 2.0, M / 2.0, M / 2.0 - margin)
    else:
        m = m_rrect((M, M), (margin, margin, M - margin, M - margin), M * 0.21)
    out = Image.new("RGBA", (M, M), (0, 0, 0, 0))
    paint_fill(out, m_blur(m_shift(m, 0, M * 0.014), M * 0.014), (10, 8, 40, 255), 0.45)
    shaped = crop.copy()
    shaped.putalpha(m)
    out.alpha_composite(shaped)
    # thin top bevel highlight
    bev = m_inter(m_sub(m, m_shift(m, 0, M * 0.012)), m)
    paint_fill(out, m_blur(bev, M * 0.004), WHITE, 0.35)
    return downscale(out, (px, px))


_CACHE = {}


def launcher_layers():
    if "launcher" not in _CACHE:
        N = 108 * 4 * SS
        fg, Rs = fitted_hero(N, 33 * 4 * SS * 0.985)
        mono = hero_scene((N, N), N / 2.0, N / 2.0, Rs, mono=True)
        bg = icon_background(N)
        _CACHE["launcher"] = (fg, bg, mono)
    return _CACHE["launcher"]


def gen_launcher():
    print("launcher icons")
    fg, bg, mono = launcher_layers()
    comp = bg.copy()
    comp.alpha_composite(fg)
    for name, k in DENSITIES:
        d = os.path.join(RES, "mipmap-" + name)
        s = int(round(108 * k))
        save_png(downscale(fg, (s, s)), os.path.join(d, "ic_launcher_foreground.png"))
        save_png(dither(downscale(bg, (s, s)), 1, 3).convert("RGB"), os.path.join(d, "ic_launcher_background.png"))
        save_png(downscale(mono, (s, s)), os.path.join(d, "ic_launcher_monochrome.png"))
        L = int(round(48 * k))
        save_png(legacy_icon(comp, L, "square"), os.path.join(d, "ic_launcher.png"))
        save_png(legacy_icon(comp, L, "round"), os.path.join(d, "ic_launcher_round.png"))


def gen_splash():
    print("splash icon")
    N = 960 * SS
    img, _ = fitted_hero(N, 316 * SS, ground=True)
    save_png(downscale(img, (960, 960)), os.path.join(NODPI, "splash_icon.png"))


def crop_pad(img, pad):
    bb = img.getchannel("A").getbbox()
    x0, y0, x1, y1 = bb
    W, H = img.size
    return img.crop((max(0, x0 - pad), max(0, y0 - pad), min(W, x1 + pad), min(H, y1 + pad)))


def fit_ss(img, target_h=None, target_w=None):
    w, h = img.size
    if target_h:
        tw = int(round(w * target_h / float(h)))
        return downscale(img, (tw, target_h))
    th = int(round(h * target_w / float(w)))
    return downscale(img, (target_w, th))


def gen_mascot():
    print("mascot")
    H = 2700
    layer, anchor, info = draw_pip(H, "wave")
    cv = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    sh = m_ell(cv.size, anchor[0], anchor[1] + 0.012 * H, 0.3 * H, 0.04 * H)
    paint_fill(cv, m_blur(sh, 0.025 * H), NAVY, 0.3)
    cv.alpha_composite(layer)
    img = crop_pad(cv, 12 * SS)
    out = fit_ss(img, target_h=800)
    save_png(out, os.path.join(NODPI, "mascot_pip.png"))


# --------------------------------------------------------------------------
# Logo
# --------------------------------------------------------------------------


def snow_cap(m, F, seed, top_frac=0.36):
    """Fluffy snow cap (with drips) sitting on the upward-facing top edges of a glyph mask."""
    size = m.size
    bb = m.getbbox()
    if bb is None:
        return mnew(size)
    x0, y0, x1, y1 = bb
    hgt = y1 - y0
    thr = m.point([0] * 128 + [255] * 128)
    step = max(2, int(round(F * 0.01)))
    xs = list(range(x0 + step // 2, x1, step))
    tops = []
    for x in xs:
        cb = thr.crop((x, y0, x + 1, y1)).getbbox()
        tops.append(None if cb is None else y0 + cb[1])
    segs, cur = [], []
    limit = y0 + top_frac * hgt
    for i, x in enumerate(xs):
        t = tops[i]
        ok = t is not None and t < limit
        if ok and cur and abs(t - cur[-1][1]) > 0.05 * F:
            segs.append(cur)
            cur = []
        if ok:
            cur.append((x, t))
        elif cur:
            segs.append(cur)
            cur = []
    if cur:
        segs.append(cur)
    rnd = random.Random(seed)
    cap = mnew(size)
    up0, dn0 = 0.05 * F, 0.08 * F
    for seg in segs:
        L = len(seg)
        if L < 4:
            continue
        ts = [t for _, t in seg]
        fs = []
        top_pts, bot_pts = [], []
        for j, (x, t) in enumerate(seg):
            ja, jb = max(0, j - 1), min(L - 1, j + 1)
            slope = abs(ts[jb] - ts[ja]) / float(step * max(1, jb - ja))
            f = clamp01(1 - (slope - 0.45) / 1.6)
            fs.append(f)
            ph = x / (0.15 * F) * 2 * math.pi
            up = up0 * f * (0.72 + 0.32 * abs(math.sin(ph / 2 + seed)))
            dn = dn0 * f * (0.85 + 0.22 * math.sin(ph * 0.6 + seed * 1.7))
            top_pts.append((x, t - up))
            bot_pts.append((x, t + dn))
        cm = m_poly(size, top_pts + bot_pts[::-1])
        for (j, sgn) in ((0, -1), (L - 1, 1)):
            if fs[j] > 0.6:
                x, t = seg[j]
                r = (up0 + dn0) * 0.5 * fs[j]
                cm = m_union(cm, m_circle(size, x + sgn * 0.012 * F, t + (dn0 - up0) * 0.4, r))
        cap = m_union(cap, cm)
        span = seg[-1][0] - seg[0][0]
        if span > 0.28 * F:
            nd = 1 if span < 0.62 * F else 2
            cands = [j for j in range(int(L * 0.18), int(L * 0.82)) if fs[j] > 0.85]
            picks = []
            for _ in range(nd):
                if not cands:
                    break
                j = cands[rnd.randrange(len(cands))]
                if all(abs(j - q) > L * 0.3 for q in picks):
                    picks.append(j)
            for j in picks:
                x, t = seg[j]
                ln = rnd.uniform(0.06, 0.12) * F
                r = 0.026 * F
                drip = m_line(size, [(x, t + dn0 * 0.5), (x, t + dn0 + ln)], 2 * r)
                drip = m_union(drip, m_circle(size, x, t + dn0 + ln + 0.008 * F, r * 1.22))
                cap = m_union(cap, drip)
    return m_soften(cap, 0.012 * F)


def logo_letter(ch, font, Fk, cap_h, rot_deg, seed, caps=True):
    """Build one FROSTY glyph on a local canvas. Returns (layer, outer_mask, pivot)."""
    adv = font.getlength(ch)
    mg = int(0.55 * Fk)
    Lw, Lh = int(adv + 2 * mg), int(cap_h + 2 * mg)
    size = (Lw, Lh)
    T = mnew(size)
    ImageDraw.Draw(T).text((mg, mg + cap_h), ch, font=font, fill=255, anchor="ls")
    piv = (mg + adv / 2.0, mg + cap_h / 2.0)
    if rot_deg:
        T = T.rotate(-rot_deg, resample=BICUBIC, center=piv)
    sw, wo, E = 0.03 * Fk, 0.055 * Fk, 0.07 * Fk
    Tw = m_dilate(T, sw)
    cap = snow_cap(Tw, Fk, seed) if caps else mnew(size)
    side = mnew(size)
    n = 10
    for k in range(1, n + 1):
        side = ImageChops.lighter(side, m_shift(Tw, 0, E * k / n))
    outer = m_dilate(m_union(Tw, cap, side), wo)
    cv = Image.new("RGBA", size, (0, 0, 0, 0))
    paint_fill(cv, outer, NAVY)
    bb = Tw.getbbox()
    paint_grad(cv, side, lin_fn((0, bb[1] + E), (0, bb[3] + E)), [(0, hx("#4C74E0")), (0.6, hx("#2E46A8")),
                                                                 (1, hx("#202C78"))])
    paint_fill(cv, m_inter(m_sub(m_shift(Tw, 0, E), m_shift(Tw, 0, E - 0.012 * Fk)), side), hx("#16205A"), 0.6)
    paint_grad(cv, Tw, lin_fn((0, bb[1]), (0, bb[3])), [(0, WHITE), (0.7, WHITE), (1, hx("#CFEAFF"))])
    tb = T.getbbox()
    paint_grad(cv, T, lin_fn((0, tb[1]), (0, tb[3])),
               [(0, WHITE), (0.2, hx("#F2FBFF")), (0.45, ICE), (0.72, hx("#9FDDFF")), (1, SKY)])
    cr = m_inter(m_blur(m_sub(T, m_shift(T, -0.01 * Fk, -0.07 * Fk)), 0.03 * Fk), T)
    paint_fill(cv, cr, hx("#2D93E6"), 0.55)
    hl = m_inter(m_blur(m_sub(T, m_shift(T, 0.012 * Fk, 0.03 * Fk)), 0.012 * Fk), T)
    paint_fill(cv, hl, WHITE, 0.8)
    # glossy diagonal streak
    gl = m_inter(m_poly(size, [(tb[0], tb[1] + 0.52 * (tb[3] - tb[1])), (tb[2], tb[1] + 0.3 * (tb[3] - tb[1])),
                               (tb[2], tb[1] + 0.38 * (tb[3] - tb[1])), (tb[0], tb[1] + 0.6 * (tb[3] - tb[1]))]),
                 m_erode(T, 0.03 * Fk))
    paint_fill(cv, m_blur(gl, 0.006 * Fk), WHITE, 0.28)
    if caps and cap.getbbox():
        sh = m_inter(m_blur(m_shift(cap, 0, 0.035 * Fk), 0.02 * Fk), m_sub(Tw, cap))
        paint_fill(cv, sh, hx("#2B5FB8"), 0.45)
        paint_fill(cv, m_dilate(cap, 0.026 * Fk), NAVY)
        cb = cap.getbbox()
        paint_grad(cv, cap, lin_fn((0, cb[1]), (0, cb[3])), [(0, WHITE), (0.55, hx("#F4FAFF")), (1, hx("#C6E0FA"))])
        csh = m_inter(m_blur(m_sub(cap, m_shift(cap, 0.01 * Fk, -0.035 * Fk)), 0.012 * Fk), cap)
        paint_fill(cv, csh, hx("#94B9E6"), 0.65)
        chl = m_inter(m_blur(m_sub(cap, m_shift(cap, 0.006 * Fk, 0.016 * Fk)), 0.006 * Fk), cap)
        paint_fill(cv, chl, WHITE, 0.9)
        rnd = random.Random(seed + 5)
        for _ in range(4):
            cxp = rnd.uniform(cb[0] + 0.1 * (cb[2] - cb[0]), cb[2] - 0.1 * (cb[2] - cb[0]))
            cyp = cb[1] + 0.35 * (cb[3] - cb[1]) * rnd.uniform(0.2, 0.8)
            paint_fill(cv, m_inter(m_circle(size, cxp, cyp, 0.009 * Fk), cap), WHITE)
    return cv, outer, piv


def ribbon_text_letter(ch, font, Fk, cap_h, rot_deg):
    adv = font.getlength(ch)
    mg = int(0.4 * Fk)
    size = (int(adv + 2 * mg), int(cap_h + 2 * mg))
    T = mnew(size)
    ImageDraw.Draw(T).text((mg, mg + cap_h), ch, font=font, fill=255, anchor="ls")
    piv = (mg + adv / 2.0, mg + cap_h / 2.0)
    wo, E = 0.085 * Fk, 0.06 * Fk
    out = m_dilate(T, wo)
    side = mnew(size)
    for k in range(1, 7):
        side = ImageChops.lighter(side, m_shift(T, 0, E * k / 6))
    cv = Image.new("RGBA", size, (0, 0, 0, 0))
    paint_fill(cv, m_union(out, m_dilate(side, wo)), NAVY)
    paint_fill(cv, side, hx("#B4570C"))
    tb = T.getbbox()
    paint_grad(cv, T, lin_fn((0, tb[1]), (0, tb[3])), [(0, hx("#FFF3B0")), (0.25, hx("#FFE27A")), (0.7, hx("#FFC23A")),
                                                       (1, hx("#FFB020"))])
    cr = m_inter(m_blur(m_sub(T, m_shift(T, 0, -0.06 * Fk)), 0.025 * Fk), T)
    paint_fill(cv, cr, hx("#E8890F"), 0.5)
    hl = m_inter(m_blur(m_sub(T, m_shift(T, 0.01 * Fk, 0.035 * Fk)), 0.01 * Fk), T)
    paint_fill(cv, hl, WHITE, 0.85)
    if rot_deg:
        cv = cv.rotate(-rot_deg, resample=BICUBIC, center=piv)
    return cv, piv


def draw_logo(F=300, sparkles=True):
    k = SS
    Fk = float(F * k)
    font = ImageFont.truetype(FONT_DISPLAY, int(Fk))
    cap_h = -font.getbbox("H", anchor="ls")[1]
    word = "FROSTY"
    W, H = int(1500 * k), int(860 * k)
    cv = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sil = mnew((W, H))
    advs = [font.getlength(c) for c in word]
    kern = -0.022 * Fk
    total = sum(advs) + kern * (len(word) - 1)
    Ra = 2.6 * total
    cx, base_y = W / 2.0, 0.33 * H
    wob = [-3.0, 2.5, -2.0, 2.5, -2.5, 3.0]
    bob = [0.0, -0.02, 0.015, -0.015, 0.02, -0.01]
    layers = []
    x = -total / 2.0
    pivots = []
    for i, ch in enumerate(word):
        sc = x + advs[i] / 2.0
        th = sc / Ra
        gx = cx + Ra * math.sin(th)
        gy = base_y + Ra - Ra * math.cos(th) + bob[i] * Fk
        rot = math.degrees(th) + wob[i]
        lay, outer, piv = logo_letter(ch, font, Fk, cap_h, rot, seed=i * 7 + 3)
        off = (int(round(gx - piv[0])), int(round(gy - piv[1])))
        layers.append((lay, off))
        tmp = mnew((W, H))
        tmp.paste(outer, off)
        sil = ImageChops.lighter(sil, tmp)
        pivots.append((gx, gy, rot))
        x += advs[i] + kern

    # ---- ribbon (concentric with the word arc) ----
    t = 0.40 * Fk
    cy0 = base_y + cap_h / 2.0 + 0.01 * cap_h + t / 2.0
    C = (cx, base_y + Ra)
    Rr = C[1] - cy0

    def M(u, v):
        ph = u / Rr
        return (C[0] + (Rr - v) * math.sin(ph), C[1] - (Rr - v) * math.cos(ph))

    def strip(u0, u1, v0, v1, n=48):
        return [M(lerp(u0, u1, i / float(n)), v0) for i in range(n + 1)] + \
               [M(lerp(u1, u0, i / float(n)), v1) for i in range(n + 1)]

    Lb = 0.80 * total
    Lt = 0.40 * Fk
    d = 0.36 * t
    notch = 0.36 * t
    size = (W, H)
    band = m_poly(size, strip(-Lb / 2, Lb / 2, -t / 2, t / 2))
    tails, folds = [], []
    for sgn in (-1, 1):
        ue = sgn * Lb / 2
        uo = sgn * (Lb / 2 + Lt)
        ui = ue - sgn * 0.5 * t
        pts = [M(ui, -t / 2 + d), M(uo, -t / 2 + d), M(uo - sgn * notch, d), M(uo, t / 2 + d), M(ui, t / 2 + d)]
        tails.append(m_poly(size, pts))
        folds.append(m_poly(size, [M(ue, t / 2 - 0.02 * t), M(ue, t / 2 + d), M(ue - sgn * 0.5 * t, t / 2 + d)]))
    rib_all = m_union(band, *tails)
    rib_out = m_dilate(rib_all, 0.05 * Fk)
    side = mnew(size)
    Er = 0.05 * Fk
    for kk in range(1, 7):
        side = ImageChops.lighter(side, m_shift(rib_all, 0, Er * kk / 6))
    rib_out = m_union(rib_out, m_dilate(side, 0.05 * Fk))
    sil = ImageChops.lighter(sil, rib_out)

    # ---- ribbon text ----
    Ft = 0.40 * Fk
    tfont = ImageFont.truetype(FONT_DISPLAY, int(Ft))
    tcap = -tfont.getbbox("H", anchor="ls")[1]
    txt = "ROLL RALLY"
    tadv = [tfont.getlength(c) for c in txt]
    tk = 0.01 * Ft
    ttot = sum(tadv) + tk * (len(txt) - 1)
    tlayers = []
    u = -ttot / 2.0
    for i, ch in enumerate(txt):
        uc = u + tadv[i] / 2.0
        u += tadv[i] + tk
        if ch == " ":
            continue
        gx, gy = M(uc, -0.04 * t)
        lay, piv = ribbon_text_letter(ch, tfont, Ft, tcap, math.degrees(uc / Rr))
        tlayers.append((lay, (int(round(gx - piv[0])), int(round(gy - piv[1])))))

    # ---- paint ----
    paint_fill(cv, m_blur(m_shift(sil, 0, 0.05 * Fk), 0.035 * Fk), hx("#0E0B2E"), 0.5)
    for lay, off in layers:
        cv.alpha_composite(lay, dest=off) if off[0] >= 0 and off[1] >= 0 else _composite_at(cv, lay, off)
    # ribbon
    paint_fill(cv, rib_out, NAVY)
    for tm in tails:
        paint_fill(cv, m_dilate(tm, 0.03 * Fk), NAVY)
        glossy(cv, tm, hx("#E8337A"), hx("#FF6FA3"), hx("#A81E5A"), kind="vertical", core=0.35, spec=0)
    paint_fill(cv, m_sub(side, rib_all), hx("#8E1A4F"))
    for fm in folds:
        paint_fill(cv, fm, hx("#7A1446"))
    paint_fill(cv, m_dilate(band, 0.03 * Fk), NAVY)
    paint_grad(cv, band, lambda x_, y_: _ribbon_v(x_, y_, C, Rr, t),
               [(0, hx("#FF8AB6")), (0.18, hx("#FF6A9E")), (0.55, BERRY), (1, hx("#D42D70"))])
    gloss = m_poly(size, strip(-Lb / 2 + 0.2 * t, Lb / 2 - 0.2 * t, -t / 2 + 0.1 * t, -t / 2 + 0.26 * t))
    paint_fill(cv, m_blur(gloss, 0.02 * Fk), WHITE, 0.4)
    st = mnew(size)
    for vv in (-t / 2 + 0.11 * t, t / 2 - 0.11 * t):
        uu = -Lb / 2 + 0.16 * t
        while uu < Lb / 2 - 0.16 * t:
            st = m_union(st, m_line(size, [M(uu, vv), M(min(uu + 0.11 * t, Lb / 2 - 0.16 * t), vv)], 0.022 * Fk))
            uu += 0.2 * t
    paint_fill(cv, st, hx("#FFC2D9"), 0.75)
    for lay, off in tlayers:
        _composite_at(cv, lay, off)
    if sparkles:
        fx, fy, _ = pivots[0]
        yx, yy, _ = pivots[-1]
        ox, oy, _ = pivots[2]
        draw_sparkle(cv, yx + 0.40 * Fk, yy - 0.52 * Fk, 0.13 * Fk, WHITE, ow=0.02 * Fk, glow=0.55,
                     core=WHITE)
        draw_sparkle(cv, yx + 0.58 * Fk, yy - 0.25 * Fk, 0.065 * Fk, GOLD, ow=0.016 * Fk, glow=0.5)
        draw_sparkle(cv, fx - 0.36 * Fk, fy - 0.42 * Fk, 0.09 * Fk, GOLD, ow=0.018 * Fk, glow=0.5,
                     core=hx("#FFF6C8"))
        draw_sparkle(cv, ox + 0.05 * Fk, oy - 0.62 * Fk, 0.07 * Fk, WHITE, ow=0.016 * Fk, glow=0.5)
    return cv


def _ribbon_v(x, y, C, Rr, t):
    r = math.hypot(x - C[0], y - C[1])
    return (Rr - r + t / 2.0) / t


def _composite_at(cv, lay, off):
    tmp = Image.new("RGBA", cv.size, (0, 0, 0, 0))
    tmp.paste(lay, off)
    cv.alpha_composite(tmp)


def gen_logo():
    print("logo")
    img = logo_4x()
    out = fit_ss(img, target_w=int(round(img.size[0] / float(SS))))
    save_png(out, os.path.join(NODPI, "logo_frosty.png"))


# --------------------------------------------------------------------------
# UI icon set (256x256, drawn at 4x in 256-unit coordinates)
# --------------------------------------------------------------------------

WOOD, WOOD_L, WOOD_D = hx("#C9743C"), hx("#F2A866"), hx("#8A431F")
STEEL, STEEL_L, STEEL_D = hx("#A9BBDD"), hx("#EEF4FF"), hx("#6578A8")
PAPER, PAPER_L, PAPER_D = hx("#FFE9B8"), hx("#FFF8E6"), hx("#E3B76A")
ICE_W, ICE_D = WHITE, hx("#B4CEEE")


class Ico:
    """256-unit icon drawn at SS x on a padded canvas (PAD units each side) so nothing can be clipped;
    done() crops back to the 256 frame and, only if the art overflows, scales it down about the centre."""
    W_OUT, W_IN, EXT = 10.0, 5.0, 6.0
    PAD, MARGIN = 24.0, 3.0

    def __init__(self):
        self.k = SS
        self.o = self.PAD
        n = int((256 + 2 * self.PAD) * SS)
        self.size = (n, n)
        self.cv = Image.new("RGBA", self.size, (0, 0, 0, 0))

    # geometry (256-unit coordinates)
    def s(self, v):
        """length -> canvas pixels"""
        return v * self.k

    def X(self, v):
        """position -> canvas pixels"""
        return (v + self.o) * self.k

    def pt(self, p):
        return ((p[0] + self.o) * self.k, (p[1] + self.o) * self.k)

    def pts(self, ps):
        return [((x + self.o) * self.k, (y + self.o) * self.k) for x, y in ps]

    def circle(self, cx, cy, r):
        return m_circle(self.size, self.X(cx), self.X(cy), r * self.k)

    def ell(self, cx, cy, rx, ry, ang=0.0):
        return m_ell(self.size, self.X(cx), self.X(cy), rx * self.k, ry * self.k, ang)

    def poly(self, ps):
        return m_poly(self.size, self.pts(ps))

    def spoly(self, ps, n=12):
        return m_poly(self.size, self.pts(catmull(ps, True, n)))

    def rrect(self, x0, y0, x1, y1, r):
        return m_rrect(self.size, (self.X(x0), self.X(y0), self.X(x1), self.X(y1)), r * self.k)

    def rrect_rot(self, x0, y0, x1, y1, r, ang, cx=None, cy=None):
        cx = (x0 + x1) / 2.0 if cx is None else cx
        cy = (y0 + y1) / 2.0 if cy is None else cy
        return self.poly(rot_pts(rrect_pts(x0, y0, x1, y1, r), cx, cy, ang))

    def line(self, ps, w, cap=True):
        return m_line(self.size, self.pts(ps), w * self.k, cap)

    def rnd(self, m, r):
        return m_open(m, r * self.k)

    def rot(self, m, ang, cx=128, cy=128):
        return m.rotate(-ang, resample=BICUBIC, center=(self.X(cx), self.X(cy)))

    def shift(self, m, dx, dy):
        return m_shift(m, dx * self.k, dy * self.k)

    def rot_pt(self, p, ang, cx=128, cy=128):
        return rot_pts([p], cx, cy, ang)[0]

    def dil(self, m, r):
        return m_dilate(m, r * self.k)

    def ero(self, m, r):
        return m_erode(m, r * self.k)

    # painting
    def outline(self, *ms, ext=None, w=None):
        sil = m_union(*ms) if len(ms) > 1 else ms[0]
        o = m_dilate(sil, (self.W_OUT if w is None else w) * self.k)
        e = (self.EXT if ext is None else ext) * self.k
        if e:
            o = m_union(o, m_shift(o, 0, e))
        paint_fill(self.cv, o, NAVY)
        return sil

    def line_under(self, m, w=None):
        paint_fill(self.cv, m_dilate(m, (self.W_IN if w is None else w) * self.k), NAVY)

    def part(self, m, base, light=None, dark=None, line=True, lw=None, sb=None, **kw):
        if line:
            self.line_under(m, lw)
        if sb is not None:
            kw["spec_box"] = tuple(self.X(v) for v in sb)
        glossy(self.cv, m, base, light, dark, **kw)

    def fill(self, m, color, op=1.0):
        paint_fill(self.cv, m, color, op)

    def grad(self, m, p0, p1, stops, op=1.0):
        paint_grad(self.cv, m, lin_fn(self.pt(p0), self.pt(p1)), stops, op)

    def rgrad(self, m, c, r, stops, op=1.0):
        paint_grad(self.cv, m, rad_fn(self.pt(c), r * self.k), stops, op)

    def spec(self, cx, cy, rx, ry, ang=-30.0, op=0.85, clip=None, blur=2.0):
        m = m_blur(self.ell(cx, cy, rx, ry, ang), blur * self.k)
        if clip is not None:
            m = m_inter(m, clip)
        self.fill(m, WHITE, op)

    def inner_shadow(self, m, dx, dy, blur, color, op):
        cr = m_sub(m, self.shift(m, dx, dy))
        self.fill(m_inter(m_blur(cr, blur * self.k), m), color, op)

    def sparkle(self, cx, cy, r, color=WHITE, ow=3.5, glow=0.0, rot=0.0):
        draw_sparkle(self.cv, self.X(cx), self.X(cy), r * self.k, color, ow=ow * self.k, glow=glow, rot=rot)

    def done(self):
        k, o, mg = self.k, self.o, self.MARGIN
        bb = self.cv.getchannel("A").point(lambda v: 255 if v > 8 else 0).getbbox()
        f = 1.0
        if bb:
            x0, y0, x1, y1 = [b / float(k) - o for b in bb]
            for d in (128 - x0, x1 - 128, 128 - y0, y1 - 128):
                if d > 128 - mg:
                    f = min(f, (128 - mg) / d)
        half = 128.0 / f * k
        c = (128 + o) * k
        return self.cv.resize((256, 256), LANCZOS, box=(c - half, c - half, c + half, c + half))


def _gold(c, m, **kw):
    kw.setdefault("kind", "sphere")
    c.part(m, GOLD, GOLD_L, GOLD_D, **kw)


def _emboss(c, m, base, light, dark, d=3.0, op=0.75):
    """Raised emboss: dark drop to the lower-right, light edge to the upper-left."""
    c.fill(m_sub(c.shift(m, d, d), m), dark, op)
    c.fill(m_sub(c.shift(m, -d * 0.6, -d * 0.6), m), light, op)
    c.fill(m, base)


def _engrave(c, m, base, light, dark, d=3.0):
    """Recessed groove: shadow on the upper-left inner edge, light on the lower-right."""
    c.fill(m, dark)
    c.fill(m_inter(m_sub(m, c.shift(m, d, d)), m), shade(dark, 0.25))
    c.fill(m_inter(m_sub(m, c.shift(m, -d, -d)), m), light, 0.8)


def icon_coin():
    c = Ico()
    face = c.circle(128, 119, 101)
    edge = c.circle(128, 133, 101)
    c.outline(face, edge)
    c.grad(edge, (0, 150), (0, 234), [(0, GOLD_D), (1, hx("#A9550C"))])
    ridges = mnew(c.size)
    for i in range(-9, 10):
        a = math.radians(90 + i * 8.5)
        x = 128 + 101 * math.cos(a)
        ridges = m_union(ridges, c.line([(x, 120), (x, 236)], 3.2))
    c.fill(m_inter(m_sub(ridges, face), edge), hx("#9A4A0A"), 0.55)
    c.part(face, GOLD, GOLD_L, GOLD_D, kind="sphere", lc=(0.3, 0.22), core=0.35, spec=0)
    ring = m_sub(c.circle(128, 119, 79), c.circle(128, 119, 70))
    _engrave(c, ring, GOLD, GOLD_L, hx("#D9860F"), 2.5)
    disc = c.circle(128, 119, 70)
    c.rgrad(disc, (100, 92), 110, [(0, hx("#FFE27A")), (1, hx("#FFB72C"))], 0.6)
    flake = snowflake_mask(c.size, c.X(128), c.X(119), c.s(46), c.s(13), 0)
    _emboss(c, flake, hx("#FFE68A"), WHITE, hx("#C46F0E"), 3.2, 0.8)
    c.grad(flake, (100, 80), (150, 160), [(0, hx("#FFF6C8")), (1, hx("#FFC83D"))], 0.9)
    c.spec(84, 68, 34, 15, -38, 0.75, clip=face, blur=3)
    c.spec(62, 108, 7, 7, 0, 0.7, clip=face, blur=1)
    c.sparkle(198, 54, 22, WHITE, ow=3.5)
    return c.done()


def icon_gem():
    c = Ico()
    A, B, C2, D = (82, 50), (110, 50), (146, 50), (174, 50)
    L, M1, M2, R = (34, 100), (94, 100), (162, 100), (222, 100)
    P = (128, 228)
    outline_pts = [A, D, R, P, L]
    sil = c.poly(outline_pts)
    sil = c.rnd(sil, 5)
    c.outline(sil)
    facets = [
        ([A, B, M1, L], hx("#FF9EC4"), hx("#FFC6DC")),
        ([B, C2, M2, M1], hx("#FFC0D9"), hx("#FFE6F0")),
        ([C2, D, R, M2], hx("#FF6FA4"), hx("#FF9EC4")),
        ([L, M1, P], hx("#FF4D8D"), hx("#FF7BAA")),
        ([M1, M2, P], hx("#E5307A"), hx("#FF5C96")),
        ([M2, R, P], hx("#B81E5E"), hx("#D9306F")),
    ]
    for pts_, base, lt in facets:
        fm = m_inter(c.poly(pts_), sil)
        c.grad(fm, pts_[0], pts_[-1] if len(pts_) == 3 else pts_[2], [(0, lt), (1, base)])
    # aurora-mint glints inside the pavilion
    g1 = m_inter(c.poly([(70, 112), (96, 112), (122, 196)]), sil)
    c.grad(g1, (80, 110), (118, 196), [(0, MINT_L), (1, alpha(MINT, 0))], 0.8)
    g2 = m_inter(c.poly([(170, 108), (196, 108), (140, 190)]), sil)
    c.grad(g2, (180, 108), (146, 186), [(0, alpha(MINT, 200)), (1, alpha(MINT, 0))], 0.55)
    lines = mnew(c.size)
    for a, b in ((L, R), (M1, P), (M2, P), (B, M1), (C2, M2)):
        lines = m_union(lines, c.line([a, b], 3.0))
    c.fill(m_inter(lines, c.ero(sil, 1)), WHITE, 0.55)
    c.inner_shadow(sil, -4, -6, 4, hx("#7E0F45"), 0.5)
    c.spec(118, 66, 22, 6, -6, 0.9, clip=sil, blur=1.5)
    c.sparkle(70, 84, 20, WHITE, ow=3.2)
    c.sparkle(196, 150, 14, WHITE, ow=3.0)
    return c.done()


def _star_mask(c, cx=128, cy=136, ro=114, ri=55, rr=14):
    m = c.poly(star_pts(cx, cy, ro, ri, 5, -90))
    return c.rnd(m, rr)


def icon_star():
    c = Ico()
    st = _star_mask(c)
    c.outline(st)
    c.part(st, GOLD, GOLD_L, GOLD_D, kind="sphere", lc=(0.32, 0.25), core=0.4, spec=0)
    inner = _star_mask(c, 128, 138, 78, 37, 9)
    c.fill(m_sub(c.dil(inner, 3), inner), hx("#E08A12"), 0.7)
    c.grad(inner, (100, 70), (150, 190), [(0, hx("#FFF0A6")), (0.55, hx("#FFD54F")), (1, hx("#FFB52A"))])
    c.inner_shadow(st, -3, -6, 4, hx("#B5620F"), 0.45)
    c.spec(96, 92, 20, 10, -35, 0.85, clip=st, blur=2.5)
    c.spec(76, 122, 5, 5, 0, 0.8, clip=st, blur=1)
    return c.done()


def icon_star_empty():
    c = Ico()
    st = _star_mask(c)
    c.outline(st)
    c.part(st, SLATE, SLATE_L, SLATE_D, kind="vertical", core=0.3, spec=0)
    inner = _star_mask(c, 128, 138, 80, 38, 9)
    c.fill(inner, hx("#3E4878"))
    c.inner_shadow(inner, 4, 7, 4, hx("#262C55"), 0.85)
    c.fill(m_inter(m_sub(inner, c.shift(inner, -2, -4)), inner), hx("#7C89BC"), 0.8)
    c.spec(92, 90, 14, 6, -35, 0.35, clip=st, blur=2)
    return c.done()


def _padlock(c, cx, cy, s, body_col=None, shackle_col=None, outline=True):
    """Padlock centred at (cx, cy) with scale s (1.0 = full icon)."""
    def X(x):
        return cx + (x - 128) * s

    def Y(y):
        return cy + (y - 128) * s
    outer = c.rrect(X(74), Y(22), X(182), Y(160), 54 * s)
    inner = c.rrect(X(100), Y(48), X(156), Y(160), 28 * s)
    shackle = m_inter(m_sub(outer, inner), c.poly([(X(0), Y(0)), (X(256), Y(0)), (X(256), Y(132)), (X(0), Y(132))]))
    body = c.rrect(X(50), Y(102), X(206), Y(232), 30 * s)
    if outline:
        c.outline(shackle, body)
    c.part(shackle, *(shackle_col or (STEEL, STEEL_L, STEEL_D)), kind="sphere", lc=(0.3, 0.3), core=0.35, spec=0)
    c.spec(X(92), Y(56), 6 * s, 16 * s, 20, 0.8, clip=shackle, blur=1.5)
    bc = body_col or (GOLD, GOLD_L, GOLD_D)
    c.part(body, bc[0], bc[1], bc[2], kind="vertical", core=0.4, spec=0, bounce=0.3)
    band = m_inter(c.rrect(X(50), Y(102), X(206), Y(128), 12 * s), body)
    c.fill(band, WHITE, 0.25)
    kh = m_union(c.circle(X(128), Y(156), 17 * s), c.poly([(X(118), Y(160)), (X(138), Y(160)), (X(134), Y(200)),
                                                          (X(122), Y(200))]))
    kh = c.rnd(kh, 2 * s)
    c.fill(kh, NAVY)
    c.fill(m_inter(c.shift(m_sub(c.dil(kh, 3 * s), kh), 1.5 * s, 2 * s), body), WHITE, 0.45)
    c.spec(X(82), Y(122), 16 * s, 8 * s, -25, 0.75, clip=body, blur=2 * s)
    return shackle, body


def icon_lock():
    c = Ico()
    _padlock(c, 128, 124, 1.0)
    return c.done()


def icon_gear():
    c = Ico()
    teeth = mnew(c.size)
    for i in range(8):
        a = i * 45 + 22.5
        tooth = c.poly(rot_pts(rrect_pts(108, 18, 148, 70, 9), 128, 128, a))
        teeth = m_union(teeth, tooth)
    gear = m_union(c.circle(128, 128, 84), teeth)
    gear = c.rnd(gear, 4)
    hole = c.circle(128, 128, 30)
    gear = m_sub(gear, hole)
    c.outline(gear)
    c.part(gear, SKY, SKY_L, SKY_D, kind="sphere", lc=(0.3, 0.25), core=0.45, spec=0)
    ring = m_sub(c.circle(128, 128, 62), c.circle(128, 128, 46))
    c.fill(ring, hx("#3A9BE6"), 0.9)
    c.fill(m_inter(m_sub(ring, c.shift(ring, -3, -3)), ring), WHITE, 0.6)
    c.fill(m_inter(m_sub(ring, c.shift(ring, 3, 3)), ring), hx("#1F67B8"), 0.7)
    c.line_under(m_sub(c.circle(128, 128, 36), hole), 0)
    c.fill(m_sub(c.circle(128, 128, 38), hole), NAVY)
    c.spec(84, 70, 22, 9, -40, 0.75, clip=gear, blur=2.5)
    return c.done()


def icon_home():
    c = Ico()
    walls = c.poly([(60, 224), (60, 128), (128, 74), (196, 128), (196, 224)])
    walls = c.rnd(walls, 6)
    chimney = c.rrect(156, 50, 184, 110, 5)
    roof = c.line([(34, 140), (128, 58), (222, 140)], 26)
    snow_pts = [(24, 132), (128, 34), (232, 132), (228, 146), (212, 142), (204, 160), (190, 144), (128, 88),
                (66, 144), (54, 162), (44, 142), (28, 146)]
    snow = c.spoly(snow_pts, 10)
    chim_cap = c.spoly([(150, 54), (170, 40), (190, 50), (191, 60), (150, 60)], 8)
    c.outline(walls, chimney, roof, snow, chim_cap)
    c.part(chimney, hx("#B8574A"), hx("#E8826F"), hx("#7E3329"), kind="vertical", core=0.3, spec=0)
    c.part(walls, WOOD, WOOD_L, WOOD_D, kind="vertical", core=0.35, spec=0)
    planks = mnew(c.size)
    for y in (150, 172, 194):
        planks = m_union(planks, c.line([(64, y), (192, y)], 3))
    c.fill(m_inter(planks, walls), WOOD_D, 0.5)
    attic = c.circle(128, 118, 13)
    c.line_under(attic, 4)
    c.rgrad(attic, (124, 113), 16, [(0, hx("#FFF6C8")), (0.6, hx("#FFD866")), (1, hx("#FFA931"))])
    c.fill(m_inter(m_union(c.line([(128, 105), (128, 131)], 3), c.line([(115, 118), (141, 118)], 3)), attic),
           hx("#B5620F"))
    sh = m_inter(m_blur(c.shift(m_union(snow, roof), 0, 5), c.s(2)), m_sub(walls, m_union(snow, roof)))
    c.fill(sh, WOOD_D, 0.45)
    c.part(roof, BERRY, BERRY_L, BERRY_D, kind="vertical", core=0.3, spec=0)
    c.part(snow, WHITE, WHITE, ICE_D, kind="vertical", core=0.45, core_d=0.18, spec=0)
    c.spec(92, 74, 22, 7, -42, 0.9, clip=snow, blur=2)
    c.part(chim_cap, WHITE, WHITE, ICE_D, kind="vertical", core=0.3, spec=0)
    door = c.rrect(110, 164, 146, 226, 16)
    door = m_inter(door, c.poly([(0, 0), (256, 0), (256, 224), (0, 224)]))
    c.part(door, hx("#7A3A1E"), hx("#A65A2E"), hx("#4E220F"), kind="vertical", core=0.3, spec=0, lw=4)
    c.fill(c.circle(137, 198, 4.5), GOLD)
    for wx in (84, 172):
        win = c.rrect(wx - 15, 160, wx + 15, 190, 7)
        c.line_under(win, 4)
        c.rgrad(win, (wx - 4, 168), 26, [(0, hx("#FFF6C8")), (0.6, hx("#FFD866")), (1, hx("#FFA931"))])
        panes = m_union(c.line([(wx, 160), (wx, 190)], 3.5), c.line([(wx - 15, 175), (wx + 15, 175)], 3.5))
        c.fill(m_inter(panes, win), hx("#B5620F"))
    return c.done()


def icon_shop():
    c = Ico()
    body = c.rnd(c.poly([(58, 92), (198, 92), (214, 226), (42, 226)]), 10)
    handles = []
    for hx_ in (98, 158):
        pts = [(hx_ - 22 + 44 * (1 - math.cos(math.pi * i / 24)) / 2, 100 - 58 * math.sin(math.pi * i / 24))
               for i in range(25)]
        handles.append(c.line(pts, 12))
    c.outline(body, *handles)
    for h in handles:
        c.part(h, hx("#FF7FAE"), hx("#FFC2D9"), BERRY_D, kind="sphere", core=0.35, spec=0, lw=4)
    c.part(body, BERRY, BERRY_L, BERRY_D, kind="vertical", core=0.4, spec=0, bounce=0.25)
    fold = m_inter(c.poly([(30, 88), (226, 88), (226, 112), (30, 112)]), body)
    c.fill(fold, hx("#E2357A"))
    c.fill(m_inter(c.line([(40, 112), (216, 112)], 3), body), hx("#9A1650"), 0.8)
    for hx_ in (98, 158):
        for sx in (-22, 22):
            c.fill(c.circle(hx_ + sx, 101, 7), NAVY)
            c.fill(c.circle(hx_ + sx, 101, 4), hx("#FFC2D9"))
    st = c.rnd(c.poly(star_pts(128, 166, 36, 17, 5, -90)), 3)
    c.line_under(st, 4)
    c.grad(st, (110, 134), (146, 198), [(0, GOLD_L), (1, GOLD)])
    c.spec(118, 154, 7, 4, -30, 0.8, clip=st, blur=1)
    c.spec(74, 134, 9, 26, 8, 0.45, clip=body, blur=3)
    return c.done()


def icon_gift():
    c = Ico()
    lid = c.rrect(34, 96, 222, 134, 10)
    box = c.rrect(48, 128, 208, 228, 10)
    loopL = c.ell(96, 70, 36, 22, 28)
    loopR = c.ell(160, 70, 36, 22, -28)
    knot = c.ell(128, 84, 18, 16)
    c.outline(lid, box, loopL, loopR, knot)
    for lp, cx_ in ((loopL, 96), (loopR, 160)):
        c.part(lp, BERRY, BERRY_L, BERRY_D, kind="sphere", core=0.35, spec=0)
        holel = c.ell(cx_ + (8 if cx_ < 128 else -8), 72, 15, 8, 28 if cx_ < 128 else -28)
        c.fill(holel, hx("#8E1A4F"))
    c.part(box, MINT, MINT_L, MINT_D, kind="vertical", core=0.4, spec=0, bounce=0.2)
    c.part(lid, hx("#5CFFD6"), MINT_L, MINT_D, kind="vertical", core=0.35, spec=0)
    rb = m_inter(c.rrect(112, 90, 144, 232, 3), m_union(lid, box))
    c.line_under(rb, 4)
    c.grad(rb, (112, 0), (144, 0), [(0, BERRY_L), (0.5, BERRY), (1, BERRY_D)])
    c.fill(m_inter(c.shift(lid, 0, 7), m_sub(box, rb)), hx("#0F8E71"), 0.35)
    c.part(knot, BERRY, BERRY_L, BERRY_D, kind="sphere", core=0.3, spec=0.6, spec_blur=0.08)
    c.spec(70, 104, 20, 5, -4, 0.7, clip=lid, blur=2)
    c.spec(68, 150, 6, 22, 0, 0.45, clip=box, blur=2.5)
    return c.done()


def icon_map():
    c = Ico()
    P1 = [(34, 62), (98, 44), (98, 212), (34, 230)]
    P2 = [(98, 44), (158, 64), (158, 230), (98, 212)]
    P3 = [(158, 64), (222, 46), (222, 214), (158, 230)]
    panels = [c.poly(p) for p in (P1, P2, P3)]
    sil = c.rnd(m_union(*panels), 6)
    c.outline(sil)
    for i, pm in enumerate(panels):
        pm = m_inter(pm, sil)
        if i == 1:
            c.grad(pm, (98, 0), (158, 0), [(0, hx("#E9C27A")), (1, hx("#F7DDA0"))])
        else:
            c.grad(pm, (0, 50), (0, 230), [(0, PAPER_L), (1, PAPER)])
    c.fill(m_inter(m_union(c.line([(98, 44), (98, 212)], 4), c.line([(158, 64), (158, 230)], 4)), sil), hx("#C99A4E"), 0.9)
    # dashed path to the X
    path = catmull([(52, 196), (82, 170), (118, 178), (140, 140), (122, 108), (160, 96), (188, 104)], closed=False, n=10)
    dash = mnew(c.size)
    acc = 0.0
    on = True
    seg = [path[0]]
    for i in range(1, len(path)):
        d = math.hypot(path[i][0] - path[i - 1][0], path[i][1] - path[i - 1][1])
        acc += d
        seg.append(path[i])
        if acc >= (13 if on else 9):
            if on:
                dash = m_union(dash, c.line(seg, 6.5))
            on = not on
            acc = 0.0
            seg = [path[i]]
    c.fill(dash, hx("#E2357A"))
    c.fill(c.circle(52, 196, 8), hx("#E2357A"))
    x1 = m_union(c.line([(178, 82), (204, 108)], 11), c.line([(204, 82), (178, 108)], 11))
    c.line_under(x1, 3.5)
    c.grad(x1, (178, 82), (204, 108), [(0, hx("#FF6A6A")), (1, hx("#D42A3A"))])
    # little peak + tree
    mt = c.poly([(56, 120), (76, 86), (96, 120)])
    c.fill(mt, hx("#7DB8E8"))
    c.fill(m_inter(c.poly([(68, 99), (76, 86), (84, 99), (76, 96)]), mt), WHITE)
    c.fill(c.poly([(184, 196), (194, 172), (204, 196)]), hx("#3FAE7E"))
    c.spec(56, 70, 14, 5, -16, 0.6, clip=sil, blur=2)
    return c.done()


def icon_profile():
    c = Ico()
    badge = c.circle(128, 140, 104)
    H = 300.0 * c.k
    pip, anchor, info = draw_pip(H, "idle", ow_k=6.0 / 300, iw_k=3.4 / 300, hat_tilt=-5.0)
    pl = Image.new("RGBA", c.size, (0, 0, 0, 0))
    at = (c.X(128), c.X(138) + 0.752 * H)
    place_layer(pl, pip, anchor, at)
    allowed = m_union(badge, c.poly([(-24, -24), (280, -24), (280, 140), (-24, 140)]))
    pa = m_inter(pl.getchannel("A"), allowed)
    pl.putalpha(pa)
    c.outline(badge, m_thresh(pa, 30, 60))
    c.rgrad(badge, (100, 100), 150, [(0, hx("#9FE3FF")), (0.6, SKY), (1, hx("#3A8FE0"))])
    ringm = m_sub(badge, c.circle(128, 140, 94))
    c.fill(ringm, WHITE, 0.9)
    c.fill(m_inter(c.shift(ringm, 0, 4), c.circle(128, 140, 94)), hx("#2B6FC0"), 0.5)
    for (sx, sy, sr) in ((60, 112, 3), (196, 96, 2.5), (186, 196, 3), (70, 196, 2.5)):
        c.fill(c.circle(sx, sy, sr), WHITE, 0.9)
    c.cv.alpha_composite(pl)
    return c.done()


def icon_trophy():
    c = Ico()
    bowl_pts = [(50, 40), (206, 40), (200, 104), (176, 140), (146, 158), (110, 158), (80, 140), (56, 104)]
    bowl = c.spoly(bowl_pts, 10)
    bowl = m_inter(bowl, c.poly([(0, 40), (256, 40), (256, 256), (0, 256)]))
    hL = m_sub(c.ell(52, 82, 34, 36), c.ell(52, 82, 18, 20))
    hR = m_sub(c.ell(204, 82, 34, 36), c.ell(204, 82, 18, 20))
    stem = c.poly([(114, 150), (142, 150), (148, 190), (108, 190)])
    base = c.rrect(68, 184, 188, 228, 12)
    cap = c.rrect(84, 184, 172, 200, 6)
    c.outline(bowl, hL, hR, stem, base)
    for hm in (hL, hR):
        _gold(c, hm, core=0.35, spec=0)
    c.part(stem, GOLD, GOLD_L, GOLD_D, kind="sphere", core=0.4, spec=0)
    c.part(base, hx("#3F3A9A"), hx("#6F6AD6"), hx("#24206A"), kind="vertical", core=0.35, spec=0)
    plate = c.rrect(100, 204, 156, 218, 4)
    c.fill(plate, GOLD)
    c.fill(m_inter(c.rrect(100, 204, 156, 209, 3), plate), GOLD_L)
    c.part(cap, GOLD, GOLD_L, GOLD_D, kind="vertical", core=0.3, spec=0, lw=4)
    _gold(c, bowl, lc=(0.3, 0.2), core=0.45, spec=0)
    rim = m_inter(c.ell(128, 42, 78, 13), bowl)
    c.fill(rim, hx("#D98A14"))
    c.fill(m_inter(c.ell(128, 46, 70, 9), rim), hx("#B5620F"), 0.7)
    st = c.rnd(c.poly(star_pts(128, 96, 30, 14, 5, -90)), 3)
    _emboss(c, st, hx("#FFE68A"), WHITE, hx("#C46F0E"), 2.5, 0.8)
    c.spec(82, 70, 10, 26, 12, 0.75, clip=bowl, blur=3)
    c.sparkle(214, 30, 18, WHITE, ow=3.2)
    return c.done()


def _white_part(c, m, **kw):
    kw.setdefault("kind", "diag")
    kw.setdefault("core", 0.5)
    kw.setdefault("core_d", 0.13)
    kw.setdefault("spec", 0)
    c.part(m, WHITE, WHITE, hx("#AFC9EC"), line=kw.pop("line", False), **kw)


def icon_play():
    c = Ico()
    tri = c.rnd(c.poly([(82, 42), (82, 214), (216, 128)]), 22)
    c.outline(tri, ext=9)
    _white_part(c, tri)
    c.spec(104, 80, 6, 22, 32, 0.9, clip=tri, blur=2)
    return c.done()


def icon_pause():
    c = Ico()
    b1 = c.rrect(64, 44, 114, 212, 18)
    b2 = c.rrect(142, 44, 192, 212, 18)
    c.outline(b1, b2, ext=9)
    for b in (b1, b2):
        _white_part(c, b)
    return c.done()


def _round_button(c, base, light, dark, cy=124, r=104):
    disc = c.circle(128, cy, r)
    c.outline(disc)
    c.part(disc, base, light, dark, kind="sphere", lc=(0.35, 0.25), R=1.05, core=0.45, spec=0, line=False)
    sheen = m_inter(c.ell(128, cy - r * 0.48, r * 0.72, r * 0.38), disc)
    c.grad(sheen, (0, cy - r), (0, cy - r * 0.1), [(0, alpha(WHITE, 150)), (1, alpha(WHITE, 0))])
    return disc


def icon_close():
    c = Ico()
    disc = _round_button(c, BERRY, BERRY_L, BERRY_D)
    x = m_union(c.line([(86, 82), (170, 166)], 38), c.line([(170, 82), (86, 166)], 38))
    c.fill(m_inter(m_blur(c.shift(x, 0, 6), c.s(2)), disc), hx("#8E1A4F"), 0.6)
    c.line_under(x, 5)
    _white_part(c, x)
    return c.done()


def icon_back():
    c = Ico()
    arr = c.rnd(c.poly([(32, 128), (118, 44), (118, 90), (218, 90), (218, 166), (118, 166), (118, 212)]), 12)
    c.outline(arr, ext=9)
    _white_part(c, arr)
    return c.done()


def icon_wheel():
    c = Ico()
    cx, cy, R = 128, 134, 102
    disc = c.circle(cx, cy, R)
    pointer = c.rnd(c.poly([(104, 12), (152, 12), (128, 62)]), 7)
    c.outline(disc, pointer)
    _gold(c, disc, core=0.4, spec=0, line=False)
    Ri = R - 17
    cols = [BERRY, GOLD, MINT, SKY, BERRY, GOLD, MINT, SKY]
    for i, col in enumerate(cols):
        wm = mnew(c.size)
        ImageDraw.Draw(wm).pieslice((c.X(cx - Ri), c.X(cy - Ri), c.X(cx + Ri), c.X(cy + Ri)), -90 + i * 45 - 22.5,
                                    -90 + i * 45 + 22.5, fill=255)
        c.rgrad(wm, (cx - 30, cy - 34), Ri * 1.6, [(0, lighten(col, 0.45)), (0.55, col), (1, shade(col, 0.25))])
    seps = mnew(c.size)
    for i in range(8):
        a = math.radians(-90 + i * 45 - 22.5)
        seps = m_union(seps, c.line([(cx, cy), (cx + Ri * math.cos(a), cy + Ri * math.sin(a))], 5))
    c.fill(seps, WHITE, 0.95)
    inner = c.circle(cx, cy, Ri)
    c.fill(m_sub(c.dil(inner, 2.5), inner), hx("#B5620F"))
    c.inner_shadow(inner, 4, 6, 4, hx("#22195A"), 0.35)
    for i in range(12):
        a = math.radians(i * 30)
        bx, by = cx + (R - 8.5) * math.cos(a), cy + (R - 8.5) * math.sin(a)
        c.fill(c.circle(bx, by, 5.5), hx("#B5620F"))
        c.fill(c.circle(bx - 0.8, by - 0.8, 4.2), hx("#FFF8D8"))
    hub = c.circle(cx, cy, 22)
    c.line_under(hub, 5)
    _gold(c, hub, core=0.35, spec=0.8, line=False, spec_blur=0.08)
    c.part(pointer, BERRY, BERRY_L, BERRY_D, kind="vertical", core=0.4, spec=0, line=False)
    c.spec(118, 24, 9, 4, 0, 0.8, clip=pointer, blur=1)
    c.spec(70, 70, 20, 8, -42, 0.5, clip=disc, blur=3)
    return c.done()


def icon_calendar():
    c = Ico()
    page = c.rrect(34, 54, 222, 228, 26)
    rings = [c.rrect(74, 26, 96, 82, 11), c.rrect(160, 26, 182, 82, 11)]
    c.outline(page, *rings)
    c.part(page, WHITE, WHITE, hx("#C2D7F2"), kind="vertical", core=0.35, spec=0, line=False)
    band = m_inter(c.rrect(34, 54, 222, 112, 26), c.poly([(0, 0), (256, 0), (256, 108), (0, 108)]))
    band = m_inter(band, page)
    c.grad(band, (0, 54), (0, 108), [(0, BERRY_L), (0.5, BERRY), (1, BERRY_D)])
    c.fill(m_inter(c.line([(34, 108), (222, 108)], 5), page), NAVY)
    c.spec(70, 68, 22, 5, 0, 0.5, clip=band, blur=1.5)
    for hx_ in (85, 171):
        c.fill(c.circle(hx_, 80, 10), NAVY)
    for r in rings:
        c.part(r, STEEL, STEEL_L, STEEL_D, kind="sphere", core=0.35, spec=0, lw=4)
    k = 0
    for row, y in enumerate((122, 172)):
        for col, x in enumerate((52, 104, 156)):
            cell = c.rrect(x, y, x + 46, y + 42, 10)
            if k == 4:
                c.line_under(cell, 3.5)
                c.grad(cell, (0, y), (0, y + 42), [(0, GOLD_L), (1, GOLD)])
                st = c.rnd(c.poly(star_pts(x + 23, y + 22, 15, 7, 5, -90)), 1.5)
                c.fill(st, WHITE)
            elif k < 4:
                c.fill(cell, hx("#DDF0FF"))
                ck = c.line([(x + 12, y + 22), (x + 20, y + 30), (x + 34, y + 13)], 6.5)
                c.fill(ck, hx("#16B892"))
            else:
                c.fill(cell, hx("#DDF0FF"))
            k += 1
    return c.done()


def icon_mission():
    c = Ico()
    paper = c.rrect(56, 42, 200, 216, 6)
    rt = c.rrect(40, 26, 216, 60, 17)
    rb = c.rrect(40, 198, 216, 232, 17)
    c.outline(paper, rt, rb)
    c.grad(paper, (0, 42), (0, 216), [(0, PAPER_L), (1, PAPER)])
    c.inner_shadow(paper, -4, 0, 3, PAPER_D, 0.5)
    for r in (rt, rb):
        c.line_under(r, 4.5)
        bb = r.getbbox()
        y0, y1 = bb[1] / c.k - c.o, bb[3] / c.k - c.o
        c.grad(r, (0, y0), (0, y1), [(0, hx("#FFF3D2")), (0.45, hx("#F4D08A")), (1, hx("#C9913E"))])
        for ex in (40, 216):
            cap = c.ell(ex + (8 if ex < 128 else -8), (y0 + y1) / 2, 7, (y1 - y0) / 2 - 3)
            c.fill(m_inter(cap, r), hx("#B9823A"))
    for i, y in enumerate((88, 128, 168)):
        box = c.rrect(72, y - 15, 102, y + 15, 7)
        c.line_under(box, 4)
        c.fill(box, WHITE)
        if i < 2:
            ck = c.line([(78, y), (86, y + 9), (104, y - 13)], 8)
            c.line_under(ck, 3.5)
            c.fill(ck, MINT)
        bar = c.rrect(114, y - 7, 184 - (i * 14), y + 7, 7)
        c.fill(bar, hx("#D7B675"))
    return c.done()


def icon_chest():
    c = Ico()
    body = c.rrect(34, 122, 222, 226, 14)
    lid = m_union(m_inter(c.ell(128, 98, 94, 46), c.poly([(0, 0), (256, 0), (256, 104), (0, 104)])),
                  c.rrect(34, 96, 222, 130, 6))
    c.outline(body, lid)
    c.part(body, WOOD, WOOD_L, WOOD_D, kind="vertical", core=0.35, spec=0)
    pl = mnew(c.size)
    for y in (158, 190):
        pl = m_union(pl, c.line([(38, y), (218, y)], 3))
    c.fill(m_inter(pl, body), WOOD_D, 0.55)
    c.part(lid, hx("#D9834A"), hx("#FFC07E"), WOOD_D, kind="vertical", core=0.3, spec=0)
    c.fill(m_inter(c.line([(38, 84), (218, 84)], 3), lid), WOOD_D, 0.45)
    straps = m_inter(m_union(c.rrect(58, 40, 80, 230, 3), c.rrect(176, 40, 198, 230, 3)), m_union(body, lid))
    c.line_under(straps, 3.5)
    c.grad(straps, (0, 50), (0, 226), [(0, GOLD_L), (0.5, GOLD), (1, GOLD_D)])
    trim = c.rrect(30, 118, 226, 134, 5)
    c.line_under(trim, 4)
    _gold(c, trim, kind="vertical", core=0.3, spec=0, line=False)
    for (sx, sy) in ((69, 104), (187, 104), (69, 150), (187, 150), (69, 200), (187, 200)):
        c.fill(c.circle(sx, sy, 4.5), hx("#B5620F"))
        c.fill(c.circle(sx - 1, sy - 1, 3), hx("#FFF3B0"))
    plate = c.rrect(106, 108, 150, 164, 10)
    c.line_under(plate, 4.5)
    _gold(c, plate, core=0.35, spec=0, line=False)
    kh = m_union(c.circle(128, 130, 8), c.poly([(123, 132), (133, 132), (131, 152), (125, 152)]))
    c.fill(kh, NAVY)
    c.spec(80, 66, 22, 7, -14, 0.55, clip=lid, blur=2.5)
    c.sparkle(214, 56, 16, WHITE, ow=3)
    return c.done()


def icon_ad():
    c = Ico()
    body = c.rrect(28, 64, 228, 210, 32)
    ants = [c.line([(110, 66), (80, 24)], 8), c.line([(146, 66), (176, 24)], 8)]
    tips = [c.circle(78, 22, 11), c.circle(178, 22, 11)]
    feet = [c.rrect(62, 200, 94, 228, 7), c.rrect(162, 200, 194, 228, 7)]
    c.outline(body, *(ants + tips + feet))
    for f in feet:
        c.part(f, hx("#3F5193"), hx("#7A8CD6"), hx("#29346A"), kind="vertical", core=0.3, spec=0)
    for a in ants:
        c.part(a, STEEL, STEEL_L, STEEL_D, kind="sphere", core=0.2, spec=0, lw=3.5)
    for t in tips:
        c.part(t, BERRY, BERRY_L, BERRY_D, kind="sphere", core=0.35, spec=0.7, spec_blur=0.1, lw=4)
    c.part(body, SKY, SKY_L, SKY_D, kind="vertical", core=0.4, spec=0, bounce=0.25)
    scr = c.rrect(46, 82, 176, 192, 22)
    c.line_under(scr, 5)
    c.rgrad(scr, (90, 110), 120, [(0, hx("#4A4FB8")), (0.6, hx("#2E2A7A")), (1, hx("#1E1B4B"))])
    refl = m_inter(c.poly([(46, 150), (130, 82), (160, 82), (46, 176)]), scr)
    c.fill(refl, WHITE, 0.12)
    play = c.rnd(c.poly([(96, 108), (96, 166), (146, 137)]), 7)
    c.line_under(play, 4)
    c.grad(play, (96, 108), (140, 166), [(0, GOLD_L), (1, GOLD)])
    for (ky, kr, kc) in ((112, 12, GOLD), (148, 9, BERRY)):
        kn = c.circle(202, ky, kr)
        c.line_under(kn, 3.5)
        c.part(kn, kc, lighten(kc, 0.5), shade(kc, 0.3), kind="sphere", core=0.3, spec=0.6, spec_blur=0.1,
               line=False)
    gr = mnew(c.size)
    for y in (172, 182):
        gr = m_union(gr, c.line([(192, y), (212, y)], 4))
    c.fill(gr, hx("#1F67B8"))
    c.spec(62, 74, 26, 6, -4, 0.55, clip=body, blur=2)
    return c.done()


def icon_music():
    c = Ico()
    h1 = c.ell(80, 184, 32, 24, -22)
    h2 = c.ell(176, 162, 32, 24, -22)
    s1 = c.rrect(96, 66, 112, 184, 7)
    s2 = c.rrect(192, 44, 208, 162, 7)
    beam = c.rnd(c.poly([(96, 58), (208, 34), (208, 72), (96, 96)]), 5)
    sil = m_union(h1, h2, s1, s2, beam)
    c.outline(sil)
    c.part(sil, MINT, MINT_L, MINT_D, kind="diag", core=0.4, spec=0, line=False)
    c.spec(68, 174, 12, 6, -22, 0.85, clip=h1, blur=1.5)
    c.spec(164, 152, 12, 6, -22, 0.85, clip=h2, blur=1.5)
    c.spec(130, 66, 24, 4, -12, 0.6, clip=beam, blur=1.5)
    c.sparkle(50, 70, 16, WHITE, ow=3)
    return c.done()


def icon_sound():
    c = Ico()
    box = c.rrect(28, 92, 90, 164, 12)
    cone = c.rnd(c.poly([(78, 94), (142, 46), (152, 46), (152, 210), (142, 210), (78, 162)]), 8)
    w1 = c.line(arc_pts(150, 128, 42, -48, 48, 30), 17)
    w2 = c.line(arc_pts(150, 128, 76, -52, 52, 40), 17)
    c.outline(box, cone, w1, w2)
    c.part(box, hx("#3F5193"), hx("#7A8CD6"), hx("#29346A"), kind="vertical", core=0.35, spec=0)
    c.part(cone, SKY, SKY_L, SKY_D, kind="sphere", lc=(0.3, 0.3), core=0.4, spec=0)
    c.spec(110, 74, 8, 18, 30, 0.7, clip=cone, blur=2)
    for w in (w1, w2):
        _white_part(c, w, line=True, lw=0.01)
    return c.done()


def icon_vibrate():
    c = Ico()
    phone = c.rrect(84, 30, 172, 226, 22)
    zz = []
    for sgn in (-1, 1):
        x0 = 128 + sgn * 70
        pts = [(x0 + (sgn * 9 if i % 2 else -sgn * 9), 66 + i * 25) for i in range(6)]
        zz.append(c.line(pts, 11))
    c.outline(phone, *zz)
    for z in zz:
        c.part(z, BERRY, BERRY_L, BERRY_D, kind="sphere", core=0.3, spec=0, line=False)
    c.part(phone, hx("#3F5193"), hx("#7A8CD6"), hx("#202A5C"), kind="vertical", core=0.35, spec=0, line=False)
    scr = c.rrect(96, 52, 160, 188, 10)
    c.grad(scr, (96, 52), (160, 188), [(0, hx("#B5E9FF")), (0.6, SKY), (1, hx("#3A8FE0"))])
    c.fill(m_inter(c.poly([(96, 120), (160, 70), (160, 92), (96, 142)]), scr), WHITE, 0.3)
    c.fill(c.rrect(116, 38, 140, 44, 3), hx("#202A5C"))
    btn = c.circle(128, 206, 8)
    c.fill(btn, hx("#C9D4F2"))
    c.spec(96, 44, 4, 10, 0, 0.6, clip=phone, blur=1.5)
    return c.done()


def icon_boost():
    c = Ico()
    streaks = []
    for (y, x0, x1, w) in ((104, 6, 66, 15), (136, 14, 58, 12), (168, 6, 50, 11)):
        streaks.append(swoosh_mask(c.size, [c.pt((x0, y + 1)), c.pt(((x0 + x1) / 2, y)), c.pt((x1, y))],
                                   c.s(w), taper_in=0.8))
    runner = c.line(catmull([(44, 194), (120, 194), (186, 194), (210, 182), (220, 160), (210, 146)], False, 10), 11)
    struts = m_union(c.line([(84, 194), (84, 168)], 10), c.line([(160, 194), (160, 168)], 10))
    deck = c.rnd(c.poly([(48, 128), (176, 128), (204, 116), (216, 126), (204, 170), (54, 170)]), 10)
    c.outline(runner, struts, deck, *streaks)
    for st_ in streaks:
        _white_part(c, st_, line=False)
    c.part(runner, GOLD, GOLD_L, GOLD_D, kind="sphere", core=0.3, spec=0)
    c.part(struts, GOLD_D, GOLD, hx("#B5620F"), kind="sphere", core=0.3, spec=0)
    c.part(deck, BERRY, BERRY_L, BERRY_D, kind="vertical", core=0.4, spec=0, bounce=0.2)
    sl = mnew(c.size)
    for x in (92, 128, 164):
        sl = m_union(sl, c.line([(x, 134), (x - 2, 164)], 4))
    c.fill(m_inter(sl, deck), hx("#A61B57"), 0.7)
    c.spec(96, 138, 34, 5, -2, 0.6, clip=deck, blur=1.5)
    c.sparkle(214, 64, 22, GOLD, ow=3.5)
    c.sparkle(178, 82, 11, WHITE, ow=3)
    return c.done()


def _shield_mask(c, cy=0.0, k=1.0):
    pts = [(128, 30), (172, 40), (212, 46), (210, 112), (190, 166), (152, 206), (128, 228), (104, 206), (66, 166),
           (46, 112), (44, 46), (84, 40)]
    pts = [(128 + (x - 128) * k, 128 + (y - 128) * k + cy) for x, y in pts]
    return c.spoly(pts, 10)


def icon_shield():
    c = Ico()
    sh = _shield_mask(c)
    c.outline(sh)
    c.rgrad(sh, (96, 70), 190, [(0, WHITE), (0.35, hx("#CDEFFF")), (0.7, hx("#7FD0FF")), (1, hx("#3E9BE8"))])
    inner = c.ero(sh, 15)
    rim = m_sub(sh, inner)
    c.grad(rim, (60, 40), (190, 220), [(0, WHITE), (0.5, hx("#E2F5FF")), (1, hx("#9CC9F0"))])
    c.fill(m_sub(c.dil(inner, 3), inner), hx("#3B86D6"), 0.9)
    ctr = (128, 122)
    facets = [([(30, 30), (226, 30), ctr], WHITE, 0.25), ([(226, 30), (226, 240), ctr], hx("#2F8FE0"), 0.25),
              ([(30, 240), (226, 240), ctr], hx("#1F67B8"), 0.25), ([(30, 30), (30, 240), ctr], WHITE, 0.12)]
    for pts_, col, op in facets:
        c.fill(m_inter(c.poly(pts_), inner), col, op)
    fl = snowflake_mask(c.size, c.X(128), c.X(122), c.s(46), c.s(11), 0)
    c.fill(c.dil(fl, 4), NAVY)
    c.grad(fl, (100, 80), (156, 170), [(0, WHITE), (1, hx("#CFEFFF"))])
    c.spec(84, 70, 16, 30, 28, 0.75, clip=inner, blur=3)
    c.sparkle(206, 196, 15, WHITE, ow=3)
    return c.done()


def icon_magnet():
    c = Ico()
    cx, cy, R, r = 128, 106, 80, 36
    arch = m_inter(m_sub(c.circle(cx, cy, R), c.circle(cx, cy, r)), c.poly([(0, 0), (256, 0), (256, cy), (0, cy)]))
    legs = m_union(c.rrect(cx - R, cy - 2, cx - r, 170, 2), c.rrect(cx + r, cy - 2, cx + R, 170, 2))
    body = c.rnd(m_union(arch, legs), 3)
    tips = m_union(c.rrect(cx - R, 166, cx - r, 206, 6), c.rrect(cx + r, 166, cx + R, 206, 6))
    ang = -32
    body, tips = c.rot(body, ang, 128, 128), c.rot(tips, ang, 128, 128)
    body = m_sub(body, tips)
    balls = [(214, 214, 15), (234, 176, 10), (182, 238, 9)]
    bm = [c.circle(x, y, rr) for x, y, rr in balls]
    c.outline(body, tips, *bm)
    c.part(body, BERRY, BERRY_L, BERRY_D, kind="sphere", lc=(0.3, 0.25), core=0.45, spec=0, line=False)
    c.part(tips, STEEL, STEEL_L, STEEL_D, kind="vertical", core=0.3, spec=0)
    for m_ in bm:
        c.part(m_, WHITE, WHITE, hx("#AFC9EC"), kind="sphere", core=0.45, spec=0, line=False)
    c.spec(70, 70, 22, 8, -50, 0.75, clip=body, blur=2.5)
    return c.done()


def icon_mitten():
    c = Ico()
    body = c.rrect(80, 30, 198, 200, 58)
    thumb = c.ell(72, 128, 26, 42, -26)
    cuff = c.rrect(70, 178, 208, 232, 24)
    bumps = mnew(c.size)
    for i in range(7):
        x = 78 + i * 20.3
        bumps = m_union(bumps, c.circle(x, 182, 11), c.circle(x, 228, 11))
    cuff = m_union(cuff, m_inter(bumps, c.rrect(64, 168, 214, 240, 20)))
    ang = -10
    body, thumb, cuff = c.rot(body, ang), c.rot(thumb, ang), c.rot(cuff, ang)
    c.outline(body, thumb, cuff)
    c.part(thumb, MINT, MINT_L, MINT_D, kind="sphere", core=0.4, spec=0)
    c.part(body, MINT, MINT_L, MINT_D, kind="sphere", lc=(0.32, 0.22), core=0.45, spec=0)
    band = m_inter(c.rot(c.rrect(60, 138, 220, 158, 4), ang), body)
    c.fill(band, BERRY)
    c.fill(m_inter(c.shift(band, 0, -3), band), BERRY_L, 0.6)
    dots = mnew(c.size)
    for i in range(6):
        dots = m_union(dots, c.circle(92 + i * 20, 148, 4))
    c.fill(m_inter(c.rot(dots, ang), band), WHITE)
    fl = snowflake_mask(c.size, c.X(136), c.X(88), c.s(26), c.s(8), 0)
    c.fill(c.rot(fl, ang), WHITE, 0.95)
    c.part(cuff, WHITE, WHITE, hx("#AFC9EC"), kind="vertical", core=0.45, spec=0)
    rib = mnew(c.size)
    for i in range(8):
        x = 86 + i * 15
        rib = m_union(rib, c.line([(x, 192), (x, 218)], 3.5))
    c.fill(m_inter(c.rot(rib, ang), cuff), hx("#BCD4F0"), 0.9)
    c.spec(*c.rot_pt((112, 58), ang), 22, 10, -40 + ang, 0.75, clip=body, blur=3)
    return c.done()


def icon_bridge():
    c = Ico()
    water = c.poly([(12, 190)] + [(12 + i * 6, 190 + 5 * math.sin(i * 0.9)) for i in range(39)] +
                   [(244, 230), (12, 230)])
    water = c.rnd(m_inter(water, c.rrect(12, 176, 244, 232, 16)), 6)
    deck = c.rrect(18, 100, 238, 138, 8)
    cx, cy, R, r = 128, 236, 108, 74
    arch = m_inter(m_sub(c.circle(cx, cy, R), c.circle(cx, cy, r)), c.poly([(0, 120), (256, 120), (256, 214), (0, 214)]))
    snow = c.rrect(14, 88, 242, 108, 10)
    for i in range(10):
        snow = m_union(snow, c.circle(24 + i * 23.1, 92 + (3 if i % 2 else 0), 12))
    posts = [c.rrect(26, 56, 50, 104, 6), c.rrect(206, 56, 230, 104, 6)]
    caps = [c.ell(38, 56, 18, 10), c.ell(218, 56, 18, 10)]
    banks = m_union(c.ell(24, 214, 30, 20), c.ell(232, 214, 30, 20))
    c.outline(water, deck, arch, snow, banks, *(posts + caps))
    c.part(water, SKY, SKY_L, SKY_D, kind="vertical", core=0.3, spec=0, line=False)
    wv = mnew(c.size)
    for (x, y) in ((86, 206), (128, 214), (170, 206)):
        wv = m_union(wv, c.line(arc_pts(x, y + 10, 11, 220, 320, 10), 4))
    c.fill(m_inter(wv, water), WHITE, 0.9)
    c.part(arch, hx("#D6EEFF"), WHITE, hx("#8FB6E3"), kind="sphere", core=0.4, spec=0, lw=4.5)
    for i in range(1, 6):
        a = math.radians(180 + i * 30)
        c.fill(m_inter(c.line([(cx + (r - 4) * math.cos(a), cy + (r - 4) * math.sin(a)),
                               (cx + (R + 4) * math.cos(a), cy + (R + 4) * math.sin(a))], 4), arch), NAVY)
    for p_ in posts:
        c.part(p_, hx("#D6EEFF"), WHITE, hx("#8FB6E3"), kind="vertical", core=0.35, spec=0, lw=4.5)
    c.part(deck, hx("#E9F6FF"), WHITE, hx("#9CC4EC"), kind="vertical", core=0.4, spec=0, lw=4.5)
    jn = mnew(c.size)
    for x in (55, 92, 128, 164, 201):
        jn = m_union(jn, c.line([(x, 104), (x, 136)], 4))
    c.fill(m_inter(jn, deck), NAVY)
    c.part(snow, WHITE, WHITE, hx("#B4CEEE"), kind="vertical", core=0.4, core_d=0.25, spec=0, lw=4.5)
    for cp in caps:
        c.part(cp, WHITE, WHITE, hx("#B4CEEE"), kind="vertical", core=0.35, spec=0, lw=4)
    c.part(banks, WHITE, WHITE, hx("#AFC9EC"), kind="vertical", core=0.4, spec=0, lw=4)
    c.spec(70, 92, 26, 4, 0, 0.8, clip=snow, blur=1.5)
    return c.done()


def icon_snowball():
    c = Ico()
    cx, cy, r = 128, 130, 98
    ball = snowball_mask(c.size, c.X(cx), c.X(cy), c.s(r), 11, lump=1.8)
    c.outline(ball)
    c.rgrad(ball, (cx - 38, cy - 44), 1.6 * r, [(0, WHITE), (0.3, hx("#F7FBFF")), (0.58, hx("#D8EBFF")),
                                                (0.84, hx("#A9CAF0")), (1, hx("#8FB4E6"))])
    rnd = random.Random(5)
    sp = mnew(c.size)
    for _ in range(16):
        a = rnd.uniform(-0.3, 2.4)
        d = rnd.uniform(0.35, 0.82) * r
        sp = m_union(sp, c.circle(cx + d * math.cos(a), cy + d * math.sin(a), rnd.uniform(1.8, 3.4)))
    c.fill(m_inter(sp, ball), hx("#8DB0E0"), 0.45)
    c.inner_shadow(ball, -9, -15, 8, hx("#6E97D4"), 0.5)
    c.fill(m_inter(m_blur(m_sub(ball, c.shift(ball, 3, -5)), c.s(1.5)), ball), WHITE, 0.6)
    c.spec(90, 74, 30, 15, -38, 0.95, clip=ball, blur=3)
    c.spec(66, 112, 5, 5, 0, 0.9, clip=ball, blur=1)
    c.sparkle(198, 56, 22, WHITE, ow=3.5)
    c.sparkle(150, 96, 9, WHITE, ow=0, glow=0.0)
    return c.done()


def icon_check():
    c = Ico()
    disc = _round_button(c, MINT, MINT_L, hx("#0FA383"))
    ck = c.line([(72, 128), (110, 166), (184, 88)], 38)
    c.fill(m_inter(m_blur(c.shift(ck, 0, 6), c.s(2)), disc), hx("#0B7C63"), 0.55)
    c.line_under(ck, 5)
    _white_part(c, ck)
    return c.done()


def icon_info():
    c = Ico()
    disc = _round_button(c, SKY, SKY_L, SKY_D)
    i_ = m_union(c.circle(128, 70, 19), c.rrect(110, 102, 146, 192, 14))
    c.fill(m_inter(m_blur(c.shift(i_, 0, 6), c.s(2)), disc), hx("#1F67B8"), 0.55)
    c.line_under(i_, 5)
    _white_part(c, i_)
    return c.done()


def icon_restart():
    c = Ico()
    cx, cy, r = 128, 136, 74
    a1 = 214
    arc = c.line(arc_pts(cx, cy, r, -52, a1, 70), 36)
    a = math.radians(a1)
    ex, ey = cx + r * math.cos(a), cy + r * math.sin(a)
    tx, ty = -math.sin(a), math.cos(a)
    nx, ny = math.cos(a), math.sin(a)
    head = c.rnd(c.poly([(ex + tx * 58, ey + ty * 58), (ex - tx * 6 + nx * 52, ey - ty * 6 + ny * 52),
                         (ex - tx * 6 - nx * 52, ey - ty * 6 - ny * 52)]), 6)
    c.outline(arc, head, ext=9)
    _white_part(c, m_union(arc, head))
    return c.done()


def icon_crown():
    c = Ico()
    band = c.rrect(44, 164, 212, 212, 14)
    body = c.rnd(c.poly([(48, 170), (36, 78), (90, 124), (128, 50), (166, 124), (220, 78), (208, 170)]), 6)
    balls = [c.circle(36, 70, 15), c.circle(128, 42, 17), c.circle(220, 70, 15)]
    c.outline(band, body, *balls)
    _gold(c, body, lc=(0.3, 0.2), core=0.45, spec=0, line=False)
    for b in balls:
        c.part(b, GOLD, GOLD_L, GOLD_D, kind="sphere", core=0.35, spec=0.8, spec_blur=0.1)
    c.part(band, GOLD_D, GOLD, hx("#B5620F"), kind="vertical", core=0.35, spec=0)
    for (x, col, rx) in ((128, BERRY, 17), (78, MINT, 12), (178, MINT, 12)):
        j = c.ell(x, 188, rx, 13 if rx > 12 else 10)
        c.line_under(j, 3.5)
        c.part(j, col, lighten(col, 0.55), shade(col, 0.3), kind="sphere", core=0.3, spec=0.85, spec_blur=0.12,
               line=False)
    gem = c.rnd(c.poly([(128, 92), (144, 120), (128, 146), (112, 120)]), 2)
    c.line_under(gem, 3.5)
    c.grad(gem, (114, 96), (142, 144), [(0, hx("#C2FFF0")), (1, MINT_D)])
    c.spec(74, 112, 8, 20, 20, 0.7, clip=body, blur=2)
    return c.done()


def icon_flag():
    c = Ico()
    pole = c.rrect(42, 34, 60, 234, 9)
    knob = c.circle(51, 32, 14)

    def fy(x, base):
        return base + 9 * math.sin((x - 58) / 160.0 * 2 * math.pi)

    xs = [58 + i * 4 for i in range(41)]
    flag = c.poly([(x, fy(x, 44)) for x in xs] + [(x, fy(x, 152)) for x in reversed(xs)])
    c.outline(pole, knob, flag)
    c.part(pole, GOLD, GOLD_L, GOLD_D, kind="sphere", core=0.3, spec=0)
    c.part(knob, GOLD, GOLD_L, GOLD_D, kind="sphere", core=0.3, spec=0.8, spec_blur=0.1)
    c.line_under(flag, 4)
    ncol, nrow = 5, 4
    for i in range(ncol):
        for j in range(nrow):
            x0, x1 = 58 + i * 160.0 / ncol, 58 + (i + 1) * 160.0 / ncol
            sub = [x0 + (x1 - x0) * q / 6.0 for q in range(7)]
            yt = lambda x: fy(x, 44) + (108.0 * j / nrow)
            yb = lambda x: fy(x, 44) + (108.0 * (j + 1) / nrow)
            cell = c.poly([(x, yt(x)) for x in sub] + [(x, yb(x)) for x in reversed(sub)])
            cell = m_inter(c.dil(cell, 0.6), flag)
            col = WHITE if (i + j) % 2 == 0 else hx("#24305E")
            c.fill(cell, col)
    shade_m = mnew(c.size)
    for x in range(58, 218, 2):
        v = math.cos((x - 58) / 160.0 * 2 * math.pi)
        if v > 0.2:
            shade_m = m_union(shade_m, c.poly([(x, 30), (x + 2, 30), (x + 2, 180), (x, 180)]))
    c.fill(m_blur(m_inter(shade_m, flag), c.s(6)), hx("#1B2A4A"), 0.18)
    hl = mnew(c.size)
    for x in range(58, 218, 2):
        v = -math.cos((x - 58) / 160.0 * 2 * math.pi)
        if v > 0.6:
            hl = m_union(hl, c.poly([(x, 30), (x + 2, 30), (x + 2, 180), (x, 180)]))
    c.fill(m_blur(m_inter(hl, flag), c.s(5)), WHITE, 0.22)
    return c.done()


def icon_doc():
    c = Ico()
    page = c.rnd(c.poly([(52, 26), (162, 26), (206, 70), (206, 232), (52, 232)]), 12)
    c.outline(page)
    c.grad(page, (52, 26), (206, 232), [(0, WHITE), (1, hx("#DCEBFB"))])
    c.inner_shadow(page, -4, -5, 3, hx("#9DB9E0"), 0.5)
    fold = c.rnd(c.poly([(160, 22), (210, 72), (160, 72)]), 4)
    c.line_under(fold, 4.5)
    c.grad(fold, (160, 72), (190, 40), [(0, hx("#9FD3FF")), (1, hx("#D9F0FF"))])
    head = c.rrect(74, 62, 140, 80, 9)
    c.fill(head, SKY)
    for i, y in enumerate((106, 132, 158, 184)):
        c.fill(c.rrect(74, y, 184 - (28 if i == 3 else 0), y + 12, 6), hx("#B5C9E6"))
    c.fill(c.rrect(74, 206, 120, 216, 5), hx("#B5C9E6"))
    return c.done()


def icon_privacy():
    c = Ico()
    sh = _shield_mask(c)
    c.outline(sh)
    c.rgrad(sh, (96, 70), 190, [(0, hx("#9AA6FF")), (0.45, hx("#5E68E8")), (1, hx("#2E2A9A"))])
    inner = c.ero(sh, 14)
    rim = m_sub(sh, inner)
    c.grad(rim, (60, 40), (190, 220), [(0, hx("#E6E9FF")), (0.6, hx("#AEB6FF")), (1, hx("#6E78E0"))])
    c.fill(m_sub(c.dil(inner, 3), inner), hx("#22206E"), 0.8)
    c.fill(m_inter(c.poly([(30, 30), (226, 30), (128, 128)]), inner), WHITE, 0.12)
    c.spec(84, 66, 14, 28, 28, 0.55, clip=inner, blur=3)
    _padlock(c, 128, 132, 0.5, outline=False)
    return c.done()


def icon_graphics():
    c = Ico()
    mon = c.rrect(26, 52, 214, 182, 18)
    neck = c.poly([(108, 180), (148, 180), (156, 210), (100, 210)])
    base = c.rrect(76, 204, 180, 226, 10)
    c.outline(mon, neck, base)
    c.part(neck, STEEL, STEEL_L, STEEL_D, kind="vertical", core=0.3, spec=0)
    c.part(base, STEEL, STEEL_L, STEEL_D, kind="vertical", core=0.3, spec=0)
    c.part(mon, hx("#3F5193"), hx("#7A8CD6"), hx("#202A5C"), kind="vertical", core=0.35, spec=0, line=False)
    scr = c.rrect(40, 66, 200, 166, 10)
    c.grad(scr, (0, 66), (0, 166), [(0, hx("#2E2A7A")), (0.55, hx("#4B57C4")), (1, hx("#7FA3EA"))])
    au = m_inter(c.poly([(40, 100), (120, 84), (200, 96), (200, 110), (120, 98), (40, 116)]), scr)
    c.fill(m_blur(au, c.s(3)), MINT, 0.75)
    hill = m_inter(c.poly([(40, 166), (40, 140), (90, 120), (140, 138), (200, 116), (200, 166)]), scr)
    c.grad(hill, (0, 116), (0, 166), [(0, WHITE), (1, hx("#BFD9F5"))])
    for (x, y) in ((70, 80), (160, 76), (184, 90)):
        c.fill(c.circle(x, y, 2.2), WHITE)
    c.fill(m_inter(c.poly([(40, 166), (120, 66), (150, 66), (70, 166)]), scr), WHITE, 0.1)
    st = m_poly(c.size, twinkle_pts(c.X(198), c.X(52), c.s(44), 2.3))
    c.fill(c.dil(st, 4.5), NAVY)
    c.rgrad(st, (198, 52), 44, [(0, hx("#FFF8D6")), (0.5, GOLD_L), (1, GOLD)])
    c.sparkle(232, 102, 13, WHITE, ow=3)
    return c.done()


def icon_controller():
    c = Ico()
    pad = m_union(c.rrect(28, 76, 228, 170, 46), c.ell(68, 168, 40, 46, 18), c.ell(188, 168, 40, 46, -18))
    pad = c.rnd(m_inter(pad, c.poly([(0, 0), (256, 0), (256, 214), (0, 214)])), 8)
    c.outline(pad)
    c.part(pad, BERRY, BERRY_L, BERRY_D, kind="sphere", lc=(0.35, 0.2), core=0.45, spec=0, line=False)
    dp = m_union(c.rrect(56, 108, 106, 130, 5), c.rrect(70, 94, 92, 144, 5))
    c.line_under(dp, 4)
    c.part(dp, hx("#3F5193"), hx("#7A8CD6"), hx("#202A5C"), kind="vertical", core=0.3, spec=0, line=False)
    for (x, y, col) in ((176, 98, MINT), (200, 120, GOLD), (152, 120, SKY), (176, 142, WHITE)):
        b = c.circle(x, y, 12)
        c.line_under(b, 3.5)
        c.part(b, col, lighten(col, 0.55), shade(col, 0.3), kind="sphere", core=0.3, spec=0.8, spec_blur=0.12,
               line=False)
    for x in (116, 140):
        c.fill(c.rrect(x - 8, 148, x + 8, 156, 4), hx("#8E1A4F"))
    c.spec(74, 88, 26, 8, -10, 0.6, clip=pad, blur=2.5)
    return c.done()


def icon_edit():
    c = Ico()
    T = (50, 206)
    d = (0.7071, -0.7071)
    n = (0.7071, 0.7071)

    def P(u, v):
        return (T[0] + d[0] * u + n[0] * v, T[1] + d[1] * u + n[1] * v)
    hw = 24
    cone = c.poly([P(0, 0), P(50, -hw), P(50, hw)])
    lead = c.poly([P(-2, 0), P(19, -hw * 0.38), P(19, hw * 0.38)])
    body = c.poly([P(48, -hw), P(162, -hw), P(162, hw), P(48, hw)])
    ferr = c.poly([P(160, -hw - 1), P(186, -hw - 1), P(186, hw + 1), P(160, hw + 1)])
    eras = m_union(c.poly([P(184, -hw), P(200, -hw), P(200, hw), P(184, hw)]), c.circle(*P(200, 0), hw))
    eras = m_inter(eras, c.poly([P(184, -hw - 2), P(226, -hw - 2), P(226, hw + 2), P(184, hw + 2)]))
    eras = c.rnd(eras, 6)
    c.outline(cone, body, ferr, eras)
    c.part(cone, hx("#FFDDB0"), hx("#FFF0DA"), hx("#D9A76A"), kind="diag", core=0.25, spec=0, line=False)
    c.fill(lead, NAVY)
    for (v0, v1, col) in ((-hw, -hw / 3.0, hx("#FFE27A")), (-hw / 3.0, hw / 3.0, GOLD), (hw / 3.0, hw, hx("#F0A11E"))):
        band = c.poly([P(48, v0), P(162, v0), P(162, v1), P(48, v1)])
        c.fill(m_inter(c.dil(band, 0.5), body), col)
    c.fill(m_inter(m_union(c.line([P(48, -hw / 3.0), P(162, -hw / 3.0)], 2.5), c.line([P(48, hw / 3.0), P(162, hw / 3.0)], 2.5)), body), hx("#D98A14"), 0.7)
    c.line_under(ferr, 4)
    c.grad(ferr, P(170, -hw), P(170, hw), [(0, STEEL_L), (0.5, STEEL), (1, STEEL_D)])
    rid = m_union(c.line([P(168, -hw), P(168, hw)], 3), c.line([P(178, -hw), P(178, hw)], 3))
    c.fill(m_inter(rid, ferr), STEEL_D, 0.8)
    c.line_under(eras, 4)
    c.grad(eras, P(200, -hw), P(200, hw), [(0, BERRY_L), (0.5, BERRY), (1, BERRY_D)])
    c.sparkle(70, 70, 18, WHITE, ow=3.2)
    return c.done()


def icon_trash():
    c = Ico()
    bin_ = c.rnd(c.poly([(62, 84), (194, 84), (182, 228), (74, 228)]), 12)
    lid = c.rrect(42, 58, 214, 90, 12)
    handle = m_sub(c.rrect(100, 32, 156, 66, 14), c.rrect(114, 44, 142, 66, 6))
    c.outline(bin_, lid, handle)
    c.part(handle, STEEL, STEEL_L, STEEL_D, kind="vertical", core=0.3, spec=0)
    c.part(bin_, STEEL, STEEL_L, STEEL_D, kind="diag", core=0.4, spec=0)
    grooves = mnew(c.size)
    for x0, x1 in ((96, 98), (128, 128), (160, 158)):
        grooves = m_union(grooves, c.line([(x0, 104), (x1, 206)], 9))
    _engrave(c, m_inter(grooves, bin_), STEEL, STEEL_L, hx("#7B8DBE"), 2)
    c.part(lid, hx("#BCCBEB"), STEEL_L, STEEL_D, kind="vertical", core=0.35, spec=0)
    c.spec(84, 66, 26, 4, 0, 0.7, clip=lid, blur=1.5)
    c.spec(80, 120, 6, 30, 4, 0.5, clip=bin_, blur=2.5)
    return c.done()


def icon_bonk():
    c = Ico()
    rnd = random.Random(42)
    outer_pts = []
    n = 12
    for i in range(2 * n):
        a = math.radians(-90 + 180.0 * i / n + rnd.uniform(-4, 4))
        rr = (rnd.uniform(100, 112) if i % 2 == 0 else rnd.uniform(64, 72))
        outer_pts.append((128 + rr * math.cos(a), 128 + rr * 0.94 * math.sin(a)))
    burst = c.rnd(c.poly(outer_pts), 4)
    inner_pts = []
    for i in range(2 * 9):
        a = math.radians(-70 + 180.0 * i / 9 + rnd.uniform(-5, 5))
        rr = (rnd.uniform(70, 78) if i % 2 == 0 else rnd.uniform(44, 50))
        inner_pts.append((128 + rr * math.cos(a), 128 + rr * math.sin(a)))
    inner = c.rnd(c.poly(inner_pts), 3)
    c.outline(burst)
    c.rgrad(burst, (110, 100), 130, [(0, hx("#FF8DB8")), (0.6, BERRY), (1, hx("#D42D70"))])
    c.inner_shadow(burst, -4, -6, 4, hx("#8E1A4F"), 0.5)
    c.line_under(inner, 4.5)
    c.rgrad(inner, (116, 108), 84, [(0, hx("#FFF8D6")), (0.45, GOLD_L), (1, GOLD)])
    core = m_poly(c.size, twinkle_pts(c.X(128), c.X(128), c.s(40), 2.0))
    c.fill(core, WHITE)
    c.fill(m_blur(c.circle(128, 128, 18), c.s(6)), WHITE, 0.9)
    for (x, y, r) in ((42, 46, 14), (214, 40, 11), (222, 206, 12)):
        st = c.rnd(c.poly(star_pts(x, y, r, r * 0.48, 5, -90)), 1.5)
        c.fill(c.dil(st, 3.5), NAVY)
        c.fill(st, GOLD)
    return c.done()


def icon_snowflake():
    c = Ico()
    flake = mnew(c.size)
    cx, cy = 128, 128
    for kk in range(6):
        a = math.radians(-90 + 60 * kk)
        ex, ey = cx + 100 * math.cos(a), cy + 100 * math.sin(a)
        flake = m_union(flake, c.line([(cx, cy), (ex, ey)], 24))
        for t, bl in ((0.5, 36), (0.76, 24)):
            bx, by = cx + 100 * t * math.cos(a), cy + 100 * t * math.sin(a)
            for sg in (-1, 1):
                b = a + sg * math.radians(52)
                flake = m_union(flake, c.line([(bx, by), (bx + bl * math.cos(b), by + bl * math.sin(b))], 17))
    flake = m_union(flake, c.poly([(cx + 30 * math.cos(math.radians(60 * q)), cy + 30 * math.sin(math.radians(60 * q)))
                                   for q in range(6)]))
    c.outline(flake)
    c.rgrad(flake, (100, 96), 150, [(0, WHITE), (0.4, hx("#D9F3FF")), (0.75, hx("#8ED6FF")), (1, hx("#4AA8F0"))])
    c.inner_shadow(flake, -3, -4, 2.5, hx("#2F7FD0"), 0.55)
    c.inner_shadow(flake, 3, 3, 2, WHITE, 0.8)
    gem = c.poly([(cx + 14 * math.cos(math.radians(60 * q + 30)), cy + 14 * math.sin(math.radians(60 * q + 30)))
                  for q in range(6)])
    c.fill(gem, WHITE)
    c.sparkle(206, 196, 15, WHITE, ow=3)
    return c.done()


def icon_hand():
    c = Ico()
    ang, sc, tx, ty = -24.0, 0.9, 30.0, 22.0

    def T(x, y):
        x, y = 128 + (x - 128) * sc, 140 + (y - 140) * sc
        q = rot_pts([(x, y)], 128, 140, ang)[0]
        return (q[0] + tx, q[1] + ty)

    def TP(ps):
        return [T(x, y) for x, y in ps]
    index = c.line([T(104, 42), T(104, 128)], 38 * sc)
    fist = m_union(c.poly(TP(rrect_pts(82, 104, 168, 192, 30))),
                   *[c.circle(*T(x, y), r * sc) for (x, y, r) in ((164, 121, 17), (169, 148, 17), (165, 174, 16))])
    thumb = c.line([T(80, 168), T(130, 142)], 34 * sc)
    cuff = c.poly(TP(rrect_pts(86, 184, 170, 228, 12)))
    tip = T(104, 22)
    dirn = math.degrees(math.atan2(*reversed(rot_pts([(0, -1)], 0, 0, ang)[0])))
    rip = [c.line(arc_pts(tip[0], tip[1], 26, dirn - 52, dirn + 52, 16), 7),
           c.line(arc_pts(tip[0], tip[1], 42, dirn - 46, dirn + 46, 18), 7)]
    c.outline(index, fist, thumb, cuff, *rip)
    for r_ in rip:
        c.part(r_, SKY, SKY_L, SKY_D, kind="sphere", core=0.2, spec=0, line=False)
    wp = dict(kind="diag", core=0.45, core_d=0.12, spec=0)
    glove_d = hx("#AFC9EC")
    c.part(cuff, WHITE, WHITE, glove_d, **wp)
    c.fill(m_inter(c.poly(TP([(80, 208), (176, 208), (176, 218), (80, 218)])), cuff), SKY)
    c.part(index, WHITE, WHITE, glove_d, line=False, **wp)
    c.part(fist, WHITE, WHITE, glove_d, **wp)
    gaps = m_union(c.line([T(146, 135), T(184, 134)], 4.5 * sc), c.line([T(146, 161), T(182, 162)], 4.5 * sc))
    c.fill(m_inter(gaps, fist), NAVY)
    c.part(thumb, WHITE, WHITE, glove_d, lw=4.5, **wp)
    c.fill(m_inter(c.line([T(104, 104), T(104, 120)], 3.5 * sc), fist), glove_d, 0.9)
    c.spec(*T(97, 60), 5, 15, ang, 0.9, clip=index, blur=1.5)
    return c.done()


ICONS = {}


def _reg(name, fn):
    ICONS[name] = fn


for _n in ("coin", "gem", "star", "star_empty", "lock", "gear", "home", "shop", "gift", "map", "profile", "trophy",
           "play", "pause", "close", "back", "wheel", "calendar", "mission", "chest", "ad", "music", "sound",
           "vibrate", "boost", "shield", "magnet", "mitten", "bridge", "snowball", "check", "info", "restart",
           "crown", "flag", "doc", "privacy", "graphics", "controller", "edit", "trash", "bonk", "snowflake",
           "hand"):
    _reg("ui_" + _n, globals()["icon_" + _n])


def gen_icons(only=None):
    print("ui icons")
    for name, fn in ICONS.items():
        if only and name not in only:
            continue
        save_png(fn(), os.path.join(NODPI, name + ".png"))


# --------------------------------------------------------------------------
# Store art
# --------------------------------------------------------------------------


def draw_mountains(cv, base_y, ridge, top_col, bot_col, snow_depth, snow_col=WHITE, snow_shade=hx("#B9D3F2")):
    """ridge: list of (x_px, y_px) alternating valley/peak points across the canvas."""
    W, H = cv.size
    pts = [(0, H)] + ridge + [(W, H)]
    m = m_poly((W, H), pts)
    paint_grad(cv, m, lambda x, y: (y - (base_y - 0.3 * H)) / (0.3 * H), [(0, top_col), (1, bot_col)], N=32)
    caps = mnew((W, H))
    for i in range(1, len(ridge) - 1):
        px, py = ridge[i]
        if not (py < ridge[i - 1][1] and py < ridge[i + 1][1]):
            continue
        d = snow_depth * min(ridge[i - 1][1] - py, ridge[i + 1][1] - py)
        lx = px + (ridge[i - 1][0] - px) * d / (ridge[i - 1][1] - py)
        rx = px + (ridge[i + 1][0] - px) * d / (ridge[i + 1][1] - py)
        zz = [(lx, py + d)]
        nz = 4
        for q in range(1, nz):
            x = lx + (rx - lx) * q / nz
            zz.append((x, py + d * (0.55 if q % 2 else 1.08)))
        zz.append((rx, py + d))
        caps = m_union(caps, m_poly((W, H), [(px, py)] + zz[::-1][:0] + [zz[-1]] + zz[::-1][1:]))
    caps = m_inter(caps, m)
    paint_fill(cv, caps, snow_col, 0.95)
    # shaded right faces of the caps
    for i in range(1, len(ridge) - 1):
        px, py = ridge[i]
        if not (py < ridge[i - 1][1] and py < ridge[i + 1][1]):
            continue
        face = m_poly((W, H), [(px, py), ridge[i + 1], (px + (ridge[i + 1][0] - px) * 0.15, ridge[i + 1][1])])
        paint_fill(cv, m_inter(face, m), shade(bot_col, 0.25), 0.35)
        paint_fill(cv, m_inter(face, caps), snow_shade, 0.8)
    return m


def draw_pine(cv, x, yb, h, col=hx("#253A78"), snow=WHITE, k=1.0):
    W, H = cv.size
    tiers = [(0.0, 0.42, 0.34), (0.26, 0.7, 0.28), (0.5, 1.0, 0.2)]
    tree = mnew((W, H))
    caps = mnew((W, H))
    for (t0, t1, wr) in tiers:
        yb_ = yb - h * t0
        yt = yb - h * t1
        tri = m_poly((W, H), [(x, yt), (x + wr * h, yb_), (x - wr * h, yb_)])
        tree = m_union(tree, tri)
        sn = m_poly((W, H), [(x, yt), (x + wr * h * 0.5, yt + (yb_ - yt) * 0.5), (x + wr * h * 0.2, yt + (yb_ - yt) * 0.42),
                             (x, yt + (yb_ - yt) * 0.56), (x - wr * h * 0.25, yt + (yb_ - yt) * 0.44),
                             (x - wr * h * 0.5, yt + (yb_ - yt) * 0.5)])
        caps = m_union(caps, sn)
    trunk = m_poly((W, H), [(x - 0.04 * h, yb), (x + 0.04 * h, yb), (x + 0.04 * h, yb + 0.08 * h), (x - 0.04 * h, yb + 0.08 * h)])
    paint_fill(cv, trunk, hx("#3B2A5A"))
    paint_fill(cv, tree, col)
    paint_fill(cv, m_inter(m_poly((W, H), [(x, yb - h), (x + h, yb), (x, yb)]), tree), shade(col, 0.3), 0.5)
    paint_fill(cv, m_inter(caps, tree), snow)


def place_racer(cv, at, H, br, pal, tilt=-8.0, ow_k=0.05, seed=3, front_puff=True):
    ow = ow_k * H
    layer, anchor, info = draw_pip(H, "push", pal=pal, ow_k=ow_k, iw_k=ow_k * 0.5, tail_len=0.26)
    hands = [rig_point(h, anchor, at, tilt) for h in info["hands"]]
    bcx, bcy = push_ball_center(hands, br, at[1] + 0.01 * H)
    sh = m_ell(cv.size, (at[0] + bcx) / 2.0, at[1] + 0.01 * H, (bcx - at[0]) / 2.0 + br * 0.9, 0.06 * H)
    paint_fill(cv, m_blur(sh, 0.03 * H), NAVY, 0.3)
    draw_snowball(cv, bcx, bcy, br, ow, seed=seed, sparkles=br > 60)
    if front_puff:
        draw_puff(cv, [(at[0] - 0.45 * H, at[1] - 0.05 * H, 0.09 * H), (at[0] - 0.33 * H, at[1] - 0.09 * H, 0.07 * H)],
                  ow * 0.6)
    place_layer(cv, layer, anchor, at, tilt)
    return bcx, bcy


def logo_4x():
    if "logo4x" not in _CACHE:
        _CACHE["logo4x"] = crop_pad(draw_logo(), 10 * SS)
    return _CACHE["logo4x"]


def draw_feature():
    k = SS
    W, H = 1024 * k, 500 * k
    size = (W, H)
    cv = Image.new("RGBA", size, NIGHT)
    paint_grad(cv, mnew(size, 255), lambda x, y: y / float(H),
               [(0, hx("#141139")), (0.22, NIGHT), (0.42, DEEP), (0.58, hx("#3550B8")), (1, hx("#4A6FD0"))], N=32)
    draw_aurora(cv, 0.19, 0.05, 0.13, waves=1.5, phase=0.2, strength=1.0, seed=31, tilt=0.06)
    draw_aurora(cv, 0.31, 0.03, 0.07, waves=2.3, phase=2.1, strength=0.5, seed=32, tilt=-0.05)
    draw_stars(cv, 130, 33, (0.0, 0.0, 1.0, 0.45), 0.0007, 0.0019, twinkles=7)

    def X(u):
        return u * W

    def Y(v):
        return v * H
    ridge_far = [(X(u), Y(v)) for u, v in ((0, 0.52), (0.07, 0.42), (0.15, 0.5), (0.24, 0.36), (0.33, 0.49), (0.42, 0.40),
                                           (0.5, 0.5), (0.6, 0.37), (0.7, 0.48), (0.79, 0.39), (0.88, 0.5), (0.95, 0.41),
                                           (1.0, 0.47))]
    draw_mountains(cv, Y(0.6), ridge_far, hx("#4656B8"), hx("#5A73CC"), 0.38, snow_col=hx("#DCEBFF"))
    ridge_near = [(X(u), Y(v)) for u, v in ((0, 0.6), (0.1, 0.5), (0.2, 0.6), (0.3, 0.53), (0.38, 0.61), (0.52, 0.52),
                                            (0.63, 0.6), (0.74, 0.5), (0.86, 0.6), (0.94, 0.52), (1.0, 0.58))]
    draw_mountains(cv, Y(0.66), ridge_near, hx("#34449E"), hx("#4B63C0"), 0.42, snow_col=hx("#EAF4FF"))

    def field_y(u):
        return Y(0.585 + 0.025 * math.sin(u * 6.0 + 0.5))
    field = hill_mask(size, field_y)
    draw_snow_hill(cv, field, Y(0.56), H, light=hx("#F4FAFF"), mid=ICE, low=hx("#B5D2F2"), rim=True)
    for (u, hh) in ((0.03, 0.17), (0.075, 0.13), (0.12, 0.1), (0.9, 0.12), (0.945, 0.16), (0.985, 0.11)):
        draw_pine(cv, X(u), field_y(u) + Y(0.03), Y(hh))

    # race lane
    def lane_top(x):
        return Y(0.665) + Y(0.012) * math.sin(x / W * 5.0)

    def lane_bot(x):
        return Y(0.765) + Y(0.012) * math.sin(x / W * 5.0 + 0.7)
    xs = [W * i / 60.0 for i in range(61)]
    lane = m_poly(size, [(x, lane_top(x)) for x in xs] + [(x, lane_bot(x)) for x in reversed(xs)])
    paint_grad(cv, lane, lambda x, y: (y - Y(0.65)) / Y(0.13), [(0, hx("#C7DEF8")), (1, hx("#E8F3FF"))])
    grooves = mnew(size)
    for f in (0.3, 0.55, 0.8):
        grooves = m_union(grooves, m_line(size, [(x, lerp(lane_top(x), lane_bot(x), f)) for x in xs], 2.2 * k, cap=False))
    paint_fill(cv, grooves, hx("#A9C8EE"), 0.6)
    bank = m_sub(m_line(size, [(x, lane_top(x)) for x in xs], 10 * k), lane)
    paint_fill(cv, m_blur(m_shift(bank, 0, 4 * k), 3 * k), hx("#7FA3DA"), 0.35)
    paint_fill(cv, m_line(size, [(x, lane_top(x) - 2 * k) for x in xs], 7 * k), WHITE)
    flag_cols = [BERRY, GOLD, MINT, SKY]
    for i, x in enumerate(range(int(0.47 * W), W, int(76 * k))):
        y0 = lane_top(x) - 2 * k
        pole = m_line(size, [(x, y0), (x, y0 - 40 * k)], 3.2 * k)
        paint_fill(cv, pole, NAVY)
        fl = m_poly(size, [(x + 1.5 * k, y0 - 40 * k), (x + 22 * k, y0 - 33 * k), (x + 1.5 * k, y0 - 26 * k)])
        paint_fill(cv, m_dilate(fl, 2.2 * k), NAVY)
        col = flag_cols[i % 4]
        paint_grad(cv, fl, lin_fn((x, y0 - 40 * k), (x, y0 - 26 * k)), [(0, lighten(col, 0.35)), (1, col)])

    racers = [
        (0.545, 0.735, 62, 27, kid_palette(BERRY, MINT, GOLD, SKY, hx("#E9B48C"), hx("#2B1A12"))),
        (0.715, 0.745, 68, 31, kid_palette(MINT, BERRY, GOLD, GOLD, hx("#8D5A3B"), hx("#1E120C"), mitt=BERRY)),
        (0.878, 0.735, 62, 26, kid_palette(SKY, GOLD, BERRY, MINT, hx("#FFE3CC"), hx("#E2B04E"), mitt=GOLD)),
    ]
    for i, (u, v, hh, br, pal) in enumerate(racers):
        place_racer(cv, (X(u), Y(v)), hh * k, br * k, pal, tilt=-8.0, ow_k=0.07, seed=20 + i)
        for j in range(2):
            yy = Y(v) - (0.55 + 0.25 * j) * hh * k
            sm = swoosh_mask(size, [(X(u) - (0.95 + 0.1 * j) * hh * k, yy), (X(u) - 0.55 * hh * k, yy - 1 * k)],
                             4.5 * k, taper_in=0.85)
            paint_fill(cv, sm, WHITE, 0.85)

    # foreground snow bank
    def bank_y(u):
        return Y(0.885) + Y(0.07) * ((u - 0.25) / 0.75) ** 2
    fb = hill_mask(size, bank_y)
    paint_fill(cv, m_dilate(fb, 2.5 * k), hx("#8FB2E6"), 0.6)
    draw_snow_hill(cv, fb, Y(0.86), H, light=WHITE, mid=hx("#EEF7FF"), low=hx("#BCD6F4"), rim=True)
    rnd = random.Random(77)
    for _ in range(40):
        u = rnd.uniform(0.0, 1.0)
        yy = bank_y(u) + rnd.uniform(0.02, 0.1) * H
        paint_fill(cv, m_circle(size, X(u), yy, rnd.uniform(1.0, 2.2) * k), WHITE, 0.95)

    # hero: Pip + giant snowball
    Hp = 226 * k
    at = (X(0.185), bank_y(0.185) + 4 * k)
    for (y, x0, x1, w) in ((-1.0, -0.86, -0.42, 0.055), (-0.8, -0.9, -0.5, 0.048)):
        m = swoosh_mask(size, [(at[0] + x0 * Hp, at[1] + y * Hp + 4 * k), (at[0] + (x0 + x1) / 2 * Hp, at[1] + y * Hp),
                               (at[0] + x1 * Hp, at[1] + y * Hp)], w * Hp, taper_in=0.8)
        paint_fill(cv, m_dilate(m, 3 * k), NAVY)
        paint_grad(cv, m, lin_fn((at[0] + x0 * Hp, 0), (at[0] + x1 * Hp, 0)), [(0, SKY), (0.6, ICE), (1, WHITE)])
    bcx, bcy = place_racer(cv, at, Hp, 126 * k, PIP_PAL, tilt=-8.0, ow_k=0.045, seed=7, front_puff=False)
    draw_puff(cv, [(bcx + 110 * k, at[1] - 10 * k, 26 * k), (bcx + 146 * k, at[1] - 2 * k, 18 * k),
                   (bcx + 76 * k, at[1] - 2 * k, 16 * k)], 4 * k)
    draw_puff(cv, [(at[0] - 0.5 * Hp, at[1] - 0.06 * Hp, 0.11 * Hp), (at[0] - 0.36 * Hp, at[1] - 0.1 * Hp, 0.08 * Hp),
                   (at[0] - 0.62 * Hp, at[1] - 0.02 * Hp, 0.07 * Hp)], 4 * k)
    draw_sparkle(cv, bcx + 40 * k, bcy - 160 * k, 26 * k, GOLD, ow=4 * k, glow=0.5, core=hx("#FFF6C8"))
    draw_sparkle(cv, bcx + 128 * k, bcy - 104 * k, 14 * k, WHITE, ow=3.5 * k, glow=0.5)

    # logo on the right
    logo = logo_4x()
    lw = int(0.47 * W)
    lh = int(logo.size[1] * lw / float(logo.size[0]))
    lg = logo.resize((lw, lh), LANCZOS)
    cv.alpha_composite(lg, dest=(int(X(0.515)), int(Y(0.03))))

    # gentle falling snow
    rnd = random.Random(99)
    flakes = mnew(size)
    d = ImageDraw.Draw(flakes)
    for _ in range(160):
        x, y = rnd.uniform(0, W), rnd.uniform(0, H * 0.9)
        r = rnd.uniform(0.8, 2.4) * k
        d.ellipse((x - r, y - r, x + r, y + r), fill=rnd.randint(140, 235))
    paint_fill(cv, flakes, WHITE)
    for _ in range(10):
        x, y = rnd.uniform(0, W), rnd.uniform(0, H * 0.85)
        paint_fill(cv, m_blur(m_circle(size, x, y, rnd.uniform(4, 7) * k), 3 * k), WHITE, 0.35)
    return cv


def gen_store():
    print("store art")
    fg, bg, mono = launcher_layers()
    comp = bg.copy()
    comp.alpha_composite(fg)
    N = comp.size[0]
    c0, c1 = int(round(N * 18 / 108.0)), int(round(N * 90 / 108.0))
    icon = downscale(comp.crop((c0, c0, c1, c1)), (512, 512))
    save_png(dither(icon, 1, 8).convert("RGB"), os.path.join(STORE, "play_icon_512.png"))
    feat = downscale(draw_feature(), (1024, 500))
    save_png(dither(feat, 1, 9).convert("RGB"), os.path.join(STORE, "feature_graphic_1024x500.png"))


# --------------------------------------------------------------------------
# Adaptive icon XML + manifest
# --------------------------------------------------------------------------

ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/gen_art.py - Pip the Snowpal pushing a giant snowball on an aurora night sky. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
"""


def gen_xml():
    print("adaptive icon xml")
    d = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(d, exist_ok=True)
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        path = os.path.join(d, name)
        with open(path, "w") as f:
            f.write(ADAPTIVE_XML)
        print("  wrote", os.path.relpath(path, ROOT))
    for name in ("ic_launcher_background.xml", "ic_launcher_foreground.xml"):
        path = os.path.join(RES, "drawable", name)
        if os.path.exists(path):
            os.remove(path)
            print("  deleted template", os.path.relpath(path, ROOT))


ICON_NOTES = {
    "ui_coin": "gold coin with embossed snowflake", "ui_gem": "faceted berry-pink gem with aurora-mint glints",
    "ui_star": "glossy gold star (earned)", "ui_star_empty": "recessed slate-blue star slot (not earned)",
    "ui_lock": "gold padlock with steel shackle", "ui_gear": "settings gear", "ui_home": "cozy cabin with snowy roof",
    "ui_shop": "berry shopping bag", "ui_gift": "mint gift box with berry bow", "ui_map": "folded treasure map with path",
    "ui_profile": "Pip head (beanie, brown bob) in an avatar badge", "ui_trophy": "gold cup on plinth", "ui_play": "white rounded play",
    "ui_pause": "white pause bars", "ui_close": "white X on berry button", "ui_back": "white left arrow",
    "ui_wheel": "prize wheel with pointer", "ui_calendar": "daily-reward calendar", "ui_mission": "checklist scroll",
    "ui_chest": "treasure chest", "ui_ad": "TV with play symbol (watch ad)", "ui_music": "music notes",
    "ui_sound": "speaker with waves", "ui_vibrate": "phone with wiggle lines", "ui_boost": "sled with speed lines",
    "ui_shield": "ice shield with snowflake", "ui_magnet": "magnet pulling snowballs", "ui_mitten": "big mint mitten",
    "ui_bridge": "snow-block bridge over water", "ui_snowball": "glossy snowball", "ui_check": "white check on mint",
    "ui_info": "white i on sky blue", "ui_restart": "circular restart arrow", "ui_crown": "gold crown with jewels",
    "ui_flag": "checkered finish flag", "ui_doc": "document page (Terms)", "ui_privacy": "shield with padlock (Privacy)",
    "ui_graphics": "monitor with sparkle (graphics quality)", "ui_controller": "gamepad",
    "ui_edit": "pencil", "ui_trash": "trash bin", "ui_bonk": "cartoon bonk star burst",
    "ui_snowflake": "six-armed snowflake", "ui_hand": "pointing tutorial glove with tap ripples",
}


def _png_info(path):
    with Image.open(path) as im:
        return "%dx%d %s" % (im.size[0], im.size[1], im.mode)


def gen_manifest():
    print("manifest")
    L = []
    L.append("# Frosty Roll Rally - art manifest\n")
    L.append("All files below are generated procedurally by `tools/gen_art.py` (Python 3 + Pillow, no numpy, "
             "no external images). Re-run from the project root with:\n")
    L.append("```\npython3 -I tools/gen_art.py            # everything\n"
             "python3 -I tools/gen_art.py icons logo # selected groups\n```\n")
    L.append("Groups: " + ", ".join("`%s`" % g for g in GROUPS) + ". Output is deterministic (fixed seeds).\n")
    L.append("Technique: 4x supersampled layered masks, gradient shading, rim/bounce light, specular blobs, "
             "blurred drop shadows and navy (#1B2A4A) outlines made by Minkowski dilation, downscaled with LANCZOS. "
             "Fonts: Lilita One (display) from `app/src/main/res/font/lilita_one.ttf`.\n")
    L.append("Brand palette: night indigo #1E1B4B, deep indigo #2E2A7A, sky blue #5EC8FF, ice #DFF6FF, "
             "aurora mint #3DF5C8, berry pink #FF4D8D, gold #FFC83D, coral #FF7A45, navy outline #1B2A4A.\n")

    def row(rel, note):
        path = os.path.join(ROOT, rel)
        info = _png_info(path) if (os.path.exists(path) and rel.endswith(".png")) else (
            "xml" if os.path.exists(path) else "MISSING")
        return "| `%s` | %s | %s |" % (rel, info, note)

    L.append("\n## Launcher icon (adaptive, API 26+) and legacy icons\n")
    L.append("| File | Size / mode | Notes |\n|---|---|---|")
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        L.append(row("app/src/main/res/mipmap-anydpi-v26/" + name,
                     "adaptive-icon: background, foreground and monochrome (themed icon) mipmaps"))
    for dens, k in DENSITIES:
        base = "app/src/main/res/mipmap-%s/" % dens
        L.append(row(base + "ic_launcher_foreground.png",
                     "Pip pushing a giant glossy snowball, sparkles and speed lines; all key art inside the 66dp safe circle"))
        L.append(row(base + "ic_launcher_background.png", "opaque aurora night sky, snowflakes, snowy hills"))
        L.append(row(base + "ic_launcher_monochrome.png", "white single-colour silhouette for Android 13 themed icons"))
        L.append(row(base + "ic_launcher.png", "legacy rounded-square composite"))
        L.append(row(base + "ic_launcher_round.png", "legacy circular composite"))
    L.append("\nRemoved template vectors: `app/src/main/res/drawable/ic_launcher_background.xml`, "
             "`app/src/main/res/drawable/ic_launcher_foreground.xml`.\n")
    L.append("\n## Brand art (`drawable-nodpi`)\n")
    L.append("| File | Size / mode | Notes |\n|---|---|---|")
    L.append(row("app/src/main/res/drawable-nodpi/splash_icon.png",
                 "Android 12 splash icon: Pip + snowball on a snow mound, inside the central 640 px circle"))
    L.append(row("app/src/main/res/drawable-nodpi/logo_frosty.png",
                 "game logo: snow-capped FROSTY + gold ROLL RALLY on a berry ribbon"))
    L.append(row("app/src/main/res/drawable-nodpi/mascot_pip.png",
                 "full-body Pip waving: chibi kid in a puffy coral snowsuit, mint beanie, berry scarf, navy boots"))
    L.append("\n## UI icon set (256x256, transparent, `drawable-nodpi`)\n")
    L.append("Consistent style: top-left light, ~10 px navy outline at 256 px with a 6 px bottom extrusion, "
             "soft inner shading and specular highlights. Readable at 24-48 dp.\n")
    L.append("| File | Size / mode | Notes |\n|---|---|---|")
    for name in ICONS:
        L.append(row("app/src/main/res/drawable-nodpi/%s.png" % name, ICON_NOTES.get(name, "")))
    L.append("\n## Store art (`store/`)\n")
    L.append("| File | Size / mode | Notes |\n|---|---|---|")
    L.append(row("store/play_icon_512.png", "Play Store icon, full-bleed, opaque"))
    L.append(row("store/feature_graphic_1024x500.png",
                 "aurora sky, mountains, race lane with flags, chibi kid racers (pink / mint / sky-blue snowsuits, "
                 "varied skin tones), Pip with a giant snowball, logo"))
    L.append("")
    path = os.path.join(ROOT, "tools", "ART_MANIFEST.md")
    with open(path, "w") as f:
        f.write("\n".join(L) + "\n")
    print("  wrote", os.path.relpath(path, ROOT))


# --------------------------------------------------------------------------
# main
# --------------------------------------------------------------------------

GROUPS = ["launcher", "splash", "mascot", "logo", "icons", "store", "xml", "manifest"]


def main(argv):
    groups = [a for a in argv[1:] if not a.startswith("-")] or GROUPS
    for gname in groups:
        fn = globals().get("gen_" + gname)
        if fn is None:
            raise SystemExit("unknown group: %s (choose from %s)" % (gname, ", ".join(GROUPS)))
        fn()


if __name__ == "__main__":
    main(sys.argv)

package com.ngstudio.snowrace.game.render

/** GLSL ES 3.00 sources. Lighting, fog and color grading are shared through [COMMON_FS]. */
object Shaders {

    private const val HEADER = "#version 300 es\n"

    const val COMMON_FS = """
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform vec3 uAmbSky;
uniform vec3 uAmbGround;
uniform vec3 uRim;
uniform vec3 uFogColor;
uniform vec2 uFog;
uniform highp vec3 uCamPosF;
uniform vec4 uGrade;
uniform vec3 uGradeTint;
uniform vec2 uScreen;
uniform mediump sampler2DShadow uShadow;
uniform float uShadowOn;
uniform float uShadowTexel;

float shadowAt(vec4 ls) {
    if (uShadowOn < 0.5) return 1.0;
    vec3 p = ls.xyz / ls.w * 0.5 + 0.5;
    if (p.x < 0.002 || p.x > 0.998 || p.y < 0.002 || p.y > 0.998 || p.z > 1.0) return 1.0;
    float zb = p.z - 0.0016;
#ifdef SOFT_SHADOW
    float t = uShadowTexel * 0.85;
    float s = texture(uShadow, vec3(p.xy + vec2(-t, -t), zb));
    s += texture(uShadow, vec3(p.xy + vec2(t, -t), zb));
    s += texture(uShadow, vec3(p.xy + vec2(-t, t), zb));
    s += texture(uShadow, vec3(p.xy + vec2(t, t), zb));
    return s * 0.25;
#else
    return texture(uShadow, vec3(p.xy, zb));
#endif
}

vec3 shade(vec3 base, vec3 N, highp vec3 W, vec4 ls, float gloss) {
    float ndl = dot(N, uSunDir);
    float sh = shadowAt(ls);
    float lit = smoothstep(-0.04, 0.22, ndl) * sh;
    vec3 amb = mix(uAmbGround, uAmbSky, N.y * 0.5 + 0.5);
    vec3 c = base * (amb + uSunColor * lit);
    vec3 V = normalize(uCamPosF - W);
    float rim = pow(1.0 - clamp(dot(N, V), 0.0, 1.0), 3.0);
    c += uRim * rim * (0.3 + 0.7 * sh) * 0.55;
    if (gloss > 0.0) {
        vec3 H = normalize(uSunDir + V);
        c += uSunColor * pow(max(dot(N, H), 0.0), 48.0) * gloss * sh;
    }
    return c;
}

vec3 grade(vec3 c) {
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(l), c, uGrade.x);
    c = (c - 0.5) * uGrade.y + 0.5;
    c *= uGradeTint * uGrade.z;
    vec2 q = gl_FragCoord.xy / uScreen - 0.5;
    c *= 1.0 - dot(q, q) * uGrade.w;
    return c;
}

vec3 finish(vec3 c, highp vec3 W) {
    highp float dist = distance(W, uCamPosF);
    float f = smoothstep(uFog.x, uFog.y, dist);
    return grade(mix(c, uFogColor, f));
}
"""

    // ------------------------------------------------------------------ instanced lit
    private const val LIT_VS_BODY = """
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aColor;
layout(location = 3) in vec4 aM0;
layout(location = 4) in vec4 aM1;
layout(location = 5) in vec4 aM2;
layout(location = 6) in vec4 aM3;
layout(location = 7) in vec4 aTint;
layout(location = 8) in vec4 aParam;
uniform mat4 uViewProj;
uniform mat4 uLightVP;
uniform float uTime;
uniform highp vec3 uCamPos;
uniform float uOutline;
out vec3 vNormal;
out vec3 vColor;
out highp vec3 vWorld;
out vec4 vLight;
out vec4 vParam;
void main() {
    mat4 M = mat4(aM0, aM1, aM2, aM3);
    vec3 s = vec3(length(aM0.xyz), length(aM1.xyz), length(aM2.xyz));
    vec3 n = normalize(mat3(M) * (aNormal / max(s * s, vec3(1e-6))));
    vec4 w = M * vec4(aPos, 1.0);
    if (aParam.y > 0.0) {
        float h = max(aPos.y, 0.0);
        float ph = uTime * 1.7 + aM3.x * 0.31 + aM3.z * 0.17;
        w.x += sin(ph) * h * aParam.y * 0.07;
        w.z += cos(ph * 0.83) * h * aParam.y * 0.05;
    }
#ifdef OUTLINE
    if (aParam.z <= 0.0) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); return; }
    float dist = length(uCamPos - w.xyz);
    w.xyz += n * uOutline * clamp(dist / 12.0, 0.55, 2.6) * aParam.z;
#endif
    vWorld = w.xyz;
    vNormal = n;
    vColor = aColor * aTint.rgb;
    vParam = vec4(aParam.x, aTint.a, aParam.z, aParam.w);
    vLight = uLightVP * vec4(w.xyz + n * 0.06, 1.0);
    gl_Position = uViewProj * w;
}
"""

    private const val LIT_FS_BODY = """
in vec3 vNormal;
in vec3 vColor;
in highp vec3 vWorld;
in vec4 vLight;
in vec4 vParam;
uniform vec3 uOutlineColor;
out vec4 frag;
void main() {
#ifdef OUTLINE
    frag = vec4(grade(uOutlineColor), 1.0);
#else
    vec3 N = normalize(vNormal);
    vec3 base = min(vColor, vec3(1.0));
    vec3 glow = max(vColor - vec3(1.0), vec3(0.0));
    vec3 col = shade(base, N, vWorld, vLight, vParam.w);
    col = mix(col, base * 1.12, vParam.x) + glow;
    frag = vec4(finish(col, vWorld), vParam.y);
#endif
}
"""

    private const val DEPTH_FS = """
out vec4 frag;
void main() { frag = vec4(1.0); }
"""

    // ------------------------------------------------------------------ terrain (deformable snow)
    private const val TERRAIN_VS = """
layout(location = 0) in vec3 aPos;
uniform mat4 uViewProj;
uniform mat4 uLightVP;
uniform vec4 uField;
uniform float uBaseY;
uniform float uHScale;
uniform vec2 uTexel;
uniform sampler2D uHeight;
out vec3 vNormal;
out highp vec3 vWorld;
out vec4 vLight;
out float vDepth;
out float vRand;
void main() {
    vec2 uv = aPos.xz;
    float h = textureLod(uHeight, uv, 0.0).r;
    float hl = textureLod(uHeight, uv - vec2(uTexel.x, 0.0), 0.0).r;
    float hr = textureLod(uHeight, uv + vec2(uTexel.x, 0.0), 0.0).r;
    float hd = textureLod(uHeight, uv - vec2(0.0, uTexel.y), 0.0).r;
    float hu = textureLod(uHeight, uv + vec2(0.0, uTexel.y), 0.0).r;
    float cw = uField.z * uTexel.x;
    float cl = uField.w * uTexel.y;
    vec3 n = normalize(vec3((hl - hr) * uHScale / (2.0 * cw), 1.0, (hd - hu) * uHScale / (2.0 * cl)));
    float y = uBaseY + h * uHScale;
    if (aPos.y > 0.5) { y = uBaseY - 0.04; n = normalize(n + vec3(0.0, 0.6, 0.0)); }
    vec3 w = vec3(uField.x + uv.x * uField.z, y, uField.y + uv.y * uField.w);
    vWorld = w;
    vNormal = n;
    vDepth = h * 1.4;
    highp float r = fract(sin(dot(floor(w.xz * 1.7), vec2(12.9898, 78.233))) * 43758.5453);
    vRand = r;
    vLight = uLightVP * vec4(w + n * 0.05, 1.0);
    gl_Position = uViewProj * vec4(w, 1.0);
}
"""

    private const val TERRAIN_FS = """
in vec3 vNormal;
in highp vec3 vWorld;
in vec4 vLight;
in float vDepth;
in float vRand;
uniform vec3 uSnowColor;
uniform vec3 uSnowPacked;
uniform float uSparkle;
out vec4 frag;
highp float h21(highp vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(highp vec2 p) {
    highp vec2 i = floor(p); highp vec2 f = fract(p);
    vec2 u = vec2(f * f * (3.0 - 2.0 * f));
    float a = h21(i), b = h21(i + vec2(1.0, 0.0)), c = h21(i + vec2(0.0, 1.0)), d = h21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}
void main() {
    vec3 N = normalize(vNormal);
    float fresh = smoothstep(0.12, 0.8, vDepth);
    vec3 base = mix(uSnowPacked, uSnowColor, fresh);
    float n1 = vnoise(vWorld.xz * 0.28);
    float n2 = vnoise(vWorld.xz * 1.35 + 7.0);
    float ripple = sin(vWorld.x * 2.1 + vWorld.z * 0.7 + n1 * 5.0) * 0.5 + 0.5;
    base *= 0.91 + n1 * 0.07 + n2 * 0.035 + ripple * 0.02 * fresh;
    base = mix(base, base * vec3(0.9, 0.95, 1.06), (1.0 - n1) * 0.35);
    base += vec3(0.04) * smoothstep(1.0, 1.35, vDepth);
    vec3 col = shade(base, N, vWorld, vLight, 0.0);
    vec3 V = normalize(uCamPosF - vWorld);
    highp vec2 g = vWorld.xz * 22.0;
    highp vec2 cell = floor(g);
    highp float h = fract(sin(dot(cell, vec2(12.9898, 78.233))) * 43758.5453);
    vec2 f = vec2(fract(g)) - 0.5;
    float dotMask = 1.0 - smoothstep(0.06, 0.2, length(f));
    highp float dist = distance(vWorld, uCamPosF);
    float glint = step(0.972, h) * dotMask * pow(max(dot(reflect(-uSunDir, N), V), 0.0), 4.0) * (1.0 - smoothstep(16.0, 38.0, dist));
    col += vec3(glint) * 1.6 * uSparkle * (0.35 + 0.65 * fresh);
    frag = vec4(finish(col, vWorld), 1.0);
}
"""

    // ------------------------------------------------------------------ water
    private const val WATER_VS = """
layout(location = 0) in vec3 aPos;
uniform mat4 uViewProj;
uniform mat4 uLightVP;
uniform float uWaterY;
out highp vec3 vWorld;
out vec4 vLight;
void main() {
    vec3 w = vec3(aPos.x, uWaterY, aPos.z);
    vWorld = w;
    vLight = uLightVP * vec4(w, 1.0);
    gl_Position = uViewProj * vec4(w, 1.0);
}
"""

    private const val WATER_FS = """
in highp vec3 vWorld;
in vec4 vLight;
uniform highp float uTime;
uniform vec3 uDeep;
uniform vec3 uShallow;
uniform vec3 uSkyRefl;
uniform sampler2D uLand;
uniform vec4 uLandRect;
out vec4 frag;
void main() {
    highp vec2 p = vWorld.xz;
    highp float t = uTime;
    vec2 g = vec2(
        sin(p.x * 0.9 + t * 1.3) + sin(p.x * 0.37 - p.y * 0.71 + t * 0.9) * 0.7 + sin(p.x * 2.3 + p.y * 1.7 + t * 2.1) * 0.25,
        cos(p.y * 0.8 + t * 1.1) + sin(p.y * 0.43 + p.x * 0.55 - t * 0.7) * 0.7 + cos(p.y * 2.1 - p.x * 1.9 + t * 1.9) * 0.25);
    vec3 N = normalize(vec3(g.x * 0.075, 1.0, g.y * 0.075));
    vec3 V = normalize(uCamPosF - vWorld);
    float fres = pow(1.0 - max(dot(N, V), 0.0), 3.0);
    vec2 muv = (p - uLandRect.xy) / uLandRect.zw;
    float land = 0.0;
    if (muv.x > 0.0 && muv.x < 1.0 && muv.y > 0.0 && muv.y < 1.0) land = texture(uLand, muv).r;
    vec3 base = mix(uDeep, uShallow, smoothstep(0.02, 0.55, land));
    float sh = shadowAt(vLight);
    vec3 col = base * (0.62 + 0.38 * sh) + uSunColor * 0.08;
    col = mix(col, uSkyRefl, fres * 0.55);
    vec3 H = normalize(uSunDir + V);
    col += uSunColor * pow(max(dot(N, H), 0.0), 140.0) * 1.6 * sh;
    float band = smoothstep(0.28, 0.5, land) * (1.0 - smoothstep(0.82, 0.97, land));
    float ripple = sin(land * 34.0 - t * 2.6 + (p.x + p.y) * 0.35) * 0.5 + 0.5;
    float foam = band * (0.45 + 0.55 * ripple);
    col = mix(col, vec3(0.97, 0.99, 1.0), clamp(foam, 0.0, 1.0) * 0.9);
    frag = vec4(finish(col, vWorld), 1.0);
}
"""

    // ------------------------------------------------------------------ sky
    private const val SKY_VS = """
layout(location = 0) in vec3 aPos;
out vec2 vNdc;
void main() {
    vNdc = aPos.xy;
    gl_Position = vec4(aPos.xy, 0.99999, 1.0);
}
"""

    private const val SKY_FS = """
in vec2 vNdc;
uniform highp mat4 uInvViewProj;
uniform vec3 uSkyTop;
uniform vec3 uSkyHorizon;
uniform float uNight;
uniform highp float uTime;
out vec4 frag;
void main() {
    highp vec4 wp = uInvViewProj * vec4(vNdc, 1.0, 1.0);
    highp vec3 dir = normalize(wp.xyz / wp.w - uCamPosF);
    float y = dir.y;
    vec3 col = mix(uSkyHorizon, uSkyTop, pow(clamp(y, 0.0, 1.0), 0.55));
    if (y < 0.0) col = mix(uSkyHorizon, uFogColor, clamp(-y * 5.0, 0.0, 1.0));
    float sd = max(dot(dir, uSunDir), 0.0);
    col += uSunColor * (pow(sd, 380.0) * 2.2 + pow(sd, 10.0) * 0.22);
    if (uNight > 0.0) {
        highp vec3 d = floor(dir * 260.0);
        highp float h = fract(sin(dot(d, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
        col += vec3(step(0.9968, h)) * uNight * smoothstep(0.04, 0.25, y) * (0.6 + 0.4 * sin(uTime * 3.0 + h * 50.0));
        float band = sin(dir.x * 5.5 + uTime * 0.22 + sin(dir.z * 3.7 + uTime * 0.27) * 1.6);
        float a = smoothstep(0.12, 0.32, y) * (1.0 - smoothstep(0.5, 0.8, y)) * (0.45 + 0.55 * band);
        col += mix(vec3(0.15, 1.0, 0.7), vec3(0.65, 0.3, 1.0), clamp(y * 2.2 - 0.35, 0.0, 1.0)) * a * 0.5 * uNight;
    }
    frag = vec4(grade(col), 1.0);
}
"""

    // ------------------------------------------------------------------ snowball with patterns
    private const val BALL_VS = """
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aColor;
uniform mat4 uViewProj;
uniform mat4 uLightVP;
uniform mat4 uModel;
out vec3 vNormal;
out vec3 vObj;
out highp vec3 vWorld;
out vec4 vLight;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    vNormal = normalize(mat3(uModel) * aNormal);
    vObj = normalize(aPos);
    vWorld = w.xyz;
    vLight = uLightVP * vec4(w.xyz + vNormal * 0.05, 1.0);
    gl_Position = uViewProj * w;
}
"""

    private const val BALL_FS = """
in vec3 vNormal;
in vec3 vObj;
in highp vec3 vWorld;
in vec4 vLight;
uniform vec3 uBase;
uniform vec3 uAccent;
uniform int uPattern;
uniform float uGlow;
uniform highp float uTime;
out vec4 frag;

highp float hash3(highp vec3 p) { return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453); }

vec3 hsv(float h) {
    vec3 k = clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    return mix(vec3(1.0), k, 0.75);
}

void main() {
    vec3 N = normalize(vNormal);
    vec3 o = vObj;
    vec3 base = uBase;
    float emis = 0.0;
    float gloss = 0.25;
    float lon = atan(o.z, o.x);
    if (uPattern == 1) {            // STRIPES (candy)
        float s = step(0.5, fract(lon / 6.2832 * 6.0 + o.y * 1.4));
        base = mix(uBase, uAccent, s);
        gloss = 0.6;
    } else if (uPattern == 2) {     // SWIRL
        float s = smoothstep(-0.25, 0.25, sin(lon * 3.0 + o.y * 7.0));
        base = mix(uBase, uAccent, s);
        gloss = 0.5;
    } else if (uPattern == 3) {     // DOTS
        highp vec3 c = fract(o * 3.2) - 0.5;
        base = mix(uBase, uAccent, 1.0 - smoothstep(0.17, 0.21, length(c)));
    } else if (uPattern == 4) {     // SPRINKLES
        highp vec3 cell = floor(o * 9.0);
        highp float h = hash3(cell);
        highp vec3 f = fract(o * 9.0) - 0.5;
        float m = step(0.62, h) * (1.0 - smoothstep(0.1, 0.16, length(f.xy * vec2(1.0, 2.6))));
        vec3 sc = hsv(fract(h * 7.3));
        base = mix(uBase, mix(uAccent, sc, 0.7), m);
        gloss = 0.4;
    } else if (uPattern == 5) {     // CHECKER (disco)
        float a = floor(lon * 2.5) + floor(o.y * 5.0);
        float c = mod(a, 2.0);
        base = mix(uBase, uAccent, c * 0.6);
        highp float h = hash3(floor(vec3(lon * 2.5, o.y * 5.0, 0.0)) + floor(uTime * 3.0));
        emis = step(0.86, h) * 0.9;
        gloss = 1.2;
    } else if (uPattern == 6) {     // CRATERS
        highp vec3 c = fract(o * 2.3) - 0.5;
        float d = length(c);
        base = mix(uBase, uAccent, smoothstep(0.24, 0.14, d) * 0.8);
        base *= 1.0 - smoothstep(0.22, 0.27, d) * smoothstep(0.32, 0.27, d) * 0.25;
    } else if (uPattern == 7) {     // CRACKS (lava)
        highp vec3 p = o * 3.0;
        float v = abs(sin(p.x * 3.1 + sin(p.y * 2.7)) + sin(p.y * 2.3 + sin(p.z * 3.3)) + sin(p.z * 2.9 + sin(p.x * 2.1)));
        float crack = 1.0 - smoothstep(0.05, 0.35, v);
        base = mix(uBase, uAccent, crack);
        emis = crack * (0.7 + 0.3 * sin(uTime * 3.0));
    } else if (uPattern == 8) {     // STARS (galaxy)
        highp float h = hash3(floor(o * 24.0));
        float neb = 0.5 + 0.5 * sin(o.x * 4.0 + o.y * 3.0 + sin(o.z * 5.0));
        base = mix(uBase, vec3(0.55, 0.25, 0.85), neb * 0.6);
        emis = step(0.93, h) * 1.0;
        base = mix(base, uAccent, emis);
        gloss = 0.7;
    } else if (uPattern == 9) {     // RAINBOW
        base = hsv(fract(o.y * 0.5 + 0.5 + uTime * 0.05));
        gloss = 0.6;
    } else if (uPattern == 10) {    // GLITTER
        highp float h = hash3(floor(o * 40.0) + floor(uTime * 4.0));
        base = mix(uBase, uAccent, 0.25 + 0.25 * sin(o.y * 8.0));
        emis = step(0.9, h) * 0.8;
        gloss = 0.9;
    } else if (uPattern == 11) {    // BANDS
        float s = smoothstep(-0.15, 0.15, sin(lon * 7.0 + sin(o.y * 5.0) * 0.6));
        base = mix(uBase, uAccent, s);
        gloss = 0.5;
    } else {                        // PLAIN snow with sparkle
        highp float h = hash3(floor(o * 30.0));
        base = uBase * (0.95 + 0.05 * h);
        gloss = 0.15;
        vec3 V = normalize(uCamPosF - vWorld);
        emis = step(0.975, h) * pow(max(dot(reflect(-uSunDir, N), V), 0.0), 4.0) * 0.8;
    }
    vec3 col = shade(base, N, vWorld, vLight, gloss);
    col += base * uGlow * 0.6 + vec3(emis) * mix(vec3(1.0), uAccent, 0.4);
    frag = vec4(finish(col, vWorld), 1.0);
}
"""

    // ------------------------------------------------------------------ billboards / particles
    private const val BB_VS = """
layout(location = 0) in vec3 aCorner;
layout(location = 1) in vec4 aPosSize;
layout(location = 2) in vec4 aColor;
layout(location = 3) in vec4 aUv;
layout(location = 4) in vec2 aRotAspect;
uniform mat4 uViewProj;
uniform vec3 uRight;
uniform vec3 uUp;
uniform float uGround;
out vec2 vUv;
out vec4 vColor;
void main() {
    float c = cos(aRotAspect.x);
    float s = sin(aRotAspect.x);
    vec2 q = vec2(aCorner.x * aRotAspect.y, aCorner.y);
    q = vec2(q.x * c - q.y * s, q.x * s + q.y * c);
    vec3 off = uGround > 0.5 ? vec3(q.x, 0.0, q.y) : (uRight * q.x + uUp * q.y);
    vec3 w = aPosSize.xyz + off * aPosSize.w;
    vec2 t = aCorner.xy + 0.5;
    vUv = vec2(mix(aUv.x, aUv.z, t.x), mix(aUv.w, aUv.y, t.y));
    vColor = aColor;
    gl_Position = uViewProj * vec4(w, 1.0);
}
"""

    private const val BB_FS = """
precision mediump float;
in vec2 vUv;
in vec4 vColor;
uniform sampler2D uTex;
out vec4 frag;
void main() {
    vec4 t = texture(uTex, vUv);
    frag = t * vColor;
}
"""

    private fun fs(defines: String, body: String) = HEADER + defines + "precision mediump float;\nprecision mediump sampler2DShadow;\n" + COMMON_FS + body
    private fun vs(defines: String, body: String) = HEADER + defines + "precision highp float;\n" + body

    fun litVs(outline: Boolean) = vs(if (outline) "#define OUTLINE\n" else "", LIT_VS_BODY)
    fun litFs(outline: Boolean, soft: Boolean) = fs((if (outline) "#define OUTLINE\n" else "") + (if (soft) "#define SOFT_SHADOW\n" else ""), LIT_FS_BODY)
    fun depthVs() = vs("", LIT_VS_BODY)
    fun depthFs() = HEADER + "precision mediump float;\n" + DEPTH_FS
    fun terrainVs() = vs("", TERRAIN_VS)
    fun terrainFs(soft: Boolean) = fs(if (soft) "#define SOFT_SHADOW\n" else "", TERRAIN_FS)
    fun waterVs() = vs("", WATER_VS)
    fun waterFs(soft: Boolean) = fs(if (soft) "#define SOFT_SHADOW\n" else "", WATER_FS)
    fun skyVs() = vs("", SKY_VS)
    fun skyFs() = fs("", SKY_FS)
    fun ballVs() = vs("", BALL_VS)
    fun ballFs(soft: Boolean) = fs(if (soft) "#define SOFT_SHADOW\n" else "", BALL_FS)
    fun bbVs() = vs("", BB_VS)
    fun bbFs() = HEADER + BB_FS
}

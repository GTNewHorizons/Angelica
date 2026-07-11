#version 330 compatibility

// EASU (Edge Adaptive Spatial Upsampling) — AMD FidelityFX Super Resolution 1.0.
// Ported from the MIT-licensed reference (GPUOpen-Effects/FidelityFX-FSR, ffx_fsr1.h),
// texelFetch variant: the four gather4 fetches are replaced by 12 direct texel loads,
// which avoids gather component-ordering pitfalls and the GL4 requirement.

uniform sampler2D uSource;
// con0 from FsrEasuCon(): .xy = input/output scale, .zw = 0.5*scale - 0.5
uniform vec4 uCon0;
// Input viewport size minus one, for edge clamping (texelFetch has no sampler clamp).
uniform ivec2 uInputMax;

out vec4 fragColor;

// ffx_a.h approximate reciprocals (bit tricks) — used instead of 1/x on purpose:
// they return large-but-finite values at 0, so flat image regions cannot produce NaNs.
float APrxLoRcpF1(float a) { return uintBitsToFloat(0x7ef07ebbu - floatBitsToUint(a)); }
float APrxLoRsqF1(float a) { return uintBitsToFloat(0x5f347d74u - (floatBitsToUint(a) >> 1u)); }

vec3 easuTapColor(ivec2 p) {
    return texelFetch(uSource, clamp(p, ivec2(0), uInputMax), 0).rgb;
}

// Filtering for a given tap. Faithful port of FsrEasuTapF.
void fsrEasuTap(inout vec3 aC, inout float aW, vec2 off, vec2 dir, vec2 len, float lob, float clp, vec3 c) {
    vec2 v = vec2(off.x * dir.x + off.y * dir.y, off.x * (-dir.y) + off.y * dir.x);
    v *= len;
    float d2 = min(dot(v, v), clp);
    // Approximation of lanczos2: (25/16 (2/5 x^2 -1)^2 - (25/16-1)) * (1/4 x^2 -1)^2
    float wB = (2.0 / 5.0) * d2 - 1.0;
    float wA = lob * d2 - 1.0;
    wB *= wB;
    wA *= wA;
    wB = (25.0 / 16.0) * wB - (25.0 / 16.0 - 1.0);
    float w = wB * wA;
    aC += c * w;
    aW += w;
}

// Accumulate direction and length. Faithful port of FsrEasuSetF.
void fsrEasuSet(inout vec2 dir, inout float len, vec2 pp, bool biS, bool biT, bool biU, bool biV,
                float lA, float lB, float lC, float lD, float lE) {
    float w = 0.0;
    if (biS) w = (1.0 - pp.x) * (1.0 - pp.y);
    if (biT) w = pp.x * (1.0 - pp.y);
    if (biU) w = (1.0 - pp.x) * pp.y;
    if (biV) w = pp.x * pp.y;
    float dc = lD - lC;
    float cb = lC - lB;
    float lenX = max(abs(dc), abs(cb));
    lenX = APrxLoRcpF1(lenX);
    float dirX = lD - lB;
    dir.x += dirX * w;
    lenX = clamp(abs(dirX) * lenX, 0.0, 1.0);
    lenX *= lenX;
    len += lenX * w;
    float ec = lE - lC;
    float ca = lC - lA;
    float lenY = max(abs(ec), abs(ca));
    lenY = APrxLoRcpF1(lenY);
    float dirY = lE - lA;
    dir.y += dirY * w;
    lenY = clamp(abs(dirY) * lenY, 0.0, 1.0);
    lenY *= lenY;
    len += lenY * w;
}

void main() {
    // FsrEasuF, with the 12-tap kernel loaded directly:
    //    b c
    //  e f g h
    //  i j k l
    //    n o
    vec2 pp = floor(gl_FragCoord.xy) * uCon0.xy + uCon0.zw;
    vec2 fp = floor(pp);
    pp -= fp;
    ivec2 sp = ivec2(fp);

    vec3 b = easuTapColor(sp + ivec2(0, -1));
    vec3 c = easuTapColor(sp + ivec2(1, -1));
    vec3 e = easuTapColor(sp + ivec2(-1, 0));
    vec3 f = easuTapColor(sp + ivec2(0, 0));
    vec3 g = easuTapColor(sp + ivec2(1, 0));
    vec3 h = easuTapColor(sp + ivec2(2, 0));
    vec3 i = easuTapColor(sp + ivec2(-1, 1));
    vec3 j = easuTapColor(sp + ivec2(0, 1));
    vec3 k = easuTapColor(sp + ivec2(1, 1));
    vec3 l = easuTapColor(sp + ivec2(2, 1));
    vec3 n = easuTapColor(sp + ivec2(0, 2));
    vec3 o = easuTapColor(sp + ivec2(1, 2));

    // Simplest multi-channel approximate luma (luma times 2).
    float bL = b.b * 0.5 + (b.r * 0.5 + b.g);
    float cL = c.b * 0.5 + (c.r * 0.5 + c.g);
    float eL = e.b * 0.5 + (e.r * 0.5 + e.g);
    float fL = f.b * 0.5 + (f.r * 0.5 + f.g);
    float gL = g.b * 0.5 + (g.r * 0.5 + g.g);
    float hL = h.b * 0.5 + (h.r * 0.5 + h.g);
    float iL = i.b * 0.5 + (i.r * 0.5 + i.g);
    float jL = j.b * 0.5 + (j.r * 0.5 + j.g);
    float kL = k.b * 0.5 + (k.r * 0.5 + k.g);
    float lL = l.b * 0.5 + (l.r * 0.5 + l.g);
    float nL = n.b * 0.5 + (n.r * 0.5 + n.g);
    float oL = o.b * 0.5 + (o.r * 0.5 + o.g);

    // Accumulate direction/length across the 4 nearest texels (f, g, j, k quad).
    vec2 dir = vec2(0.0);
    float len = 0.0;
    fsrEasuSet(dir, len, pp, true, false, false, false, bL, eL, fL, gL, jL);
    fsrEasuSet(dir, len, pp, false, true, false, false, cL, fL, gL, hL, kL);
    fsrEasuSet(dir, len, pp, false, false, true, false, fL, iL, jL, kL, nL);
    fsrEasuSet(dir, len, pp, false, false, false, true, gL, jL, kL, lL, oL);

    // Normalize with approximation, and cleanup close to zero.
    vec2 dir2 = dir * dir;
    float dirR = dir2.x + dir2.y;
    bool zro = dirR < (1.0 / 32768.0);
    dirR = APrxLoRsqF1(dirR);
    dirR = zro ? 1.0 : dirR;
    dir.x = zro ? 1.0 : dir.x;
    dir *= vec2(dirR);
    // Transform from {0 to 2} to {0 to 1} range, and shape with square.
    len = len * 0.5;
    len *= len;
    // Stretch kernel {1.0 vert|horz, to sqrt(2.0) on diagonal}.
    float stretch = (dir.x * dir.x + dir.y * dir.y) * APrxLoRcpF1(max(abs(dir.x), abs(dir.y)));
    vec2 len2 = vec2(1.0 + (stretch - 1.0) * len, 1.0 + (-0.5) * len);
    // Based on the amount of 'edge', window shifts from +/-{sqrt(2.0) to slightly beyond 2.0}.
    float lob = 0.5 + ((1.0 / 4.0 - 0.04) - 0.5) * len;
    float clp = APrxLoRcpF1(lob);

    // Deringing bounds from the 4 nearest texels.
    vec3 min4 = min(min(f, g), min(j, k));
    vec3 max4 = max(max(f, g), max(j, k));

    vec3 aC = vec3(0.0);
    float aW = 0.0;
    fsrEasuTap(aC, aW, vec2(0.0, -1.0) - pp, dir, len2, lob, clp, b);
    fsrEasuTap(aC, aW, vec2(1.0, -1.0) - pp, dir, len2, lob, clp, c);
    fsrEasuTap(aC, aW, vec2(-1.0, 1.0) - pp, dir, len2, lob, clp, i);
    fsrEasuTap(aC, aW, vec2(0.0, 1.0) - pp, dir, len2, lob, clp, j);
    fsrEasuTap(aC, aW, vec2(0.0, 0.0) - pp, dir, len2, lob, clp, f);
    fsrEasuTap(aC, aW, vec2(-1.0, 0.0) - pp, dir, len2, lob, clp, e);
    fsrEasuTap(aC, aW, vec2(1.0, 1.0) - pp, dir, len2, lob, clp, k);
    fsrEasuTap(aC, aW, vec2(2.0, 1.0) - pp, dir, len2, lob, clp, l);
    fsrEasuTap(aC, aW, vec2(2.0, 0.0) - pp, dir, len2, lob, clp, h);
    fsrEasuTap(aC, aW, vec2(1.0, 0.0) - pp, dir, len2, lob, clp, g);
    fsrEasuTap(aC, aW, vec2(1.0, 2.0) - pp, dir, len2, lob, clp, o);
    fsrEasuTap(aC, aW, vec2(0.0, 2.0) - pp, dir, len2, lob, clp, n);

    // Normalize and dering.
    fragColor = vec4(min(max4, max(min4, aC * vec3(1.0 / aW))), 1.0);
}

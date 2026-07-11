#version 330 compatibility

// RCAS (Robust Contrast Adaptive Sharpening) — AMD FidelityFX Super Resolution 1.0.
// Ported from the MIT-licensed reference (GPUOpen-Effects/FidelityFX-FSR, ffx_fsr1.h).
// Denominators are floored to keep pure-black/pure-white regions NaN-free; uniform
// regions are a fixed point of the filter regardless of lobe, so the floors are exact there.

uniform sampler2D uSource;
// exp2(-sharpnessStops): 1.0 = maximum sharpening, halves per stop.
uniform float uSharpness;

out vec4 fragColor;

#define FSR_RCAS_LIMIT (0.25 - (1.0 / 16.0))

void main() {
    // Minimal 3x3 ring:
    //    b
    //  d e f
    //    h
    ivec2 sp = ivec2(gl_FragCoord.xy);
    vec3 b = texelFetch(uSource, sp + ivec2(0, -1), 0).rgb;
    vec3 d = texelFetch(uSource, sp + ivec2(-1, 0), 0).rgb;
    vec3 e = texelFetch(uSource, sp, 0).rgb;
    vec3 f = texelFetch(uSource, sp + ivec2(1, 0), 0).rgb;
    vec3 h = texelFetch(uSource, sp + ivec2(0, 1), 0).rgb;

    // Min and max of ring.
    vec3 mn4 = min(min(b, d), min(f, h));
    vec3 mx4 = max(max(b, d), max(f, h));
    // Immediate constants for peak range.
    vec2 peakC = vec2(1.0, -1.0 * 4.0);
    // Limiters. Reference uses high-precision rcp; floors added against 0/0 at exact extremes.
    vec3 hitMin = min(mn4, e) / max(4.0 * mx4, vec3(1.0e-4));
    vec3 hitMax = (peakC.x - max(mx4, e)) / min(4.0 * mn4 + peakC.y, vec3(-1.0e-4));
    vec3 lobeRGB = max(-hitMin, hitMax);
    float lobe = max(-FSR_RCAS_LIMIT, min(max(lobeRGB.r, max(lobeRGB.g, lobeRGB.b)), 0.0)) * uSharpness;
    // Resolve, which needs medium precision rcp approximation to avoid visible tonality changes.
    float rcpL = 1.0 / (4.0 * lobe + 1.0);
    vec3 pix = (lobe * b + lobe * d + lobe * h + lobe * f + e) * rcpL;
    fragColor = vec4(pix, 1.0);
}

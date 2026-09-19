#version 330 core

#import <sodium:include/fog.glsl>

in vec4 v_Color;
in vec2 v_TexCoord;

in float v_ChunkAgeMs;

in float v_MaterialMipBias;
#ifdef USE_FRAGMENT_DISCARD
in float v_MaterialAlphaCutoff;
#endif

#if defined(USE_FOG_POSTMODERN)
in float v_SphericalFragDistance;
in float v_CylindricalFragDistance;
#elif defined(USE_FOG)
in float v_FragDistance;
#endif

uniform sampler2D u_BlockTex;

uniform vec4 u_FogColor;

#ifdef USE_FOG_SMOOTH
uniform float u_FogStart;
uniform float u_FogEnd;
#endif

#ifdef USE_FOG_POSTMODERN
uniform float u_RenderDistFogStart;
uniform float u_RenderDistFogEnd;
uniform float u_EnvFogStart;
uniform float u_EnvFogEnd;
#endif

#if defined(USE_FOG_EXP) || defined(USE_FOG_EXP2)
uniform float u_FogDensity;
#endif

#ifndef LEGACY
out vec4 fragColor;
#else
#define fragColor gl_FragColor
#endif

vec2 snapToTexelCentre(vec2 uv, vec2 texelSize, vec2 texelsPerPixel) {
    vec2 centreCoords = uv / texelSize - 0.5;
    vec2 lowerCentre = floor(centreCoords);
    vec2 alongSeam = centreCoords - lowerCentre;

    vec2 rampWidth = clamp(texelsPerPixel, vec2(1e-8), vec2(1.0));
    alongSeam = clamp((alongSeam - 0.5) / rampWidth + 0.5, 0.0, 1.0);

    return (lowerCentre + alongSeam + 0.5) * texelSize;
}

vec2 texelsPerPixelOf(vec2 du, vec2 dv, vec2 texelSize) {
    return sqrt(du * du + dv * dv) / texelSize;
}

#ifdef USE_ANISOTROPIC
float footprintScale(vec2 du, vec2 dv, vec2 texelSize, float limitTexels) {
    float footprint = max(length(du / texelSize), length(dv / texelSize));
    return min(1.0, limitTexels / max(footprint, 1e-8));
}
#endif

vec4 sampleTexelSnapped(sampler2D tex, vec2 uv, vec2 texelSize, vec2 du, vec2 dv, vec2 texelsPerPixel) {
    vec2 snapped = snapToTexelCentre(uv, texelSize, texelsPerPixel);

#if defined(TERRAIN_NO_MIPS) && !defined(USE_ANISOTROPIC)
    return textureLod(tex, snapped, 0.0);
#else
    float gradientScale = exp2(v_MaterialMipBias);
#ifdef USE_ANISOTROPIC
    gradientScale *= footprintScale(du * gradientScale, dv * gradientScale, texelSize, TERRAIN_GUTTER);
#endif

    return textureGrad(tex, snapped, du * gradientScale, dv * gradientScale);
#endif
}

vec4 sampleTexelSnapped(sampler2D tex, vec2 uv, vec2 texelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);

    return sampleTexelSnapped(tex, uv, texelSize, du, dv, texelsPerPixelOf(du, dv, texelSize));
}

#ifdef USE_RGSS

#ifndef RGSS_MIP_BIAS
#define RGSS_MIP_BIAS -1.0
#endif

// Standard rotated-grid supersampling offsets, in texels.
const vec2 RGSS_OFFSETS[4] = vec2[4](
    vec2( 0.125,  0.375),
    vec2(-0.125, -0.375),
    vec2( 0.375, -0.125),
    vec2(-0.375,  0.125)
);

#ifdef USE_ANISOTROPIC
const float RGSS_FOOTPRINT_LIMIT = 0.75 * TERRAIN_GUTTER;
#endif

vec4 sampleRGSS(sampler2D tex, vec2 uv, vec2 texelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);

    vec2 texelsPerPixel = texelsPerPixelOf(du, dv, texelSize);
    float rgssFactor = smoothstep(1.0, 2.0, max(texelsPerPixel.x, texelsPerPixel.y));

#ifdef USE_ANISOTROPIC
    float footprint = max(length(du / texelSize), length(dv / texelSize));
    rgssFactor *= 1.0 - smoothstep(RGSS_FOOTPRINT_LIMIT, TERRAIN_GUTTER, footprint);
#endif

    if (rgssFactor <= 0.0) {
        return sampleTexelSnapped(tex, uv, texelSize, du, dv, texelsPerPixel);
    }

    vec4 rgss = vec4(0.0);
#ifdef USE_ANISOTROPIC
    float fit = footprintScale(du, dv, texelSize, RGSS_FOOTPRINT_LIMIT);
    vec2 spreadU = du * fit;
    vec2 spreadV = dv * fit;

    // The taps span the footprint; the gradients are one mip sharper, which is what four of them buy.
    float gradientScale = exp2(v_MaterialMipBias + RGSS_MIP_BIAS);
    vec2 gradU = spreadU * gradientScale;
    vec2 gradV = spreadV * gradientScale;

    for (int i = 0; i < 4; i++) {
        rgss += textureGrad(tex, uv + RGSS_OFFSETS[i].x * spreadU + RGSS_OFFSETS[i].y * spreadV, gradU, gradV);
    }
#elif defined(TERRAIN_NO_MIPS)
    const float lod = 0.0;

    for (int i = 0; i < 4; i++) {
        rgss += textureLod(tex, uv + RGSS_OFFSETS[i] * texelSize, lod);
    }
#else
    float duLength = length(du / texelSize);
    float dvLength = length(dv / texelSize);
    float lod = max(0.0, 0.5 * log2(duLength * dvLength) + v_MaterialMipBias + RGSS_MIP_BIAS);

    for (int i = 0; i < 4; i++) {
        rgss += textureLod(tex, uv + RGSS_OFFSETS[i] * texelSize, lod);
    }
#endif
    rgss *= 0.25;

    if (rgssFactor >= 1.0) {
        return rgss;
    }

    return mix(sampleTexelSnapped(tex, uv, texelSize, du, dv, texelsPerPixel), rgss, rgssFactor);
}
#endif

void main() {
#ifdef USE_TEXEL_SNAP
    vec2 texelSize = 1.0 / vec2(textureSize(u_BlockTex, 0));
#ifdef USE_RGSS
    vec4 diffuseColor = sampleRGSS(u_BlockTex, v_TexCoord, texelSize);
#else
    vec4 diffuseColor = sampleTexelSnapped(u_BlockTex, v_TexCoord, texelSize);
#endif
#else
    vec4 diffuseColor = texture(u_BlockTex, v_TexCoord, v_MaterialMipBias);
#endif

#ifdef USE_FRAGMENT_DISCARD
    if (diffuseColor.a < v_MaterialAlphaCutoff) {
        discard;
    }
#endif

    vec4 m_color = v_Color;

#ifdef USE_VANILLA_COLOR_FORMAT
    diffuseColor *= m_color;
#else
    diffuseColor.rgb *= m_color.rgb;
    diffuseColor.rgb *= m_color.a;
#endif

#ifdef USE_FOG
#if defined(CHUNK_FADE_IN_DURATION_MS) && CHUNK_FADE_IN_DURATION_MS > 0
    diffuseColor = vec4(mix(u_FogColor.rgb, diffuseColor.rgb, (clamp(v_ChunkAgeMs, 0, CHUNK_FADE_IN_DURATION_MS) / CHUNK_FADE_IN_DURATION_MS)), diffuseColor.a);
#endif

#ifdef USE_FOG_POSTMODERN
    float fogValue = max(_linearFogValue(v_CylindricalFragDistance, u_RenderDistFogStart, u_RenderDistFogEnd),
                         _linearFogValue(v_SphericalFragDistance, u_EnvFogStart, u_EnvFogEnd));

    fragColor = vec4(mix(diffuseColor.rgb, u_FogColor.rgb, fogValue * u_FogColor.a), diffuseColor.a);
#elif defined(USE_FOG_EXP2)
    fragColor = _exp2Fog(diffuseColor, v_FragDistance, u_FogColor, u_FogDensity);
#elif defined(USE_FOG_EXP)
    fragColor = _expFog(diffuseColor, v_FragDistance, u_FogColor, u_FogDensity);
#elif defined(USE_FOG_SMOOTH)
    fragColor = _linearFog(diffuseColor, v_FragDistance, u_FogColor, u_FogStart, u_FogEnd);
#endif
#else
    fragColor = diffuseColor;
#endif
}

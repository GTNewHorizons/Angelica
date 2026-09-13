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

vec4 sampleTexelSnapped(sampler2D tex, vec2 uv, vec2 texelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);

    float gradientScale = exp2(v_MaterialMipBias);
    return textureGrad(tex, snapToTexelCentre(uv, texelSize, texelsPerPixelOf(du, dv, texelSize)),
        du * gradientScale, dv * gradientScale);
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

vec4 sampleRGSS(sampler2D tex, vec2 uv, vec2 texelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);

    vec2 snapped = snapToTexelCentre(uv, texelSize, texelsPerPixelOf(du, dv, texelSize));

    float gradientScale = exp2(v_MaterialMipBias + RGSS_MIP_BIAS);
    vec2 gradU = du * gradientScale;
    vec2 gradV = dv * gradientScale;

    vec4 color = vec4(0.0);
    for (int i = 0; i < 4; i++) {
        vec2 tap = RGSS_OFFSETS[i].x * du + RGSS_OFFSETS[i].y * dv;
        color += textureGrad(tex, snapped + tap, gradU, gradV);
    }

    return color * 0.25;
}
#endif

void main() {
    vec2 texelSize = 1.0 / vec2(textureSize(u_BlockTex, 0));
#ifdef USE_RGSS
    vec4 diffuseColor = sampleRGSS(u_BlockTex, v_TexCoord, texelSize);
#else
    vec4 diffuseColor = sampleTexelSnapped(u_BlockTex, v_TexCoord, texelSize);
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

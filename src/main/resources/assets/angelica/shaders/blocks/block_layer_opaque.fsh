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

#ifdef USE_FOG_EXP2
uniform float u_FogDensity;
#endif

#ifndef LEGACY
out vec4 fragColor;
#else
#define fragColor gl_FragColor
#endif

#ifdef USE_RGSS
// Standard rotated-grid supersampling offsets, in texels.
const vec2 RGSS_OFFSETS[4] = vec2[4](
    vec2( 0.125,  0.375),
    vec2(-0.125, -0.375),
    vec2( 0.375, -0.125),
    vec2(-0.375,  0.125)
);

// Sprites are padded so RGSS taps don't cross into a neighbouring atlas sprite
const float MAX_TAP_SCALE = 8.0;

vec4 sampleRGSS(sampler2D tex, vec2 uv, vec2 texSize, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);

    vec2 duTexels = du * texSize;
    vec2 dvTexels = dv * texSize;

    vec2 du2 = duTexels * duTexels;
    vec2 dv2 = dvTexels * dvTexels;

    vec2 texelScreenSize = sqrt(du2 + dv2);
    float blendFactor = smoothstep(1.0, 2.0, max(texelScreenSize.x, texelScreenSize.y));
    float lod = max(0.0, 0.25 * log2((du2.x + du2.y) * (dv2.x + dv2.y)) + v_MaterialMipBias);
    vec4 rgss = vec4(0.0);

    if (blendFactor > 0.0) {
        vec2 tapSize = pixelSize * min(exp2(lod), MAX_TAP_SCALE);

        for (int i = 0; i < 4; i++) {
            rgss += textureLod(tex, uv + RGSS_OFFSETS[i] * tapSize, lod);
        }
        rgss *= 0.25;

        if (blendFactor >= 1.0) {
            return rgss;
        }
    }

    vec2 t = uv * texSize;
    vec2 c = round(t) - 0.5;
    vec2 o = t - c;
    o = clamp((o - 0.5) / max(texelScreenSize, vec2(1e-8)) + 0.5, 0.0, 1.0);
    vec4 nearest = textureLod(tex, (c + o) * pixelSize, lod);

    return mix(nearest, rgss, blendFactor);
}
#endif

void main() {
#ifdef USE_RGSS
    vec2 texSize = vec2(textureSize(u_BlockTex, 0));
    vec4 diffuseColor = sampleRGSS(u_BlockTex, v_TexCoord, texSize, 1.0 / texSize);
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
#elif defined(USE_FOG_SMOOTH)
    fragColor = _linearFog(diffuseColor, v_FragDistance, u_FogColor, u_FogStart, u_FogEnd);
#endif
#else
    fragColor = diffuseColor;
#endif
}

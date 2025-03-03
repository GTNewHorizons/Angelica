#version 150 core
#extension GL_ARB_derivative_control : enable

#define SAMPLE_MAX 64

#define saturate(x) (clamp((x), 0.0, 1.0))

in vec2 TexCoord;

out vec4 fragColor;

uniform sampler2D gDepthMap;
uniform int gSampleCount;
uniform float gRadius;
uniform float gStrength;
uniform float gMinLight;
uniform float gBias;
uniform mat4 gInvProj;
uniform mat4 gProj;

const float EPSILON = 1.e-6;
const float GOLDEN_ANGLE = 2.39996323;
const vec3 MAGIC = vec3(0.06711056, 0.00583715, 52.9829189);
const float PI = 3.1415926538;
const float TAU = PI * 2.0;


vec3 unproject(vec4 pos) {
    return pos.xyz / pos.w;
}

float InterleavedGradientNoise(const in vec2 pixel) {
    float x = dot(pixel, MAGIC.xy);
    return fract(MAGIC.z * fract(x));
}

vec3 calcViewPosition(const in vec3 clipPos) {
    vec4 viewPos = gInvProj * vec4(clipPos * 2.0 - 1.0, 1.0);
    return viewPos.xyz / viewPos.w;
}


float GetSpiralOcclusion(const in vec2 uv, const in vec3 viewPos, const in vec3 viewNormal) {
    float dither = InterleavedGradientNoise(gl_FragCoord.xy);
    float rotatePhase = dither * TAU;
    float rStep = gRadius / gSampleCount;

    vec2 offset;

    float ao = 0.0;
    int sampleCount = 0;
    float radius = rStep;
    for (int i = 0; i < clamp(gSampleCount, 1, SAMPLE_MAX); i++) {
        vec2 offset = vec2(
            sin(rotatePhase),
            cos(rotatePhase)
        ) * radius;
        
        radius += rStep;
        rotatePhase += GOLDEN_ANGLE;

        vec3 sampleViewPos = viewPos + vec3(offset, -0.1);
        vec3 sampleClipPos = unproject(gProj * vec4(sampleViewPos, 1.0)) * 0.5 + 0.5;
        sampleClipPos = saturate(sampleClipPos);

        float sampleClipDepth = textureLod(gDepthMap, sampleClipPos.xy, 0.0).r;
        if (sampleClipDepth >= 1.0 - EPSILON) continue;

        sampleClipPos.z = sampleClipDepth;
        sampleViewPos = unproject(gInvProj * vec4(sampleClipPos * 2.0 - 1.0, 1.0));

        vec3 diff = sampleViewPos - viewPos;
        float sampleDist = length(diff);
        vec3 sampleNormal = diff / sampleDist;

        float sampleNoLm = max(dot(viewNormal, sampleNormal) - gBias, 0.0);
        float aoF = 1.0 - saturate(sampleDist / gRadius);
        ao += sampleNoLm * aoF;
        sampleCount++;
    }

    ao /= max(sampleCount, 1);
    ao = smoothstep(0.0, gStrength, ao);

    return ao * (1.0 - gMinLight);
}


void main() {
    float fragmentDepth = textureLod(gDepthMap, TexCoord, 0).r;
    float occlusion = 0.0;
    
    // Do not apply to sky
    if (fragmentDepth < 1.0) {
        vec3 viewPos = calcViewPosition(vec3(TexCoord, fragmentDepth));
        
        #ifdef GL_ARB_derivative_control
            // Get higher precision derivatives when available
            vec3 viewNormal = cross(dFdxFine(viewPos.xyz), dFdyFine(viewPos.xyz));
        #else
            vec3 viewNormal = cross(dFdx(viewPos.xyz), dFdy(viewPos.xyz));
        #endif

        viewNormal = normalize(viewNormal);

        occlusion = GetSpiralOcclusion(TexCoord, viewPos, viewNormal);
    }
    
    fragColor = vec4(vec3(1.0 - occlusion), 1.0);
}

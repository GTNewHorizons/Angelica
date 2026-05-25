#version 330 core

uniform sampler2D sampler;
uniform float strength;

flat in vec4 color;
flat in vec4 tB; // uMin, vMin, uMax, vMax
in vec2 texCoord;

out vec4 fragColor;

/*
Hacky MSAA and anisotropic filtering. Cursed beyond belief. There _may_ have been simpler means of
achieving the produced effects, but this appears to work without noticeable performance losses.
*/

float totalWt;

float txSample(vec2 uv, float du, float dv, float factorU, float factorV) {
    float distSquared = sqrt(du * du + dv * dv);
    float weight = exp(-distSquared / 6);
    totalWt += weight;
    float finalU = uv.x + factorU * du;
    float finalV = uv.y + factorV * dv;
    if (finalU < tB.x || finalV < tB.y || finalU > tB.z || finalV > tB.w) return 0.0f;
    return weight * texture(sampler, vec2(finalU, finalV)).a;
}

void main() {
    vec4 col = color;
    float original_alpha = col.a;
    #if AA_MODE == 0

        col.a = original_alpha * texture(sampler, texCoord).a;

    #else

        vec2 texScaled = texCoord * strength;
        float res = 0;
        float fu = abs(dFdx(texScaled.x)) + abs(dFdy(texScaled.x));
        float fv = abs(dFdx(texScaled.y)) + abs(dFdy(texScaled.y));
        totalWt = 0;


        #if AA_MODE == 1

            res += txSample(texCoord,  2,  6, fu, fv);
            res += txSample(texCoord,  6, -2, fu, fv);
            res += txSample(texCoord, -2, -6, fu, fv);
            res += txSample(texCoord, -6,  2, fu, fv);

        #elif AA_MODE == 2

            res += txSample(texCoord,  1,  1, fu, fv);
            res += txSample(texCoord, -1, -3, fu, fv);
            res += txSample(texCoord, -3,  2, fu, fv);
            res += txSample(texCoord,  4, -1, fu, fv);
            res += txSample(texCoord, -5, -2, fu, fv);
            res += txSample(texCoord,  2,  5, fu, fv);
            res += txSample(texCoord,  5,  3, fu, fv);
            res += txSample(texCoord,  3, -5, fu, fv);
            res += txSample(texCoord, -2,  6, fu, fv);
            res += txSample(texCoord,  0, -7, fu, fv);
            res += txSample(texCoord, -4, -6, fu, fv);
            res += txSample(texCoord, -6,  4, fu, fv);
            res += txSample(texCoord, -8,  0, fu, fv);
            res += txSample(texCoord,  7, -4, fu, fv);
            res += txSample(texCoord,  6,  7, fu, fv);
            res += txSample(texCoord, -7, -8, fu, fv);

        #endif

        res /= totalWt;
        col.a = original_alpha * res;

    #endif

    if (col.a < 0.1) discard;

    fragColor = col;
}

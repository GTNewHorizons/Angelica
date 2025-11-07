#version 120

uniform sampler2D sampler;
uniform int aaMode;
uniform float strength;

varying vec4 color;
varying vec4 tB;

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
    if (finalU < tB.x || finalU > tB.y || finalV < tB.z || finalV > tB.w) {
        return 0.0f;
    }
    return weight * texture2D(sampler, vec2(finalU, finalV)).a;
}

void main() {
    vec2 texCoords = gl_TexCoord[0].st;
    vec2 texScaled = texCoords * strength;
    vec4 col = color;
    float original_alpha = col.a;
    if (texCoords.s != 0 || texCoords.t != 0) {
        float res = 0;
        float fu = abs(dFdx(texScaled.x)) + abs(dFdy(texScaled.x));
        float fv = abs(dFdx(texScaled.y)) + abs(dFdy(texScaled.y));
        totalWt = 0;
        if (aaMode == 1) {
            res += txSample(texCoords,  2,  6, fu, fv);
            res += txSample(texCoords,  6, -2, fu, fv);
            res += txSample(texCoords, -2, -6, fu, fv);
            res += txSample(texCoords, -6,  2, fu, fv);
        } else {
            res += txSample(texCoords,  1,  1, fu, fv);
            res += txSample(texCoords, -1, -3, fu, fv);
            res += txSample(texCoords, -3,  2, fu, fv);
            res += txSample(texCoords,  4, -1, fu, fv);
            res += txSample(texCoords, -5, -2, fu, fv);
            res += txSample(texCoords,  2,  5, fu, fv);
            res += txSample(texCoords,  5,  3, fu, fv);
            res += txSample(texCoords,  3, -5, fu, fv);
            res += txSample(texCoords, -2,  6, fu, fv);
            res += txSample(texCoords,  0, -7, fu, fv);
            res += txSample(texCoords, -4, -6, fu, fv);
            res += txSample(texCoords, -6,  4, fu, fv);
            res += txSample(texCoords, -8,  0, fu, fv);
            res += txSample(texCoords,  7, -4, fu, fv);
            res += txSample(texCoords,  6,  7, fu, fv);
            res += txSample(texCoords, -7, -8, fu, fv);
        }
        res /= totalWt;
        col.a = original_alpha * res;
    }

    gl_FragColor = col;
}

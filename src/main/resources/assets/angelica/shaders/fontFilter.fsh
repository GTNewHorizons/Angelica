#version 130

uniform sampler2D sampler;
uniform int aaMode;
uniform float strength;

varying vec4 color;
varying vec4 tB;

/*
A rather nonstandard and hacky effect that approximates how MSAA works. There _may_ have been simpler means of
achieving the produced effects, but this appears to work without noticeable performance losses.
*/

float totalWt;

float txSample(vec2 uv, float du, float dv, float factor) {
    float distSquared = sqrt(du * du + dv * dv);
    float weight = exp(-distSquared / 6);
    totalWt += weight;
    float finalU = uv.x + factor * du;
    float finalV = uv.y + factor * dv;
    if (finalU < tB.x || finalU > tB.y || finalV < tB.z || finalV > tB.w) {
        return 0;
    }
    return weight * texture2D(sampler, vec2(finalU, finalV)).a;
}

void main() {
    vec2 texCoords = gl_TexCoord[0].st;
    vec4 col = color;
    if (texCoords.s != 0 || texCoords.t != 0) {
        float res = 0;
        vec2 txSize = textureSize(sampler, 0).xy;
        float f = strength * fwidth(dot(texCoords, txSize)) / sqrt(txSize.x * txSize.x + txSize.y * txSize.y);
        totalWt = 0;
        if (aaMode == 1) {
            res += txSample(texCoords,  2,  6, f);
            res += txSample(texCoords,  6, -2, f);
            res += txSample(texCoords, -2, -6, f);
            res += txSample(texCoords, -6,  2, f);
        } else {
            res += txSample(texCoords,  1,  1, f);
            res += txSample(texCoords, -1, -3, f);
            res += txSample(texCoords, -3,  2, f);
            res += txSample(texCoords,  4, -1, f);
            res += txSample(texCoords, -5, -2, f);
            res += txSample(texCoords,  2,  5, f);
            res += txSample(texCoords,  5,  3, f);
            res += txSample(texCoords,  3, -5, f);
            res += txSample(texCoords, -2,  6, f);
            res += txSample(texCoords,  0, -7, f);
            res += txSample(texCoords, -4, -6, f);
            res += txSample(texCoords, -6,  4, f);
            res += txSample(texCoords, -8,  0, f);
            res += txSample(texCoords,  7, -4, f);
            res += txSample(texCoords,  6,  7, f);
            res += txSample(texCoords, -7, -8, f);
        }
        res /= totalWt;
        col.a = res;
    }

    gl_FragColor = col;
}

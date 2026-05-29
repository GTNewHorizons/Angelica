#version 330 core

uniform sampler2DArray sampler;
uniform float strength;

flat in vec4 tB; // uMin, vMin, uMax, vMax
flat in vec4 color;
flat in uint layer;

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
    return weight * texture(sampler, vec3(finalU, finalV, layer)).r;
}

float texSample(vec2 uv) {
    if (uv.x < tB.x || uv.y < tB.y || uv.x > tB.z || uv.y > tB.w) return 0.0f;
    return texture(sampler, vec3(uv, layer)).r;
}

float easeOut(float t) {
    t = clamp(t, 0.0, 1.0);
    return 1.0 - pow(1.0 - t, 2.0);
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

    /*
    float alpha = texSample(texCoord);
    float width = fwidth(alpha);

    float smoothAlpha = smoothstep(0.5 - width, 0.5 + width, alpha);
    col.a = smoothAlpha;
    */


    //col.a = smoothstep(0, 1, texSample(texCoord));
    float a = texSample(texCoord);
    a = easeOut(a);
    //a = smoothstep(0.1, 1, a);
    //a = pow(a, 1/2.2);
    //a = (a - 0.1) / 0.9;
    //a = 1 - exp(-a + 1);
    //a = mix(a, 1, 0.1);
    //col.a = a;
    //col.a = 1.0;

    if (col.a <= 0.1) discard;

    fragColor = col;
}

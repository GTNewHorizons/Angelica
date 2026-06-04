#version 330 core

uniform sampler2DArray sampler0; // Minecraft fonts
#ifdef MULTISAMPLING
uniform sampler2DArray sampler1; // Custom fonts
#endif
uniform float strength;

flat in vec4 tB; // uMin, vMin, uMax, vMax
flat in vec4 color;
flat in uint layer;

#ifdef MULTISAMPLING
flat in uint secondTexture;
#endif


in vec2 texCoord;


out vec4 fragColor;

/*
Hacky MSAA and anisotropic filtering. Cursed beyond belief. There _may_ have been simpler means of
achieving the produced effects, but this appears to work without noticeable performance losses.
*/

float totalWt;

float aaSample(vec2 uv, float du, float dv, float factorU, float factorV) {
    float distSquared = sqrt(du * du + dv * dv);
    float weight = exp(-distSquared / 6);
    totalWt += weight;
    float finalU = uv.x + factorU * du;
    float finalV = uv.y + factorV * dv;
    if (finalU < tB.x || finalV < tB.y || finalU > tB.z || finalV > tB.w) return 0.0f;
    return weight * texture(sampler0, vec3(finalU, finalV, layer)).r;
}

float easeOut(float t) {
    t = clamp(t, 0.0, 1.0);
    return 1.0 - pow(1.0 - t, 2.0);
}

void main() {
    vec4 col = color;
    float alpha = 0;
    // For custom fonts, don't apply the AA as it results in a blurry look.

    #ifdef MULTISAMPLING
    if (secondTexture != 0u) {
        alpha = texture(sampler1, vec3(texCoord, layer)).r;
        if (alpha < 0.05) discard;


        #ifdef FONT_BRIGHTNESS
        alpha = min(alpha * FONT_BRIGHTNESS, 1);
        #endif

        fragColor = vec4(color.rgb, alpha * color.a);

        return;
    }
    #endif

    #if AA_MODE == 0


    alpha = texture(sampler0, vec3(texCoord, layer)).r;

    #else

        vec2 texScaled = texCoord * AA_STRENGTH;
        float fu = abs(dFdx(texScaled.x)) + abs(dFdy(texScaled.x));
        float fv = abs(dFdx(texScaled.y)) + abs(dFdy(texScaled.y));

        float res = 0;
        totalWt = 0;


        #if AA_MODE == 1

            res += aaSample(texCoord,  2,  6, fu, fv);
            res += aaSample(texCoord,  6, -2, fu, fv);
            res += aaSample(texCoord, -2, -6, fu, fv);
            res += aaSample(texCoord, -6,  2, fu, fv);

        #elif AA_MODE == 2

            res += aaSample(texCoord,  1,  1, fu, fv);
            res += aaSample(texCoord, -1, -3, fu, fv);
            res += aaSample(texCoord, -3,  2, fu, fv);
            res += aaSample(texCoord,  4, -1, fu, fv);
            res += aaSample(texCoord, -5, -2, fu, fv);
            res += aaSample(texCoord,  2,  5, fu, fv);
            res += aaSample(texCoord,  5,  3, fu, fv);
            res += aaSample(texCoord,  3, -5, fu, fv);
            res += aaSample(texCoord, -2,  6, fu, fv);
            res += aaSample(texCoord,  0, -7, fu, fv);
            res += aaSample(texCoord, -4, -6, fu, fv);
            res += aaSample(texCoord, -6,  4, fu, fv);
            res += aaSample(texCoord, -8,  0, fu, fv);
            res += aaSample(texCoord,  7, -4, fu, fv);
            res += aaSample(texCoord,  6,  7, fu, fv);
            res += aaSample(texCoord, -7, -8, fu, fv);

        #endif

        alpha = res / totalWt;

    #endif

    #ifdef FONT_BRIGHTNESS
    alpha = min(alpha * FONT_BRIGHTNESS, 1);
    #endif

    if (alpha < 0.05) discard;

    fragColor = vec4(color.rgb, alpha * color.a);
}

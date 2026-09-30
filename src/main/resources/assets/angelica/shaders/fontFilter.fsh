#version 330 core

uniform sampler2D sampler;
uniform int aaMode;
uniform float strength;
uniform float fontBrightness;
uniform float alphaTestRef;

in vec4 color;
in vec4 tB;
in vec2 texCoord;

out vec4 fragColor;

/*
Hacky multisampling and fake anisotropic filtering. Cursed beyond belief. There _may_ have been simpler
means of achieving the produced effects, but this appears to work without noticeable performance losses.
*/

float txSample(vec2 uv, float du, float dv, float factorU, float factorV) {
    float finalU = uv.x + factorU * du;
    float finalV = uv.y + factorV * dv;
    if (finalU < tB.x || finalU > tB.y || finalV < tB.z || finalV > tB.w) {
        return 0.0;
    }
    return texture(sampler, vec2(finalU, finalV)).a;
}

void main() {
    vec4 col = color;
    float original_alpha = col.a;
    if (texCoord.s != 0.0 || texCoord.t != 0.0) {
        if (aaMode == 0) {
            // No AA - single texture sample
            col.a = original_alpha * texture(sampler, texCoord).a;
        } else {
            vec2 texScaled = texCoord * strength;
            float fu = abs(dFdx(texScaled.x)) + abs(dFdy(texScaled.x));
            float fv = abs(dFdx(texScaled.y)) + abs(dFdy(texScaled.y));
            float res = 0.0;
            if (aaMode == 1) {
                res += txSample(texCoord,  2.0,  6.0, fu, fv);
                res += txSample(texCoord,  6.0, -2.0, fu, fv);
                res += txSample(texCoord, -2.0, -6.0, fu, fv);
                res += txSample(texCoord, -6.0,  2.0, fu, fv);
                res /= 4.0;
            } else {
                res += txSample(texCoord,  1.0,  1.0, fu, fv);
                res += txSample(texCoord, -1.0, -3.0, fu, fv);
                res += txSample(texCoord, -3.0,  2.0, fu, fv);
                res += txSample(texCoord,  4.0, -1.0, fu, fv);
                res += txSample(texCoord, -5.0, -2.0, fu, fv);
                res += txSample(texCoord,  2.0,  5.0, fu, fv);
                res += txSample(texCoord,  5.0,  3.0, fu, fv);
                res += txSample(texCoord,  3.0, -5.0, fu, fv);
                res += txSample(texCoord, -2.0,  6.0, fu, fv);
                res += txSample(texCoord,  0.0, -7.0, fu, fv);
                res += txSample(texCoord, -4.0, -6.0, fu, fv);
                res += txSample(texCoord, -6.0,  4.0, fu, fv);
                res += txSample(texCoord, -8.0,  0.0, fu, fv);
                res += txSample(texCoord,  7.0, -4.0, fu, fv);
                res += txSample(texCoord,  6.0,  7.0, fu, fv);
                res += txSample(texCoord, -7.0, -8.0, fu, fv);
                res /= 16.0;
            }
            float b = fontBrightness;
            res = (b * res) / (b * res + (1 - b) * (1 - res));
            col.a = original_alpha * res;
        }
    }

    if (col.a <= alphaTestRef) {
        discard;
    }

    fragColor = col;
}

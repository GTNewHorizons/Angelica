#version 330 core

uniform sampler2D sampler;
uniform float alphaTestRef;
uniform vec2 uTexelSize;
uniform float uTime;

const int MAX_RADIUS = 8;

in vec4 color;
in vec4 tB;
in vec2 texCoord;

out vec4 fragColor;

float totalWt;

float txSample(float finalU, float finalV) {
    if (finalU < tB.x || finalU > tB.y || finalV < tB.z || finalV > tB.w) {
        return 0.0f;
    }
    return texture(sampler, vec2(finalU, finalV)).a;
}

float gaussian(int x, int y, float sigma)
{
    float sx = float(x * x);
    float sy = float(y * y);

    float sigma2 = sigma * sigma;

    return exp(-(sx + sy) / (2.0 * sigma2));
}

float blur2D(vec2 uv)
{
    float alpha = 0.0;
    float weightSum = 0.0;

    int uRadius = 2;
    float uSigma = 4.0;

    for (int y = -MAX_RADIUS; y <= MAX_RADIUS; y++)
    {

        for (int x = -MAX_RADIUS; x <= MAX_RADIUS; x++)
        {

            float weight = gaussian(x, y, uSigma);

            vec2 offset = vec2(x, y) * uTexelSize * 3;

            alpha += txSample(uv.x + offset.x, uv.y + offset.y);

            weightSum += weight;
        }
    }

    return alpha / weightSum;
}

void main() {
    vec3 color = mix(vec3(0xb3, 0x87, 0xcb) / 255.0, vec3(0xba, 0x56, 0xf0) / 255.0, uTime);
    fragColor = vec4(color, blur2D(texCoord));
    // 0xb387cb
    // 0xba56f0
}

#version 330 core

uniform sampler2D textFBO;
uniform sampler2D sceneFBO;
uniform float alphaTestRef;
uniform vec2 uTexelSize;
uniform float uTime;

const int MAX_RADIUS = 32;

in vec2 texCoord;

out vec4 fragColor;

float totalWt;

float txSample(float finalU, float finalV) {
    if (finalU < 0 || finalU > 1 || finalV < 0 || finalV > 1) {
        return 0.0f;
    }
    return texture(textFBO, vec2(finalU, finalV)).a;
}

float gaussian(int x, int y, float sigma) {
    float sx = float(x * x);
    float sy = float(y * y);

    float sigma2 = sigma * sigma;

    return exp(-(sx + sy) / (2.0 * sigma2));
}


float glow(vec2 uv) {
    int uRadius = 6;

    float dist = 9999;

    for (int y = -MAX_RADIUS; y <= MAX_RADIUS; y++)
    {
        for (int x = -MAX_RADIUS; x <= MAX_RADIUS; x++)
        {
            // Skip outside current radius
            if (abs(x) > uRadius || abs(y) > uRadius)
            continue;

            float currDist = sqrt(x * x + y * y);

            float weight = 1;

            vec2 offset = vec2(x, y) * uTexelSize;

            if (txSample(uv.x + offset.x, uv.y + offset.y) > 0.1) {
                if (dist > currDist) {
                    dist = currDist;
                }
            }
        }
    }

    if (dist == 9999) return 0;

    float d = dist / (2 + (sin(uTime * 2.0) + 1) * 0.4);
    return min(1.0 / pow(d, 2.5), 1);
}

vec3 mixHexColors(int colorA, int colorB, float t)
{
    t = clamp(t, 0.0, 1.0);

    // Convert packed hex ints to normalized RGB vec3
    vec3 a = vec3(
    float((colorA >> 16) & 0xFF),
    float((colorA >> 8)  & 0xFF),
    float(colorA & 0xFF)
    ) / 255.0;

    vec3 b = vec3(
    float((colorB >> 16) & 0xFF),
    float((colorB >> 8)  & 0xFF),
    float(colorB & 0xFF)
    ) / 255.0;

    return mix(a, b, t);
}


vec2 rand2(vec2 p)
{
    p = vec2(dot(p, vec2(12.9898,78.233)), dot(p, vec2(26.65125, 83.054543)));
    return fract(sin(p) * 43758.5453);
}

float rand(vec2 p)
{
    return fract(sin(dot(p.xy ,vec2(54.90898,18.233))) * 4337.5453);
}



float starSDF(vec2 p)
{
    float r = length(p);
    float a = atan(p.y, p.x);

    float k = 0.45; // star indentation strength

    float starRadius = 1.0 + k * cos(4.0 * a);

    return r - starRadius;
}


float stars(in vec2 x, float numCells, float size, float br)
{
    float aspect = uTexelSize.y / uTexelSize.x;
    x.x *= aspect;
    vec2 n = x * numCells;

    // conveyor belt drift (UPWARD)
    n.y -= uTime * 0.5;

    vec2 f = floor(n);

    float d = 1.0e10;

    for (int i = -1; i <= 1; ++i)
    for (int j = -1; j <= 1; ++j)
    {
        vec2 g = f + vec2(float(i), float(j));

        // stable per-cell randomness
        vec2 jitter = rand2(mod(g, 1280.0)) - 0.5;

        vec2 p = n - g - jitter;

        // scale into star space
        p *= 1.0 / size;

        float s = starSDF(p);

        d = min(d, s);
    }

    // ─────────────────────────────
    // ORIGINAL LOOK (kept intentionally)
    // ─────────────────────────────
    float sparkle = 1.0 / (0.1 + abs(d));

    return br * sparkle;
}

/*
void main() {
    vec4 scene = texture(sceneFBO, texCoord);
    vec4 textColor = texture(textFBO, texCoord);

    // Calculate glow
    float glow = glow(texCoord);
    vec3 glowColor = mixHexColors(0xb387cb, 0xba56f0, sin(uTime + uTexelSize.x));

    // Calculate stars
    float sparkleAlpha = stars(vec2(texCoord.x, texCoord.y), 32, 0.01, 2);
    vec3 sparkleColor = vec3(1.0);

    vec3 light = glow > 0.1 ? glowColor * glow : vec3(0, 0, 0);
    light += sparkleColor * sparkleAlpha;

    light = clamp(mix(light, textColor.rgb, textColor.a), 0, 1);

    fragColor = vec4(vec3(1.0) - (vec3(1.0) - scene.rgb) * (vec3(1.0) - light), 1);
}
*/

void main() {

    vec4 scene = texture(sceneFBO, texCoord);
    vec4 textColor = texture(textFBO, texCoord);

    // ─────────────────────────────
    // LIGHT (independent)
    // ─────────────────────────────
    float glowVal = glow(texCoord);
    glowVal = glowVal > 0.1 ? glowVal : 0;

    vec3 glowColor = mixHexColors(0xb387cb, 0xfda63a, 1);

    vec3 light = glowColor * glowVal;

    //float sparkle = stars(texCoord, 32.0, 0.01, 2.0);
    //sparkle = sparkle > 0.3 ? sparkle : 0;
    //light += vec3(1.0) * sparkle;

    // ─────────────────────────────
    // TEXT ONLY MASKS LIGHT
    // ─────────────────────────────
    float mask = 1.0 - textColor.a;
    light *= mask;

    // ─────────────────────────────
    // COMPOSITE ON SCENE (SCREEN BLEND)
    // ─────────────────────────────
    vec3 base = scene.rgb * scene.a;

    vec3 finalColor = vec3(1.0) - (vec3(1.0) - base) * (vec3(1.0) - light);
    finalColor = mix(finalColor, vec3(0, 0, 0), textColor.a);

    fragColor = vec4(finalColor, 1.0);
}

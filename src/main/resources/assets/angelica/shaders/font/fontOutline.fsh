#version 330 core


// ----- GLOW -----
#define USE_GLOW
//#define RGB_GLOW
#define GLOW_COLOR vec3(0.882, 0.686, 1.0)

// ----- SPARKLES -----
#define USE_SPARKLES
#define SPARKLE_COLOR vec3(1)

// ----- WAVE -----
//#define USE_WAVE
#define WAVE_COLOR vec3(1)

// ----- TEXT -----
#define TEXT_COLOR vec3(0, 0, 0)

// ----- SPOTLIGHT -----
#define USE_SPOTLIGHT
#define SPOTLIGHT_COLOR GLOW_COLOR
#define SPOTLIGHT_STRENGTH 0.333




uniform sampler2D textFBO;
uniform sampler2D sceneFBO;
uniform vec2 uTexelSize;
uniform float uTime;
uniform float uScale; //TODO
uniform vec4 uTexBounds;

const int MAX_RADIUS = 16;

in vec2 texCoord;

out vec4 fragColor;



vec3 mixHexColors(int colorA, int colorB, float t) {
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

float random(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

vec2 random2(vec2 p) {
    float r = random(p);
    return vec2(r, random(p + r));
}


float glow(vec2 uv) {
    int uRadius = 6;

    float dist = 1e10;

    for (int y = -MAX_RADIUS; y <= MAX_RADIUS; y++) {
        for (int x = -MAX_RADIUS; x <= MAX_RADIUS; x++) {
            if (abs(x) > uRadius || abs(y) > uRadius) continue;

            // Manhatten for better readability
            //float currDist = abs(x) + abs(y);
            float currDist = max(abs(x), abs(y)) + min(abs(x), abs(y)) * 0.5;
            vec2 offset = vec2(x, y) * uTexelSize;

            if (texture(textFBO, vec2(uv.x + offset.x, uv.y + offset.y)).a > 0.1) {
                if (dist > currDist) {
                    dist = currDist;
                }
            }
        }
    }

    if (dist == 1e10) return 0;

    dist = dist - 1.5;

    //TODO
    //float jitter = (fract(sin(uTime + (texCoord.x + texCoord.y) * 2) * 43758.0) - 0.5) * 5;
    //dist += jitter;
    //TODO

    //if (dist <= 0) return 1;
    //else return 0;
    float timeGlow = (sin(uTime * 2.0)) * 0.2;
    float d = dist;
    //d = d / (2 + timeGlow);
    float glow = exp(-d * 0.7);
    //float d = (dist / (1 + (sin(uTime * 2.0)) * 0.1));
    //return min(1.0 / pow(d, 1.2), 1);
    return min(glow, 1);
}

// Adapted from https://www.shadertoy.com/view/3d33zM
float starfield(vec2 uv) {
    vec2 resolution = 1.0 / uTexelSize;
    vec2 fragCoord  = uv * resolution;

    float widthHeightRatio = resolution.x / resolution.y;

    float t = uTime * 0.015;

    float dist = 0.0;

    const float layers = 4.0;
    const float scale  = 256.0;
    const float star_size = 1.0 / 10;

    vec2 centre = vec2(0.5);

    for (float i = 0.0; i < layers; i++) {
        float depth = fract(i/layers);

        centre.x = 0.5 + 0.1 * t * depth;
        centre.y = 0.5 + 0.1 * t * depth;

        vec2 uv = centre - fragCoord / resolution;

        uv.y /= widthHeightRatio;

        uv *= mix(scale, 0.0, depth);

        vec2 index = floor(uv);

        vec2 seed = 20.0 * i + index;

        vec2 local_uv = fract(i + uv) - 0.5;

        vec2 pos = 0.8 * (random2(seed) - 0.5);

        float phase = 128.0 * random(seed);

        vec2 d = (local_uv - pos) * star_size;
        float len = length(d);

        // Core
        float radial = max(0.0, 1.0 - len * 3.5);
        radial = pow(radial, 30.0);

        // Fade spikes with distance from center
        float spikeFade = pow(max(0.0, 1.0 - len * 6.0), 4.0);

        // Compact sparkle spikes
        float spikeX = pow(max(0.0, 1.0 - abs(d.x) * 35.0), 8.0);
        float spikeY = pow(max(0.0, 1.0 - abs(d.y) * 35.0), 8.0);

        float spikeD1 = pow(max(0.0, 1.0 - abs(d.x + d.y) * 25.0), 8.0);
        float spikeD2 = pow(max(0.0, 1.0 - abs(d.x - d.y) * 25.0), 8.0);

        // Apply radial fade to spikes
        float spikes =
        (spikeX + spikeY) * 0.12 +
        (spikeD1 + spikeD2) * 0.05;

        spikes *= spikeFade;

        // Final sparkle
        float sparkle = radial * 0.9 + spikes;

        float speed = mix(0.5, 3.0, random(seed + 17.0));

        float twinkle =
        sin(uTime * speed + phase) * 0.5 + 0.5;

        twinkle = pow(twinkle, 6.0);

        float brightness =
        mix(0.2, 1.0, random(seed + 42.0));

        dist += sparkle * twinkle * brightness * depth;
    }

    return dist * 2;
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0/3.0, 1.0/3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec3 rgb(vec2 uv) {
    float h = (uv.x + uv.y) * 4 + uTime / 10; // 0 → 1 hue
    return hsv2rgb(vec3(h, 1.0, 1.0));
}

float wave(vec2 uv) {
    float speed = 0.25;
    float pos = fract(uTime * speed);

    float lines = 5.0;

    float x = uv.x * lines;

    float id = floor(x);
    float local = fract(x);

    float dist = abs(local - pos);

    float thickness = 0.1;

    return 1.0 - smoothstep(0.0, thickness, dist);
}

float spotlight(
    vec2 uv,
    float minX,
    float maxX,
    float minY,
    float maxY
) {
    vec2 center = vec2(
    (minX + maxX) * 0.5,
    (minY + maxY) * 0.5
    );

    vec2 radius = vec2(
    (maxX - minX) * 0.5,
    (maxY - minY) * 0.5
    );

    vec2 p = abs((uv - center) / radius);

    float dist = pow(
        pow(p.x, 4.0) + pow(p.y, 4.0),
        1.0 / 4.0
    );

    return (1.0 - smoothstep(0.0, 1.0, dist)) * SPOTLIGHT_STRENGTH;
}

//0xfda63a orang


//0xfda63a
//0xe1afff

void main() {
    float minX = uTexBounds.x;
    float maxX = uTexBounds.y;
    float minY = uTexBounds.z;
    float maxY = uTexBounds.w;
    vec2 relativeUV = vec2(texCoord.x - minX, texCoord.y - minY);

    vec3 overlay = vec3(0);

    #ifdef USE_SPARKLES
    if (
        texCoord.x >= minX - uTexelSize.x * 2 &&
        texCoord.x <= maxX + uTexelSize.x * 2 &&
        texCoord.y >= minY - uTexelSize.y * 4 &&
        texCoord.y <= maxY + uTexelSize.y * 4
    ) {
        float sparkle = starfield(vec2(texCoord.x - minX, texCoord.y - minY));
        if (sparkle > 0.05) {
            // glow sits in front of sparkle
            overlay = mix(overlay, SPARKLE_COLOR, sparkle);
        }
    }
    #endif

    #ifdef USE_SPOTLIGHT
    overlay = mix(overlay, SPOTLIGHT_COLOR, spotlight(texCoord, minX, maxX, minY, maxY));
    #endif

    #ifdef USE_GLOW
    float glowVal = glow(texCoord);
    if (glowVal > 0.1) {
        #ifdef RGB_GLOW
        vec3 glowColor = rgb(relativeUV);
        #else
        vec3 glowColor = GLOW_COLOR;
        #endif
        #ifdef USE_WAVE
        glowColor = mix(glowColor, vec3(1, 1, 1), wave(relativeUV));
        #endif
        overlay = mix(overlay, glowColor, glowVal);
    }

    #endif






    // Since Minecraft doesn't have HDR colors, I need an alternative approach to blending the colors together
    vec4 scene = texture(sceneFBO, texCoord);
    vec3 base = scene.rgb;

    vec3 finalColor = vec3(1.0) - (vec3(1.0) - base) * (vec3(1.0) - overlay);

    // Mix based on text color
    vec4 textColor = texture(textFBO, texCoord);
    if (textColor.a > 0.1) {
        finalColor = mix(finalColor, TEXT_COLOR, textColor.a);
    }

    fragColor = vec4(finalColor, 1.0);
}

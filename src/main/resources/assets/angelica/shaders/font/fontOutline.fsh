#version 330 core

/*
// ----- GLOW -----

#define test 0



*/


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


float glow(vec2 uv) {
    int uRadius = 6;

    float dist = 9999;

    for (int y = -MAX_RADIUS; y <= MAX_RADIUS; y++) {
        for (int x = -MAX_RADIUS; x <= MAX_RADIUS; x++) {
            if (abs(x) > uRadius || abs(y) > uRadius) continue;

            // Manhatten for better readability
            float currDist = abs(x) + abs(y);

            float weight = 1;

            vec2 offset = vec2(x, y) * uTexelSize;

            if (texture(textFBO, vec2(uv.x + offset.x, uv.y + offset.y)).a > 0.1) {
                if (dist > currDist) {
                    dist = currDist;
                }
            }
        }
    }

    if (dist == 9999) return 0;

    dist = dist - 1.5;

    //TODO
    float jitter = (fract(sin(uTime + (texCoord.x + texCoord.y) * 2) * 43758.0) - 0.5) * 5;
    dist += jitter;
    //TODO

    if (dist <= 0) return 1;
    float timeGlow = (sin(uTime * 2.0)) * 0.1;
    float d = dist;
    d = d / (2 + timeGlow);
    float glow = exp(-d);
    //float d = (dist / (1 + (sin(uTime * 2.0)) * 0.1));
    //return min(1.0 / pow(d, 1.2), 1);
    return min(glow, 1);
}

float random(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

vec2 random2(vec2 p) {
    float r = random(p);
    return vec2(r, random(p + r));
}

// Adapted from https://www.shadertoy.com/view/3d33zM
vec4 starfield(vec2 uv) {
    vec2 resolution = 1.0 / uTexelSize;
    vec2 fragCoord  = uv * resolution;

    float widthHeightRatio = resolution.x / resolution.y;

    float t = uTime * 0.01;

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

    return vec4(1, 1, 1, dist * 2);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0/3.0, 1.0/3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec3 rainbow(vec2 uv) {
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

void main() {
    float minX = uTexBounds.x - uTexelSize.x * 6;
    float maxX = uTexBounds.y + uTexelSize.x * 6;
    float minY = uTexBounds.z - uTexelSize.y * 6;
    float maxY = uTexBounds.w + uTexelSize.y * 6;
    vec2 relativeUV = vec2(texCoord.x - minX, texCoord.y - maxX);
    //float middleY = (minY + maxY) / 2;
    float middleY = 0;
    float yWidth = (maxY - minY) * 2;

    vec4 scene = texture(sceneFBO, texCoord);
    vec4 textColor = texture(textFBO, texCoord);

    // ─────────────────────────────
    // LIGHT (independent)
    // ─────────────────────────────
    float glowVal = glow(texCoord);
    glowVal = glowVal > 0.2 ? glowVal : 0;

    //0xfda63a orang


    //0xfda63a
    //0xe1afff
    vec3 glowColor = mixHexColors(0xe1afff, 0xd0a4fa, 0);
    //vec3 glowColor = rainbow(texCoord);
    //glowColor = mix(glowColor, vec3(1, 1, 1), wave(relativeUV));



    vec3 light = glowColor * glowVal;
    if (
        texCoord.x >= minX &&
        texCoord.x <= maxX &&
        texCoord.y >= minY &&
        texCoord.y <= maxY
    ) {
        vec4 sparkle = starfield(vec2(texCoord.x - minX, texCoord.y - minY));
        if (sparkle.a > 0.1) {
            vec3 sparkleLayer = sparkle.rgb * sparkle.a;

            // glow sits in front of sparkle
            light = mix(sparkleLayer, glowColor, glowVal);
        }
    }


    float mask = 1.0 - textColor.a;
    if (textColor.a > 0.1) {
        light = vec3(0);
    }

    vec3 base = scene.rgb;

    vec3 finalColor = vec3(1.0) - (vec3(1.0) - base) * (vec3(1.0) - light);
    finalColor = mix(finalColor, vec3(0, 0, 0), textColor.a);

    fragColor = vec4(finalColor, 1.0);
}

#version 330 core

in vec3 v_Tex[16];
flat in vec4 v_Light;

uniform sampler2D u_Tunnel;
uniform sampler2D u_Field;
uniform vec3 u_LayerColor[16];

#ifdef FOG
in float v_FogCoord;
uniform vec4 u_FogParams;
uniform vec3 u_FogColor;
uniform int u_FogMode;
#endif

out vec4 fragColor;

// TC blends each layer into an 8-bit framebuffer, which rounds after every blend; rounding the same way keeps the sum
// on the same steps.
vec3 store8(vec3 color) {
    return floor(clamp(color, 0.0, 1.0) * 255.0 + 0.5) / 255.0;
}

void main() {
#ifdef FOG
    float f;
    if (u_FogMode == 1) {
        f = v_FogCoord * u_FogParams.x + u_FogParams.y;
    } else if (u_FogMode == 2) {
        f = exp2(-(v_FogCoord * u_FogParams.z));
    } else {
        float fogTmp = v_FogCoord * u_FogParams.w;
        f = exp2(-(fogTmp * fogTmp));
    }
    f = clamp(f, 0.0, 1.0);
    // Fixed function fogs each layer's draw before it blends, so every additive layer adds its own share of fog color
    #define LAYER(rgb) mix(u_FogColor, (rgb), f)
#else
    #define LAYER(rgb) (rgb)
#endif
    vec3 color = store8(LAYER((vec4(u_LayerColor[0], 1.0) * textureProj(u_Tunnel, v_Tex[0]) * v_Light).rgb));
    for (int i = 1; i < 16; i++) {
        color = store8(color + LAYER((vec4(u_LayerColor[i], 1.0) * textureProj(u_Field, v_Tex[i]) * v_Light).rgb));
    }
    fragColor = vec4(color, 1.0);
}

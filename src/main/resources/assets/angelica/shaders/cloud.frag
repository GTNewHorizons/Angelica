#version 330 core

in vec4 v_Color;
in vec2 v_TexCoord0;
in float v_FogCoord;

uniform sampler2D u_CloudTexture;
uniform vec3 u_ColorMultiplier;
uniform vec4 u_FogParams; // x: -1/(end-start), y: end/(end-start)
uniform vec4 u_FogColor;
uniform bool u_FogEnabled;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(u_CloudTexture, v_TexCoord0);
    vec4 color = v_Color * texColor;
    color.rgb *= u_ColorMultiplier;

    if (color.a < 0.1) discard;

    if (u_FogEnabled) {
        float f = clamp(v_FogCoord * u_FogParams.x + u_FogParams.y, 0.0, 1.0);
        color.rgb = mix(u_FogColor.rgb, color.rgb, f);
    }

    fragColor = color;
}

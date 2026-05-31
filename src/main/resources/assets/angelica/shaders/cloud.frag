#version 330 core

in vec2 v_UV;
in float v_FogCoord;
in float v_Factor;

uniform sampler2D u_Tex;
uniform vec4 u_ColorMult;
uniform vec4 u_FogParams; // x: -1/(end-start), y: end/(end-start)
uniform vec4 u_FogColor;
uniform bool u_FogEnabled;

out vec4 fragColor;

void main() {
    vec4 texel = texture(u_Tex, v_UV);
    if (texel.a < 0.1) discard;
    vec4 color = texel * u_ColorMult;
    color.rgb *= v_Factor;

    if (u_FogEnabled) {
        float f = clamp(v_FogCoord * u_FogParams.x + u_FogParams.y, 0.0, 1.0);
        color.rgb = mix(u_FogColor.rgb, color.rgb, f);
    }

    fragColor = color;
}

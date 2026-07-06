#version 330 core

in vec2 v_UV;
in vec3 v_EyePos;
in float v_Factor;

uniform sampler2D u_Tex;
uniform vec4 u_ColorMult;
uniform vec4 u_FogParams; // linear: x=-1/(end-start), y=end/(end-start); exp/exp2: z=density; w=mode (0=linear, 1=exp, 2=exp2)
uniform vec4 u_FogColor;
uniform bool u_FogEnabled;

out vec4 fragColor;

void main() {
    vec4 texel = texture(u_Tex, v_UV);
    if (texel.a < 0.1) discard;
    vec4 color = texel * u_ColorMult;
    color.rgb *= v_Factor;

    if (u_FogEnabled) {
        float fogCoord = length(v_EyePos);
        float f;
        if (u_FogParams.w == 0.0) {
            f = fogCoord * u_FogParams.x + u_FogParams.y;
        } else if (u_FogParams.w == 1.0) {
            f = exp(-u_FogParams.z * fogCoord);
        } else {
            float d = u_FogParams.z * fogCoord;
            f = exp(-d * d);
        }
        color.rgb = mix(u_FogColor.rgb, color.rgb, clamp(f, 0.0, 1.0));
    }

    fragColor = color;
}

#version 330 core

#import <angelica:include/cloud_fog.glsl>

in vec3 v_EyePos;
in float v_Shade;

uniform vec4 u_ColorMult;

out vec4 fragColor;

void main() {
    vec4 color = u_ColorMult;
    color.rgb *= v_Shade;
    color.rgb = applyCloudFog(color.rgb, v_EyePos);

    fragColor = color;
}

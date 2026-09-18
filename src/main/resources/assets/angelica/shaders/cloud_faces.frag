#version 330 core

#import <angelica:include/cloud_fog.glsl>

in vec3 v_EyePos;
flat in float v_Shade;

#ifndef UNTEXTURED
in vec2 v_UV;
uniform sampler2D u_Tex;
#endif

uniform vec4 u_ColorMult;

out vec4 fragColor;

void main() {
#ifdef UNTEXTURED
    vec4 color = u_ColorMult;
#else
    vec4 texel = texture(u_Tex, v_UV);
    if (texel.a < 0.1) discard;
    vec4 color = texel * u_ColorMult;
#endif
    color.rgb *= v_Shade;
    color.rgb = applyCloudFog(color.rgb, v_EyePos);

    fragColor = color;
}

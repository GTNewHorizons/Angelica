#version 330 core

layout(location = 0) in vec2 a_Position;
layout(location = 1) in vec2 a_TexCoord0;
layout(location = 2) in vec4 a_Color;
layout(location = 3) in vec4 a_TexBounds;
// xy = lightmap coords as normalized bytes, z = apply flag. An absent attribute defaults to 0, so
// text renders unmodulated rather than with garbage coords.
layout(location = 4) in vec3 a_Lightmap;

uniform mat4 u_MVPMatrix;
uniform mat4 u_LightmapMatrix;
uniform sampler2D lightmap;

out vec4 tB;
out vec4 color;
out vec2 texCoord;

void main() {
    gl_Position = u_MVPMatrix * vec4(a_Position, 0.0, 1.0);
    texCoord = a_TexCoord0;
    color = a_Color;

    // Binding a program bypasses the fixed function pipeline, so unit 1 no longer modulates glyphs the
    // way it did for vanilla's FontRenderer. Scale rgb only, leaving the alpha test untouched.
    if (a_Lightmap.z > 0.5) {
        vec2 lmUv = (u_LightmapMatrix * vec4(a_Lightmap.xy * 255.0, 0.0, 1.0)).st;
        color.rgb *= textureLod(lightmap, lmUv, 0.0).rgb;
    }

    tB = a_TexBounds;
}

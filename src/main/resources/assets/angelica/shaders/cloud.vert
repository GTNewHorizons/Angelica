#version 330 core

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec4 a_Color;
layout(location = 2) in vec2 a_TexCoord0;

uniform mat4 u_MVPMatrix;
uniform vec2 u_TexOffset;
uniform vec2 u_FogOrigin;

out vec4 v_Color;
out vec2 v_TexCoord0;
out float v_FogCoord;

void main() {
    vec4 pos4 = vec4(a_Position, 1.0);
    gl_Position = u_MVPMatrix * pos4;
    v_Color = a_Color;
    v_TexCoord0 = a_TexCoord0 + u_TexOffset;
    vec2 horizDelta = a_Position.xz - u_FogOrigin;
    v_FogCoord = length(horizDelta);
}

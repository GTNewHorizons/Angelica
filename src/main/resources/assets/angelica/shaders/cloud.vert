#version 330 core

layout(location = 0) in vec3 a_Position;
layout(location = 2) in vec2 a_UV;
layout(location = 5) in float a_Factor;

uniform mat4 u_MVPMatrix;

out vec2 v_UV;
out float v_FogCoord;
out float v_Factor;

void main() {
    gl_Position = u_MVPMatrix * vec4(a_Position, 1.0);
    v_UV = a_UV;
    v_FogCoord = length(a_Position.xz);
    v_Factor = a_Factor;
}

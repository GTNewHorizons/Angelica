#version 330 core

layout(location = 0) in vec3 a_Position;
layout(location = 2) in vec2 a_UV;
layout(location = 5) in float a_Factor;

uniform mat4 u_MVPMatrix;
uniform mat4 u_MVMatrix;

out vec2 v_UV;
out vec3 v_EyePos;
out float v_Factor;

void main() {
    gl_Position = u_MVPMatrix * vec4(a_Position, 1.0);
    v_UV = a_UV;
    v_EyePos = (u_MVMatrix * vec4(a_Position, 1.0)).xyz;
    v_Factor = a_Factor;
}

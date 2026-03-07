#version 330 core

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec2 a_TexCoord;
layout(location = 2) in float a_Alpha;

uniform mat4 u_MVP;

out vec2 v_TexCoord;
out float v_Alpha;

void main() {
    gl_Position = u_MVP * vec4(a_Position, 1.0);
    v_TexCoord = a_TexCoord;
    v_Alpha = a_Alpha;
}

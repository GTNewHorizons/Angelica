#version 330 core

layout(location = 0) in vec2 a_Position;

uniform mat4 u_MVPMatrix;

out vec2 texCoord;

void main() {
    gl_Position = u_MVPMatrix * vec4(a_Position, 0.0, 1.0);
    texCoord = (gl_Position.xy / gl_Position.w) * 0.5 + 0.5;
}

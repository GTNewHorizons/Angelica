#version 330 core

layout(location = 0) in vec2 a_Position;
layout(location = 1) in vec4 a_Color;
layout(location = 2) in vec2 a_TexCoord0;
layout(location = 3) in vec4 a_TexBounds;

uniform mat4 u_MVPMatrix;

out vec4 tB;
out vec4 color;
out vec2 texCoord;

void main() {
    gl_Position = u_MVPMatrix * vec4(a_Position, 0.0, 1.0);
    texCoord = a_TexCoord0;
    color = a_Color;
    tB = a_TexBounds;
}

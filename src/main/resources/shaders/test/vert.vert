#version 150 core

in vec2 vPosition;
in vec4 color;

out vec4 fColor;
void main()
{
    gl_Position = vec4(vPosition, 0.0, 1.0);
    fColor = color;
}
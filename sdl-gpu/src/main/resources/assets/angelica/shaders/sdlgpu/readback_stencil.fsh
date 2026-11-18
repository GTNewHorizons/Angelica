#version 460 core
uniform vec4 bitValue;
layout(location = 0) out vec4 stencilOut;
void main() { stencilOut = bitValue; }

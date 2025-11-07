#version 120

attribute vec4 texBounds;
varying vec4 tB;
varying vec4 color;

void main() {
    gl_Position = ftransform();
    gl_TexCoord[0] = gl_MultiTexCoord0;
    color = gl_Color;
    tB = texBounds;
}

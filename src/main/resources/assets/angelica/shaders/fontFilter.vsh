#version 120

varying vec4 color;

void main() {
    gl_Position = ftransform();
    gl_TexCoord[0] = gl_MultiTexCoord0;
    color = gl_Color;
}

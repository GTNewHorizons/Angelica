#version 330 core

in vec2 v_TexCoord;

uniform sampler2D u_GlowDepth;

void main() {
    gl_FragDepth = texture(u_GlowDepth, v_TexCoord).r;
}

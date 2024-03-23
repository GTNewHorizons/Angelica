#version 330

in vec2 v_TexCoord;

uniform sampler2D u_BlockTex;

void main() {
    gl_FragColor = texture2D(u_BlockTex, v_TexCoord);
}

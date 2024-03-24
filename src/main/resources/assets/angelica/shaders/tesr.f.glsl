#version 330 core

in vec2 v_TexCoord; // The tex coord

uniform sampler2D u_BlockTex; // The block texture sampler
out vec4 fragColor; // The frag color

void main() {
    fragColor = texture(u_BlockTex, v_TexCoord);
}

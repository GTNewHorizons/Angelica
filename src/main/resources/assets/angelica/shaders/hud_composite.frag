#version 330 core

in vec2 v_TexCoord;

uniform sampler2D u_Texture;
uniform sampler2D u_Depth;

out vec4 fragColor;

void main() {
    fragColor = texture(u_Texture, v_TexCoord);
    gl_FragDepth = texture(u_Depth, v_TexCoord).r;
}

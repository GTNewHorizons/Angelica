#version 330 core

in vec2 v_TexCoord;

uniform sampler2D u_Texture;
uniform float u_ScaleV;
uniform float u_ScaleU;

out vec4 fragColor;

void main() {
    float u = 0.5 + u_ScaleV * (2.0 * v_TexCoord.y - 1.0);
    float v = 0.5 + u_ScaleU * (1.0 - 2.0 * v_TexCoord.x);
    fragColor = texture(u_Texture, vec2(u, v));
}

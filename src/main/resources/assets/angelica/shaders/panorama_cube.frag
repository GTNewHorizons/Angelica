#version 330 core

in vec2 v_TexCoord;
in float v_Alpha;

uniform sampler2D u_Texture;

out vec4 fragColor;

void main() {
    vec4 color = texture(u_Texture, v_TexCoord);
    fragColor = vec4(color.rgb, v_Alpha);
}

#version 330 core

in vec4 v_Color;
#ifdef TEXTURED
in vec2 v_TexCoord0;
uniform sampler2D u_Texture;
#endif

out vec4 fragColor;

void main() {
#ifdef TEXTURED
    fragColor = v_Color * texture(u_Texture, v_TexCoord0);
#else
    fragColor = v_Color;
#endif
}

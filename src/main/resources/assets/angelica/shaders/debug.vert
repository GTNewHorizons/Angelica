#version 330 core

in vec3 a_Position;
in vec4 a_Color;
#ifdef TEXTURED
in vec2 a_TexCoord0;
out vec2 v_TexCoord0;
#endif

uniform mat4 u_MVP;

out vec4 v_Color;

void main() {
    gl_Position = u_MVP * vec4(a_Position, 1.0);
    v_Color = a_Color;
#ifdef TEXTURED
    v_TexCoord0 = a_TexCoord0;
#endif
}

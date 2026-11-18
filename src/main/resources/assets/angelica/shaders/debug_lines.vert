#version 330 core

#import <angelica:include/line_quads.glsl>

in vec3 a_Position;
in vec4 a_Color;
in vec3 a_LineOtherEnd;

uniform mat4 u_MVP;
uniform vec2 u_ViewportSize;
uniform float u_LineWidth;

out vec4 v_Color;

void main() {
    float lineT;
    gl_Position = angelica_lineQuad(u_MVP * vec4(a_Position, 1.0), u_MVP * vec4(a_LineOtherEnd, 1.0), gl_VertexID % 6, u_ViewportSize, u_LineWidth, lineT);
    v_Color = a_Color;
}

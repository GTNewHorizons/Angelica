#version 330 core

layout(lines) in;
layout(triangle_strip, max_vertices = 4) out;

uniform vec2 u_ViewportSize;
uniform float u_LineWidth;

void main() {
    vec4 c0 = gl_in[0].gl_Position;
    vec4 c1 = gl_in[1].gl_Position;

    vec2 n0 = c0.xy / c0.w;
    vec2 n1 = c1.xy / c1.w;

    vec2 dir = normalize((n1 - n0) * u_ViewportSize);
    vec2 offset = vec2(-dir.y, dir.x) * u_LineWidth / u_ViewportSize;

    gl_Position = vec4((n0 + offset) * c0.w, c0.z, c0.w);
    EmitVertex();
    gl_Position = vec4((n0 - offset) * c0.w, c0.z, c0.w);
    EmitVertex();
    gl_Position = vec4((n1 + offset) * c1.w, c1.z, c1.w);
    EmitVertex();
    gl_Position = vec4((n1 - offset) * c1.w, c1.z, c1.w);
    EmitVertex();
    EndPrimitive();
}

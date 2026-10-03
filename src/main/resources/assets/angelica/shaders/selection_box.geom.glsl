#version 330 core

layout(lines) in;
layout(triangle_strip, max_vertices = 4) out;

uniform vec2 u_ViewportSize;
uniform float u_LineWidth;

void main() {
    vec4 c0 = gl_in[0].gl_Position;
    vec4 c1 = gl_in[1].gl_Position;

    float d0 = c0.z + c0.w;
    float d1 = c1.z + c1.w;
    if (d0 < 0.0 && d1 < 0.0) return;
    vec4 p0 = d0 < 0.0 ? mix(c0, c1, d0 / (d0 - d1)) : c0;
    vec4 p1 = d1 < 0.0 ? mix(c0, c1, d0 / (d0 - d1)) : c1;

    vec2 n0 = p0.xy / p0.w;
    vec2 n1 = p1.xy / p1.w;

    vec2 dir = normalize((n1 - n0) * u_ViewportSize);
    vec2 offset = vec2(-dir.y, dir.x) * u_LineWidth / u_ViewportSize;

    gl_Position = vec4((n0 + offset) * p0.w, p0.z, p0.w);
    EmitVertex();
    gl_Position = vec4((n0 - offset) * p0.w, p0.z, p0.w);
    EmitVertex();
    gl_Position = vec4((n1 + offset) * p1.w, p1.z, p1.w);
    EmitVertex();
    gl_Position = vec4((n1 - offset) * p1.w, p1.z, p1.w);
    EmitVertex();
    EndPrimitive();
}

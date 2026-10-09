#version 330 core

layout(location = 0) in vec3 a_Position;
layout(location = 4) in vec3 a_LineOtherEnd;

uniform mat4 u_MVP;
uniform vec2 u_ViewportSize;
uniform float u_LineWidth;

void main() {
    int corner = gl_VertexID % 6;
    bool start = corner == 0 || corner == 1 || corner == 3;
    vec4 self = u_MVP * vec4(a_Position, 1.0);
    vec4 other = u_MVP * vec4(a_LineOtherEnd, 1.0);
    vec4 c0 = start ? self : other;
    vec4 c1 = start ? other : self;

    float d0 = c0.z + c0.w;
    float d1 = c1.z + c1.w;
    if (d0 < 0.0 && d1 < 0.0) {
        gl_Position = vec4(0.0, 0.0, 2.0, 1.0);
        return;
    }
    vec4 p0 = d0 < 0.0 ? mix(c0, c1, d0 / (d0 - d1)) : c0;
    vec4 p1 = d1 < 0.0 ? mix(c0, c1, d0 / (d0 - d1)) : c1;

    vec2 n0 = p0.xy / p0.w;
    vec2 n1 = p1.xy / p1.w;

    vec2 pixel = 2.0 / u_ViewportSize;
    float halfWidth = 0.5 * u_LineWidth;
    vec2 offset;
    vec2 shift;
    vec2 span = abs(n1 - n0) * u_ViewportSize;
    if (span.x > span.y) {
        offset = vec2(0.0, halfWidth * pixel.y);
        shift = vec2(n0.x < n1.x ? -0.5 : 0.5, -0.125) * pixel;
    } else {
        offset = vec2(halfWidth * pixel.x, 0.0);
        shift = vec2(0.125, n0.y < n1.y ? -0.5 : 0.5) * pixel;
    }

    vec4 p = start ? p0 : p1;
    vec2 n = start ? n0 : n1;
    gl_Position = vec4((corner % 2 == 0 ? n + offset + shift : n - offset + shift) * p.w, p.z, p.w);
}

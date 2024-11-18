#version 120

attribute vec2 pos;

uniform float fbHeight;
uniform float fbWidth;

void main() {
    // Shift the bounds (x = 2 through x = 482, y = 0 through y = max - 2) to clip space
    float x = pos.x / fbWidth * 2.0 - 1.0;
    float y = clamp(pos.y, 2.0 / fbHeight * 2.0 - 1.0, 1.0);
    gl_Position = vec4(x, y, -1.0, 1.0);
}

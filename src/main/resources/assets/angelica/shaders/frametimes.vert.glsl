#version 120

attribute vec2 pos;

uniform float fbWidth;

void main() {
    // Shift the bounds (x = 2 through x = 482) to clip space
    gl_Position = vec4(pos.x / fbWidth * 2.0 - 1.0, pos.y, -1.0, 1.0);
}

#version 120

attribute vec2 pos;

uniform float fbWidth;
uniform float fbHeight;
uniform float scaleFactor;
uniform bool left;

void main() {
    // Shift the bounds (originally in pixel coords) to clip space. Flip horizontally if on the right, and ensure the
    // top vertices are pinned to the top of the screen.
    float x = pos.x / fbWidth * scaleFactor * 2.0 - 1.0;
    float y = pos.y / fbHeight * scaleFactor * 2.0 - 1.0;
    x = left ? x : -x;
    y = y < -0.9 ? y : 1.0; // -0.9 arbitrariy chosen as "probably between bottom and top verts"
    gl_Position = vec4(x, y, 0.0, 1.0);
}

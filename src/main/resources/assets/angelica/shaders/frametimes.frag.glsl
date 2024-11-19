#version 120

uniform int headIdx;
uniform float scaleFactor;
uniform float frametimes[240];

void main() {
    // Get position - gl_FragCoord starts from the lower left and returns the position of the center of the pixel
    // i.e. the lower-left-most pixel is (0.5, 0.5)
    int dx = int(gl_FragCoord.x / scaleFactor);
    int dy = int(gl_FragCoord.y / scaleFactor);

    // Get the frametime for this frag. Last at the left edge, first at the right, shifting as needed when the head
    // moves.
    int idx = int(mod(dx + headIdx, 240));
    float time = frametimes[idx];

    // Time is in nanoseconds. The bar should be 60 px high at 30 FPS, i.e. 33333333ns = 60px. 0.0000018 px/ns
    // If it's higher or as high as we are, recolor accordingly.
    // Also, apparently GLSL 120 doesn't have rounding
    float height = floor(time * 0.0000018 + 0.5);

    // Increase red from 0-28ms, and decrease green from 28-56ms
    vec4 color = vec4(0.0, 0.0, 0.0, 0.0);
    float r = clamp(time / 28000000.0, 0.0, 1.0);
    float g = 1.0 - clamp((time - 28000000.0) / 28000000.0, 0.0, 1.0);
    if (dy <= height)
        color = vec4(r, g, 0.0, 1.0);
    gl_FragColor = color;
}

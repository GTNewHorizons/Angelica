#version 330 core

uniform int headIdx;
uniform float scaleFactor;
uniform float samples[240];
uniform float pxPerNs;
uniform bool left;
uniform float fbWidth;

out vec4 fragColor;

void main() {
    // Get position - gl_FragCoord starts from the lower left and returns the position of the center of the pixel
    // i.e. the lower-left-most pixel is (0.5, 0.5). Shift if we're on the right side of the screen.
    int dx = int((left ? gl_FragCoord.x : gl_FragCoord.x - fbWidth) / scaleFactor) - 1;
    int dy = int(gl_FragCoord.y / scaleFactor);

    // Get the time for this frag. Last at the left edge, first at the right, shifting as needed when the head moves.
    int idx = int(mod(dx + headIdx, 240));
    float time = samples[idx];

    // Time is in nanoseconds. Height is calculated from the given pixels/ns factor.
    // If it's higher or as high as we are, recolor accordingly.
    // Also, apparently GLSL 120 doesn't have rounding.
    float height = floor(time * pxPerNs + 0.5);

    // Calculate the nanoseconds at the midpoint, 30px. The bar can exceed twice that, but it's used for coloring
    float midNs = 30.0 / pxPerNs;

    // Increase red from 0-midpoint, and decrease green from midpoint-"max"
    vec4 color = vec4(0.0, 0.0, 0.0, 0.0);
    float r = clamp(time / midNs, 0.0, 1.0);
    float g = 1.0 - clamp((time - midNs) / midNs, 0.0, 1.0);
    if (dy <= height)
        color = vec4(r, g, 0.0, 1.0);
    fragColor = color;
}

#version 330 compatibility

// Fullscreen pass: vertices are fed as a clip-space quad (-1..1), no matrices involved.
void main() {
    gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);
}

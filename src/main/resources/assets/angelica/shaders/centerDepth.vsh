#version 330 core

layout(location = 0) in vec3 a_Position;

void main() {
    // Full-screen quad vertices are in [0,1] range, map to [-1,1] clip space
    gl_Position = vec4(a_Position.xyz * 2.0 - 1.0, 1.0);
}

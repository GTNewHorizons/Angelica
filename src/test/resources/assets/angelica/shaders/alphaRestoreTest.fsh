#version 330 core
uniform float alphaReference;
uniform int alphaFunction;
out vec4 color;
void main() {
    color = vec4(alphaReference, float(alphaFunction), 0.0, 1.0);
}

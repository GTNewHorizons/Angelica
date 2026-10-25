#version 330 core
uniform sampler2D normals;
uniform sampler2D specular;
out vec4 color;
void main() {
    color = texture(normals, vec2(0.5)) + texture(specular, vec2(0.5));
}

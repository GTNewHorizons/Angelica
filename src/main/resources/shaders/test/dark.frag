#version 150 core

out vec4 fragColor;

// A test shader that makes everything darker
void main()
{
    fragColor = vec4(0., 0., 1., 0.5);
}
#version 150 core

uniform mat4 uTransform;

in vec3 vPosition;

void main()
{
    gl_Position = uTransform * vec4(vPosition, 1.0);
}
#version 150 core

in vec2 vPosition;

out vec2 TexCoord;

/** 
 * This is specifically used by application shaders.
 * IE post process or pixel transfer shaders, anything that is rendered using a single rectangle.
 *
 * TODO rename this shader to better denote the above message.
 */
void main()
{
    gl_Position = vec4(vPosition, 1.0, 1.0);
    TexCoord = vPosition.xy * 0.5 + 0.5;
}
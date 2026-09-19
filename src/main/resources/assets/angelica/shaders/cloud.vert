#version 330 core

layout(location = 0) in vec3 a_Position;
#ifndef UNTEXTURED
layout(location = 2) in vec2 a_UV;
#endif
layout(location = 5) in float a_Shade;

uniform mat4 u_MVPMatrix;
uniform mat4 u_MVMatrix;

#ifndef UNTEXTURED
out vec2 v_UV;
#endif
out vec3 v_EyePos;
out float v_Shade;

void main() {
    gl_Position = u_MVPMatrix * vec4(a_Position, 1.0);
#ifndef UNTEXTURED
    v_UV = a_UV;
#endif
    v_EyePos = (u_MVMatrix * vec4(a_Position, 1.0)).xyz;
    v_Shade = a_Shade;
}

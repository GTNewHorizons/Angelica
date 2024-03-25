#version 330 core

layout(location = 0) in vec3 a_Pos;
layout(location = 1) in vec2 a_TexCoord;

uniform mat4 u_ModelProjection;
uniform float u_SectionHeight;
uniform int u_BaseY;

out vec2 v_TexCoord;
out float v_yPos; // The y-position of the vertex in world space

void main() {
    vec4 pos = vec4(a_Pos, 1.0);
    pos.y += u_SectionHeight * float(gl_InstanceID);

    vec4 projectedPosition = u_ModelProjection * pos;

    gl_Position = projectedPosition;

    v_TexCoord = a_TexCoord;
    v_yPos = max(u_BaseY + u_SectionHeight * float(gl_InstanceID), 0.0);
}

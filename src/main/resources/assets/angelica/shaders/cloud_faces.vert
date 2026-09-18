#version 330 core

layout(location = 0) in int a_Face;

uniform mat4 u_MVPMatrix;
uniform mat4 u_MVMatrix;
uniform float u_CellHeight;

out vec3 v_EyePos;
flat out float v_Shade;

#ifndef UNTEXTURED
uniform vec4 u_Scroll;
out vec2 v_UV;
const float SCROLL_SPEED = 1.0 / 256.0;
const int DIR_NORTH = 2;
const int DIR_WEST = 4;
#endif

const int FLAG_INSIDE = 1;

const vec3 CORNERS[24] = vec3[24](
    vec3(1, 0, 0), vec3(1, 0, 1), vec3(0, 0, 1), vec3(0, 0, 0),
    vec3(0, 1, 0), vec3(0, 1, 1), vec3(1, 1, 1), vec3(1, 1, 0),
    vec3(0, 0, 0), vec3(0, 1, 0), vec3(1, 1, 0), vec3(1, 0, 0),
    vec3(1, 0, 1), vec3(1, 1, 1), vec3(0, 1, 1), vec3(0, 0, 1),
    vec3(0, 0, 1), vec3(0, 1, 1), vec3(0, 1, 0), vec3(0, 0, 0),
    vec3(1, 0, 0), vec3(1, 1, 0), vec3(1, 1, 1), vec3(1, 0, 1)
);

const float FACE_SHADE[6] = float[6](0.7, 1.0, 0.8, 0.8, 0.9, 0.9);

void main() {
    int faceBits = a_Face;

    int cellX = (faceBits & 0x3FF) - 512;
    int cellZ = ((faceBits >> 10) & 0x3FF) - 512;
    int dir = (faceBits >> 20) & 7;
    int flags = (faceBits >> 23) & 3;

    vec3 extent = vec3(1.0, u_CellHeight, 1.0);

    int corner = gl_VertexID;
    if ((flags & FLAG_INSIDE) != 0) corner = 3 - corner;
    vec3 unit = vec3(CORNERS[dir * 4 + corner]);

    vec3 pos = vec3(float(cellX), 0.0, float(cellZ)) + unit * extent;

#ifndef UNTEXTURED
    vec2 uvCell = vec2(float(cellX), float(cellZ)) + unit.xz;
    if (dir >= DIR_WEST) uvCell.x = float(cellX) + 0.5;
    else if (dir >= DIR_NORTH) uvCell.y = float(cellZ) + 0.5;
    v_UV = uvCell * SCROLL_SPEED + u_Scroll.xy;
#endif

    gl_Position = u_MVPMatrix * vec4(pos, 1.0);
    v_EyePos = (u_MVMatrix * vec4(pos, 1.0)).xyz;
    v_Shade = FACE_SHADE[dir];
}

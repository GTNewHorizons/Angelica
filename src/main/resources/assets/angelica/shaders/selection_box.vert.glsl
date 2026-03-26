#version 330 core

uniform mat4 u_MVP;
uniform vec3 u_Min;
uniform vec3 u_Max;

const vec3 edges[24] = vec3[24](
    vec3(0,0,0), vec3(1,0,0),  vec3(1,0,0), vec3(1,0,1),
    vec3(1,0,1), vec3(0,0,1),  vec3(0,0,1), vec3(0,0,0),
    vec3(0,1,0), vec3(1,1,0),  vec3(1,1,0), vec3(1,1,1),
    vec3(1,1,1), vec3(0,1,1),  vec3(0,1,1), vec3(0,1,0),
    vec3(0,0,0), vec3(0,1,0),  vec3(1,0,0), vec3(1,1,0),
    vec3(1,0,1), vec3(1,1,1),  vec3(0,0,1), vec3(0,1,1)
);

void main() {
    vec3 pos = mix(u_Min, u_Max, edges[gl_VertexID]);
    gl_Position = u_MVP * vec4(pos, 1.0);
}

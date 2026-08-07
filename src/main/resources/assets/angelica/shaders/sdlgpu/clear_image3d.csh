#version 460 core

layout(local_size_x = 4, local_size_y = 4, local_size_z = 4) in;

layout(binding = 0) writeonly uniform uimage3D u_target;

uniform ivec3 u_extent;

void main() {
    ivec3 texel = ivec3(gl_GlobalInvocationID);
    if (any(greaterThanEqual(texel, u_extent))) return;
    imageStore(u_target, texel, uvec4(0u));
}

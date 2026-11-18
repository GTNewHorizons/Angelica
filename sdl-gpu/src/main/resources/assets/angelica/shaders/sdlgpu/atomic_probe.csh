#version 460 core
layout(local_size_x = 64) in;
layout(binding = 0, r32i) uniform iimage2D counter;

void main() {
    imageAtomicAdd(counter, ivec2(0), 1);
}

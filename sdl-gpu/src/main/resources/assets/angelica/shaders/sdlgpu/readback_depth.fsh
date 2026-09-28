#version 460 core
uniform sampler2D depthSampler;
layout(location = 0) out vec4 depthOut;
void main() { depthOut = vec4(texelFetch(depthSampler, ivec2(gl_FragCoord.xy), 0).r, 0.0, 0.0, 1.0); }

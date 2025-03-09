#version 150

#import <sodium:include/fog.glsl>

uniform mat4 ProjMat;
uniform vec4 ColorModulator;
uniform vec4 FogColor;
uniform float FogStart;
uniform float FogEnd;

in vec4 vertexColor;
in float vertexDistance;

out vec4 fragColor;

// Custom cloud fog algorithm by Balint, for use in Sodium
void main() {
    vec4 color = vertexColor * ColorModulator;

    if (color.a < 0.1) {
        discard;
    }

    fragColor = _linearFog(color, vertexDistance, FogColor, FogStart, FogEnd);
}


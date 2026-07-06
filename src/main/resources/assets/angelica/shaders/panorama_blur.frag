#version 330 core

in vec2 v_TexCoord;

uniform sampler2D u_Texture;
uniform vec2 u_Direction;
uniform float u_Radius;

out vec4 fragColor;

void main() {
    float sigma = max(u_Radius * 0.5, 1.0);
    float invTwoSigmaSq = 1.0 / (2.0 * sigma * sigma);

    vec3 sum = vec3(0.0);
    float weightSum = 0.0;

    for (float i = -u_Radius; i <= u_Radius; i += 1.0) {
        float w = exp(-i * i * invTwoSigmaSq);
        sum += texture(u_Texture, v_TexCoord + u_Direction * i).rgb * w;
        weightSum += w;
    }
    fragColor = vec4(sum / weightSum, 1.0);
}

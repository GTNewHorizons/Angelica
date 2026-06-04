#version 330 core

// Given Uniforms:
uniform sampler2D textFBO; // The texture of the text's Framebuffer (use this for the post-processing)
uniform sampler2D sceneFBO; // The texture of the scene (for better scene blending)
uniform vec2 uTexelSize; // The size of each pixel (1 / displayWidth, 1 / displayHeight)
uniform float uTime; // The time, measured in seconds
uniform int uScale; // The ScaledResolution scale
uniform vec4 uTexBounds; // The bounds of the texture

// In variables:
in vec2 texCoord;

// Out variables:
out vec4 fragColor;

// Example:
void main() {
    vec4 scene = texture(sceneFBO, texCoord);
    vec4 textColor = texture(textFBO, texCoord);

    fragColor = vec4(mix(vec3(0, 0, 0), textColor.rgb, textColor.a), 1);
}


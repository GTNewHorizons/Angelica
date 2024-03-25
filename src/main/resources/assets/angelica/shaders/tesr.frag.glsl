#version 330 core

in vec2 v_TexCoord; // The tex coord
in float v_yPos; // The y-coord in world space
uniform sampler2D u_BlockTex; // The block texture sampler
uniform float u_Time; // loops from 0 to 1 every second
uniform vec2 u_GlowU; // Bounds of the glowy bit's U, [min, max]
uniform vec2 u_GlowV; // Bounds of the glowy bit's V, [min, max]

out vec4 fragColor; // The frag color

const vec3 MAX = vec3(1.0, 1.0, 1.0);
const vec3 glow_color = vec3(0.0, 0.65, 1.0); // The glow color, RGB

vec3 frontWave(in vec3 color, in vec2 uv, in float t, in float waveletCount, in float speed) {
    float y = fract(v_yPos / 255.0 * waveletCount + speed * t);
    float sy = sin(y * 3.14);
    return color * sy * pow(y, 4.0) * 6.0;
}

void main() {

    float glowMul =
    (v_TexCoord.x >= u_GlowU.x && v_TexCoord.x <= u_GlowU.y && v_TexCoord.y >= u_GlowV.x && v_TexCoord.y <= u_GlowV.y)
    ? 1.0 : 0.0;

    vec4 tex = texture(u_BlockTex, v_TexCoord);
    vec3 col = frontWave(glow_color, v_TexCoord, u_Time, 5.0, -1.0);
    fragColor = vec4(min(tex.rgb + col * glowMul, MAX), tex.a);
}

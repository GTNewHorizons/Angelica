#version 330 core

in vec2 v_TexCoord; // The tex coord
in float v_yPos; // The y-coord in world space
uniform sampler2D u_BlockTex; // The block texture sampler
uniform float u_Time; // loops from 0 to 1 every second

out vec4 fragColor; // The frag color

const vec3 MAX = vec3(1.0, 1.0, 1.0);
const float glow_factor = 0.5;
const vec3 glow_color = vec3(0.0, 0.325, 0.5) * glow_factor; // The RGB glow color to mix in

vec3 frontWave(in vec3 color, in vec2 uv, in float t, in float waveletCount, in float speed) {
    float y = fract(v_yPos / 255.0 * waveletCount + speed * t);
    float sy = sin(y * 3.14);
    return color * sy * pow(y, 4.0) * 6.0;
}

void main() {

    vec4 tex = texture(u_BlockTex, v_TexCoord);
    vec3 col = frontWave(glow_color, v_TexCoord, u_Time, 5.0, -1.0);
    fragColor = vec4(min(tex.rgb + col, MAX), tex.a);
}

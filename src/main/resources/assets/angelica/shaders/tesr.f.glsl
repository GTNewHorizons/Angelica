#version 330 core

in vec2 v_TexCoord; // The tex coord
in float v_yPos; // The y-coord in world space
uniform sampler2D u_BlockTex; // The block texture sampler
uniform float u_Time; // loops from 0 to 1 every second

out vec4 fragColor; // The frag color

vec3 frontWave(in vec3 color, in vec2 uv, in float t, in float waveletCount, in float speed) {
    float y = fract(v_yPos / 255.0 * waveletCount + speed * t);
    float sy = sin(y * 3.14);
    return color * sy * pow(y, 4.0) * 6.0;
}

void main() {
    //float factor = mod(u_Time * -255.0, 255.0) / 255.0;
    /*vec4 tex = texture(u_BlockTex, v_TexCoord);

    fragColor = vec4(min(tex.r + factor, 1.0), tex.g, tex.b, tex.a);*/

    vec4 tex = texture(u_BlockTex, v_TexCoord);
    vec3 col = frontWave(vec3(1.0, 1.65, 0), v_TexCoord, u_Time, 5.0, -1.0);
    fragColor = vec4(max(tex.rgb, col), 1.0);
}
/*
void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
    // Normalized pixel coordinates (from 0 to 1)
    vec2 uv = fragCoord/iResolution.xy;
    float t = iTime;

    vec3 col = frontWave(vec3(0, 0.65, 1.0), uv, t, 5.0, -2.0);

    fragColor = vec4(col,1.0);
}*/

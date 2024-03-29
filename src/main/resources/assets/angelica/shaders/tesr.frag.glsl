#version 120

varying vec2 v_TexCoord; // The tex coord
varying float v_yPos; // The y-coord in world space
uniform sampler2D u_BlockTex; // The block texture sampler
uniform float u_Time; // loops from 0 to 1 every second
uniform vec2 u_GlowU; // Bounds of the glowy bit's U, [min, max]
uniform vec2 u_GlowV; // Bounds of the glowy bit's V, [min, max]

const vec3 MAX = vec3(1.0, 1.0, 1.0);
const vec3 glow_color = vec3(0.0, 0.65, 1.0); // The glow color, RGB
const float cableHeight = 512.0;

vec3 frontWave(in vec3 color, in vec2 uv, in float t, in float sections, in float speed) {
    float front = t;
    float y = v_yPos / cableHeight;
    float dist = abs(front - y);
    //Disable lights if they are too far from the front
    float lightsOn = dist <= 0.03 ? 1.0 : 0.0;
    float sy = sin(1.57 + (dist*33) * 1.57);
    return color * pow(sy, 2.0) * lightsOn;
}

void main() {

    float glowMul =
    (v_TexCoord.x >= u_GlowU.x && v_TexCoord.x <= u_GlowU.y && v_TexCoord.y >= u_GlowV.x && v_TexCoord.y <= u_GlowV.y)
    ? 1.0 : 0.0;

    vec4 tex = texture2D(u_BlockTex, v_TexCoord);
    vec3 col = frontWave(glow_color, v_TexCoord, u_Time, 1.0, -10.0);
    gl_FragColor = vec4(min(tex.rgb + col * glowMul, MAX), tex.a);
}

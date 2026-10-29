#version 330 core

// A sample is skipped only when all zero, so additive light with no coverage still counts.

in vec2 v_TexCoord;

uniform sampler2D u_Depth;
uniform sampler2D u_Layer0;
uniform sampler2D u_Layer1;
uniform sampler2D u_Layer2;
uniform sampler2D u_Layer3;
uniform sampler2D u_Layer4;
uniform sampler2D u_LayerDepth0;
uniform sampler2D u_LayerDepth1;
uniform sampler2D u_LayerDepth2;
uniform sampler2D u_LayerDepth3;
uniform sampler2D u_LayerDepth4;
uniform int u_LayerMask;

out vec4 fragColor;

vec4 colors[5];
float depths[5];
int count = 0;
float mainDepth;

void insert(int bit, sampler2D colorTexture, sampler2D depthTexture) {
    if ((u_LayerMask & bit) == 0) return;
    vec4 color = texture(colorTexture, v_TexCoord);
    if (color == vec4(0.0)) return;
    float depth = texture(depthTexture, v_TexCoord).r;
    if (depth > mainDepth) return;
    int j = count++;
    colors[j] = color;
    depths[j] = depth;
    while (j > 0 && depths[j] > depths[j - 1]) {
        vec4 c = colors[j];
        colors[j] = colors[j - 1];
        colors[j - 1] = c;
        float d = depths[j];
        depths[j] = depths[j - 1];
        depths[j - 1] = d;
        j--;
    }
}

void main() {
    mainDepth = texture(u_Depth, v_TexCoord).r;
    insert(1, u_Layer0, u_LayerDepth0);
    insert(2, u_Layer1, u_LayerDepth1);
    insert(4, u_Layer2, u_LayerDepth2);
    insert(8, u_Layer3, u_LayerDepth3);
    insert(16, u_Layer4, u_LayerDepth4);
    vec4 result = vec4(0.0);
    for (int i = 0; i < count; i++) {
        result = result * (1.0 - colors[i].a) + colors[i];
    }
    fragColor = result;
    gl_FragDepth = count > 0 ? depths[count - 1] : mainDepth;
}

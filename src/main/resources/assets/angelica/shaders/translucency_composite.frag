#version 330 core

// A sample is skipped only when all zero, so additive light with no coverage still counts. The TERRAIN_BEHIND sheet
// sorts at the block-entity depth where the front sheet has terrain, inserted first so a tie keeps it behind.

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
#ifdef TERRAIN_BEHIND
uniform sampler2D u_TerrainBehind;
#define SHEETS 6
#else
#define SHEETS 5
#endif

out vec4 fragColor;

vec4 colors[SHEETS];
float depths[SHEETS];
int count = 0;
float mainDepth;

void insertSheet(vec4 color, float depth) {
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

void insert(int bit, sampler2D colorTexture, sampler2D depthTexture) {
    if ((u_LayerMask & bit) == 0) return;
    vec4 color = texture(colorTexture, v_TexCoord);
    if (color == vec4(0.0)) return;
    insertSheet(color, texture(depthTexture, v_TexCoord).r);
}

#ifdef TERRAIN_BEHIND
void insertTerrain() {
    vec4 behind = texture(u_TerrainBehind, v_TexCoord);
    vec4 front = texture(u_Layer0, v_TexCoord);
    if (behind != vec4(0.0)) {
        insertSheet(behind, front == vec4(0.0) ? texture(u_LayerDepth0, v_TexCoord).r : texture(u_LayerDepth1, v_TexCoord).r);
    }
    if (front != vec4(0.0)) insertSheet(front, texture(u_LayerDepth0, v_TexCoord).r);
}
#endif

void main() {
    mainDepth = texture(u_Depth, v_TexCoord).r;
#ifdef TERRAIN_BEHIND
    insertTerrain();
#else
    insert(1, u_Layer0, u_LayerDepth0);
#endif
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

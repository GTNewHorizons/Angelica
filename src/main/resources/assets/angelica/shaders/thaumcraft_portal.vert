#version 330 core

// One instance per face, TC's four vertices in TC's order. a_Corner0.w is axis + 4 * plane index (axis 0 X, 1 Y, 2 Z);
// when capturing, the w of corners 1 to 3 is the face's atlas slot: x, y and size in atlas pixels.
layout(location = 0) in vec4 a_Corner0;
layout(location = 1) in vec4 a_Corner1;
layout(location = 2) in vec4 a_Corner2;
layout(location = 3) in vec4 a_Corner3;

// 12 vec4s per plane, built on the CPU with GLStateManager's float operations so they match bit for bit: 8 of texture
// matrix translations (m30, m31), two layers each, then 4 of the w glTexGen stores in the Q eye plane, four layers each.
uniform vec4 u_Planes[MAX_PLANES * 12];

uniform mat4 u_ModelViewProjection;
uniform mat4 u_ModelView;
// TC's R texgen plane, (0, 0, 0, 1). A uniform rather than a constant, so the compiler cannot fold the texture matrix
// product into a different rounding order than fixed function's.
uniform vec4 u_ObjectPlaneR;
uniform vec3 u_EyePlane[3];
uniform vec3 u_RowS[48];
uniform vec3 u_RowT[48];
uniform sampler2D u_Lightmap;
uniform vec2 u_LightmapUv;
uniform float u_LightmapEnabled;
#ifdef CAPTURE
uniform float u_AtlasSize;
#endif
#ifdef FOG
uniform int u_FogDistanceMode;
out float v_FogCoord;
#endif

out vec3 v_Tex[16];
flat out vec4 v_Light;

const vec2 SLOT_CORNERS[4] = vec2[](vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0), vec2(0.0, 1.0));

vec2 objectLinearST(vec3 v, int axis) {
    return axis == 0 ? v.zy : (axis == 1 ? v.xz : v.xy);
}

void main() {
    vec3 corners[4] = vec3[](a_Corner0.xyz, a_Corner1.xyz, a_Corner2.xyz, a_Corner3.xyz);
    vec4 pos4 = vec4(corners[gl_VertexID], 1.0);
    vec4 eyePos = u_ModelView * pos4;

    int axisAndPlane = int(a_Corner0.w);
    int axis = axisAndPlane & 3;
    int plane = (axisAndPlane >> 2) * 12;
    vec2 st = objectLinearST(pos4.xyz, axis);
    float r = dot(pos4, u_ObjectPlaneR);
    vec3 eyePlane = u_EyePlane[axis];

    for (int i = 0; i < 16; i++) {
        float q = dot(eyePos, vec4(eyePlane, u_Planes[plane + 8 + (i >> 2)][i & 3]));
        vec3 rowS = u_RowS[axis * 16 + i];
        vec3 rowT = u_RowT[axis * 16 + i];
        vec4 pair = u_Planes[plane + (i >> 1)];
        vec2 translation = (i & 1) == 0 ? pair.xy : pair.zw;
        mat4 textureMatrix = mat4(vec4(rowS.x, rowT.x, 0.0, 0.0), vec4(rowS.y, rowT.y, 0.0, 0.0),
                                  vec4(rowS.z, rowT.z, 1.0, 0.0), vec4(translation, 0.0, 1.0));
        v_Tex[i] = (textureMatrix * vec4(st, r, q)).xyw;
    }

    v_Light = u_LightmapEnabled > 0.5 ? texture(u_Lightmap, u_LightmapUv) : vec4(1.0);
#ifdef FOG
    v_FogCoord = u_FogDistanceMode == 0 ? length(eyePos.xyz) : (u_FogDistanceMode == 1 ? eyePos.z : abs(eyePos.z));
#endif

#ifdef CAPTURE
    vec2 slot = vec2(a_Corner1.w, a_Corner2.w) + SLOT_CORNERS[gl_VertexID] * a_Corner3.w;
    gl_Position = vec4(slot / u_AtlasSize * 2.0 - 1.0, 0.0, 1.0);
#else
    gl_Position = u_ModelViewProjection * pos4;
#endif
}

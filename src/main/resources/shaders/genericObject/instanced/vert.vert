#version 330 core

layout (location = 1) in vec4 aColor;
layout (location = 2) in vec3 aScale;
layout (location = 3) in ivec3 aTranslateChunk;
layout (location = 4) in vec3 aTranslateSubChunk;
layout (location = 5) in int aMaterial;

uniform ivec3 uOffsetChunk;
uniform vec3 uOffsetSubChunk;
uniform ivec3 uCameraPosChunk;
uniform vec3 uCameraPosSubChunk;

uniform mat4 uProjectionMvm;
uniform int uSkyLight;
uniform int uBlockLight;
uniform sampler2D uLightMap;

uniform float uNorthShading;
uniform float uSouthShading;
uniform float uEastShading;
uniform float uWestShading;
uniform float uTopShading;
uniform float uBottomShading;


in vec3 vPosition;

out vec4 fColor;

void main()
{
    // aTranslate - moves the vertex to the boxGroup's relative position
    // uOffset - moves the vertex to the boxGroup's world position
    // uCameraPos - moves the vertex into camera space
    vec3 trans = (aTranslateChunk + uOffsetChunk - uCameraPosChunk) * 16.0f;
    // separate float and int values are to fix percission loss at extreme distances from the origin (IE 10,000,000+)
    // luckily large translate values minus large cameraPos generally equal values that cleanly fit in a float
    trans += (aTranslateSubChunk + uOffsetSubChunk - uCameraPosSubChunk);
    
    // combination translation and scaling matrix
    mat4 transform = mat4(
        aScale.x, 0.0,      0.0,      0.0,
        0.0,      aScale.y, 0.0,      0.0,
        0.0,      0.0,      aScale.z, 0.0,
        trans.x,  trans.y,  trans.z,  1.0
    );
    
    gl_Position = uProjectionMvm * transform * vec4(vPosition, 1.0);

    float blockLight = (float(uBlockLight)+0.5) / 16.0;
    float skyLight = (float(uSkyLight)+0.5) / 16.0;
    vec4 lightColor = vec4(texture(uLightMap, vec2(blockLight, skyLight)).xyz, 1.0);
    
    
    fColor = lightColor * aColor;
    
    // apply directional shading
    if (gl_VertexID >= 0 && gl_VertexID < 4) { fColor.rgb *= uNorthShading; }
    else if (gl_VertexID >= 4 && gl_VertexID < 8) { fColor.rgb *= uSouthShading; }
    else if (gl_VertexID >= 8 && gl_VertexID < 12) { fColor.rgb *= uWestShading; }
    else if (gl_VertexID >= 12 && gl_VertexID < 16) { fColor.rgb *= uEastShading; }
    else if (gl_VertexID >= 16 && gl_VertexID < 20) { fColor.rgb *= uBottomShading; }
    else if (gl_VertexID >= 20 && gl_VertexID < 24) { fColor.rgb *= uTopShading; }
    
}

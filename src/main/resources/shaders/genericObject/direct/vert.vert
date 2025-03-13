#version 150 core

uniform mat4 uTransform;
uniform vec4 uColor;
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
    gl_Position = uTransform * vec4(vPosition, 1.0);
    
    
    float blockLight = (float(uBlockLight)+0.5) / 16.0;
    float skyLight = (float(uSkyLight)+0.5) / 16.0;
    vec4 lightColor = vec4(texture(uLightMap, vec2(blockLight, skyLight)).xyz, 1.0);
    
    
    fColor = lightColor * uColor;
    
    // apply directional shading
    if (gl_VertexID >= 0 && gl_VertexID < 4) { fColor.rgb *= uNorthShading; }
    else if (gl_VertexID >= 4 && gl_VertexID < 8) { fColor.rgb *= uSouthShading; }
    else if (gl_VertexID >= 8 && gl_VertexID < 12) { fColor.rgb *= uWestShading; }
    else if (gl_VertexID >= 12 && gl_VertexID < 16) { fColor.rgb *= uEastShading; }
    else if (gl_VertexID >= 16 && gl_VertexID < 20) { fColor.rgb *= uBottomShading; }
    else if (gl_VertexID >= 20 && gl_VertexID < 24) { fColor.rgb *= uTopShading; }

}
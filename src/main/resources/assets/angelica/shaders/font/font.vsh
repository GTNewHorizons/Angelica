#version 330 core

layout(location = 0) in vec2 quadCorner;
layout(location = 1) in vec4 glyphRect;
layout(location = 2) in vec4 uvRect;
layout(location = 3) in vec4 aColor;
layout(location = 4) in uint aLayer;


uniform mat4 u_MVPMatrix;

flat out vec4 tB;
flat out vec4 color;
out vec2 texCoord;
flat out uint layer;

//TODO italic
//TODO separate shader for untex rect
void main() {
    vec2 pos = glyphRect.xy + quadCorner * glyphRect.zw;
    vec2 uv = uvRect.xy + quadCorner * uvRect.zw;
    gl_Position = u_MVPMatrix * vec4(pos, 0.0, 1.0);
    texCoord = uv;
    color = aColor;
    tB = vec4(uvRect.xy, uvRect.x + uvRect.z, uvRect.y + uvRect.w);
    layer = aLayer;
}

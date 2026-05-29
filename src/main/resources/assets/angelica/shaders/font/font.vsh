#version 330 core

layout(location = 0) in vec2 quadCorner;
layout(location = 1) in vec4 glyphRect;
layout(location = 2) in vec4 uvRect; // uMin, vMin, uSize, vSize
layout(location = 3) in vec4 aColor;
layout(location = 4) in uint aLayer;
layout(location = 5) in uint flags;


uniform mat4 u_MVPMatrix;

flat out vec4 tB;
flat out vec4 color;
flat out uint layer;

out vec2 texCoord;

void main() {
    vec2 pos = glyphRect.xy + quadCorner * glyphRect.zw;
    if ((flags & FLAG_ITALIC) != 0u) {
        pos.x += (1.0 - 2.0 * quadCorner.y);
    }
    vec2 uv = uvRect.xy + quadCorner * uvRect.zw;
    if ((flags & FLAG_DINNERBONE) != 0u) {
        uv = vec2(uvRect.x + quadCorner.x * uvRect.z, uvRect.y + (1.0 - quadCorner.y) * uvRect.w);
    }
    gl_Position = u_MVPMatrix * vec4(pos, 0.0, 1.0);
    texCoord = uv;
    color = aColor;
    tB = vec4(uvRect.xy, uvRect.x + uvRect.z, uvRect.y + uvRect.w);
    layer = aLayer;
}

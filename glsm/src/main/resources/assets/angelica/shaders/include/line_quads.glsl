bool angelica_lineStart(int corner) {
    return corner == 0 || corner == 1 || corner == 3;
}

vec4 angelica_lineQuad(vec4 clipThis, vec4 clipOther, int corner, vec2 viewport, float width, out float lineT) {
    bool lineStart = angelica_lineStart(corner);
    vec4 lineC0 = lineStart ? clipThis : clipOther;
    vec4 lineC1 = lineStart ? clipOther : clipThis;
    lineT = 0.0;
    float lineD0 = lineC0.z + lineC0.w;
    float lineD1 = lineC1.z + lineC1.w;
    if (lineD0 < 0.0 && lineD1 < 0.0) {
        return vec4(0.0, 0.0, 2.0, 1.0);
    } else {
        float lineT0 = lineD0 < 0.0 ? lineD0 / (lineD0 - lineD1) : 0.0;
        float lineT1 = lineD1 < 0.0 ? lineD0 / (lineD0 - lineD1) : 1.0;
        lineT = lineStart ? lineT0 : lineT1;
        vec4 lineP0 = mix(lineC0, lineC1, lineT0);
        vec4 lineP1 = mix(lineC0, lineC1, lineT1);
        vec2 lineN0 = lineP0.xy / lineP0.w;
        vec2 lineN1 = lineP1.xy / lineP1.w;
        vec2 linePixel = 2.0 / viewport;
        float lineHalfWidth = 0.5 * width;
        vec2 lineOffset;
        vec2 lineShift;
        vec2 lineSpan = abs(lineN1 - lineN0) * viewport;
        if (lineSpan.x > lineSpan.y) {
            lineOffset = vec2(0.0, lineHalfWidth * linePixel.y);
            lineShift = vec2(lineN0.x < lineN1.x ? -0.5 : 0.5, -0.125) * linePixel;
        } else {
            lineOffset = vec2(lineHalfWidth * linePixel.x, 0.0);
            lineShift = vec2(0.125, lineN0.y < lineN1.y ? -0.5 : 0.5) * linePixel;
        }
        vec4 lineP = lineStart ? lineP0 : lineP1;
        vec2 lineN = lineStart ? lineN0 : lineN1;
        return vec4((lineN + (corner % 2 == 0 ? lineOffset : -lineOffset) + lineShift) * lineP.w, lineP.z, lineP.w);
    }
}

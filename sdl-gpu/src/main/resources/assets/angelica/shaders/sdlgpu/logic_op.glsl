vec4 angelica_logicOp(vec4 src, sampler2D dstTex, vec4 maxv) {
    uvec4 s = uvec4(roundEven(clamp(src, 0.0, 1.0) * maxv));
    uvec4 d = uvec4(roundEven(clamp(texelFetch(dstTex, ivec2(gl_FragCoord.xy), 0), 0.0, 1.0) * maxv));
    uvec4 t0 = uvec4(0u - (ANGELICA_LOGIC_OP & 1u));
    uvec4 t1 = uvec4(0u - ((ANGELICA_LOGIC_OP >> 1u) & 1u));
    uvec4 t2 = uvec4(0u - ((ANGELICA_LOGIC_OP >> 2u) & 1u));
    uvec4 t3 = uvec4(0u - ((ANGELICA_LOGIC_OP >> 3u) & 1u));
    uvec4 r = ((~s & ~d & t0) | (~s & d & t1) | (s & ~d & t2) | (s & d & t3)) & uvec4(maxv);
    return mix(src, vec4(r) / max(maxv, vec4(1.0)), greaterThan(maxv, vec4(0.0)));
}

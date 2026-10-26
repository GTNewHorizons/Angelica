const vec2 SAMPLE_OFFSETS[TEXTURE_SUPERSAMPLING_SAMPLES] = vec2[](
#if TEXTURE_SUPERSAMPLING_SAMPLES == 2
    vec2(0.25,   0.25), vec2(-0.25, -0.25)
#elif TEXTURE_SUPERSAMPLING_SAMPLES == 4
    vec2(0.125,  0.375), vec2(-0.125, -0.375),
    vec2(0.375, -0.125), vec2(-0.375, 0.125)
#elif TEXTURE_SUPERSAMPLING_SAMPLES == 8
    vec2(-0.1875, -0.0625), vec2( 0.3125,  0.0625),
    vec2(-0.0625,  0.1875), vec2(-0.3125, -0.1875),
    vec2( 0.1875, -0.3125), vec2( 0.4375, -0.4375),
    vec2(-0.4375,  0.3125), vec2( 0.0625,  0.4375)
#elif TEXTURE_SUPERSAMPLING_SAMPLES == 16
    vec2( 0.0625,  0.0625), vec2(-0.0625, -0.1875),
    vec2(-0.1875,  0.1250), vec2( 0.2500, -0.0625),
    vec2(-0.3125, -0.1250), vec2( 0.1250,  0.3125),
    vec2( 0.3125,  0.1875), vec2( 0.1875, -0.3125),
    vec2(-0.1250,  0.3750), vec2( 0.0000, -0.4375),
    vec2(-0.2500, -0.3750), vec2(-0.3750,  0.2500),
    vec2(-0.5000,  0.0000), vec2( 0.4375, -0.2500),
    vec2( 0.3750,  0.4375), vec2(-0.4375, -0.5000)
#endif
);

vec4 sampleSupersampledTexture(sampler2D tex, vec4 coord) {
    vec2 uv = coord.xy / coord.w;
    vec2 dx = dFdx(uv);
    vec2 dy = dFdy(uv);
    ivec2 size = textureSize(tex, 0);
    vec2 offsets[TEXTURE_SUPERSAMPLING_SAMPLES];
    vec2 extent = vec2(0.0);
    for (int i = 0; i < TEXTURE_SUPERSAMPLING_SAMPLES; i++) {
        offsets[i] = SAMPLE_OFFSETS[i].x * dx + SAMPLE_OFFSETS[i].y * dy;
        extent = max(extent, abs(offsets[i]));
    }
    extent *= vec2(size);
    float footprint = max(extent.x, extent.y);
    if (footprint >= u_AtlasGutter) return textureGrad(tex, uv, dx, dy);
    float supersampling = 1.0 - smoothstep(0.75 * u_AtlasGutter, u_AtlasGutter, footprint);

    // The atlas gutter is 2^maxMipLevel.
    float texelsPerPixel = max(length(dx * vec2(size)), length(dy * vec2(size)));
    int lod = int(min(ceil(log2(max(texelsPerPixel / sqrt(float(TEXTURE_SUPERSAMPLING_SAMPLES)), 1.0))),
        log2(u_AtlasGutter)));

    // Transparent texels must not tint the visible samples.
    vec4 color = vec4(0.0);
    for (int i = 0; i < TEXTURE_SUPERSAMPLING_SAMPLES; i++) {
        ivec2 texel = min(ivec2(fract(uv + offsets[i]) * vec2(size)), size - 1);
        vec4 sampleColor = texelFetch(tex, texel >> lod, lod);
        color += vec4(sampleColor.rgb * sampleColor.a, sampleColor.a);
    }
    color /= float(TEXTURE_SUPERSAMPLING_SAMPLES);
    if (supersampling < 1.0) {
        vec4 filtered = textureGrad(tex, uv, dx, dy);
        color = mix(vec4(filtered.rgb * filtered.a, filtered.a), color, supersampling);
    }
    return vec4(color.rgb / max(color.a, 0.00001), color.a);
}

const int FOG_SHAPE_SPHERICAL = 0;
const int FOG_SHAPE_CYLINDRICAL = 1;

vec4 _linearFog(vec4 fragColor, float fragDistance, vec4 fogColor, float fogStart, float fogEnd) {
#ifdef USE_FOG
    if (fragDistance <= fogStart) {
        return fragColor;
    }
    float factor = fragDistance < fogEnd ? smoothstep(fogStart, fogEnd, fragDistance) : 1.0; // alpha value of fog is used as a weight
    vec3 blended = mix(fragColor.rgb, fogColor.rgb, factor * fogColor.a);

    return vec4(blended, fragColor.a); // alpha value of fragment cannot be modified
#else
    return fragColor;
#endif
}

float _linearFogFade(float fragDistance, float fogStart, float fogEnd) {
#ifdef USE_FOG
    if (fragDistance <= fogStart) {
        return 1.0;
    } else if (fragDistance >= fogEnd) {
        return 0.0;
    }

    return smoothstep(fogEnd, fogStart, fragDistance);
#else
    return 1.0;
#endif
}

vec4 _exp2Fog(vec4 fragColor, float fragDistance, vec4 fogColor, float fogDensity) {
#ifdef USE_FOG
    float dist = fragDistance * fogDensity;
    float factor = clamp(1.0 / exp2(dist * dist), 0.0, 1.0);
    vec3 blended = mix(fogColor.rgb, fragColor.rgb, factor * fogColor.a);

    return vec4(blended, fragColor.a); // alpha value of fragment cannot be modified
#else
    return fragColor;
#endif
}

float getFragDistance(int fogShape, vec3 position) {
    // Use the maximum of the horizontal and vertical distance to get cylindrical fog if fog shape is cylindrical
    switch (fogShape) {
        case FOG_SHAPE_SPHERICAL: return length(position);
        case FOG_SHAPE_CYLINDRICAL: return max(length(position.xz), abs(position.y));
        default: return length(position); // This shouldn't be possible to get, but return a sane value just in case
    }
}
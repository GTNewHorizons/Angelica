out vec4 fragColor;

vec4 glintLayer(vec4 coord, out bool visible) {
    vec4 color = textureProj(u_Sampler0, coord) * v_Color;
#if GLINT_OVERLAY == 2
    color.rgb = mix(color.rgb, v_Overlay.rgb, v_Overlay.a);
#elif GLINT_OVERLAY == 1
    color.rgb = mix(color.rgb, u_OverlayColor.rgb, u_OverlayColor.a);
#endif
#if GLINT_LIGHTMAP
    color *= textureProj(u_Sampler1, v_TexCoord1);
#endif
    float qa = round(color.a * 255.0);
    float ref = u_AlphaRef * 255.0;
#if GLINT_ALPHA_FUNC == 0
    visible = false;
#elif GLINT_ALPHA_FUNC == 1
    visible = qa < ref;
#elif GLINT_ALPHA_FUNC == 2
    visible = qa == round(ref);
#elif GLINT_ALPHA_FUNC == 3
    visible = qa <= ref;
#elif GLINT_ALPHA_FUNC == 4
    visible = qa > ref;
#elif GLINT_ALPHA_FUNC == 5
    visible = qa != round(ref);
#elif GLINT_ALPHA_FUNC == 6
    visible = qa >= ref;
#else
    visible = true;
#endif
    if (!visible) return vec4(0.0);
#if GLINT_FOG_MODE == 1
    float fog = v_FogCoord * u_FogParams.x + u_FogParams.y;
#elif GLINT_FOG_MODE == 2
    float fog = exp2(-(v_FogCoord * u_FogParams.z));
#elif GLINT_FOG_MODE == 3
    float fogDistance = v_FogCoord * u_FogParams.w;
    float fog = exp2(-(fogDistance * fogDistance));
#endif
#if GLINT_FOG_MODE != 0
    color.rgb = mix(u_FogColor.rgb, color.rgb, clamp(fog, 0.0, 1.0));
#endif
    return color;
}

void main() {
    bool visible0, visible1;
    vec4 a = glintLayer(v_TexCoord0, visible0);
    vec4 b = glintLayer(u_TextureMatrix3 * v_TexCoord0, visible1);
    if (!visible0 && !visible1) discard;
    fragColor = sqrt(a * a + b * b);
#if GLINT_REPLACE_ALPHA
    fragColor.a = visible1 ? b.a : a.a;
#endif
}

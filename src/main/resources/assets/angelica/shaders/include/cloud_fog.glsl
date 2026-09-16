uniform vec4 u_FogParams; // linear: x=-1/(end-start), y=end/(end-start); exp/exp2: z=density; w=mode (0=linear, 1=exp, 2=exp2)
uniform vec4 u_FogColor;
uniform bool u_FogEnabled;

vec3 applyCloudFog(vec3 color, vec3 eyePos) {
    if (!u_FogEnabled) return color;

    float fogCoord = length(eyePos);
    float f;
    if (u_FogParams.w == 0.0) {
        f = fogCoord * u_FogParams.x + u_FogParams.y;
    } else if (u_FogParams.w == 1.0) {
        f = exp(-u_FogParams.z * fogCoord);
    } else {
        float d = u_FogParams.z * fogCoord;
        f = exp(-d * d);
    }
    return mix(u_FogColor.rgb, color, clamp(f, 0.0, 1.0));
}

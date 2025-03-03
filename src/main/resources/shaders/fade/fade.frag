#version 150 core

in vec2 TexCoord;

out vec4 fragColor;

uniform sampler2D uMcDepthTexture;
uniform sampler2D uDhDepthTexture;
uniform sampler2D uCombinedMcDhColorTexture;
uniform sampler2D uDhColorTexture;
// inverted model view matrix and projection matrix
uniform mat4 uDhInvMvmProj;
uniform mat4 uMcInvMvmProj;

uniform float uStartFadeBlockDistance;
uniform float uEndFadeBlockDistance;
uniform float uMaxLevelHeight;



vec3 calcViewPosition(float fragmentDepth, mat4 invMvmProj) 
{
    // normalized device coordinates
    vec4 ndc = vec4(TexCoord.xy, fragmentDepth, 1.0);
    ndc.xyz = ndc.xyz * 2.0 - 1.0;

    vec4 eyeCoord = invMvmProj * ndc;
    return eyeCoord.xyz / eyeCoord.w;
}

/**
 * Used to fade out vanilla chunks so the transition
 * between DH and vanilla is smoother.
 */
void main() 
{
    // includes both the vanilla chunks as well as DH
    vec4 combinedMcDhColor = texture(uCombinedMcDhColorTexture, TexCoord);
    // just the DH render pass
    vec4 dhColor = texture(uDhColorTexture, TexCoord);
    
    // the DH texture will have white if nothing was written to that pixel.
    // TODO replace with a depth texture check, this feels janky
    if (dhColor == vec4(1))
    {
        // if not done vanilla clouds will render incorrectly at night
        dhColor = combinedMcDhColor;
    }
    
    float mcFragmentDepth = texture(uMcDepthTexture, TexCoord).r;
    float dhFragmentDepth = texture(uDhDepthTexture, TexCoord).r;
    vec3 dhVertexWorldPos = calcViewPosition(dhFragmentDepth, uDhInvMvmProj);
    
	// this is a work around to prevent MC clouds rendering behind DH clouds
    if (dhVertexWorldPos.y > uMaxLevelHeight)
    {
        fragColor = vec4(combinedMcDhColor.rgb, 0.0);
    }
    // a fragment depth of "1" means the fragment wasn't drawn to,
    // we only want to fade vanilla rendered objects, not to the sky or LODs
    else if (mcFragmentDepth < 1.0) 
    {
        // fade based on distance from the camera
        vec3 mcVertexWorldPos = calcViewPosition(mcFragmentDepth, uMcInvMvmProj);
        float mcFragmentDistance = length(mcVertexWorldPos.xzy);
        
        
        // Smoothly transition between combinedMcDhColor and uDhColorTexture
        // as the depth increases from the camera
        float fadeStep = smoothstep(uStartFadeBlockDistance, uEndFadeBlockDistance, mcFragmentDistance);
        fragColor = mix(combinedMcDhColor, dhColor, fadeStep);
        fragColor.a = 1.0; // TODO is setting the alpha needed?
    }
    else
    {
        fragColor = vec4(combinedMcDhColor.rgb, 0.0);
    }
}


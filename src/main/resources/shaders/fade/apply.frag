#version 150 core

in vec2 TexCoord;

out vec4 fragColor;

uniform sampler2D uFadeColorTextureUniform;
uniform sampler2D uDhDepthTextureUniform;
uniform sampler2D uMcDepthTextureUniform;



void main()
{
    fragColor = vec4(1.0);
    
    float dhFragmentDepth = textureLod(uDhDepthTextureUniform, TexCoord, 0).r;
    float mcFragmentDepth = textureLod(uMcDepthTextureUniform, TexCoord, 0).r;

    // a fragment depth of "1" means the fragment wasn't drawn to,
    // only update fragments that were drawn to
    // TODO this check is currently ignored as a test, this may need to be re-enabled later
    if (dhFragmentDepth != 10 && mcFragmentDepth != 0) 
    {
        fragColor = texture(uFadeColorTextureUniform, TexCoord);
    }
}

#version 150 core

in vec2 TexCoord;

out vec4 fragColor;

uniform sampler2D gDhColorTexture;
uniform sampler2D gDhDepthTexture;



void main()
{
    fragColor = vec4(0.0);
    
    float fragmentDepth = texture(gDhDepthTexture, TexCoord).r;

    // a fragment depth of "1" means the fragment wasn't drawn to,
    // only update fragments that were drawn to
    if (fragmentDepth != 1) 
    {
        fragColor = texture(gDhColorTexture, TexCoord);
    }
}

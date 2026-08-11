package com.gtnewhorizons.angelica.sdlgpu.shader;

public final class SpirvTestShaders {

    private SpirvTestShaders() {}

    public static final String VERTEX_GLSL = """
        #version 460 core
        layout(set = 0, binding = 0) uniform sampler2D u_Texture;
        layout(set = 1, binding = 0) uniform UBO { mat4 u_Mvp; } ubo;
        layout(location = 0) in vec3 a_Position;
        layout(location = 1) in vec2 a_TexCoord;
        layout(location = 0) out vec2 v_TexCoord;
        void main() {
            gl_Position = ubo.u_Mvp * vec4(a_Position, 1.0);
            v_TexCoord = a_TexCoord + texture(u_Texture, a_TexCoord).xy * 0.0;
        }
        """;

    public static final String FRAGMENT_GLSL = """
        #version 460 core
        layout(set = 2, binding = 0) uniform sampler2D u_TexA;
        layout(set = 2, binding = 1) uniform sampler2D u_TexB;
        layout(set = 3, binding = 0) uniform UBO { vec4 u_Tint; } ubo;
        layout(location = 0) in vec2 v_TexCoord;
        layout(location = 0) out vec4 fragColor;
        void main() {
            fragColor = (texture(u_TexA, v_TexCoord) + texture(u_TexB, v_TexCoord)) * ubo.u_Tint;
        }
        """;

    public static final String HIZ_INIT_GLSL = """
        #version 460 core
        layout(local_size_x = 8, local_size_y = 8) in;
        layout(binding = 0) uniform sampler2D u_DepthSrc;
        layout(r32f, binding = 0) uniform writeonly image2D u_HizMip0;
        layout(std140, binding = 0) uniform Init { ivec2 srcSize; ivec2 dstSize; float zNear; float zFar; int projMode; } init;
        void main() {
            ivec2 c = ivec2(gl_GlobalInvocationID.xy);
            if (c.x >= init.dstSize.x || c.y >= init.dstSize.y) return;
            float d = texelFetch(u_DepthSrc, c, 0).r;
            imageStore(u_HizMip0, c, vec4(d * float(init.projMode)));
        }
        """;

    public static final String LDS_MULTI_MIP_GLSL = """
        #version 460 core
        layout(local_size_x = 64) in;
        layout(r32f, binding = 0) uniform image2D u_Src;
        layout(r32f, binding = 1) uniform writeonly image2D u_Dst1;
        layout(r32f, binding = 2) uniform writeonly image2D u_Dst2;
        layout(std140, binding = 0) uniform Down { ivec2 baseSize; int numMips; } down;
        shared float lds[64];
        void main() {
            uint t = gl_LocalInvocationID.x;
            ivec2 c = ivec2(gl_GlobalInvocationID.x, 0);
            float m = max(imageLoad(u_Src, c * 2).r, imageLoad(u_Src, c * 2 + ivec2(1, 0)).r);
            imageStore(u_Dst1, c, vec4(m));
            lds[t] = m;
            memoryBarrierShared();
            barrier();
            if (t % 2u == 0u && down.numMips > 1) {
                float m2 = max(lds[t], lds[t + 1u]);
                imageStore(u_Dst2, ivec2(int(t) / 2, 0), vec4(m2));
            }
        }
        """;
}

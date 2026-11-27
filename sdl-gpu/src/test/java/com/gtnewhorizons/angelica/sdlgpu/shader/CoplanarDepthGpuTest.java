package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.sdlgpu.GpuTestPrograms;
import com.gtnewhorizons.angelica.sdlgpu.RealDeviceExtension;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineCache;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineStore;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.sdl.SDL_GPUColorTargetInfo;
import org.lwjgl.sdl.SDL_GPUDepthStencilTargetInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLGPU.*;

@ExtendWith(RealDeviceExtension.class)
@Tag("sdl-gpu-device")
class CoplanarDepthGpuTest {

    private static final int SIZE = 256;
    private static final int GRID = 32;
    private static final int UBO_BYTES = 4 * 64 + 2 * 16;
    private static final double CAM_X = 1475.7192986248613, CAM_Y = 70.62, CAM_Z = -935.8161276616005;

    private static final String HEADER = """
        #version 460 core
        layout(std140) uniform Mats { mat4 proj; mat4 view; mat4 viewInv; mat4 mv; vec4 params; vec4 region; };
        vec3 _vert_position;
        void _vert_init() {
            const ivec2 corner[6] = ivec2[](ivec2(0, 0), ivec2(1, 0), ivec2(1, 1), ivec2(0, 0), ivec2(1, 1), ivec2(0, 1));
            int g = int(params.w);
            int q = gl_VertexID / 6;
            int c = gl_VertexID % 6;
            vec2 uv = vec2(float(q % g + corner[c].x), float(q / g + corner[c].y)) / params.w;
            vec3 packed = floor(vec3(uv.x * 16.0, 1.37 + uv.y * 3.1, 9.0 + uv.y * 7.0) * 2048.0);
            _vert_position = packed * (1.0 / 2048.0) + vec3(params.z);
        }
        vec4 _celeritas_getVertexPosition() { return vec4(_vert_position + region.xyz, 1.0); }
        """;

    private static final String TERRAIN_VERTEX = HEADER + """
        out vec3 vWorld;
        void main() {
            _vert_init();
            vec4 position = viewInv * (mv * _celeritas_getVertexPosition());
            vWorld = position.xyz;
            gl_Position = proj * (view * position);
        }
        """;

    private static final String WATER_VERTEX = HEADER + """
        out vec3 vWorld;
        out vec3 vView;
        out float vWave;
        out vec3 vNormal;
        void main() {
            _vert_init();
            vec4 position = viewInv * (mv * _celeritas_getVertexPosition());
            vWorld = position.xyz;
            float wave = sin(position.x * 0.7 + params.y) * cos(position.z * 0.3 + params.y) * params.x;
            if (params.x > 0.0) position.y += wave;
            vWave = wave;
            vView = (view * position).xyz;
            vNormal = normalize(mat3(view) * vec3(0.0, 1.0, 0.0) + vec3(wave));
            gl_Position = proj * (view * position);
        }
        """;

    private static SdlTestRig rig;
    private static ShaderManager sm;
    private static PipelineStore store;
    private static long colorTexture;
    private static int colorFormat;
    private static long depthTexture;
    private static int depthFormat;
    private static long terrainPipeline;
    private static long waterPipeline;

    @BeforeAll
    static void setUp() {
        rig = RealDeviceExtension.rig();
        sm = new ShaderManager(rig.device);
        store = new PipelineStore(rig.device);

        final RealDeviceExtension.Target color = RealDeviceExtension.colorTarget(9600, SIZE, SIZE);
        colorTexture = color.texture();
        colorFormat = color.sdlFormat();
        PipelineCache.setSwapchainFormats(new int[]{ colorFormat });

        final RealDeviceExtension.Target depth = RealDeviceExtension.depthStencilTarget(9601, SIZE, SIZE);
        depthTexture = depth.texture();
        depthFormat = depth.sdlFormat();

        terrainPipeline = createPipeline(TERRAIN_VERTEX, GpuTestPrograms.solidFragment("1.0, 0.0, 0.0"), SDL_GPU_COMPAREOP_LESS, true);
        waterPipeline = createPipeline(WATER_VERTEX, GpuTestPrograms.solidFragment("0.0, 1.0, 0.0"), SDL_GPU_COMPAREOP_EQUAL, false);
    }

    @AfterAll
    static void tearDown() {
        if (store != null) store.shutdown();
        if (sm != null) sm.shutdown();
    }

    private static long createPipeline(String vertexSource, String fragmentSource, int compareOp, boolean depthWrite) {
        final int prog = GpuTestPrograms.link(sm, vertexSource, fragmentSource);
        assertEquals(1, sm.getProgram(prog).vertexResources.numUBOs(), "the matrix block must be the only vertex UBO, pushed at slot 0");
        return GpuTestPrograms.pipeline(store, sm, prog, colorFormat, depthFormat, cache -> {
            cache.depthTestEnabled = true;
            cache.depthWriteEnabled = depthWrite;
            cache.depthCompareOp = compareOp;
        });
    }

    private static void writeMats(ByteBuffer ubo) {
        final Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), 1.0f, 0.05f, 512.0f);
        final Matrix4f view = new Matrix4f().rotationX(0.35f).rotateY((float) Math.PI - 0.2f);
        proj.get(0, ubo);
        view.get(64, ubo);
        view.invert(new Matrix4f()).get(128, ubo);
        view.get(192, ubo);
        ubo.putFloat(256, 0.0f).putFloat(260, 0.25f).putFloat(264, -0.37f).putFloat(268, GRID);
        ubo.putFloat(272, (float) (1472.0 - CAM_X)).putFloat(276, (float) (64.0 - CAM_Y));
    }

    private static void drawAndRead(ByteBuffer ubo, ByteBuffer pixels) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final long cb = SDL_AcquireGPUCommandBuffer(rig.device.getDevice());
            assertTrue(cb != 0, "no command buffer");
            final SDL_GPUColorTargetInfo.Buffer color = SDL_GPUColorTargetInfo.calloc(1, stack);
            color.get(0).texture(colorTexture).load_op(SDL_GPU_LOADOP_CLEAR).store_op(SDL_GPU_STOREOP_STORE);
            final SDL_GPUDepthStencilTargetInfo ds = SDL_GPUDepthStencilTargetInfo.calloc(stack)
                .texture(depthTexture)
                .load_op(SDL_GPU_LOADOP_CLEAR).store_op(SDL_GPU_STOREOP_DONT_CARE).clear_depth(1.0f)
                .stencil_load_op(SDL_GPU_LOADOP_CLEAR).stencil_store_op(SDL_GPU_STOREOP_DONT_CARE);
            SDL_PushGPUVertexUniformData(cb, 0, ubo);
            final long pass = SDL_BeginGPURenderPass(cb, color, ds);
            assertTrue(pass != 0, "no render pass");
            SDL_BindGPUGraphicsPipeline(pass, terrainPipeline);
            SDL_DrawGPUPrimitives(pass, GRID * GRID * 6, 1, 0, 0);
            SDL_BindGPUGraphicsPipeline(pass, waterPipeline);
            SDL_DrawGPUPrimitives(pass, GRID * GRID * 6, 1, 0, 0);
            SDL_EndGPURenderPass(pass);
            pixels.clear();
            rig.resourceManager.downloadFromTexture(cb, colorTexture, 0, 0, SIZE, SIZE, 0, pixels);
        }
    }

    @Test
    void secondProgramMatchesFirstProgramDepthOnCoplanarGeometry() throws IOException {
        final ByteBuffer ubo = MemoryUtil.memCalloc(UBO_BYTES);
        final ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
        try {
            int mismatched = 0, covered = 0;
            Path firstBad = null;
            writeMats(ubo);
            for (double chunkZ : new double[]{ 0, 16, 64, 256 }) {
                ubo.putFloat(280, (float) (-944.0 + chunkZ - CAM_Z));
                drawAndRead(ubo, pixels);
                int red = 0;
                for (int i = 0; i < SIZE * SIZE * 4; i += 4) {
                    if ((pixels.get(i) & 0xFF) > 128) red++;
                    else if ((pixels.get(i + 1) & 0xFF) > 128) covered++;
                }
                if (red > 0 && firstBad == null) firstBad = GpuTestPrograms.dumpPng(pixels, SIZE, SIZE, "coplanar_depth_dz" + (int) chunkZ);
                mismatched += red;
                covered += red;
            }
            assertTrue(covered > 4 * SIZE, "the plane must cover the target, covered=" + covered);
            assertEquals(0, mismatched, "depth EQUAL failed on " + mismatched + "/" + covered + " pixels drawn by both programs; first failing view: " + firstBad);
        } finally {
            MemoryUtil.memFree(ubo);
            MemoryUtil.memFree(pixels);
        }
    }
}

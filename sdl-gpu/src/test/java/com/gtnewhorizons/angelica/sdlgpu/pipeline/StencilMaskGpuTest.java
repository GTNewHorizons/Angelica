package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.resource.PixelOps;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDL_GPUColorTargetInfo;
import org.lwjgl.sdl.SDL_GPUDepthStencilTargetInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLGPU.*;

class StencilMaskGpuTest {

    private static SdlTestRig rig;
    private static ShaderManager sm;

    private static final int SIZE = 64;
    private static final int MASK = 32;

    private static PipelineStore store;
    private static long colorTexture;
    private static int colorFormat;
    private static long depthTexture;
    private static int depthFormat;
    private static long maskPipeline;
    private static long fillPipeline;

    private static final String VERTEX = String.join("\n",
        "#version 460 core",
        "void main() {",
        "    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);",
        "    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);",
        "}",
        "");

    private static final String MASK_FRAGMENT = String.join("\n",
        "#version 460 core",
        "layout(location = 0) out vec4 fragColor;",
        "void main() {",
        "    if (gl_FragCoord.x >= " + MASK + ".0 || gl_FragCoord.y >= " + MASK + ".0) discard;",
        "    fragColor = vec4(0.0, 0.0, 1.0, 1.0);",
        "}",
        "");

    private static final String FILL_FRAGMENT = String.join("\n",
        "#version 460 core",
        "layout(location = 0) out vec4 fragColor;",
        "void main() {",
        "    fragColor = vec4(1.0, 0.0, 0.0, 1.0);",
        "}",
        "");

    @BeforeAll
    static void setUp() throws Exception {
        rig = SdlTestRig.acquireRealDevice();
        rig.resourceManager.cachePreferredDepthFormats();
        sm = new ShaderManager(rig.device);
        VAOManager.init(0);
        store = new PipelineStore(rig.device);

        colorTexture = rig.resourceManager.createTexture(9500, GL11.GL_TEXTURE_2D, GL11.GL_RGBA8, SIZE, SIZE, 1, 1);
        assertTrue(colorTexture != 0, "color target creation failed");
        colorFormat = rig.resourceManager.getTextureMeta(9500).sdlFormat();
        PipelineCache.setSwapchainFormats(new int[]{ colorFormat });

        rig.resourceManager.createTexture(9501, GL11.GL_TEXTURE_2D, GL30.GL_DEPTH24_STENCIL8, SIZE, SIZE, 1, 1);
        depthTexture = rig.resourceManager.ensureTextureUsage(9501, SDL_GPU_TEXTUREUSAGE_DEPTH_STENCIL_TARGET);
        assertTrue(depthTexture != 0, "depth+stencil target creation failed");
        depthFormat = rig.resourceManager.getTextureMeta(9501).sdlFormat();
        assertEquals(8, PixelOps.stencilBits(depthFormat), "test needs a packed depth+stencil format");

        maskPipeline = createPipeline(MASK_FRAGMENT, true);
        fillPipeline = createPipeline(FILL_FRAGMENT, false);
    }

    @AfterAll
    static void tearDown() {
        SdlTestRig.releaseRealDevice();
    }

    private static long createPipeline(String fragmentSource, boolean writesMask) {
        final int vs = sm.createShader(GL20.GL_VERTEX_SHADER);
        sm.shaderSource(vs, VERTEX);
        sm.compileShader(vs);
        final int fs = sm.createShader(GL20.GL_FRAGMENT_SHADER);
        sm.shaderSource(fs, fragmentSource);
        sm.compileShader(fs);
        final int prog = sm.createProgram();
        sm.attachShader(prog, vs);
        sm.attachShader(prog, fs);
        sm.linkProgram(prog);
        final ShaderManager.ProgramObject po = sm.getProgram(prog);
        assertTrue(po != null && po.linked, "link failed: " + (po == null ? "no program" : po.infoLog));

        po.sdlVertexShader = sm.createSDLShader(po.vertexSpirv, SDL_GPU_SHADERSTAGE_VERTEX, po.vertexResources.numSamplers(), po.vertexResources.numUBOs(), po.vertexResources.numStorageBuffers(), po.vertexResources.numStorageTextures());
        po.sdlFragmentShader = sm.createSDLShader(po.fragmentSpirv, SDL_GPU_SHADERSTAGE_FRAGMENT, po.fragmentResources.numSamplers(), po.fragmentResources.numUBOs(), po.fragmentResources.numStorageBuffers(), po.fragmentResources.numStorageTextures());
        assertTrue(po.sdlVertexShader != 0 && po.sdlFragmentShader != 0, "SDL shader creation failed");

        final ContextState cs = new ContextState();
        final PipelineCache cache = cs.pipeline;
        cache.vertexShader = po.sdlVertexShader;
        cache.fragmentShader = po.sdlFragmentShader;
        cache.programId = prog;
        cache.maxFragOutputLocation = po.maxFragOutputLocation;
        cache.shaderInputMask = po.vertexInputMask;
        cache.shaderInputVecSize = po.vertexInputVecSize;
        cache.shaderInputBaseType = po.vertexInputBaseType;
        cache.shaderInputName = po.vertexInputName;
        cache.setColorTargetFormats(new int[]{ colorFormat });
        cache.setDrawBuffers(new int[]{ 0 });
        cache.hasDepthTarget = true;
        cache.depthTargetFormat = depthFormat;
        cache.depthTestEnabled = false;
        cache.depthWriteEnabled = false;
        cache.stencilTestEnabled = true;
        cache.stencilCompareMask = 0xFF;
        cache.stencilWriteMask = writesMask ? 0xFF : 0x00;
        final int compareOp = writesMask ? SDL_GPU_COMPAREOP_ALWAYS : SDL_GPU_COMPAREOP_EQUAL;
        final int passOp = writesMask ? SDL_GPU_STENCILOP_REPLACE : SDL_GPU_STENCILOP_KEEP;
        cache.stencilFrontCompareOp = compareOp;
        cache.stencilBackCompareOp = compareOp;
        cache.stencilFrontPassOp = passOp;
        cache.stencilBackPassOp = passOp;
        cache.markShaderDirty();
        cache.markOutputDirty();

        assertTrue(cache.effectiveStencilTestEnabled(), "stencil must survive the depth-format gate");
        final long pipeline = cache.getOrCreatePipeline(store, cs);
        assertTrue(pipeline != 0, "pipeline creation failed: " + SDLError.SDL_GetError());
        return pipeline;
    }

    private static void render(int secondPassStencilLoad) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final long cb = SDL_AcquireGPUCommandBuffer(rig.device.getDevice());
            assertTrue(cb != 0, "no command buffer");

            final SDL_GPUColorTargetInfo.Buffer color = SDL_GPUColorTargetInfo.calloc(1, stack);
            color.get(0).texture(colorTexture).load_op(SDL_GPU_LOADOP_CLEAR).store_op(SDL_GPU_STOREOP_STORE);
            final SDL_GPUDepthStencilTargetInfo ds = SDL_GPUDepthStencilTargetInfo.calloc(stack)
                .texture(depthTexture)
                .load_op(SDL_GPU_LOADOP_CLEAR).store_op(SDL_GPU_STOREOP_STORE).clear_depth(1.0f)
                .stencil_load_op(SDL_GPU_LOADOP_CLEAR).stencil_store_op(SDL_GPU_STOREOP_STORE).clear_stencil((byte) 0);

            long pass = SDL_BeginGPURenderPass(cb, color, ds);
            assertTrue(pass != 0, "no render pass");
            SDL_SetGPUStencilReference(pass, (byte) 1);
            SDL_BindGPUGraphicsPipeline(pass, maskPipeline);
            SDL_DrawGPUPrimitives(pass, 3, 1, 0, 0);
            if (secondPassStencilLoad < 0) {
                SDL_BindGPUGraphicsPipeline(pass, fillPipeline);
                SDL_DrawGPUPrimitives(pass, 3, 1, 0, 0);
            }
            SDL_EndGPURenderPass(pass);

            if (secondPassStencilLoad >= 0) {
                color.get(0).load_op(SDL_GPU_LOADOP_LOAD);
                ds.load_op(SDL_GPU_LOADOP_LOAD).stencil_load_op(secondPassStencilLoad).clear_stencil((byte) 0);
                pass = SDL_BeginGPURenderPass(cb, color, ds);
                assertTrue(pass != 0, "no second render pass");
                SDL_SetGPUStencilReference(pass, (byte) 1);
                SDL_BindGPUGraphicsPipeline(pass, fillPipeline);
                SDL_DrawGPUPrimitives(pass, 3, 1, 0, 0);
                SDL_EndGPURenderPass(pass);
            }

            assertTrue(SDL_SubmitGPUCommandBuffer(cb), "draw submit failed");
            SDL_WaitForGPUIdle(rig.device.getDevice());
        }
    }

    private static int[] readRedPixels() {
        final ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
        try {
            final long cb = SDL_AcquireGPUCommandBuffer(rig.device.getDevice());
            assertTrue(cb != 0, "no readback command buffer");
            rig.resourceManager.downloadFromTexture(cb, colorTexture, 0, 0, SIZE, SIZE, 0, pixels);
            int count = 0, maxX = -1, minX = SIZE;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    final int i = (y * SIZE + x) * 4;
                    final boolean red = (pixels.get(i) & 0xFF) > 200 && (pixels.get(i + 1) & 0xFF) < 64;
                    if (red) {
                        count++;
                        if (x > maxX) maxX = x;
                        if (x < minX) minX = x;
                    }
                }
            }
            return new int[]{ count, minX, maxX };
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    @Test
    void fillIsClippedToTheStencilMask() {
        render(-1);
        final int[] r = readRedPixels();
        assertEquals(MASK * MASK, r[0], "fill must cover exactly the masked region, not the whole target");
        assertEquals(0, r[1], "masked region starts at x=0");
        assertEquals(MASK - 1, r[2], "masked region must not bleed past the mask");
    }

    @Test
    void maskSurvivesAPassBoundaryWhenStencilIsLoaded() {
        render(SDL_GPU_LOADOP_LOAD);
        final int[] r = readRedPixels();
        assertEquals(MASK * MASK, r[0], "a stored stencil buffer must still clip the next pass");
        assertEquals(MASK - 1, r[2]);
    }

    @Test
    void clearingStencilWipesTheMask() {
        render(SDL_GPU_LOADOP_CLEAR);
        assertEquals(0, readRedPixels()[0], "a cleared stencil buffer must clip everything");
    }
}

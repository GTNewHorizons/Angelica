package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.sdl.SDL_GPUColorTargetDescription;
import org.lwjgl.sdl.SDL_GPUColorTargetInfo;
import org.lwjgl.sdl.SDL_GPUGraphicsPipelineCreateInfo;
import org.lwjgl.sdl.SDL_GPUGraphicsPipelineTargetInfo;
import org.lwjgl.sdl.SDL_GPUMultisampleState;
import org.lwjgl.sdl.SDL_GPURasterizerState;
import org.lwjgl.sdl.SDL_GPUTextureSamplerBinding;
import org.lwjgl.sdl.SDL_GPUVertexInputState;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.lwjgl.sdl.SDLGPU.*;

class FragmentStorageTextureReadGpuTest {

    private static SdlTestRig rig;
    private static ShaderManager sm;

    private static final int VOL = 8;
    private static final int TARGET = 64;

    @BeforeAll
    static void setUp() throws Exception {
        rig = SdlTestRig.acquireRealDevice();
        sm = new ShaderManager(rig.device);
    }

    @AfterAll
    static void tearDown() {
        SdlTestRig.releaseRealDevice();
    }

    private static final String VERTEX = String.join("\n",
        "#version 460 core",
        "void main() {",
        "    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);",
        "    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);",
        "}",
        "");

    private static String fragment(int samplerCount) {
        final StringBuilder sb = new StringBuilder();
        sb.append("#version 460 core\n");
        for (int i = 0; i < samplerCount; i++) sb.append("uniform sampler2D tex").append(i).append(";\n");
        sb.append("layout(r16ui) readonly uniform uimage3D voxel_img;\n");
        sb.append("layout(location = 0) out vec4 fragColor;\n");
        sb.append("void main() {\n");
        sb.append("    ivec2 p = ivec2(gl_FragCoord.xy);\n");
        sb.append("    ivec3 cell = ivec3(p.x % 8, p.y % 8, (p.x / 8 + p.y / 8) % 8);\n");
        sb.append("    uint v = imageLoad(voxel_img, cell).x;\n");
        sb.append("    vec4 acc = vec4(0.0);\n");
        for (int i = 0; i < samplerCount; i++) sb.append("    acc += texture(tex").append(i).append(", vec2(0.5)) * 0.0;\n");
        sb.append("    fragColor = vec4(float(v) / 255.0, float(v & 15u) / 15.0, float(v >> 4u) / 15.0, 1.0) + acc;\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static int patternValue(int x, int y, int z) {
        return (x == y) ? 255 - z * 16 : z * 32 + x * 2 + y;
    }

    private int createVoxelTexture() {
        final int glId = 9001;
        final long handle = rig.resourceManager.createTexture(glId, GL12.GL_TEXTURE_3D, GL30.GL_R16UI, VOL, VOL, VOL, 1);
        assertTrue(handle != 0, "voxel texture creation failed");
        final ByteBuffer data = MemoryUtil.memAlloc(VOL * VOL * VOL * 2);
        for (int z = 0; z < VOL; z++)
            for (int y = 0; y < VOL; y++)
                for (int x = 0; x < VOL; x++)
                    data.putShort((short) patternValue(x, y, z));
        data.flip();
        try {
            final long cb = SDL_AcquireGPUCommandBuffer(rig.device.getDevice());
            assertTrue(cb != 0, "no command buffer");
            final long cp = SDL_BeginGPUCopyPass(cb);
            assertTrue(cp != 0, "no copy pass");
            rig.resourceManager.uploadToTexture3D(cp, data, handle, 0, 0, 0, VOL, VOL, VOL, 0);
            SDL_EndGPUCopyPass(cp);
            assertTrue(SDL_SubmitGPUCommandBuffer(cb), "upload submit failed");
            SDL_WaitForGPUIdle(rig.device.getDevice());
        } finally {
            MemoryUtil.memFree(data);
        }
        return glId;
    }

    private int linkProgram(int samplerCount) {
        final int vs = sm.createShader(GL20.GL_VERTEX_SHADER);
        sm.shaderSource(vs, VERTEX);
        sm.compileShader(vs);
        final int fs = sm.createShader(GL20.GL_FRAGMENT_SHADER);
        sm.shaderSource(fs, fragment(samplerCount));
        sm.compileShader(fs);
        final int prog = sm.createProgram();
        sm.attachShader(prog, vs);
        sm.attachShader(prog, fs);
        sm.linkProgram(prog);
        final ShaderManager.ProgramObject po = sm.getProgram(prog);
        assertTrue(po != null && po.linked, "link failed: " + (po == null ? "no program" : po.infoLog));
        assertEquals(samplerCount, po.fragmentResources.numSamplers(), "production sampler count");
        assertEquals(1, po.fragmentResources.numStorageTextures(), "production storage texture count");
        return prog;
    }

    private void runCase(int samplerCount) throws IOException {
        final int voxelGlId = createVoxelTexture();
        final long voxelHandle = rig.resourceManager.getTextureHandle(voxelGlId);

        final int targetGlId = 9100 + samplerCount;
        final long targetHandle = rig.resourceManager.createTexture(targetGlId, GL11.GL_TEXTURE_2D, GL11.GL_RGBA8, TARGET, TARGET, 1, 1);
        assertTrue(targetHandle != 0, "target creation failed");
        final int targetFormat = rig.resourceManager.getTextureMeta(targetGlId).sdlFormat();

        final long[] dummyTex = new long[samplerCount];
        for (int i = 0; i < samplerCount; i++) {
            final int id = 9200 + samplerCount * 10 + i;
            dummyTex[i] = rig.resourceManager.createTexture(id, GL11.GL_TEXTURE_2D, GL11.GL_RGBA8, 1, 1, 1, 1);
            assertTrue(dummyTex[i] != 0, "dummy sampler texture creation failed");
        }

        final ShaderManager.ProgramObject po = sm.getProgram(linkProgram(samplerCount));
        System.out.println("[GpuReadTest] S=" + samplerCount
            + " VS=" + po.vertexResources + " FS=" + po.fragmentResources);

        final ShaderManager.ResourceCounts vr = po.vertexResources;
        po.sdlVertexShader = sm.createSDLShader(po.vertexSpirv, SDL_GPU_SHADERSTAGE_VERTEX, vr.numSamplers(), vr.numUBOs(), vr.numStorageBuffers(), vr.numStorageTextures());
        final ShaderManager.ResourceCounts fr = po.fragmentResources;
        po.sdlFragmentShader = sm.createSDLShader(po.fragmentSpirv, SDL_GPU_SHADERSTAGE_FRAGMENT, fr.numSamplers(), fr.numUBOs(), fr.numStorageBuffers(), fr.numStorageTextures());
        assertTrue(po.sdlVertexShader != 0, "SDL vertex shader creation failed");
        assertTrue(po.sdlFragmentShader != 0, "SDL fragment shader creation failed");

        final long pipeline;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final SDL_GPUColorTargetDescription.Buffer colorDesc = SDL_GPUColorTargetDescription.calloc(1, stack);
            colorDesc.get(0).format(targetFormat);
            final SDL_GPUGraphicsPipelineTargetInfo targetInfo = SDL_GPUGraphicsPipelineTargetInfo.calloc(stack)
                .num_color_targets(1)
                .color_target_descriptions(colorDesc);
            final SDL_GPUGraphicsPipelineCreateInfo ci = SDL_GPUGraphicsPipelineCreateInfo.calloc(stack)
                .vertex_shader(po.sdlVertexShader)
                .fragment_shader(po.sdlFragmentShader)
                .primitive_type(SDL_GPU_PRIMITIVETYPE_TRIANGLELIST)
                .rasterizer_state(SDL_GPURasterizerState.calloc(stack)
                    .cull_mode(SDL_GPU_CULLMODE_NONE)
                    .fill_mode(SDL_GPU_FILLMODE_FILL))
                .multisample_state(SDL_GPUMultisampleState.calloc(stack).sample_count(SDL_GPU_SAMPLECOUNT_1))
                .target_info(targetInfo)
                .vertex_input_state(SDL_GPUVertexInputState.calloc(stack));
            pipeline = SDL_CreateGPUGraphicsPipeline(rig.device.getDevice(), ci);
        }
        assertTrue(pipeline != 0, "pipeline creation failed");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final long cb = SDL_AcquireGPUCommandBuffer(rig.device.getDevice());
            assertTrue(cb != 0, "no command buffer");
            final SDL_GPUColorTargetInfo.Buffer target = SDL_GPUColorTargetInfo.calloc(1, stack);
            target.get(0).texture(targetHandle).load_op(SDL_GPU_LOADOP_CLEAR).store_op(SDL_GPU_STOREOP_STORE);
            final ByteBuffer zeroUbo = stack.calloc(256);
            for (int slot = 0; slot < po.vertexResources.numUBOs(); slot++) {
                SDL_PushGPUVertexUniformData(cb, slot, zeroUbo);
            }
            for (int slot = 0; slot < po.fragmentResources.numUBOs(); slot++) {
                SDL_PushGPUFragmentUniformData(cb, slot, zeroUbo);
            }
            final long pass = SDL_BeginGPURenderPass(cb, target, null);
            assertTrue(pass != 0, "no render pass");
            SDL_BindGPUGraphicsPipeline(pass, pipeline);
            if (samplerCount > 0) {
                final SDL_GPUTextureSamplerBinding.Buffer samplers = SDL_GPUTextureSamplerBinding.calloc(samplerCount, stack);
                final long defaultSampler = rig.resourceManager.getOrCreateDefaultSampler();
                for (int i = 0; i < samplerCount; i++) {
                    samplers.get(i).texture(dummyTex[i]).sampler(defaultSampler);
                }
                SDL_BindGPUFragmentSamplers(pass, 0, samplers);
            }
            SDL_BindGPUFragmentStorageTextures(pass, 0, stack.pointers(voxelHandle));
            SDL_DrawGPUPrimitives(pass, 3, 1, 0, 0);
            SDL_EndGPURenderPass(pass);
            assertTrue(SDL_SubmitGPUCommandBuffer(cb), "draw submit failed");
            SDL_WaitForGPUIdle(rig.device.getDevice());
        }

        final ByteBuffer pixels = MemoryUtil.memAlloc(TARGET * TARGET * 4);
        try {
            final long cb = SDL_AcquireGPUCommandBuffer(rig.device.getDevice());
            assertTrue(cb != 0, "no readback command buffer");
            rig.resourceManager.downloadFromTexture(cb, targetHandle, 0, 0, TARGET, TARGET, 0, pixels);
            final Path png = dumpPng(pixels, samplerCount);
            // SDL's Y-flip means the readback may be row-inverted vs gl_FragCoord; either orientation
            // is a fixed convention, not this bug - accept whichever matches.
            final int direct = countMismatches(pixels, false, null);
            final StringBuilder firstBad = new StringBuilder();
            final int flipped = countMismatches(pixels, true, firstBad);
            final int mismatches = Math.min(direct, flipped);
            assertEquals(0, mismatches, "fragment imageLoad visualizer diverged on " + mismatches + "/" + (TARGET * TARGET) + " pixels (S=" + samplerCount + ", direct=" + direct + " flipped=" + flipped + "); " + firstBad + "; inspect " + png);
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    private static int countMismatches(ByteBuffer pixels, boolean flipY, StringBuilder firstBad) {
        int mismatches = 0;
        for (int py = 0; py < TARGET; py++) {
            final int fragY = flipY ? TARGET - 1 - py : py;
            for (int px = 0; px < TARGET; px++) {
                final int cellX = px % VOL, cellY = fragY % VOL, cellZ = (px / VOL + fragY / VOL) % VOL;
                final int expected = patternValue(cellX, cellY, cellZ);
                final int got = pixels.get((py * TARGET + px) * 4) & 0xFF;
                if (Math.abs(got - expected) > 1) {
                    if (mismatches == 0 && firstBad != null) {
                        firstBad.append("first mismatch at pixel (").append(px).append(',').append(py)
                            .append(") cell (").append(cellX).append(',').append(cellY).append(',').append(cellZ)
                            .append("): expected ").append(expected).append(" got ").append(got);
                    }
                    mismatches++;
                }
            }
        }
        return mismatches;
    }

    private static Path dumpPng(ByteBuffer rgba, int samplerCount) throws IOException {
        final Path out = Path.of("build", "test-artifacts");
        Files.createDirectories(out);
        final java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(TARGET, TARGET, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < TARGET; y++) {
            for (int x = 0; x < TARGET; x++) {
                final int i = (y * TARGET + x) * 4;
                img.setRGB(x, y, ((rgba.get(i) & 0xFF) << 16) | ((rgba.get(i + 1) & 0xFF) << 8) | (rgba.get(i + 2) & 0xFF));
            }
        }
        final Path file = out.resolve("storage_read_s" + samplerCount + ".png").toAbsolutePath();
        javax.imageio.ImageIO.write(img, "png", file.toFile());
        System.out.println("[GpuReadTest] wrote " + file);
        return file;
    }

    @Test void fragmentImageLoadReadsCorrectTexels_noSamplers() throws IOException { runCase(0); }
    @Test void fragmentImageLoadReadsCorrectTexels_twoSamplers() throws IOException { runCase(2); }
}

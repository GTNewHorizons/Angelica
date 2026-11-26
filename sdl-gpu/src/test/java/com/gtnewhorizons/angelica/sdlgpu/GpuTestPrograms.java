package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineCache;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineStore;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import org.lwjgl.opengl.GL20;
import org.lwjgl.sdl.SDLError;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_SHADERSTAGE_FRAGMENT;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_SHADERSTAGE_VERTEX;

public final class GpuTestPrograms {

    private GpuTestPrograms() {}

    public static int link(ShaderManager sm, String vertex, String fragment) {
        final int vs = sm.createShader(GL20.GL_VERTEX_SHADER);
        sm.shaderSource(vs, vertex);
        sm.compileShader(vs);
        final int fs = sm.createShader(GL20.GL_FRAGMENT_SHADER);
        sm.shaderSource(fs, fragment);
        sm.compileShader(fs);
        final int program = sm.createProgram();
        sm.attachShader(program, vs);
        sm.attachShader(program, fs);
        sm.linkProgram(program);
        final ShaderManager.ProgramObject po = sm.getProgram(program);
        assertTrue(po != null && po.linked, "link failed: " + (po == null ? "no program" : po.infoLog));

        final ShaderManager.ResourceCounts vr = po.vertexResources;
        final ShaderManager.ResourceCounts fr = po.fragmentResources;
        po.sdlVertexShader = sm.createSDLShader(po.vertexSpirv, SDL_GPU_SHADERSTAGE_VERTEX, vr.numSamplers(), vr.numUBOs(), vr.numStorageBuffers(), vr.numStorageTextures());
        po.sdlFragmentShader = sm.createSDLShader(po.fragmentSpirv, SDL_GPU_SHADERSTAGE_FRAGMENT, fr.numSamplers(), fr.numUBOs(), fr.numStorageBuffers(), fr.numStorageTextures());
        assertTrue(po.sdlVertexShader != 0 && po.sdlFragmentShader != 0, "SDL shader creation failed: " + SDLError.SDL_GetError());
        return program;
    }

    public static String solidFragment(String rgb) {
        return """
            #version 460 core
            layout(location = 0) out vec4 fragColor;
            void main() { fragColor = vec4(%s, 1.0); }
            """.formatted(rgb);
    }

    public static long pipeline(PipelineStore store, ShaderManager sm, int program, int colorFormat, int depthFormat, Consumer<PipelineCache> state) {
        final ShaderManager.ProgramObject po = sm.getProgram(program);
        final ContextState cs = new ContextState();
        final PipelineCache cache = cs.pipeline;
        cache.vertexShader = po.sdlVertexShader;
        cache.fragmentShader = po.sdlFragmentShader;
        cache.programId = program;
        cache.maxFragOutputLocation = po.maxFragOutputLocation;
        cache.setVertexInputs(po.vertexInputMask, po.vertexInputVecSize, po.vertexInputBaseType, po.vertexInputName, po.vertexInputSetId);
        cache.setColorTargetFormats(new int[]{ colorFormat });
        cache.setDrawBuffers(new int[]{ 0 });
        cache.hasDepthTarget = true;
        cache.depthTargetFormat = depthFormat;
        state.accept(cache);
        cache.markShaderDirty();
        cache.markOutputDirty();

        final long pipeline = cache.getOrCreatePipeline(store, cs);
        assertTrue(pipeline != 0, "pipeline creation failed: " + SDLError.SDL_GetError());
        return pipeline;
    }

    public static Path dumpPng(ByteBuffer rgba, int width, int height, String name) throws IOException {
        final Path out = Path.of("build", "test-artifacts");
        Files.createDirectories(out);
        final BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int i = (y * width + x) * 4;
                img.setRGB(x, y, ((rgba.get(i) & 0xFF) << 16) | ((rgba.get(i + 1) & 0xFF) << 8) | (rgba.get(i + 2) & 0xFF));
            }
        }
        final Path file = out.resolve(name + ".png").toAbsolutePath();
        ImageIO.write(img, "png", file.toFile());
        return file;
    }
}

package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.CompatShaderTransformer;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import lombok.Getter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.KHRDebug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

/**
 * A compiled and linked FFP emulation program (vertex + fragment + optional geometry shaders). Owns the GL program handle and caches uniform locations.
 */
public class Program {

    private static final AtomicInteger PROGRAM_COUNTER = new AtomicInteger();

    @Getter private final int programId;
    @Getter private final VertexKey vertexKey;
    @Getter private final FragmentKey fragmentKey;

    public final int[] locSampler = { -1, -1, -1, -1 };

    Program(int programId, VertexKey vertexKey, FragmentKey fragmentKey) {
        this.programId = programId;
        this.vertexKey = vertexKey;
        this.fragmentKey = fragmentKey;
        for (int i = 0; i < 4; i++) {
            locSampler[i] = RENDER_BACKEND.getUniformLocation(programId, "u_Sampler" + i);
        }
    }

    public void destroy() {
        GLStateManager.glDeleteProgram(programId);
    }

    /**
     * Compile vertex + fragment + optional geometry shaders, link into a program, and return the FFPProgram.
     */
    static Program create(VertexKey vk, FragmentKey fk, String vertSrc, String fragSrc, String geomSrc) {
        final RenderBackend backend = RENDER_BACKEND;
        final int id = PROGRAM_COUNTER.getAndIncrement();
        final String vkHex = Long.toHexString(vk.pack());
        final boolean hasGeom = geomSrc != null;

        int shaderCount = 0;
        final int[] shaders = new int[5]; // vertex, fragment, geometry, tess control, tess evaluation
        int program = 0;
        try {
            shaders[shaderCount++] = compileShader(GL20.GL_VERTEX_SHADER, vertSrc, "ffp_v_" + vkHex);
            shaders[shaderCount++] = compileShader(GL20.GL_FRAGMENT_SHADER, fragSrc, "ffp_f_" + id);
            if (geomSrc != null) {
                shaders[shaderCount++] = compileShader(GL32.GL_GEOMETRY_SHADER, geomSrc, "ffp_g_" + id);
            }

            program = backend.createProgram();
            for (int i = 0; i < shaderCount; i++) backend.attachShader(program, shaders[i]);
            backend.linkProgram(program);

            final String log = backend.getProgramInfoLog(program, backend.getProgrami(program, GL20.GL_INFO_LOG_LENGTH));
            if (!log.isEmpty()) {
                GLStateManager.LOGGER.warn("FFP program link log (vk=0x{}, fk={}): {}", vkHex, fk, log);
            }

            if (backend.getProgrami(program, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) {
                throw new RuntimeException("FFP shader link failed (vk=0x" + vkHex + ", fk=" + fk + "): " + log);
            }

            final String debugName = "FFP(v=0x" + vkHex + ",f=" + id + (hasGeom ? ",g" : "") + ")";
            GLDebug.nameObject(KHRDebug.GL_PROGRAM, program, debugName);

            // Detach and delete individual shaders - they're linked into the program
            for (int i = 0; i < shaderCount; i++) {
                backend.detachShader(program, shaders[i]);
                backend.deleteShader(shaders[i]);
            }
            shaderCount = 0;

            final Program ffpProgram = new Program(program, vk, fk);

            final int blockIndex = backend.getUniformBlockIndex(program, FFPUniformBlock.BLOCK_NAME);
            if (blockIndex != GL31.GL_INVALID_INDEX) {
                backend.uniformBlockBinding(program, blockIndex, FFPUniformBlock.BINDING_POINT);
            }

            final int previousProgram = GLStateManager.getActiveProgram();
            backend.useProgram(program);
            for (int i = 0; i < 4; i++) {
                if (ffpProgram.locSampler[i] != -1) backend.uniform1i(ffpProgram.locSampler[i], i);
            }
            backend.useProgram(previousProgram);

            return ffpProgram;
        } catch (RuntimeException e) {
            if (program != 0) backend.deleteProgram(program);
            for (int i = 0; i < shaderCount; i++) backend.deleteShader(shaders[i]);
            throw e;
        }
    }

    private static int compileShader(int type, String src, String name) {
        final RenderBackend backend = RENDER_BACKEND;
        final int shader = backend.createShader(type);
        if (RenderSystem.isGLES()) {
            src = CompatShaderTransformer.toGLES(src, type, type == GL20.GL_FRAGMENT_SHADER);
            dumpTranslatedFFPShader(type, src, name);
        }
        backend.shaderSource(shader, src);
        backend.compileShader(shader);

        final String typeName = switch (type) {
            case GL20.GL_VERTEX_SHADER -> "vertex";
            case GL20.GL_FRAGMENT_SHADER -> "fragment";
            case GL32.GL_GEOMETRY_SHADER -> "geometry";
            case GL40.GL_TESS_CONTROL_SHADER -> "tess_control";
            case GL40.GL_TESS_EVALUATION_SHADER -> "tess_evaluation";
            case GL43.GL_COMPUTE_SHADER -> "compute";
            default -> "unknown(0x" + Integer.toHexString(type) + ")";
        };
        GLDebug.nameObject(KHRDebug.GL_SHADER, shader, name + "(" + typeName + ")");

        final String log = backend.getShaderInfoLog(shader, backend.getShaderi(shader, GL20.GL_INFO_LOG_LENGTH));
        if (!log.isEmpty()) {
            GLStateManager.LOGGER.warn("FFP {} shader compilation log for {}: {}", typeName, name, log);
        }

        final int result = backend.getShaderi(shader, GL20.GL_COMPILE_STATUS);
        if (result != GL11.GL_TRUE) {
            GLStateManager.LOGGER.error("FFP {} shader source:\n{}", typeName, src);
            backend.deleteShader(shader);
            throw new RuntimeException("FFP " + typeName + " shader compilation failed for " + name + ": " + log);
        }

        return shader;
    }

    // Post-translation GLSL ES dump, paired with ShaderCache's pre-translation 330 core dump.
    private static void dumpTranslatedFFPShader(int type, String translatedSrc, String name) {
        final Path dir = SystemProperties.shaderDumpDir("ffp");
        if (dir == null) return;
        final String suffix = switch (type) {
            case GL20.GL_VERTEX_SHADER -> ".vert.glsl";
            case GL20.GL_FRAGMENT_SHADER -> ".frag.glsl";
            case GL32.GL_GEOMETRY_SHADER -> ".geom.glsl";
            default -> ".glsl";
        };
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name + ".gles" + suffix), translatedSrc);
        } catch (IOException e) {
            GLStateManager.LOGGER.warn("Failed to dump translated FFP shader: {}", e.getMessage());
        }
    }
}

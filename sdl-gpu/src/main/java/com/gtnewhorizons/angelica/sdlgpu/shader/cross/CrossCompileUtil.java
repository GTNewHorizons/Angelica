package com.gtnewhorizons.angelica.sdlgpu.shader.cross;

import org.lwjgl.system.MemoryUtil;
import com.gtnewhorizons.angelica.config.SystemProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.spvc.Spv;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.util.spvc.SpvcReflectedResource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

public final class CrossCompileUtil {

    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    public static final AtomicInteger SHADER_DUMP_COUNTER = new AtomicInteger();

    private CrossCompileUtil() {}

    public static String stageSuffix(int glShaderType) {
        if (glShaderType == GL20.GL_VERTEX_SHADER) return "vert";
        if (glShaderType == GL20.GL_FRAGMENT_SHADER) return "frag";
        if (glShaderType == GL32.GL_GEOMETRY_SHADER) return "geom";
        if (glShaderType == GL43.GL_COMPUTE_SHADER) return "comp";
        return "unknown";
    }

    public static void dumpSpirv(ByteBuffer spirv, int id, int glShaderType) {
        final Path dir = SystemProperties.shaderDumpDir("cross");
        if (dir == null) return;
        try {
            Files.createDirectories(dir);
            final ByteBuffer dup = spirv.duplicate();
            final byte[] bytes = new byte[dup.remaining()];
            dup.get(bytes);
            Files.write(dir.resolve(id + "." + stageSuffix(glShaderType) + ".spv"), bytes);
        } catch (IOException e) {
            LOG.warn("Failed to dump SPIR-V #{}: {}", id, e.getMessage());
        }
    }

    public static void dumpText(String src, int id, int glShaderType, String ext) {
        final Path dir = SystemProperties.shaderDumpDir("cross");
        if (dir == null) return;
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(id + "." + stageSuffix(glShaderType) + "." + ext), src, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Failed to dump {} #{}: {}", ext, id, e.getMessage());
        }
    }

    public static void dumpBytes(ByteBuffer data, int id, int glShaderType, String ext) {
        final Path dir = SystemProperties.shaderDumpDir("cross");
        if (dir == null) return;
        try {
            Files.createDirectories(dir);
            final ByteBuffer dup = data.duplicate();
            final byte[] bytes = new byte[dup.remaining()];
            dup.get(bytes);
            Files.write(dir.resolve(id + "." + stageSuffix(glShaderType) + "." + ext), bytes);
        } catch (IOException e) {
            LOG.warn("Failed to dump {} #{}: {}", ext, id, e.getMessage());
        }
    }

    public static int countResources(long resources, int resourceType, MemoryStack stack) {
        final PointerBuffer pList = stack.pointers(0);
        final PointerBuffer pCount = stack.pointers(0);
        if (Spvc.spvc_resources_get_resource_list_for_type(resources, resourceType, pList, pCount) != Spvc.SPVC_SUCCESS) {
            return 0;
        }
        return (int) pCount.get(0);
    }

    public static RuntimeException spvcError(long ctx, String backend, String stage) {
        final String msg = Spvc.spvc_context_get_last_error_string(ctx);
        return new RuntimeException(backend + " cross-compile failed at " + stage + ": " + (msg != null ? msg : "(no message)"));
    }

    public static int[] countStorageImagesSplit(long resources, long compiler, MemoryStack stack) {
        int ro = countResources(resources, Spvc.SPVC_RESOURCE_TYPE_SEPARATE_IMAGE, stack);
        int rw = 0;
        final PointerBuffer pList = stack.pointers(0);
        final PointerBuffer pCount = stack.pointers(0);
        if (Spvc.spvc_resources_get_resource_list_for_type(resources, Spvc.SPVC_RESOURCE_TYPE_STORAGE_IMAGE, pList, pCount) != Spvc.SPVC_SUCCESS) {
            return new int[] { ro, rw };
        }
        final int count = (int) pCount.get(0);
        if (count == 0) return new int[] { ro, rw };
        final SpvcReflectedResource.Buffer list = SpvcReflectedResource.create(pList.get(0), count);
        for (int i = 0; i < count; i++) {
            if (Spvc.spvc_compiler_has_decoration(compiler, list.get(i).id(), Spv.SpvDecorationNonWritable)) ro++;
            else rw++;
        }
        return new int[] { ro, rw };
    }

    public static int[] countStorageBuffersSplit(long resources, long compiler, MemoryStack stack) {
        final PointerBuffer pList = stack.pointers(0);
        final PointerBuffer pCount = stack.pointers(0);
        if (Spvc.spvc_resources_get_resource_list_for_type(resources, Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER, pList, pCount) != Spvc.SPVC_SUCCESS) {
            return new int[] { 0, 0 };
        }
        final int count = (int) pCount.get(0);
        if (count == 0) return new int[] { 0, 0 };
        final SpvcReflectedResource.Buffer list = SpvcReflectedResource.create(pList.get(0), count);
        int ro = 0, rw = 0;
        for (int i = 0; i < count; i++) {
            if (isStorageBufferReadOnly(compiler, list.get(i).id(), stack)) ro++;
            else rw++;
        }
        return new int[] { ro, rw };
    }

    public static boolean isStorageBufferReadOnly(long compiler, int varId, MemoryStack stack) {
        final PointerBuffer pDecorations = stack.pointers(0);
        final PointerBuffer pNumDecorations = stack.pointers(0);
        if (Spvc.spvc_compiler_get_buffer_block_decorations(compiler, varId, pDecorations, pNumDecorations) != Spvc.SPVC_SUCCESS) {
            return false;
        }
        final long n = pNumDecorations.get(0);
        if (n <= 0) return false;
        final long ptr = pDecorations.get(0);
        final IntBuffer decs = MemoryUtil.memIntBuffer(ptr, (int) n);
        for (int i = 0; i < n; i++) {
            if (decs.get(i) == Spv.SpvDecorationNonWritable) return true;
        }
        return false;
    }

    public static int[] parseLocalSize(ByteBuffer spirv) {
        final IntBuffer w = spirv.asIntBuffer();
        final int wordCount = w.limit();
        int i = 5; // 5-word SPIR-V header (magic, version, generator, bound, schema)
        int lx = 1, ly = 1, lz = 1;
        boolean foundLiteral = false;
        boolean sawExecutionModeId = false;
        while (i < wordCount) {
            final int word0 = w.get(i);
            final int opcode = word0 & 0xFFFF;
            final int instrLen = (word0 >>> 16) & 0xFFFF;
            if (instrLen == 0) break;
            if (opcode == 16 && instrLen >= 6) { // SpvOpExecutionMode
                final int mode = w.get(i + 2);
                if (mode == 17) { // SpvExecutionModeLocalSize
                    lx = w.get(i + 3);
                    ly = w.get(i + 4);
                    lz = w.get(i + 5);
                    foundLiteral = true;
                }
            } else if (opcode == 331) { // SpvOpExecutionModeId
                sawExecutionModeId = true;
            }
            i += instrLen;
        }
        if (!foundLiteral && sawExecutionModeId) {
            LOG.warn("SPIR-V uses OpExecutionModeId (spec-constant LocalSizeId); falling back to {{1,1,1}} - not supported by parseLocalSize");
        }
        return new int[] { lx, ly, lz };
    }
}

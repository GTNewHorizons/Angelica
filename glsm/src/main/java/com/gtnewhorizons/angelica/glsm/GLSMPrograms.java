package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizon.gtnhlib.bytebuf.Pointer;
import com.gtnewhorizons.angelica.glsm.DisplayListManager.RecordMode;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.shader.ProgramBinaryCache;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.IntegerStateStack;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import lombok.Getter;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.KHRDebug;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.LOGGER;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.ctx;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.initConfig;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.isCachingEnabled;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.rejectExitCapture;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.requireContext;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.warnOnce;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public final class GLSMPrograms {

    private GLSMPrograms() {}

    public static long programSwitches;

    private static final String TEXTURE = "texture";
    private static final String TEXTURE_RENAMED = "gtexture";
    public static void setFfpInstancing(Instancing mode) {
        ctx().ffpInstancing = mode;
    }

    public static void glUseProgram(int program) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordUseProgram(program);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }

        final ShaderManager ffp = glCtx.ffp;
        if (program == 0 && ffp.isEnabled()) {
            final int prev = glCtx.activeProgram;
            final boolean changed = prev != 0;
            if (changed) glCtx.programGeneration++;
            glCtx.activeProgram = 0; // Track that FFP was requested
            if (changed && GLSMHooks.PROGRAM_CHANGE.hasListeners()) {
                GLSMHooks.programChangeEvent.previousProgram = prev;
                GLSMHooks.programChangeEvent.newProgram = 0;
                GLSMHooks.programChangeEvent.postBind = false;
                GLSMHooks.PROGRAM_CHANGE.post(GLSMHooks.programChangeEvent);
            }
            ffp.activate();
            if (changed && GLSMHooks.PROGRAM_CHANGE.hasListeners()) {
                GLSMHooks.programChangeEvent.previousProgram = prev;
                GLSMHooks.programChangeEvent.newProgram = 0;
                GLSMHooks.programChangeEvent.postBind = true;
                GLSMHooks.PROGRAM_CHANGE.post(GLSMHooks.programChangeEvent);
            }
            return;
        }

        // Non-zero program or FFP emulation not enabled
        if (program == 0) {
            warnOnce("useprogram-zero-ffp-disabled",
                "[FFP] glUseProgram(0) reached the driver because emulation is disabled");
        }
        if (ffp.isActive()) {
            ffp.deactivate();
        }

        final boolean caching = isCachingEnabled();
        if (!caching || program != glCtx.activeProgram) {
            if (Tracy.ENABLED) programSwitches++;
            glCtx.programGeneration++;
            final int prev = glCtx.activeProgram;
            glCtx.activeProgram = program;
            if (GLSMHooks.PROGRAM_CHANGE.hasListeners()) {
                GLSMHooks.programChangeEvent.previousProgram = prev;
                GLSMHooks.programChangeEvent.newProgram = program;
                GLSMHooks.programChangeEvent.postBind = false;
                GLSMHooks.PROGRAM_CHANGE.post(GLSMHooks.programChangeEvent);
            }
            if (initConfig != null && initConfig.isLwjglDebug()) {
                final String programName = GLDebug.getObjectLabel(KHRDebug.GL_PROGRAM, program);
                GLDebug.debugMessage("Activating Program - " + program + ":" + programName);
            }
            RENDER_BACKEND.useProgram(program);
            if (GLSMHooks.PROGRAM_CHANGE.hasListeners()) {
                GLSMHooks.programChangeEvent.previousProgram = prev;
                GLSMHooks.programChangeEvent.newProgram = program;
                GLSMHooks.programChangeEvent.postBind = true;
                GLSMHooks.PROGRAM_CHANGE.post(GLSMHooks.programChangeEvent);
            }
        }
    }

    public static void glShaderSource(int shader, CharSequence source) {
        rejectExitCapture();
        String src = source.toString();
        // Always rename reserved words for the target GLSL version (e.g. 'sampler' at 460)
        src = GlslTransformUtils.renameReservedWords(src, RENDER_BACKEND.getMinGLSLVersion());
        if (ShaderManager.isEnabled()) {
            final int shaderType = RENDER_BACKEND.getShaderi(shader, GL20.GL_SHADER_TYPE);
            src = CompatShaderTransformer.transform(src, shaderType, shaderType == GL20.GL_FRAGMENT_SHADER);
        }
        RENDER_BACKEND.shaderSource(shader, src);
    }

    public static void nglShaderSource(int shader, int count, long strings, long lengths) {
        rejectExitCapture();
        if (count <= 0) return;
        final CharSequence source;
        if (count == 1) {
            source = decodeShaderSourceString(strings, lengths, 0);
        } else {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < count; i++) sb.append(decodeShaderSourceString(strings, lengths, i));
            source = sb.toString();
        }
        glShaderSource(shader, source);
    }

    private static String decodeShaderSourceString(long strings, long lengths, int i) {
        final long strAddr = MemoryUtilities.memGetAddress(strings + (long) i * Pointer.POINTER_SIZE);
        final int len = lengths == MemoryUtilities.NULL ? -1 : MemoryUtilities.memGetInt(lengths + (long) i * 4);
        return len < 0 ? MemoryUtilities.memUTF8(strAddr) : MemoryUtilities.memUTF8(strAddr, len);
    }

    public static void glShaderSource(int shader, ByteBuffer source) {
        final byte[] bytes = new byte[source.remaining()];
        final int pos = source.position();
        source.get(bytes);
        source.position(pos);
        glShaderSource(shader, new String(bytes, StandardCharsets.UTF_8));
    }

    public static void glShaderSource(int shader, CharSequence[] sources) {
        int totalLen = 0;
        for (CharSequence s : sources) totalLen += s.length();
        final StringBuilder sb = new StringBuilder(totalLen);
        for (CharSequence s : sources) sb.append(s);
        glShaderSource(shader, sb.toString());
    }

    public static void glSpecializeShader(int shader, CharSequence entryPoint, IntBuffer constantIndex, IntBuffer constantValue) {
        rejectExitCapture();
        RENDER_BACKEND.specializeShader(shader, entryPoint, constantIndex, constantValue);
    }

    public static int glGetUniformBlockIndex(int program, CharSequence name) { rejectExitCapture(); return RENDER_BACKEND.getUniformBlockIndex(program, name); }
    public static void glUniformBlockBinding(int program, int blockIndex, int binding) { rejectExitCapture(); RENDER_BACKEND.uniformBlockBinding(program, blockIndex, binding); }
    public static void glBindFragDataLocation(int program, int colorNumber, CharSequence name) { rejectExitCapture(); RENDER_BACKEND.bindFragDataLocation(program, colorNumber, name); }
    public static void glUniform3(int location, float[] values) { rejectExitCapture(); RENDER_BACKEND.uniform3fv(location, values); }
    public static void glUniform4(int location, float[] values) { rejectExitCapture(); RENDER_BACKEND.uniform4fv(location, values); }

    public static void glLinkProgram(int program) {
        rejectExitCapture();
        if (ShaderManager.isEnabled()) {
            generateVertexShaderIfNeeded(program);
        }
        if (ProgramBinaryCache.isEnabled()) {
            ProgramBinaryCache.markRetrievable(program);
        }
        RENDER_BACKEND.linkProgram(program);
        if (ShaderManager.isEnabled() && RENDER_BACKEND.getProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            LOGGER.warn("Program {} failed to link: {}", program, RENDER_BACKEND.getProgramInfoLog(program));
        }
        CompatUniformManager.onLinkProgram(program);
    }

    public static void glProgramParameteri(int program, int pname, int value) {
        rejectExitCapture();
        RENDER_BACKEND.programParameteri(program, pname, value);
    }

    public static void glGetProgramBinary(int program, IntBuffer length, IntBuffer binaryFormat, ByteBuffer binary) {
        rejectExitCapture();
        RENDER_BACKEND.getProgramBinary(program, length, binaryFormat, binary);
    }

    public static void glProgramBinary(int program, int binaryFormat, ByteBuffer binary) {
        rejectExitCapture();
        RENDER_BACKEND.programBinary(program, binaryFormat, binary);
        if (RENDER_BACKEND.getProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_TRUE) {
            CompatUniformManager.onLinkProgram(program);
        }
    }

    /**
     * If a program has a fragment shader but no vertex shader, generate a passthrough vertex shader from the fragment's in declarations and attach it.
     */
    private static void generateVertexShaderIfNeeded(int program) {
        final GLContextState glCtx = ctx();
        final int shaderCount = RENDER_BACKEND.getProgrami(program, GL20.GL_ATTACHED_SHADERS);
        if (shaderCount == 0 || shaderCount > glCtx.shaderBuf.capacity()) return;

        glCtx.shaderBuf.clear().limit(shaderCount);
        RENDER_BACKEND.getAttachedShaders(program, null, glCtx.shaderBuf);

        int fragmentShader = 0;
        boolean hasVertex = false;
        for (int i = 0; i < shaderCount; i++) {
            final int shader = glCtx.shaderBuf.get(i);
            final int type = RENDER_BACKEND.getShaderi(shader, GL20.GL_SHADER_TYPE);
            if (type == GL20.GL_VERTEX_SHADER) {
                hasVertex = true;
                break;
            } else if (type == GL20.GL_FRAGMENT_SHADER) {
                fragmentShader = shader;
            }
        }

        if (hasVertex || fragmentShader == 0) return;

        // Fragment-only program - generate a passthrough vertex shader
        final String fragSource = RENDER_BACKEND.getShaderSource(fragmentShader, 65536);
        String vertSource = CompatShaderTransformer.generatePassthroughVertexShader(fragSource);
        if (RenderSystem.isGLES()) {
            vertSource = CompatShaderTransformer.toGLES(vertSource, GL20.GL_VERTEX_SHADER, false);
        }

        final int vertShader = RENDER_BACKEND.createShader(GL20.GL_VERTEX_SHADER);
        RENDER_BACKEND.shaderSource(vertShader, vertSource);
        RENDER_BACKEND.compileShader(vertShader);

        if (RENDER_BACKEND.getShaderi(vertShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            final String log = RENDER_BACKEND.getShaderInfoLog(vertShader, 8192);
            LOGGER.warn("CompatShaderTransformer: Generated passthrough vertex shader failed to compile:\n{}\nSource:\n{}", log, vertSource);
            RENDER_BACKEND.deleteShader(vertShader);
            return;
        }

        LOGGER.debug("CompatShaderTransformer: Generated passthrough vertex shader for fragment-only program {}", program);
        RENDER_BACKEND.attachShader(program, vertShader);
        RENDER_BACKEND.deleteShader(vertShader);
    }

    @Getter
    static long programLifetimeGeneration;
    private static final IntOpenHashSet programsPendingDeletion = new IntOpenHashSet();

    public static boolean isProgramPendingDeletion(int program) {
        return programsPendingDeletion.contains(program);
    }

    public static void glDeleteProgram(int program) {
        rejectExitCapture();
        if (program == 0) return;
        CompatUniformManager.onDeleteProgram(program);

        // GL defers deletion of a bound program until unbind; activeProgram stays set so the
        // cache matches GL_CURRENT_PROGRAM and the next glUseProgram is not skipped as redundant.
        if (GLSMHooks.PROGRAM_DELETE.hasListeners()) {
            GLSMHooks.programDeleteEvent.program = program;
            GLSMHooks.PROGRAM_DELETE.post(GLSMHooks.programDeleteEvent);
        }
        RENDER_BACKEND.deleteProgram(program);
        programsPendingDeletion.add(program);
        programsPendingDeletion.removeIf((int id) -> !RENDER_BACKEND.isProgram(id));
        programLifetimeGeneration++;
    }

    public static int glCreateShader(int type) {
        requireContext("glCreateShader");
        return RENDER_BACKEND.createShader(type);
    }

    public static void glCompileShader(int shader) {
        rejectExitCapture();
        RENDER_BACKEND.compileShader(shader);
    }

    public static int glCreateProgram() {
        requireContext("glCreateProgram");
        final int program = RENDER_BACKEND.createProgram();
        programsPendingDeletion.remove(program);
        programLifetimeGeneration++;
        return program;
    }

    public static void glAttachShader(int program, int shader) {
        rejectExitCapture();
        RENDER_BACKEND.attachShader(program, shader);
    }

    public static void glDetachShader(int program, int shader) {
        rejectExitCapture();
        RENDER_BACKEND.detachShader(program, shader);
    }

    public static void glDeleteShader(int shader) {
        rejectExitCapture();
        RENDER_BACKEND.deleteShader(shader);
    }

    public static void glBindAttribLocation(int program, int index, CharSequence name) {
        rejectExitCapture();
        RENDER_BACKEND.bindAttribLocation(program, index, name);
    }

    public static void glValidateProgram(int program) {
        rejectExitCapture();
        RENDER_BACKEND.validateProgram(program);
    }

    public static int glGetShaderi(int shader, int pname) {
        rejectExitCapture();
        return RENDER_BACKEND.getShaderi(shader, pname);
    }

    public static String glGetShaderInfoLog(int shader, int maxLength) {
        rejectExitCapture();
        return RENDER_BACKEND.getShaderInfoLog(shader, maxLength);
    }

    public static String glGetShaderInfoLog(int shader) {
        rejectExitCapture();
        return RENDER_BACKEND.getShaderInfoLog(shader);
    }

    public static int glGetProgrami(int program, int pname) {
        rejectExitCapture();
        return RENDER_BACKEND.getProgrami(program, pname);
    }

    public static String glGetProgramInfoLog(int program, int maxLength) {
        rejectExitCapture();
        return RENDER_BACKEND.getProgramInfoLog(program, maxLength);
    }

    public static String glGetProgramInfoLog(int program) {
        rejectExitCapture();
        return RENDER_BACKEND.getProgramInfoLog(program);
    }

    public static boolean glIsProgram(int obj) {
        rejectExitCapture();
        final boolean valid = RENDER_BACKEND.isProgram(obj);
        if (!valid) programsPendingDeletion.remove(obj);
        return valid;
    }

    public static boolean glIsShader(int obj) {
        rejectExitCapture();
        return RENDER_BACKEND.isShader(obj);
    }
    public static void glDispatchCompute(int x, int y, int z) { rejectExitCapture(); RENDER_BACKEND.dispatchCompute(x, y, z); }

    public static int glGetUniformLocation(int program, CharSequence name) {
        rejectExitCapture();
        int loc = RENDER_BACKEND.getUniformLocation(program, name);
        if (loc == -1 && TEXTURE.contentEquals(name)) {
            loc = RENDER_BACKEND.getUniformLocation(program, TEXTURE_RENAMED);
        }
        return loc;
    }

    public static int glGetUniformLocation(int program, ByteBuffer name) {
        rejectExitCapture();
        int loc = RENDER_BACKEND.getUniformLocation(program, name);
        if (loc == -1 && isTextureBuffer(name)) {
            loc = RENDER_BACKEND.getUniformLocation(program, TEXTURE_RENAMED);
        }
        return loc;
    }

    public static int glGetAttribLocation(int program, CharSequence name) {
        rejectExitCapture();
        int loc = RENDER_BACKEND.getAttribLocation(program, name);
        if (loc == -1 && TEXTURE.contentEquals(name)) {
            loc = RENDER_BACKEND.getAttribLocation(program, TEXTURE_RENAMED);
        }
        return loc;
    }

    public static int glGetAttribLocation(int program, ByteBuffer name) {
        rejectExitCapture();
        int loc = RENDER_BACKEND.getAttribLocation(program, name);
        if (loc == -1 && isTextureBuffer(name)) {
            loc = RENDER_BACKEND.getAttribLocation(program, TEXTURE_RENAMED);
        }
        return loc;
    }

    private static final ByteBuffer TEXTURE_BYTES = ByteBuffer.wrap(TEXTURE.getBytes(StandardCharsets.US_ASCII));

    private static boolean isTextureBuffer(ByteBuffer buf) {
        final int len = buf.remaining();
        if (len != 7 && len != 8) return false;
        // I believe the driver accepts null terminated or not
        if (len == 8 && buf.get(buf.position() + 7) != 0) return false;
        return buf.slice(buf.position(), 7).equals(TEXTURE_BYTES);
    }

    public static void glDeleteObjectARB(int obj) {
        rejectExitCapture();
        if (RENDER_BACKEND.isShader(obj)) {
            RENDER_BACKEND.deleteShader(obj);
        } else {
            glDeleteProgram(obj);
        }
    }

    public static int glGetHandleARB(int pname) {
        rejectExitCapture();
        return pname == ARBShaderObjects.GL_PROGRAM_OBJECT_ARB ? GLSMQueries.glGetInteger(GL20.GL_CURRENT_PROGRAM) : RENDER_BACKEND.getInteger(pname);
    }

    public static void glGetObjectParameterARB(int obj, int pname, IntBuffer params) {
        rejectExitCapture();
        final var backend = RENDER_BACKEND;
        if (backend.isShader(obj)) {
            params.put(params.position(), backend.getShaderi(obj, pname));
        } else {
            backend.getProgramiv(obj, pname, params);
        }
    }

    public static void glGetObjectParameterARB(int obj, int pname, FloatBuffer params) {
        rejectExitCapture();
        final var backend = RENDER_BACKEND;
        params.put(params.position(), (float) (backend.isShader(obj) ? backend.getShaderi(obj, pname) : backend.getProgrami(obj, pname)));
    }

    public static int glGetObjectParameteriARB(int obj, int pname) {
        rejectExitCapture();
        final var backend = RENDER_BACKEND;
        return backend.isShader(obj) ? backend.getShaderi(obj, pname) : backend.getProgrami(obj, pname);
    }

    public static String glGetInfoLogARB(int obj, int maxLength) {
        rejectExitCapture();
        final var backend = RENDER_BACKEND;
        return backend.isShader(obj) ? backend.getShaderInfoLog(obj, maxLength) : backend.getProgramInfoLog(obj, maxLength);
    }

    public static void glGetInfoLogARB(int obj, IntBuffer length, ByteBuffer infoLog) {
        rejectExitCapture();
        final var backend = RENDER_BACKEND;
        if (backend.isShader(obj)) {
            backend.getShaderInfoLog(obj, length, infoLog);
        } else {
            backend.getProgramInfoLog(obj, length, infoLog);
        }
    }

    public static void glUniform1f(int location, float v0) {
        rejectExitCapture();
        RENDER_BACKEND.uniform1f(location, v0);
    }

    public static void glUniform2f(int location, float v0, float v1) {
        rejectExitCapture();
        RENDER_BACKEND.uniform2f(location, v0, v1);
    }

    public static void glUniform3f(int location, float v0, float v1, float v2) {
        rejectExitCapture();
        RENDER_BACKEND.uniform3f(location, v0, v1, v2);
    }

    public static void glUniform4f(int location, float v0, float v1, float v2, float v3) {
        rejectExitCapture();
        RENDER_BACKEND.uniform4f(location, v0, v1, v2, v3);
    }

    public static void glUniform1i(int location, int v0) {
        rejectExitCapture();
        RENDER_BACKEND.uniform1i(location, v0);
    }

    public static void glUniform2i(int location, int v0, int v1) {
        rejectExitCapture();
        RENDER_BACKEND.uniform2i(location, v0, v1);
    }

    public static void glUniform3i(int location, int v0, int v1, int v2) {
        rejectExitCapture();
        RENDER_BACKEND.uniform3i(location, v0, v1, v2);
    }

    public static void glUniform4i(int location, int v0, int v1, int v2, int v3) {
        rejectExitCapture();
        RENDER_BACKEND.uniform4i(location, v0, v1, v2, v3);
    }

    public static void glUniform1(int location, FloatBuffer values) {
        rejectExitCapture();
        RENDER_BACKEND.uniform1fv(location, values);
    }

    public static void glUniform1(int location, IntBuffer values) {
        rejectExitCapture();
        RENDER_BACKEND.uniform1iv(location, values);
    }

    public static void glUniform2(int location, FloatBuffer values) {
        rejectExitCapture();
        RENDER_BACKEND.uniform2(location, values);
    }

    public static void glUniform2(int location, IntBuffer values) {
        rejectExitCapture();
        RENDER_BACKEND.uniform2iv(location, values);
    }

    public static void glUniform3(int location, FloatBuffer values) {
        rejectExitCapture();
        RENDER_BACKEND.uniform3(location, values);
    }

    public static void glUniform3(int location, IntBuffer values) {
        rejectExitCapture();
        RENDER_BACKEND.uniform3iv(location, values);
    }

    public static void glUniform4(int location, FloatBuffer values) {
        rejectExitCapture();
        RENDER_BACKEND.uniform4(location, values);
    }

    public static void glUniform4(int location, IntBuffer values) {
        rejectExitCapture();
        RENDER_BACKEND.uniform4iv(location, values);
    }

    public static void glUniformMatrix2(int location, boolean transpose, FloatBuffer matrices) {
        rejectExitCapture();
        RENDER_BACKEND.uniformMatrix2(location, transpose, matrices);
    }

    public static void glUniformMatrix3(int location, boolean transpose, FloatBuffer matrices) {
        rejectExitCapture();
        RENDER_BACKEND.uniformMatrix3(location, transpose, matrices);
    }

    public static void glUniformMatrix4(int location, boolean transpose, FloatBuffer matrices) {
        rejectExitCapture();
        RENDER_BACKEND.uniformMatrix4(location, transpose, matrices);
    }

    public static void glGetActiveUniform(int program, int index, IntBuffer length, IntBuffer size, IntBuffer type, ByteBuffer name) {
        rejectExitCapture();
        RENDER_BACKEND.getActiveUniform(program, index, length, size, type, name);
    }

    public static void glGetActiveAttrib(int program, int index, IntBuffer length, IntBuffer size, IntBuffer type, ByteBuffer name) {
        rejectExitCapture();
        RENDER_BACKEND.getActiveAttrib(program, index, length, size, type, name);
    }

    public static String glGetActiveAttrib(int program, int index, int maxLength, IntBuffer sizeType) {
        rejectExitCapture();
        return RENDER_BACKEND.getActiveAttrib(program, index, maxLength, sizeType);
    }

    public static void glGetAttachedShaders(int program, IntBuffer count, IntBuffer shaders) {
        rejectExitCapture();
        RENDER_BACKEND.getAttachedShaders(program, count, shaders);
    }

    public static String glGetShaderSource(int shader, int maxLength) {
        rejectExitCapture();
        return RENDER_BACKEND.getShaderSource(shader, maxLength);
    }

    public static void glGetShaderSource(int shader, IntBuffer length, ByteBuffer source) {
        rejectExitCapture();
        RENDER_BACKEND.getShaderSource(shader, length, source);
    }

    public static void glGetUniform(int program, int location, FloatBuffer params) {
        rejectExitCapture();
        RENDER_BACKEND.getUniformfv(program, location, params);
    }

    public static void glGetUniform(int program, int location, IntBuffer params) {
        rejectExitCapture();
        RENDER_BACKEND.getUniformiv(program, location, params);
    }

    public static int getProgramGeneration() {
        return ctx().programGeneration;
    }

    public static IntegerStateStack getProgramStack() {
        return ctx().programStack;
    }

    public static BooleanStateStack getVertexProgramTwoSideState() {
        return ctx().vertexProgramTwoSideState;
    }

    public static int getActiveProgram() {
        return ctx().activeProgram;
    }
}

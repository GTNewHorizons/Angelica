package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMQueries;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.sdlgpu.device.MemoryStackWrapper;
import com.gtnewhorizons.angelica.sdlgpu.util.MemoryAccess;
import org.taumc.celeritas.lwjgl.DebugMessageHandler;
import org.taumc.celeritas.lwjgl.GLExtension;
import org.taumc.celeritas.lwjgl.LWJGLService;
import org.taumc.celeritas.lwjgl.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

public final class SDLGPULWJGLService implements LWJGLService {

    private static final ThreadLocal<MemoryStackWrapper> STACK =
        ThreadLocal.withInitial(() -> new MemoryStackWrapper(org.lwjgl.system.MemoryStack.stackGet()));

    private static volatile boolean constructed;

    public static SDLGPULWJGLService create() {
        if (!SDLGPUGate.isEngaged()) {
            throw new IllegalStateException("SDL GPU is not engaged; " + SystemProperties.KEY_CELERITAS_LWJGL_SERVICE + " must not name " + SDLGPULWJGLService.class.getName());
        }
        constructed = true;
        return new SDLGPULWJGLService();
    }

    public static boolean isConstructed() { return constructed; }

    static void bind() {
        System.setProperty(SystemProperties.KEY_CELERITAS_LWJGL_SERVICE, SDLGPULWJGLService.class.getName());
    }

    static void unbind() {
        final String key = SystemProperties.KEY_CELERITAS_LWJGL_SERVICE;
        if (SDLGPULWJGLService.class.getName().equals(System.getProperty(key))) {
            System.clearProperty(key);
        }
    }

    @Override public boolean isOpenGLVersionSupported(int major, int minor) {
        return major < 4 || (major == 4 && minor <= 6);
    }

    @Override public boolean isExtensionSupported(GLExtension extension) {
        return switch (extension) {
            case ARB_buffer_storage,
                 ARB_map_buffer_range,
                 ARB_copy_buffer,
                 ARB_multi_draw_indirect,
                 ARB_draw_elements_base_vertex,
                 ARB_sync,
                 ARB_uniform_buffer_object,
                 ARB_vertex_array_object,
                 ARB_shader_storage_buffer_object,
                 KHR_debug
                 -> true;
            case ARB_timer_query,
                 ARB_debug_output,
                 AMD_debug_output,
                 ARB_compatibility,
                 ARB_direct_state_access,
                 ARB_texture_storage,
                 ARB_base_instance
                 -> false;
        };
    }

    @Override public int getPointerSize() { return 8; } // 64-bit

    @Override public int glGenBuffers() { return GLSMBuffers.glGenBuffers(); }
    @Override public void glDeleteBuffers(int buffer) { GLSMBuffers.glDeleteBuffers(buffer); }
    @Override public void glBindBuffer(int target, int buffer) { GLSMBuffers.glBindBuffer(target, buffer); }
    @Override public void glBufferData(int target, long size, int usage) {
        GLSMBuffers.glBufferData(target, size, usage);
    }
    @Override public void glBufferData(int target, ByteBuffer data, int usage) {
        GLSMBuffers.glBufferData(target, data, usage);
    }
    @Override public void glBufferData(int target, long size, long data, int usage) {
        if (data != 0) {
            GLSMBuffers.nglBufferData(target, size, data, usage);
        } else {
            GLSMBuffers.glBufferData(target, size, usage);
        }
    }
    @Override public void glBufferStorage(int target, long size, int flags) {
        GLSMBuffers.glBufferStorage(target, size, flags);
    }
    @Override public void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        GLSMFramebuffer.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    @Override public void glDrawArrays(int mode, int first, int count) {
        GLSMDraws.glDrawArrays(mode, first, count);
    }
    @Override public void glDrawBuffers(int[] buffers) {
        try (var stack = MemoryStack.stackPush()) {
            GLSMFramebuffer.glDrawBuffers(stack.ints(buffers));
        }
    }
    @Override public void glTexImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        GLSMTextures.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }
    @Override public void glTexParameteri(int target, int pname, int param) { GLSMTextures.glTexParameteri(target, pname, param); }
    @Override public ByteBuffer glMapBufferRange(int target, long offset, long length, int flags) {
        return GLSMBuffers.glMapBufferRange(target, offset, length, flags);
    }
    @Override public long nglMapBuffer(int target, int access) { return 0; }
    @Override public ByteBuffer glMapBuffer(int target, int access) {
        return GLSMBuffers.glMapBuffer(target, access);
    }
    @Override public void glUnmapBuffer(int target) { GLSMBuffers.glUnmapBuffer(target); }
    @Override public void glFlushMappedBufferRange(int target, long offset, long length) {
        GLSMBuffers.glFlushMappedBufferRange(target, offset, length);
    }
    @Override public void glCopyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset,
                                              long size) {
        GLSMBuffers.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }
    @Override public void glBindBufferBase(int target, int index, int buffer) {
        GLSMBuffers.glBindBufferBase(target, index, buffer);
    }

    @Override public int glGenVertexArrays() { return GLSMVertexInput.glGenVertexArrays(); }
    @Override public void glDeleteVertexArrays(int array) { GLSMVertexInput.glDeleteVertexArrays(array); }
    @Override public void glBindVertexArray(int array) { GLSMVertexInput.glBindVertexArray(array); }
    @Override public void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) { GLSMVertexInput.glVertexAttribPointer(index, size, type, normalized, stride, pointer); }
    @Override public void glVertexAttribIPointer(int index, int size, int type, int stride, long pointer) { GLSMVertexInput.glVertexAttribIPointer(index, size, type, stride, pointer); }
    @Override public void glEnableVertexAttribArray(int index) { GLSMVertexInput.glEnableVertexAttribArray(index); }
    @Override public void glVertexAttribDivisor(int index, int divisor) { GLSMVertexInput.glVertexAttribDivisor(index, divisor); }

    @Override public int glCreateShader(int type) { return GLSMPrograms.glCreateShader(type); }
    @Override public void glShaderSource(int shader, CharSequence source) {
        GLSMPrograms.glShaderSource(shader, source);
    }
    @Override public void glShaderSourceSafe(int shader, CharSequence source) {
        GLSMPrograms.glShaderSource(shader, source);
    }
    @Override public void glCompileShader(int shader) { GLSMPrograms.glCompileShader(shader); }
    @Override public String glGetShaderInfoLog(int shader, int maxLength) {
        return GLSMPrograms.glGetShaderInfoLog(shader, maxLength);
    }
    @Override public int glGetShaderi(int shader, int pname) { return GLSMPrograms.glGetShaderi(shader, pname); }
    @Override public void glDeleteShader(int shader) { GLSMPrograms.glDeleteShader(shader); }

    @Override public int glCreateProgram() { return GLSMPrograms.glCreateProgram(); }
    @Override public void glAttachShader(int program, int shader) { GLSMPrograms.glAttachShader(program, shader); }
    @Override public void glLinkProgram(int program) { GLSMPrograms.glLinkProgram(program); }
    @Override public String glGetProgramInfoLog(int program, int maxLength) {
        return GLSMPrograms.glGetProgramInfoLog(program, maxLength);
    }
    @Override public int glGetProgrami(int program, int pname) { return GLSMPrograms.glGetProgrami(program, pname); }
    @Override public void glUseProgram(int program) { GLSMPrograms.glUseProgram(program); }
    @Override public void glDeleteProgram(int program) { GLSMPrograms.glDeleteProgram(program); }
    @Override public void glBindAttribLocation(int program, int index, CharSequence name) {
        GLSMPrograms.glBindAttribLocation(program, index, name);
    }
    @Override public void glBindFragDataLocation(int program, int colorNumber, CharSequence name) {}

    @Override public int glGetUniformLocation(int program, CharSequence name) {
        return GLSMPrograms.glGetUniformLocation(program, name);
    }
    @Override public int glGetUniformBlockIndex(int program, CharSequence name) { return -1; /* TODO */ }
    @Override public void glUniformBlockBinding(int program, int blockIndex, int blockBinding) { /* TODO */ }
    @Override public void glUniform1f(int location, float v0) { GLSMPrograms.glUniform1f(location, v0); }
    @Override public void glUniform1i(int location, int v0) { GLSMPrograms.glUniform1i(location, v0); }
    @Override public void glUniform1fv(int location, FloatBuffer value) { GLSMPrograms.glUniform1(location, value); }
    @Override public void glUniform2i(int location, int v0, int v1) { GLSMPrograms.glUniform2i(location, v0, v1); }
    @Override public void glUniform3f(int location, float v0, float v1, float v2) {
        GLSMPrograms.glUniform3f(location, v0, v1, v2);
    }
    @Override public void glUniform3fv(int location, FloatBuffer value) { GLSMPrograms.glUniform3(location, value); }
    @Override public void glUniform3fv(int location, float[] value) { GLSMPrograms.glUniform3(location, value); }
    @Override public void glUniform4fv(int location, FloatBuffer value) { GLSMPrograms.glUniform4(location, value); }
    @Override public void glUniform4fv(int location, float[] value) { GLSMPrograms.glUniform4(location, value); }
    @Override public void glUniformMatrix3fv(int location, boolean transpose, FloatBuffer value) {
        GLSMPrograms.glUniformMatrix3(location, transpose, value);
    }
    @Override public void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) {
        GLSMPrograms.glUniformMatrix4(location, transpose, value);
    }

    @Override public void glDrawElementsBaseVertex(int mode, int count, int type, long indices, int basevertex) {
        GLSMDraws.glDrawElementsBaseVertex(mode, count, type, indices, basevertex);
    }
    @Override public void glMultiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawcount,
                                                        long pBaseVertex) {
        GLSMDraws.glMultiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawcount, pBaseVertex);
    }
    @Override public void glMultiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride) {
        GLSMDraws.glMultiDrawElementsIndirect(mode, type, indirect, drawcount, stride);
    }

    @Override public long glFenceSync(int condition, int flags) { return GLSMBuffers.glFenceSync(condition, flags); }
    @Override public int glClientWaitSync(long sync, int flags, long timeout) {
        return GLSMBuffers.glClientWaitSync(sync, flags, timeout);
    }
    @Override public int glGetSynci(long sync, int pname, IntBuffer length) {
        if (length != null && length.remaining() > 0) length.put(0, 1);
        return ((SDLGPURenderBackend) BackendManager.RENDER_BACKEND).getSyncStatus(sync);
    }
    @Override public void glWaitSync(long sync, int flags, long timeout) {
        GLSMBuffers.glWaitSync(sync, flags, timeout);
    }
    @Override public void glDeleteSync(long sync) { GLSMBuffers.glDeleteSync(sync); }

    private long[] queryElapsed = new long[64];
    private int activeQueryId = -1;
    private long activeQueryStart;

    private void ensureQueryCapacity(int id) {
        if (id >= queryElapsed.length) {
            queryElapsed = Arrays.copyOf(queryElapsed, Math.max(id + 1, queryElapsed.length * 2));
        }
    }

    @Override public int glGenQueries() { return GLSMQueries.glGenQueries(); }
    @Override public void glDeleteQueries(int query) { }
    @Override public void glBeginQuery(int target, int id) {
        ensureQueryCapacity(id);
        activeQueryId = id;
        activeQueryStart = System.nanoTime();
    }
    @Override public void glEndQuery(int target) {
        if (activeQueryId < 0) return;
        queryElapsed[activeQueryId] = System.nanoTime() - activeQueryStart;
        activeQueryId = -1;
    }
    @Override public long glGetQueryObjectui64(int id, int pname) {
        if (id >= 0 && id < queryElapsed.length) return queryElapsed[id];
        return 0;
    }

    @Override public int setupDebugCallback(DebugMessageHandler handler) { return 0; }
    @Override public void disableDebugCallback() { }
    @Override public void glObjectLabel(int identifier, int name, CharSequence label) {
        GLSMDraws.glObjectLabel(identifier, name, label);
    }
    @Override public void glPushDebugGroup(int source, int id, CharSequence message) {
        GLSMDraws.glPushDebugGroup(source, id, message);
    }
    @Override public void glPopDebugGroup() { GLSMDraws.glPopDebugGroup(); }

    @Override public int glGenTextures() { return GLSMTextures.glGenTextures(); }
    @Override public void glGenTextures(int[] textures) {
        for (int i = 0; i < textures.length; i++) textures[i] = GLSMTextures.glGenTextures();
    }
    @Override public void glDeleteTextures(int texture) { GLSMTextures.glDeleteTextures(texture); }
    @Override public void glDeleteTextures(int[] textures) {
        for (int t : textures) GLSMTextures.glDeleteTextures(t);
    }
    @Override public void glBindTexture(int target, int texture) { GLSMTextures.glBindTexture(target, texture); }
    @Override public void glActiveTexture(int texture) { GLSMTextures.glActiveTexture(texture); }
    @Override public int glGetTexLevelParameteri(int target, int level, int pname) {
        return GLSMTextures.glGetTexLevelParameteri(target, level, pname);
    }
    @Override public void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width,
                                              int height) {
        GLSMTextures.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }
    @Override public void glPixelStorei(int pname, int param) { GLSMTextures.glPixelStorei(pname, param); }

    @Override public int glGenFramebuffers() { return GLSMFramebuffer.glGenFramebuffers(); }
    @Override public void glDeleteFramebuffers(int framebuffer) { GLSMFramebuffer.glDeleteFramebuffers(framebuffer); }
    @Override public void glBindFramebuffer(int target, int framebuffer) { GLSMFramebuffer.glBindFramebuffer(target, framebuffer); }
    @Override public int glCheckFramebufferStatus(int target) {
        return GLSMFramebuffer.glCheckFramebufferStatus(target);
    }
    @Override public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        GLSMFramebuffer.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }

    @Override public void glEnable(int cap) { GLSMEnable.glEnable(cap); }
    @Override public void glDisable(int cap) { GLSMEnable.glDisable(cap); }
    @Override public void glBlendFunc(int sfactor, int dfactor) { GLSMBlend.glBlendFunc(sfactor, dfactor); }
    @Override public void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) { GLSMBlend.tryBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha); }
    @Override public void glDepthFunc(int func) { GLSMDepthStencilAlpha.glDepthFunc(func); }
    @Override public void glDepthMask(boolean flag) { GLSMDepthStencilAlpha.glDepthMask(flag); }
    @Override public void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) { GLSMBlend.glColorMask(red, green, blue, alpha); }
    @Override public void glViewport(int x, int y, int width, int height) { GLSMRasterizer.glViewport(x, y, width, height); }
    @Override public void glClear(int mask) { GLSMFramebuffer.glClear(mask); }
    @Override public void glClearColor(float red, float green, float blue, float alpha) {
        GLSMBlend.glClearColor(red, green, blue, alpha);
    }
    @Override public int glGetError() { return GLSMQueries.glGetError(); }

    @Override public void glMatrixMode(int mode) { GLSMMatrices.glMatrixMode(mode); }
    @Override public void glLoadMatrixf(FloatBuffer m) { GLSMMatrices.glLoadMatrix(m); }

    @Override public int glGetInteger(int pname) { return GLSMQueries.glGetInteger(pname); }
    @Override public void glGetIntegerv(int pname, int[] params) { GLSMQueries.glGetInteger(pname, params); }
    @Override public boolean glGetBoolean(int pname) { return GLSMQueries.glGetBoolean(pname); }
    @Override public String glGetString(int pname) { return GLSMQueries.glGetString(pname); }
    @Override public int glGetAttribLocation(int program, CharSequence name) {
        return GLSMPrograms.glGetAttribLocation(program, name);
    }

    @Override public MemoryStack stackPush() {
        org.lwjgl.system.MemoryStack.stackPush();
        return STACK.get();
    }

    @Override public long nmemAlloc(long size) { return MemoryUtil.nmemAlloc(size); }
    @Override public long nmemCalloc(long count, long size) { return MemoryUtil.nmemCalloc(count, size); }
    @Override public long nmemAlignedAlloc(long alignment, long size) { return MemoryUtil.nmemAlignedAlloc(alignment, size); }
    @Override public long nmemRealloc(long ptr, long size) { return MemoryUtil.nmemRealloc(ptr, size); }
    @Override public void nmemFree(long ptr) { MemoryUtil.nmemFree(ptr); }
    @Override public void nmemAlignedFree(long ptr) { MemoryUtil.nmemAlignedFree(ptr); }

    @Override public ByteBuffer memAlloc(int size) { return MemoryUtil.memAlloc(size); }
    @Override public ByteBuffer memCalloc(int size) { return MemoryUtil.memCalloc(size); }
    @Override public ByteBuffer memRealloc(ByteBuffer buffer, int size) { return MemoryUtil.memRealloc(buffer, size); }
    @Override public void memFree(Buffer buffer) { MemoryUtil.memFree(buffer); }
    @Override public ByteBuffer memByteBuffer(long address, int capacity) { return MemoryUtil.memByteBuffer(address, capacity); }
    @Override public long memAddress(Buffer buffer) { return MemoryUtil.memAddress(buffer); }
    @Override public long memAddress(Buffer buffer, int position) { return MemoryUtil.memAddress((ByteBuffer) buffer, position); }

    @Override public void memSet(long address, int value, long bytes) { MemoryUtil.memSet(address, value, bytes); }
    @Override public void memCopy(long src, long dst, long bytes) { MemoryUtil.memCopy(src, dst, bytes); }

    @Override public void memPutByte(long address, byte value) { MemoryAccess.putByte(address, value); }
    @Override public void memPutShort(long address, short value) { MemoryAccess.putShort(address, value); }
    @Override public void memPutInt(long address, int value) { MemoryAccess.putInt(address, value); }
    @Override public void memPutFloat(long address, float value) { MemoryAccess.putFloat(address, value); }
    @Override public void memPutLong(long address, long value) { MemoryAccess.putLong(address, value); }
    @Override public void memPutAddress(long address, long value) { MemoryAccess.putAddress(address, value); }

    @Override public byte memGetByte(long address) { return MemoryAccess.getByte(address); }
    @Override public short memGetShort(long address) { return MemoryAccess.getShort(address); }
    @Override public int memGetInt(long address) { return MemoryAccess.getInt(address); }
    @Override public float memGetFloat(long address) { return MemoryAccess.getFloat(address); }
    @Override public long memGetLong(long address) { return MemoryAccess.getLong(address); }
    @Override public long memGetAddress(long address) { return MemoryAccess.getAddress(address); }
    @Override public ByteBuffer memSlice(ByteBuffer buffer, int offset, int capacity) { return memByteBuffer(memAddress(buffer) + offset, capacity); }
}

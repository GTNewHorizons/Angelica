package com.mitchej123.lwjgl;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;

import java.io.PrintStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Headless {@link LWJGLService} for unit tests.
 */
public final class HeadlessTestLWJGLService extends LWJGLService {

    @Override
    public int getPriority() {
        return -1000;
    }

    @Override
    public boolean isOpenGLVersionSupported(int major, int minor) {
        throw new UnsupportedOperationException("isOpenGLVersionSupported requires a GL context");
    }

    @Override
    public boolean isExtensionSupported(GLExtension extension) {
        throw new UnsupportedOperationException("isExtensionSupported requires a GL context");
    }

    @Override
    public int getPointerSize() {
        return 8;
    }

    @Override
    public int glGenBuffers() {
        throw new UnsupportedOperationException("glGenBuffers requires a GL context");
    }

    @Override
    public void glDeleteBuffers(int buffer) {
        throw new UnsupportedOperationException("glDeleteBuffers requires a GL context");
    }

    @Override
    public void glBindBuffer(int target, int buffer) {
        throw new UnsupportedOperationException("glBindBuffer requires a GL context");
    }

    @Override
    public void glBufferData(int target, long size, int usage) {
        throw new UnsupportedOperationException("glBufferData requires a GL context");
    }

    @Override
    public void glBufferData(int target, ByteBuffer data, int usage) {
        throw new UnsupportedOperationException("glBufferData requires a GL context");
    }

    @Override
    public void glBufferData(int target, long size, long data, int usage) {
        throw new UnsupportedOperationException("glBufferData requires a GL context");
    }

    @Override
    public void glBufferSubData(int target, long offset, ByteBuffer data) {
        throw new UnsupportedOperationException("glBufferSubData requires a GL context");
    }

    @Override
    public void glBufferSubData(int target, long offset, long size, long data) {
        throw new UnsupportedOperationException("glBufferSubData requires a GL context");
    }

    @Override
    public void glBufferStorage(int target, long size, int flags) {
        throw new UnsupportedOperationException("glBufferStorage requires a GL context");
    }

    @Override
    public ByteBuffer glMapBufferRange(int target, long offset, long length, int flags) {
        throw new UnsupportedOperationException("glMapBufferRange requires a GL context");
    }

    @Override
    public long nglMapBuffer(int target, int access) {
        throw new UnsupportedOperationException("nglMapBuffer requires a GL context");
    }

    @Override
    public ByteBuffer glMapBuffer(int target, int access) {
        throw new UnsupportedOperationException("glMapBuffer requires a GL context");
    }

    @Override
    public void glUnmapBuffer(int target) {
        throw new UnsupportedOperationException("glUnmapBuffer requires a GL context");
    }

    @Override
    public void glFlushMappedBufferRange(int target, long offset, long length) {
        throw new UnsupportedOperationException("glFlushMappedBufferRange requires a GL context");
    }

    @Override
    public void glCopyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        throw new UnsupportedOperationException("glCopyBufferSubData requires a GL context");
    }

    @Override
    public void glBindBufferBase(int target, int index, int buffer) {
        throw new UnsupportedOperationException("glBindBufferBase requires a GL context");
    }

    @Override
    public int glGenVertexArrays() {
        throw new UnsupportedOperationException("glGenVertexArrays requires a GL context");
    }

    @Override
    public void glDeleteVertexArrays(int array) {
        throw new UnsupportedOperationException("glDeleteVertexArrays requires a GL context");
    }

    @Override
    public void glBindVertexArray(int array) {
        throw new UnsupportedOperationException("glBindVertexArray requires a GL context");
    }

    @Override
    public void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        throw new UnsupportedOperationException("glVertexAttribPointer requires a GL context");
    }

    @Override
    public void glVertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        throw new UnsupportedOperationException("glVertexAttribIPointer requires a GL context");
    }

    @Override
    public void glEnableVertexAttribArray(int index) {
        throw new UnsupportedOperationException("glEnableVertexAttribArray requires a GL context");
    }

    @Override
    public void glVertexAttribDivisor(int index, int divisor) {
        throw new UnsupportedOperationException("glVertexAttribDivisor requires a GL context");
    }

    @Override
    public int glCreateShader(int type) {
        throw new UnsupportedOperationException("glCreateShader requires a GL context");
    }

    @Override
    public void glShaderSource(int shader, CharSequence source) {
        throw new UnsupportedOperationException("glShaderSource requires a GL context");
    }

    @Override
    public void glShaderSourceSafe(int shader, CharSequence source) {
        throw new UnsupportedOperationException("glShaderSourceSafe requires a GL context");
    }

    @Override
    public void glCompileShader(int shader) {
        throw new UnsupportedOperationException("glCompileShader requires a GL context");
    }

    @Override
    public String glGetShaderInfoLog(int shader, int maxLength) {
        throw new UnsupportedOperationException("glGetShaderInfoLog requires a GL context");
    }

    @Override
    public int glGetShaderi(int shader, int pname) {
        throw new UnsupportedOperationException("glGetShaderi requires a GL context");
    }

    @Override
    public void glDeleteShader(int shader) {
        throw new UnsupportedOperationException("glDeleteShader requires a GL context");
    }

    @Override
    public int glCreateProgram() {
        throw new UnsupportedOperationException("glCreateProgram requires a GL context");
    }

    @Override
    public void glAttachShader(int program, int shader) {
        throw new UnsupportedOperationException("glAttachShader requires a GL context");
    }

    @Override
    public void glLinkProgram(int program) {
        throw new UnsupportedOperationException("glLinkProgram requires a GL context");
    }

    @Override
    public String glGetProgramInfoLog(int program, int maxLength) {
        throw new UnsupportedOperationException("glGetProgramInfoLog requires a GL context");
    }

    @Override
    public int glGetProgrami(int program, int pname) {
        throw new UnsupportedOperationException("glGetProgrami requires a GL context");
    }

    @Override
    public void glUseProgram(int program) {
        throw new UnsupportedOperationException("glUseProgram requires a GL context");
    }

    @Override
    public void glDeleteProgram(int program) {
        throw new UnsupportedOperationException("glDeleteProgram requires a GL context");
    }

    @Override
    public void glBindAttribLocation(int program, int index, CharSequence name) {
        throw new UnsupportedOperationException("glBindAttribLocation requires a GL context");
    }

    @Override
    public void glBindFragDataLocation(int program, int colorNumber, CharSequence name) {
        throw new UnsupportedOperationException("glBindFragDataLocation requires a GL context");
    }

    @Override
    public int glGetUniformLocation(int program, CharSequence name) {
        throw new UnsupportedOperationException("glGetUniformLocation requires a GL context");
    }

    @Override
    public int glGetUniformBlockIndex(int program, CharSequence name) {
        throw new UnsupportedOperationException("glGetUniformBlockIndex requires a GL context");
    }

    @Override
    public void glUniformBlockBinding(int program, int blockIndex, int blockBinding) {
        throw new UnsupportedOperationException("glUniformBlockBinding requires a GL context");
    }

    @Override
    public void glUniform1f(int location, float v0) {
        throw new UnsupportedOperationException("glUniform1f requires a GL context");
    }

    @Override
    public void glUniform1i(int location, int v0) {
        throw new UnsupportedOperationException("glUniform1i requires a GL context");
    }

    @Override
    public void glUniform1fv(int location, FloatBuffer value) {
        throw new UnsupportedOperationException("glUniform1fv requires a GL context");
    }

    @Override
    public void glUniform2i(int location, int v0, int v1) {
        throw new UnsupportedOperationException("glUniform2i requires a GL context");
    }

    @Override
    public void glUniform3f(int location, float v0, float v1, float v2) {
        throw new UnsupportedOperationException("glUniform3f requires a GL context");
    }

    @Override
    public void glUniform3fv(int location, FloatBuffer value) {
        throw new UnsupportedOperationException("glUniform3fv requires a GL context");
    }

    @Override
    public void glUniform3fv(int location, float[] value) {
        throw new UnsupportedOperationException("glUniform3fv requires a GL context");
    }

    @Override
    public void glUniform4fv(int location, FloatBuffer value) {
        throw new UnsupportedOperationException("glUniform4fv requires a GL context");
    }

    @Override
    public void glUniform4fv(int location, float[] value) {
        throw new UnsupportedOperationException("glUniform4fv requires a GL context");
    }

    @Override
    public void glUniformMatrix3fv(int location, boolean transpose, FloatBuffer value) {
        throw new UnsupportedOperationException("glUniformMatrix3fv requires a GL context");
    }

    @Override
    public void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) {
        throw new UnsupportedOperationException("glUniformMatrix4fv requires a GL context");
    }

    @Override
    public void glDrawElementsBaseVertex(int mode, int count, int type, long indices, int basevertex) {
        throw new UnsupportedOperationException("glDrawElementsBaseVertex requires a GL context");
    }

    @Override
    public void glMultiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawcount, long pBaseVertex) {
        throw new UnsupportedOperationException("glMultiDrawElementsBaseVertex requires a GL context");
    }

    @Override
    public void glMultiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride) {
        throw new UnsupportedOperationException("glMultiDrawElementsIndirect requires a GL context");
    }

    @Override
    public long glFenceSync(int condition, int flags) {
        throw new UnsupportedOperationException("glFenceSync requires a GL context");
    }

    @Override
    public int glClientWaitSync(long sync, int flags, long timeout) {
        throw new UnsupportedOperationException("glClientWaitSync requires a GL context");
    }

    @Override
    public int glGetSynci(long sync, int pname, IntBuffer length) {
        throw new UnsupportedOperationException("glGetSynci requires a GL context");
    }

    @Override
    public void glWaitSync(long sync, int flags, long timeout) {
        throw new UnsupportedOperationException("glWaitSync requires a GL context");
    }

    @Override
    public void glDeleteSync(long sync) {
        throw new UnsupportedOperationException("glDeleteSync requires a GL context");
    }

    @Override
    public int glGenQueries() {
        throw new UnsupportedOperationException("glGenQueries requires a GL context");
    }

    @Override
    public void glDeleteQueries(int query) {
        throw new UnsupportedOperationException("glDeleteQueries requires a GL context");
    }

    @Override
    public void glBeginQuery(int target, int id) {
        throw new UnsupportedOperationException("glBeginQuery requires a GL context");
    }

    @Override
    public void glEndQuery(int target) {
        throw new UnsupportedOperationException("glEndQuery requires a GL context");
    }

    @Override
    public long glGetQueryObjectui64(int id, int pname) {
        throw new UnsupportedOperationException("glGetQueryObjectui64 requires a GL context");
    }

    @Override
    public int setupDebugCallback(DebugMessageHandler handler) {
        throw new UnsupportedOperationException("setupDebugCallback requires a GL context");
    }

    @Override
    public void disableDebugCallback() {
        throw new UnsupportedOperationException("disableDebugCallback requires a GL context");
    }

    @Override
    public void glObjectLabel(int identifier, int name, CharSequence label) {
        throw new UnsupportedOperationException("glObjectLabel requires a GL context");
    }

    @Override
    public void glPushDebugGroup(int source, int id, CharSequence message) {
        throw new UnsupportedOperationException("glPushDebugGroup requires a GL context");
    }

    @Override
    public void glPopDebugGroup() {
        throw new UnsupportedOperationException("glPopDebugGroup requires a GL context");
    }

    @Override
    public int glGenTextures() {
        throw new UnsupportedOperationException("glGenTextures requires a GL context");
    }

    @Override
    public void glGenTextures(int[] textures) {
        throw new UnsupportedOperationException("glGenTextures requires a GL context");
    }

    @Override
    public void glDeleteTextures(int texture) {
        throw new UnsupportedOperationException("glDeleteTextures requires a GL context");
    }

    @Override
    public void glDeleteTextures(int[] textures) {
        throw new UnsupportedOperationException("glDeleteTextures requires a GL context");
    }

    @Override
    public void glBindTexture(int target, int texture) {
        throw new UnsupportedOperationException("glBindTexture requires a GL context");
    }

    @Override
    public void glActiveTexture(int texture) {
        throw new UnsupportedOperationException("glActiveTexture requires a GL context");
    }

    @Override
    public int glGetTexLevelParameteri(int target, int level, int pname) {
        throw new UnsupportedOperationException("glGetTexLevelParameteri requires a GL context");
    }

    @Override
    public void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        throw new UnsupportedOperationException("glCopyTexSubImage2D requires a GL context");
    }

    @Override
    public void glPixelStorei(int pname, int param) {
        throw new UnsupportedOperationException("glPixelStorei requires a GL context");
    }

    @Override
    public int glGenFramebuffers() {
        throw new UnsupportedOperationException("glGenFramebuffers requires a GL context");
    }

    @Override
    public void glDeleteFramebuffers(int framebuffer) {
        throw new UnsupportedOperationException("glDeleteFramebuffers requires a GL context");
    }

    @Override
    public void glBindFramebuffer(int target, int framebuffer) {
        throw new UnsupportedOperationException("glBindFramebuffer requires a GL context");
    }

    @Override
    public int glCheckFramebufferStatus(int target) {
        throw new UnsupportedOperationException("glCheckFramebufferStatus requires a GL context");
    }

    @Override
    public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        throw new UnsupportedOperationException("glFramebufferTexture2D requires a GL context");
    }

    @Override
    public void glEnable(int cap) {
        throw new UnsupportedOperationException("glEnable requires a GL context");
    }

    @Override
    public void glDisable(int cap) {
        throw new UnsupportedOperationException("glDisable requires a GL context");
    }

    @Override
    public void glBlendFunc(int sfactor, int dfactor) {
        throw new UnsupportedOperationException("glBlendFunc requires a GL context");
    }

    @Override
    public void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        throw new UnsupportedOperationException("glBlendFuncSeparate requires a GL context");
    }

    @Override
    public void glDepthFunc(int func) {
        throw new UnsupportedOperationException("glDepthFunc requires a GL context");
    }

    @Override
    public void glDepthMask(boolean flag) {
        throw new UnsupportedOperationException("glDepthMask requires a GL context");
    }

    @Override
    public void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        throw new UnsupportedOperationException("glColorMask requires a GL context");
    }

    @Override
    public void glViewport(int x, int y, int width, int height) {
        throw new UnsupportedOperationException("glViewport requires a GL context");
    }

    @Override
    public void glClear(int mask) {
        throw new UnsupportedOperationException("glClear requires a GL context");
    }

    @Override
    public void glClearColor(float red, float green, float blue, float alpha) {
        throw new UnsupportedOperationException("glClearColor requires a GL context");
    }

    @Override
    public int glGetError() {
        throw new UnsupportedOperationException("glGetError requires a GL context");
    }

    @Override
    public void glMatrixMode(int mode) {
        throw new UnsupportedOperationException("glMatrixMode requires a GL context");
    }

    @Override
    public void glLoadMatrixf(FloatBuffer m) {
        throw new UnsupportedOperationException("glLoadMatrixf requires a GL context");
    }

    @Override
    public int glGetInteger(int pname) {
        throw new UnsupportedOperationException("glGetInteger requires a GL context");
    }

    @Override
    public void glGetIntegerv(int pname, int[] params) {
        throw new UnsupportedOperationException("glGetIntegerv requires a GL context");
    }

    @Override
    public boolean glGetBoolean(int pname) {
        throw new UnsupportedOperationException("glGetBoolean requires a GL context");
    }

    @Override
    public String glGetString(int pname) {
        throw new UnsupportedOperationException("glGetString requires a GL context");
    }

    @Override
    public int glGetAttribLocation(int program, CharSequence name) {
        throw new UnsupportedOperationException("glGetAttribLocation requires a GL context");
    }

    @Override
    public MemoryStack stackPush() {
        throw new UnsupportedOperationException("stackPush requires a GL context");
    }

    @Override
    public long nmemAlloc(long size) {
        return MemoryUtilities.nmemAlloc(size);
    }

    @Override
    public long nmemCalloc(long count, long size) {
        return MemoryUtilities.nmemCalloc(count, size);
    }

    @Override
    public long nmemAlignedAlloc(long alignment, long size) {
        throw new UnsupportedOperationException("nmemAlignedAlloc is not provided by GTNHLib MemoryUtilities");
    }

    @Override
    public long nmemRealloc(long ptr, long size) {
        return MemoryUtilities.nmemRealloc(ptr, size);
    }

    @Override
    public void nmemFree(long ptr) {
        MemoryUtilities.nmemFree(ptr);
    }

    @Override
    public void nmemAlignedFree(long ptr) {
        throw new UnsupportedOperationException("nmemAlignedFree is not provided by GTNHLib MemoryUtilities");
    }

    @Override
    public ByteBuffer memAlloc(int size) {
        return MemoryUtilities.memAlloc(size);
    }

    @Override
    public ByteBuffer memCalloc(int size) {
        return MemoryUtilities.memCalloc(size);
    }

    @Override
    public ByteBuffer memRealloc(ByteBuffer buffer, int size) {
        return MemoryUtilities.memRealloc(buffer, size);
    }

    @Override
    public void memFree(Buffer buffer) {
        MemoryUtilities.memFree(buffer);
    }

    @Override
    public ByteBuffer memByteBuffer(long address, int capacity) {
        return MemoryUtilities.memByteBuffer(address, capacity);
    }

    @Override
    public long memAddress(Buffer buffer) {
        return MemoryUtilities.memAddress(buffer);
    }

    @Override
    public long memAddress(Buffer buffer, int position) {
        throw new UnsupportedOperationException("memAddress(Buffer, int) has no generic MemoryUtil overload");
    }

    @Override
    public void memSet(long address, int value, long bytes) {
        MemoryUtilities.memSet(address, value, bytes);
    }

    @Override
    public void memCopy(long src, long dst, long bytes) {
        MemoryUtilities.memCopy(src, dst, bytes);
    }

    @Override
    public void memPutByte(long address, byte value) {
        MemoryUtilities.memPutByte(address, value);
    }

    @Override
    public void memPutShort(long address, short value) {
        MemoryUtilities.memPutShort(address, value);
    }

    @Override
    public void memPutInt(long address, int value) {
        MemoryUtilities.memPutInt(address, value);
    }

    @Override
    public void memPutFloat(long address, float value) {
        MemoryUtilities.memPutFloat(address, value);
    }

    @Override
    public void memPutLong(long address, long value) {
        MemoryUtilities.memPutLong(address, value);
    }

    @Override
    public void memPutAddress(long address, long value) {
        MemoryUtilities.memPutAddress(address, value);
    }

    @Override
    public byte memGetByte(long address) {
        return MemoryUtilities.memGetByte(address);
    }

    @Override
    public short memGetShort(long address) {
        return MemoryUtilities.memGetShort(address);
    }

    @Override
    public int memGetInt(long address) {
        return MemoryUtilities.memGetInt(address);
    }

    @Override
    public float memGetFloat(long address) {
        return MemoryUtilities.memGetFloat(address);
    }

    @Override
    public long memGetLong(long address) {
        return MemoryUtilities.memGetLong(address);
    }

    @Override
    public long memGetAddress(long address) {
        return MemoryUtilities.memGetAddress(address);
    }

    @Override
    public ByteBuffer memSlice(ByteBuffer buffer, int offset, int capacity) {
        return MemoryUtilities.memSlice(buffer, offset, capacity);
    }
}

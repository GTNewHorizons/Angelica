package com.gtnewhorizons.angelica.glsm.backend;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * Abstract rendering backend.
 */
public abstract class RenderBackend {

    public abstract void init();
    public abstract void shutdown();
    public abstract boolean isAvailable();
    public abstract String getName();

    /** Higher priority backends are preferred. */
    public int getPriority() { return 0; }

    public abstract int getMinGLSLVersion();

    public abstract void flush();
    public abstract void finish();

    public void onFrameBegin() {}
    public void onFrameEnd() {}

    public abstract void enable(int cap);
    public abstract void enablei(int cap, int index);
    public abstract void disable(int cap);
    public abstract void disablei(int cap, int index);
    public abstract void blendFuncSeparatei(int buf, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha);
    public abstract void blendFunc(int sfactor, int dfactor);
    public abstract void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha);
    public abstract void blendEquation(int mode);
    public abstract void blendEquationSeparate(int modeRGB, int modeAlpha);
    public abstract void blendColor(float red, float green, float blue, float alpha);
    public abstract void depthFunc(int func);
    public abstract void depthMask(boolean flag);
    public abstract void colorMask(boolean red, boolean green, boolean blue, boolean alpha);
    public abstract void cullFace(int mode);
    public abstract void frontFace(int mode);
    public abstract void polygonMode(int face, int mode);
    public abstract void polygonOffset(float factor, float units);
    public abstract void stencilFunc(int func, int ref, int mask);
    public abstract void stencilOp(int sfail, int dpfail, int dppass);
    public abstract void stencilMask(int mask);
    public abstract void stencilFuncSeparate(int face, int func, int ref, int mask);
    public abstract void stencilOpSeparate(int face, int sfail, int dpfail, int dppass);
    public abstract void stencilMaskSeparate(int face, int mask);
    public abstract void viewport(int x, int y, int width, int height);
    public abstract void depthRange(double nearVal, double farVal);
    public abstract void scissor(int x, int y, int width, int height);
    public abstract void clearColor(float red, float green, float blue, float alpha);
    public abstract void clearDepth(double depth);
    public abstract void clearStencil(int s);
    public abstract void clear(int mask);
    public abstract void lineWidth(float width);
    public abstract void pointSize(float size);
    public abstract void logicOp(int opcode);
    public abstract void hint(int target, int mode);

    public abstract void drawArrays(int mode, int first, int count);
    public abstract void drawElements(int mode, int count, int type, long indices);
    public void drawElements(int mode, ByteBuffer indices) {}
    public void drawElements(int mode, IntBuffer indices) {}
    public void drawElements(int mode, ShortBuffer indices) {}
    public void drawElements(int mode, int count, int type, ByteBuffer indices) {}
    public abstract void multiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride);
    public abstract void copyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size);
    public abstract void drawElementsInstanced(int mode, int count, int type, long indices, int primcount);
    public abstract void drawElementsBaseVertex(int mode, int count, int type, long indices, int baseVertex);
    public abstract void multiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawcount, long pBaseVertex);
    public abstract void drawBuffer(int mode);
    public abstract void dispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ);
    public abstract void dispatchComputeIndirect(long offset);

    public abstract int genTextures();
    public abstract void genTextures(IntBuffer textures);
    public abstract void deleteTextures(int texture);
    public abstract void bindTexture(int target, int texture);
    public abstract void activeTexture(int texture);
    public abstract void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, ByteBuffer pixels);
    public abstract void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, DoubleBuffer pixels);
    public abstract void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, FloatBuffer pixels);
    public abstract void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, IntBuffer pixels);
    public abstract void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, long pixelBufferOffset);
    public abstract void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels);
    public abstract void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels);
    public abstract void copyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height);
    public void texImage1D(int target, int level, int internalFormat, int width, int border, int format, int type, ByteBuffer pixels) {}
    public void texImage3D(int target, int level, int internalFormat, int width, int height, int depth, int border, int format, int type, ByteBuffer pixels) {}
    public void texImage3D(int target, int level, int internalFormat, int width, int height, int depth, int border, int format, int type, IntBuffer pixels) {}
    public void texSubImage1D(int target, int level, int xoffset, int width, int format, int type, ByteBuffer pixels) {}
    public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, long pboOffset) {}
    public void texSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, ByteBuffer pixels) {}
    public void copyTexImage1D(int target, int level, int internalFormat, int x, int y, int width, int border) {}
    public void copyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {}
    public void copyTexSubImage1D(int target, int level, int xoffset, int x, int y, int width) {}
    public void copyTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int x, int y, int width, int height) {}
    public abstract void texParameteri(int target, int pname, int param);
    public abstract void texParameterf(int target, int pname, float param);
    public abstract void texParameteriv(int target, int pname, IntBuffer params);
    public abstract void texParameterfv(int target, int pname, FloatBuffer params);
    public abstract int getTexParameteri(int target, int pname);
    public abstract float getTexParameterf(int target, int pname);
    public abstract int getTexLevelParameteri(int target, int level, int pname);
    public abstract void generateMipmap(int target);
    public abstract void pixelStorei(int pname, int param);
    public void pixelStoref(int pname, float param) {}
    public void sampleCoverage(float value, boolean invert) {}


    public abstract int genSamplers();
    public abstract void deleteSamplers(int sampler);
    public abstract void bindSampler(int unit, int sampler);
    public abstract void samplerParameteri(int sampler, int pname, int param);
    public abstract void samplerParameterf(int sampler, int pname, float param);

    public abstract int genFramebuffers();
    public abstract void deleteFramebuffers(int framebuffer);
    public abstract void bindFramebuffer(int target, int framebuffer);
    public abstract void framebufferTexture2D(int target, int attachment, int textarget, int texture, int level);
    public abstract void framebufferTexture(int target, int attachment, int texture, int level);
    public abstract int checkFramebufferStatus(int target);
    public abstract void blitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);
    public abstract void drawBuffers(int buffer);
    public abstract void drawBuffers(IntBuffer bufs);
    public abstract void readBuffer(int mode);
    public abstract void readPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels);
    public abstract void readPixels(int x, int y, int width, int height, int format, int type, FloatBuffer pixels);
    public abstract void readPixels(int x, int y, int width, int height, int format, int type, IntBuffer pixels);
    public abstract void getTexImage(int target, int level, int format, int type, ByteBuffer pixels);
    public abstract void getTexImage(int target, int level, int format, int type, IntBuffer pixels);
    public abstract int getFramebufferAttachmentParameteri(int target, int attachment, int pname);

    public abstract int createShader(int type);
    public abstract void deleteShader(int shader);
    public abstract void shaderSource(int shader, CharSequence source);
    public abstract void compileShader(int shader);
    public abstract int createProgram();
    public abstract void deleteProgram(int program);
    public abstract void attachShader(int program, int shader);
    public abstract void detachShader(int program, int shader);
    public abstract void linkProgram(int program);
    public abstract void useProgram(int program);
    public abstract String getShaderInfoLog(int shader, int maxLength);
    public String getShaderInfoLog(int shader) {
        return getShaderInfoLog(shader, getShaderi(shader, GL20.GL_INFO_LOG_LENGTH));
    }
    public abstract void getShaderInfoLog(int shader, IntBuffer length, ByteBuffer infoLog);
    public abstract String getProgramInfoLog(int program, int maxLength);
    public String getProgramInfoLog(int program) {
        return getProgramInfoLog(program, getProgrami(program, GL20.GL_INFO_LOG_LENGTH));
    }
    public abstract void getProgramInfoLog(int program, IntBuffer length, ByteBuffer infoLog);
    public abstract int getShaderi(int shader, int pname);
    public abstract int getProgrami(int program, int pname);
    public abstract void getProgramiv(int program, int pname, IntBuffer params);
    public abstract String getActiveUniform(int program, int index, int maxLength, IntBuffer sizeType);
    public abstract void getActiveUniform(int program, int index, IntBuffer length, IntBuffer size, IntBuffer type, ByteBuffer name);
    public abstract void bindAttribLocation(int program, int index, CharSequence name);
    public abstract int getAttribLocation(int program, CharSequence name);
    public abstract int getAttribLocation(int program, ByteBuffer name);
    public abstract int getUniformLocation(int program, CharSequence name);
    public abstract int getUniformLocation(int program, ByteBuffer name);
    public boolean isShader(int obj) { return false; }
    public boolean isProgram(int obj) { return false; }
    public void validateProgram(int program) {}
    public void getAttachedShaders(int program, IntBuffer count, IntBuffer shaders) {
        if (count != null) count.put(0, 0);
    }
    public String getShaderSource(int shader, int maxLength) { return ""; }
    public abstract void getShaderSource(int shader, IntBuffer length, ByteBuffer source);

    public abstract void uniform1i(int location, int v0);
    public abstract void uniform1f(int location, float v0);
    public void uniform1fv(int location, FloatBuffer values) {}
    public abstract void uniform2f(int location, float v0, float v1);
    public abstract void uniform2i(int location, int v0, int v1);
    public abstract void uniform3f(int location, float v0, float v1, float v2);
    public abstract void uniform3i(int location, int v0, int v1, int v2);
    public abstract void uniform4f(int location, float v0, float v1, float v2, float v3);
    public abstract void uniform4i(int location, int v0, int v1, int v2, int v3);
    public abstract void uniform3(int location, FloatBuffer value);
    public abstract void uniform4(int location, FloatBuffer value);
    public abstract void uniformMatrix3(int location, boolean transpose, FloatBuffer value);
    public abstract void uniformMatrix4(int location, boolean transpose, FloatBuffer value);
    public void uniformMatrix2(int location, boolean transpose, FloatBuffer matrices) {}
    public void getUniformfv(int program, int location, FloatBuffer params) {}
    public void getUniformiv(int program, int location, IntBuffer params) {}

    public abstract void vertexAttrib2f(int index, float v0, float v1);
    public abstract void vertexAttrib3f(int index, float v0, float v1, float v2);
    public abstract void vertexAttrib4f(int index, float v0, float v1, float v2, float v3);

    public abstract int genBuffers();
    public abstract void deleteBuffers(int buffer);
    public abstract void deleteBuffers(IntBuffer buffers);
    public abstract void bindBuffer(int target, int buffer);
    public abstract void bindBufferBase(int target, int index, int buffer);
    public abstract void bufferData(int target, long size, int usage);
    public abstract void bufferData(int target, ByteBuffer data, int usage);
    public abstract void bufferData(int target, FloatBuffer data, int usage);
    public abstract void bufferData(int target, ShortBuffer data, int usage);
    public abstract void bufferSubData(int target, long offset, ByteBuffer data);
    public abstract ByteBuffer mapBuffer(int target, int access);
    public abstract boolean unmapBuffer(int target);
    public abstract void bufferStorage(int target, ByteBuffer data, int flags);
    public abstract void bufferStorage(int target, long size, int flags);
    public abstract void getBufferSubData(int target, long offset, ByteBuffer data);
    public abstract int getBufferParameteri(int target, int pname);
    public abstract boolean isBuffer(int buffer);
    public abstract ByteBuffer mapBufferRange(int target, long offset, long length, int access);

    public abstract int genVertexArrays();
    public abstract void deleteVertexArrays(int array);
    public abstract void bindVertexArray(int array);
    public abstract void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer);
    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, ByteBuffer pointer) {
        vertexAttribPointer(index, size, type, normalized, stride, MemoryUtilities.memAddress0(pointer));
    }
    public void vertexAttribIPointer(int index, int size, int type, int stride, ByteBuffer pointer) {
        vertexAttribIPointer(index, size, type, stride, MemoryUtilities.memAddress0(pointer));
    }
    public abstract void vertexAttribIPointer(int index, int size, int type, int stride, long pointer);
    public abstract void enableVertexAttribArray(int index);
    public abstract void disableVertexAttribArray(int index);
    public abstract void vertexAttribDivisor(int index, int divisor);
    public abstract void bindVertexBuffer(int bindingindex, int buffer, long offset, int stride);
    public abstract void vertexAttribFormat(int attribindex, int size, int type, boolean normalized, int relativeoffset);
    public abstract void vertexAttribIFormat(int attribindex, int size, int type, int relativeoffset);
    public abstract void vertexAttribBinding(int attribindex, int bindingindex);

    public abstract int createTextures(int target);
    public abstract void bindTextureUnit(int unit, int texture);
    public abstract void textureParameteri(int texture, int target, int pname, int param);
    public abstract void textureParameterf(int texture, int target, int pname, float param);
    public abstract void textureParameteriv(int texture, int target, int pname, IntBuffer params);
    public abstract void texStorage1D(int target, int levels, int internalFormat, int width);
    public abstract void texStorage2D(int target, int levels, int internalFormat, int width, int height);
    public abstract void texStorage3D(int target, int levels, int internalFormat, int width, int height, int depth);
    public abstract void textureStorage1D(int texture, int levels, int internalFormat, int width);
    public abstract void textureStorage2D(int texture, int levels, int internalFormat, int width, int height);
    public abstract void textureStorage3D(int texture, int levels, int internalFormat, int width, int height, int depth);
    public abstract void generateTextureMipmap(int texture);
    public abstract void textureImage2DEXT(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels);
    public abstract void textureImage2DEXT(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels);
    public abstract void textureSubImage2D(int texture, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels);
    public abstract void textureSubImage2D(int texture, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels);
    public abstract int createFramebuffers();
    public abstract void namedFramebufferTexture(int framebuffer, int attachment, int texture, int level);
    public abstract void namedFramebufferReadBuffer(int framebuffer, int mode);
    public abstract void namedFramebufferDrawBuffers(int framebuffer, IntBuffer bufs);
    public abstract void blitNamedFramebuffer(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);
    public abstract int createBuffers();
    public abstract void namedBufferData(int buffer, ByteBuffer data, int usage);
    public abstract void namedBufferData(int buffer, FloatBuffer data, int usage);
    public abstract void namedBufferSubData(int buffer, long offset, ByteBuffer data);
    public abstract void copyTextureSubImage2D(int texture, int target, int level, int xoffset, int yoffset, int x, int y, int width, int height);
    public abstract int getTextureParameteri(int texture, int target, int pname);
    public abstract int getTextureLevelParameteri(int texture, int level, int pname);

    public abstract int getInteger(int pname);
    public abstract void getInteger(int pname, IntBuffer params);
    public abstract float getFloat(int pname);
    public abstract void getFloat(int pname, FloatBuffer params);
    public abstract boolean getBoolean(int pname);
    public abstract void getBoolean(int pname, ByteBuffer params);
    public abstract String getString(int pname);
    public abstract String getStringi(int name, int index);
    public abstract int getError();

    public abstract long fenceSync(int condition, int flags);
    public abstract int clientWaitSync(long sync, int flags, long timeout);
    public abstract void deleteSync(long sync);

    public abstract void clearBufferSubData(int target, int internalFormat, long offset, long size, int format, int type, ByteBuffer data);
    public abstract void clearTexImage(int texture, int level, int format, int type);

    public abstract void bindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format);
    public abstract void memoryBarrier(int barriers);

    public abstract void copyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ,
                                           int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ,
                                           int srcWidth, int srcHeight, int srcDepth);

    // Debug output
    public boolean supportsDebugOutput() { return false; }
    public int setupDebugOutput(DebugMessageHandler handler) { return 0; }
    public int disableDebugOutput() { return 0; }

    // Object labeling
    public void objectLabel(int identifier, int name, CharSequence label) {}
    public String getObjectLabel(int identifier, int name) { return ""; }

    // Debug groups
    public void pushDebugGroup(int source, int id, CharSequence message) {}
    public void popDebugGroup() {}

    // Debug message insertion
    public void debugMessageInsert(int source, int type, int id, int severity, CharSequence message) {}

}

package com.gtnewhorizons.angelica.glsm.backend;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.AMDDebugOutput;
import org.lwjgl.opengl.AMDDebugOutputCallback;
import org.lwjgl.opengl.ARBClearTexture;
import org.lwjgl.opengl.ARBDebugOutput;
import org.lwjgl.opengl.ARBDebugOutputCallback;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.EXTDirectStateAccess;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL44;
import org.lwjgl.opengl.GL45;
import org.lwjgl.opengl.KHRDebug;
import org.lwjgl.opengl.KHRDebugCallback;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import static com.mitchej123.lwjgl.LWJGLServiceProvider.LWJGL;

/**
 * LWJGL2 GL implementation of {@link RenderBackend}.
 */
public final class Lwjgl2GLRenderBackend extends RenderBackend {
    private ContextCapabilities caps;
    private IntBuffer intArrayBuffer = BufferUtils.createIntBuffer(16);
    private FloatBuffer floatArrayBuffer = BufferUtils.createFloatBuffer(16);

    // Debug output state: 0=none, 1=GL43/KHR, 2=ARB, 3=AMD
    private int activeDebugExtension;
    private KHRDebugCallback khrCallback;
    private ARBDebugOutputCallback arbCallback;
    private AMDDebugOutputCallback amdCallback;

    private IntBuffer getIntArrayBuffer(int size) {
        if (intArrayBuffer.capacity() < size) {
            intArrayBuffer = BufferUtils.createIntBuffer(size);
        }
        intArrayBuffer.clear();
        return intArrayBuffer;
    }

    private FloatBuffer getFloatArrayBuffer(int size) {
        if (floatArrayBuffer.capacity() < size) {
            floatArrayBuffer = BufferUtils.createFloatBuffer(size);
        }
        floatArrayBuffer.clear();
        return floatArrayBuffer;
    }

    @Override
    public void init() {
        caps = org.lwjgl.opengl.GLContext.getCapabilities();
        activeDebugExtension = 0;
    }

    @Override
    public void shutdown() {
        // no-op
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getName() {
        return "OpenGL (LWJGL2)";
    }

    @Override
    public int getMinGLSLVersion() {return 330;}

    @Override
    public void flush() {GL11.glFlush();}

    @Override
    public void finish() {GL11.glFinish();}

    @Override
    public void enable(int cap) {
        GL11.glEnable(cap);
    }

    @Override
    public void enablei(int cap, int index) {
        GL30.glEnablei(cap, index);
    }

    @Override
    public void disable(int cap) {
        GL11.glDisable(cap);
    }

    @Override
    public void disablei(int cap, int index) {
        GL30.glDisablei(cap, index);
    }

    @Override
    public void blendFuncSeparatei(int buf, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        GL40.glBlendFuncSeparatei(buf, srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    @Override
    public void blendFunc(int sfactor, int dfactor) {
        GL11.glBlendFunc(sfactor, dfactor);
    }

    @Override
    public void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        GL14.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    @Override
    public void blendEquation(int mode) {
        GL14.glBlendEquation(mode);
    }

    @Override
    public void blendEquationSeparate(int modeRGB, int modeAlpha) {
        GL20.glBlendEquationSeparate(modeRGB, modeAlpha);
    }

    @Override
    public void blendColor(float red, float green, float blue, float alpha) {
        GL14.glBlendColor(red, green, blue, alpha);
    }

    @Override
    public void depthFunc(int func) {
        GL11.glDepthFunc(func);
    }

    @Override
    public void depthMask(boolean flag) {
        GL11.glDepthMask(flag);
    }

    @Override
    public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GL11.glColorMask(red, green, blue, alpha);
    }

    @Override
    public void cullFace(int mode) {
        GL11.glCullFace(mode);
    }

    @Override
    public void frontFace(int mode) {
        GL11.glFrontFace(mode);
    }

    @Override
    public void polygonMode(int face, int mode) {
        GL11.glPolygonMode(face, mode);
    }

    @Override
    public void polygonOffset(float factor, float units) {
        GL11.glPolygonOffset(factor, units);
    }

    @Override
    public void stencilFunc(int func, int ref, int mask) {
        GL11.glStencilFunc(func, ref, mask);
    }

    @Override
    public void stencilOp(int sfail, int dpfail, int dppass) {
        GL11.glStencilOp(sfail, dpfail, dppass);
    }

    @Override
    public void stencilMask(int mask) {
        GL11.glStencilMask(mask);
    }

    @Override
    public void stencilFuncSeparate(int face, int func, int ref, int mask) {
        GL20.glStencilFuncSeparate(face, func, ref, mask);
    }

    @Override
    public void stencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        GL20.glStencilOpSeparate(face, sfail, dpfail, dppass);
    }

    @Override
    public void stencilMaskSeparate(int face, int mask) {
        GL20.glStencilMaskSeparate(face, mask);
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        GL11.glViewport(x, y, width, height);
    }

    @Override
    public void depthRange(double nearVal, double farVal) {
        GL11.glDepthRange(nearVal, farVal);
    }

    @Override
    public void scissor(int x, int y, int width, int height) {
        GL11.glScissor(x, y, width, height);
    }

    @Override
    public void clearColor(float red, float green, float blue, float alpha) {
        GL11.glClearColor(red, green, blue, alpha);
    }

    @Override
    public void clearDepth(double depth) {
        GL11.glClearDepth(depth);
    }

    @Override
    public void clearStencil(int s) {
        GL11.glClearStencil(s);
    }

    @Override
    public void clear(int mask) {
        GL11.glClear(mask);
    }

    @Override
    public void lineWidth(float width) {
        GL11.glLineWidth(width);
    }

    @Override
    public void pointSize(float size) {
        GL11.glPointSize(size);
    }

    @Override
    public void logicOp(int opcode) {
        GL11.glLogicOp(opcode);
    }

    @Override
    public void hint(int target, int mode) {
        GL11.glHint(target, mode);
    }

    @Override
    public void drawArrays(int mode, int first, int count) {
        GL11.glDrawArrays(mode, first, count);
    }

    @Override
    public void drawElements(int mode, int count, int type, long indices) {
        GL11.glDrawElements(mode, count, type, indices);
    }

    @Override
    public void drawElements(int mode, ByteBuffer indices) {
        GL11.glDrawElements(mode, indices);
    }

    @Override
    public void drawElements(int mode, IntBuffer indices) {
        GL11.glDrawElements(mode, indices);
    }

    @Override
    public void drawElements(int mode, ShortBuffer indices) {
        GL11.glDrawElements(mode, indices);
    }

    @Override
    public void drawElements(int mode, int count, int type, ByteBuffer indices) {
        GL11.glDrawElements(mode, count, type, indices);
    }

    @Override
    public void drawElementsInstanced(int mode, int count, int type, long indices, int primcount) {
        GL31.glDrawElementsInstanced(mode, count, type, indices, primcount);
    }

    @Override
    public void multiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride) {
        org.lwjgl.opengl.GL43.glMultiDrawElementsIndirect(mode, type, indirect, drawcount, stride);
    }

    @Override
    public void copyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        throw new UnsupportedOperationException("GLRenderBackend.copyBufferSubData should not be called — LWJGL service handles this");
    }

    @Override
    public void drawElementsBaseVertex(int mode, int count, int type, long indices, int baseVertex) {
        // On GL path, LWJGL3 service dispatches directly. This is only called via VulkanRenderBackend.
        throw new UnsupportedOperationException("GLRenderBackend.drawElementsBaseVertex should not be called — LWJGL service handles this");
    }

    @Override
    public void multiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawcount, long pBaseVertex) {
        throw new UnsupportedOperationException("GLRenderBackend.multiDrawElementsBaseVertex should not be called — LWJGL service handles this");
    }

    @Override
    public void drawBuffer(int mode) {
        GL11.glDrawBuffer(mode);
    }

    @Override
    public void dispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ) {
        GL43.glDispatchCompute(numGroupsX, numGroupsY, numGroupsZ);
    }

    @Override
    public void dispatchComputeIndirect(long offset) {
        GL43.glDispatchComputeIndirect(offset);
    }

    @Override
    public int genTextures() {
        return GL11.glGenTextures();
    }

    @Override
    public void genTextures(IntBuffer textures) {
        GL11.glGenTextures(textures);
    }

    @Override
    public void deleteTextures(int texture) {
        GL11.glDeleteTextures(texture);
    }

    @Override
    public void bindTexture(int target, int texture) {
        GL11.glBindTexture(target, texture);
    }

    @Override
    public void activeTexture(int texture) {
        GL13.glActiveTexture(texture);
    }

    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, DoubleBuffer pixels) {
        GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, FloatBuffer pixels) {
        GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, long pixelBufferOffset) {
        GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixelBufferOffset);
    }

    @Override
    public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    @Override
    public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
        GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    @Override
    public void copyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        GL11.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }

    @Override
    public void texImage1D(int target, int level, int internalFormat, int width, int border, int format, int type, ByteBuffer pixels) {
        GL11.glTexImage1D(target, level, internalFormat, width, border, format, type, pixels);
    }

    @Override
    public void texImage3D(int target, int level, int internalFormat, int width, int height, int depth, int border, int format, int type, ByteBuffer pixels) {
        GL12.glTexImage3D(target, level, internalFormat, width, height, depth, border, format, type, pixels);
    }

    @Override
    public void texImage3D(int target, int level, int internalFormat, int width, int height, int depth, int border, int format, int type, IntBuffer pixels) {
        GL12.glTexImage3D(target, level, internalFormat, width, height, depth, border, format, type, pixels);
    }

    @Override
    public void texSubImage1D(int target, int level, int xoffset, int width, int format, int type, ByteBuffer pixels) {
        GL11.glTexSubImage1D(target, level, xoffset, width, format, type, pixels);
    }

    @Override
    public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, long pboOffset) {
        GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pboOffset);
    }

    @Override
    public void texSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type,
            ByteBuffer pixels) {
        GL12.glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, pixels);
    }

    @Override
    public void copyTexImage1D(int target, int level, int internalFormat, int x, int y, int width, int border) {
        GL11.glCopyTexImage1D(target, level, internalFormat, x, y, width, border);
    }

    @Override
    public void copyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
        GL11.glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border);
    }

    @Override
    public void copyTexSubImage1D(int target, int level, int xoffset, int x, int y, int width) {
        GL11.glCopyTexSubImage1D(target, level, xoffset, x, y, width);
    }

    @Override
    public void copyTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int x, int y, int width, int height) {
        GL12.glCopyTexSubImage3D(target, level, xoffset, yoffset, zoffset, x, y, width, height);
    }

    @Override
    public void texParameteri(int target, int pname, int param) {
        GL11.glTexParameteri(target, pname, param);
    }

    @Override
    public void texParameterf(int target, int pname, float param) {
        GL11.glTexParameterf(target, pname, param);
    }

    @Override
    public void texParameteriv(int target, int pname, IntBuffer params) {
        GL11.glTexParameter(target, pname, params);
    }

    @Override
    public void texParameterfv(int target, int pname, FloatBuffer params) {
        GL11.glTexParameter(target, pname, params);
    }

    @Override
    public int getTexParameteri(int target, int pname) {
        return GL11.glGetTexParameteri(target, pname);
    }

    @Override
    public float getTexParameterf(int target, int pname) {
        return GL11.glGetTexParameterf(target, pname);
    }

    @Override
    public int getTexLevelParameteri(int target, int level, int pname) {
        return GL11.glGetTexLevelParameteri(target, level, pname);
    }

    @Override
    public void generateMipmap(int target) {
        GL30.glGenerateMipmap(target);
    }

    @Override
    public void pixelStorei(int pname, int param) {
        GL11.glPixelStorei(pname, param);
    }

    @Override
    public void pixelStoref(int pname, float param) {
        GL11.glPixelStoref(pname, param);
    }

    @Override
    public void sampleCoverage(float value, boolean invert) {
        GL13.glSampleCoverage(value, invert);
    }

    @Override
    public int genSamplers() {
        return GL33.glGenSamplers();
    }

    @Override
    public void deleteSamplers(int sampler) {
        GL33.glDeleteSamplers(sampler);
    }

    @Override
    public void bindSampler(int unit, int sampler) {
        GL33.glBindSampler(unit, sampler);
    }

    @Override
    public void samplerParameteri(int sampler, int pname, int param) {
        GL33.glSamplerParameteri(sampler, pname, param);
    }

    @Override
    public void samplerParameterf(int sampler, int pname, float param) {
        GL33.glSamplerParameterf(sampler, pname, param);
    }

    @Override
    public int genFramebuffers() {
        return GL30.glGenFramebuffers();
    }

    @Override
    public void deleteFramebuffers(int framebuffer) {
        GL30.glDeleteFramebuffers(framebuffer);
    }

    @Override
    public void bindFramebuffer(int target, int framebuffer) {
        GL30.glBindFramebuffer(target, framebuffer);
    }

    @Override
    public void framebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }

    @Override
    public void framebufferTexture(int target, int attachment, int texture, int level) {
        GL32.glFramebufferTexture(target, attachment, texture, level);
    }

    @Override
    public int checkFramebufferStatus(int target) {
        return GL30.glCheckFramebufferStatus(target);
    }

    @Override
    public void blitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    @Override
    public void drawBuffers(int buffer) {
        GL20.glDrawBuffers(buffer);
    }

    @Override
    public void drawBuffers(IntBuffer bufs) {
        GL20.glDrawBuffers(bufs);
    }

    @Override
    public void readBuffer(int mode) {
        GL11.glReadBuffer(mode);
    }

    @Override
    public void readPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        GL11.glReadPixels(x, y, width, height, format, type, pixels);
    }

    @Override
    public void readPixels(int x, int y, int width, int height, int format, int type, FloatBuffer pixels) {
        GL11.glReadPixels(x, y, width, height, format, type, pixels);
    }

    @Override
    public void readPixels(int x, int y, int width, int height, int format, int type, IntBuffer pixels) {
        GL11.glReadPixels(x, y, width, height, format, type, pixels);
    }

    @Override
    public void getTexImage(int target, int level, int format, int type, ByteBuffer pixels) {
        GL11.glGetTexImage(target, level, format, type, pixels);
    }

    @Override
    public void getTexImage(int target, int level, int format, int type, IntBuffer pixels) {
        GL11.glGetTexImage(target, level, format, type, pixels);
    }

    @Override
    public int getFramebufferAttachmentParameteri(int target, int attachment, int pname) {
        return GL30.glGetFramebufferAttachmentParameteri(target, attachment, pname);
    }

    @Override
    public int createShader(int type) {
        return GL20.glCreateShader(type);
    }

    @Override
    public void deleteShader(int shader) {
        GL20.glDeleteShader(shader);
    }

    @Override
    public void shaderSource(int shader, CharSequence source) {
        GL20.glShaderSource(shader, source);
    }

    @Override
    public void compileShader(int shader) {
        GL20.glCompileShader(shader);
    }

    @Override
    public int createProgram() {
        return GL20.glCreateProgram();
    }

    @Override
    public void deleteProgram(int program) {
        GL20.glDeleteProgram(program);
    }

    @Override
    public void attachShader(int program, int shader) {
        GL20.glAttachShader(program, shader);
    }

    @Override
    public void detachShader(int program, int shader) {
        GL20.glDetachShader(program, shader);
    }

    @Override
    public void linkProgram(int program) {
        GL20.glLinkProgram(program);
    }

    @Override
    public void useProgram(int program) {
        GL20.glUseProgram(program);
    }

    @Override
    public String getShaderInfoLog(int shader, int maxLength) {
        return GL20.glGetShaderInfoLog(shader, maxLength);
    }

    @Override
    public void getShaderInfoLog(int shader, IntBuffer length, ByteBuffer infoLog) {
        GL20.glGetShaderInfoLog(shader, length, infoLog);
    }

    @Override
    public String getProgramInfoLog(int program, int maxLength) {
        return GL20.glGetProgramInfoLog(program, maxLength);
    }

    @Override
    public void getProgramInfoLog(int program, IntBuffer length, ByteBuffer infoLog) {
        GL20.glGetProgramInfoLog(program, length, infoLog);
    }

    @Override
    public int getShaderi(int shader, int pname) {
        return GL20.glGetShaderi(shader, pname);
    }

    @Override
    public int getProgrami(int program, int pname) {
        return GL20.glGetProgrami(program, pname);
    }

    @Override
    public void getProgramiv(int program, int pname, IntBuffer params) {
        GL20.glGetProgram(program, pname, params);
    }

    @Override
    public String getActiveUniform(int program, int index, int maxLength, IntBuffer sizeType) {
        return GL20.glGetActiveUniform(program, index, maxLength, sizeType);
    }

    @Override
    public void getActiveUniform(int program, int index, IntBuffer length, IntBuffer size, IntBuffer type, ByteBuffer name) {
        GL20.glGetActiveUniform(program, index, length, size, type, name);
    }

    @Override
    public void bindAttribLocation(int program, int index, CharSequence name) {
        GL20.glBindAttribLocation(program, index, name);
    }

    @Override
    public int getAttribLocation(int program, CharSequence name) {
        return GL20.glGetAttribLocation(program, name);
    }

    @Override
    public int getAttribLocation(int program, ByteBuffer name) {
        return GL20.glGetAttribLocation(program, name);
    }

    @Override
    public int getUniformLocation(int program, CharSequence name) {
        return GL20.glGetUniformLocation(program, name);
    }

    @Override
    public int getUniformLocation(int program, ByteBuffer name) {
        return GL20.glGetUniformLocation(program, name);
    }

    @Override
    public boolean isShader(int obj) {
        return GL20.glIsShader(obj);
    }

    @Override
    public boolean isProgram(int obj) {
        return GL20.glIsProgram(obj);
    }

    @Override
    public void validateProgram(int program) {
        GL20.glValidateProgram(program);
    }

    @Override
    public void getAttachedShaders(int program, IntBuffer count, IntBuffer shaders) {
        GL20.glGetAttachedShaders(program, count, shaders);
    }

    @Override
    public String getShaderSource(int shader, int maxLength) {
        return GL20.glGetShaderSource(shader, maxLength);
    }

    @Override
    public void getShaderSource(int shader, IntBuffer length, ByteBuffer source) {
        GL20.glGetShaderSource(shader, length, source);
    }

    @Override
    public void uniform1i(int location, int v0) {
        GL20.glUniform1i(location, v0);
    }

    @Override
    public void uniform1f(int location, float v0) {
        GL20.glUniform1f(location, v0);
    }

    @Override
    public void uniform1fv(int location, FloatBuffer values) {
        GL20.glUniform1(location, values);
    }

    @Override
    public void uniform2f(int location, float v0, float v1) {
        GL20.glUniform2f(location, v0, v1);
    }

    @Override
    public void uniform2i(int location, int v0, int v1) {
        GL20.glUniform2i(location, v0, v1);
    }

    @Override
    public void uniform3f(int location, float v0, float v1, float v2) {
        GL20.glUniform3f(location, v0, v1, v2);
    }

    @Override
    public void uniform3i(int location, int v0, int v1, int v2) {
        GL20.glUniform3i(location, v0, v1, v2);
    }

    @Override
    public void uniform4f(int location, float v0, float v1, float v2, float v3) {
        GL20.glUniform4f(location, v0, v1, v2, v3);
    }

    @Override
    public void uniform4i(int location, int v0, int v1, int v2, int v3) {
        GL20.glUniform4i(location, v0, v1, v2, v3);
    }

    @Override
    public void uniform3(int location, FloatBuffer value) {
        GL20.glUniform3(location, value);
    }

    @Override
    public void uniform4(int location, FloatBuffer value) {
        GL20.glUniform4(location, value);
    }

    @Override
    public void uniformMatrix3(int location, boolean transpose, FloatBuffer value) {
        GL20.glUniformMatrix3(location, transpose, value);
    }

    @Override
    public void uniformMatrix4(int location, boolean transpose, FloatBuffer value) {
        GL20.glUniformMatrix4(location, transpose, value);
    }

    @Override
    public void uniformMatrix2(int location, boolean transpose, FloatBuffer matrices) {
        GL20.glUniformMatrix2(location, transpose, matrices);
    }

    @Override
    public void getUniformfv(int program, int location, FloatBuffer params) {
        GL20.glGetUniform(program, location, params);
    }

    @Override
    public void getUniformiv(int program, int location, IntBuffer params) {
        GL20.glGetUniform(program, location, params);
    }

    @Override
    public void vertexAttrib2f(int index, float v0, float v1) {
        GL20.glVertexAttrib2f(index, v0, v1);
    }

    @Override
    public void vertexAttrib3f(int index, float v0, float v1, float v2) {
        GL20.glVertexAttrib3f(index, v0, v1, v2);
    }

    @Override
    public void vertexAttrib4f(int index, float v0, float v1, float v2, float v3) {
        GL20.glVertexAttrib4f(index, v0, v1, v2, v3);
    }

    @Override
    public int genBuffers() {
        return GL15.glGenBuffers();
    }

    @Override
    public void deleteBuffers(int buffer) {
        GL15.glDeleteBuffers(buffer);
    }

    @Override
    public void deleteBuffers(IntBuffer buffers) {
        GL15.glDeleteBuffers(buffers);
    }

    @Override
    public void bindBuffer(int target, int buffer) {
        GL15.glBindBuffer(target, buffer);
    }

    @Override
    public void bindBufferBase(int target, int index, int buffer) {
        GL30.glBindBufferBase(target, index, buffer);
    }

    @Override
    public void bufferData(int target, long size, int usage) {
        GL15.glBufferData(target, size, usage);
    }

    @Override
    public void bufferData(int target, ByteBuffer data, int usage) {
        GL15.glBufferData(target, data, usage);
    }

    @Override
    public void bufferData(int target, FloatBuffer data, int usage) {
        GL15.glBufferData(target, data, usage);
    }

    @Override
    public void bufferData(int target, ShortBuffer data, int usage) {
        GL15.glBufferData(target, data, usage);
    }

    @Override
    public void bufferData(int target, IntBuffer data, int usage) {
        GL15.glBufferData(target, data, usage);
    }

    @Override
    public void bufferData(int target, DoubleBuffer data, int usage) {
        GL15.glBufferData(target, data, usage);
    }

    @Override
    public void bufferData(int target, int[] data, int usage) {
        final IntBuffer buffer = getIntArrayBuffer(data.length);
        buffer.put(data);
        buffer.flip();
        GL15.glBufferData(target, buffer, usage);
    }

    @Override
    public void bufferData(int target, float[] data, int usage) {
        final FloatBuffer buffer = getFloatArrayBuffer(data.length);
        buffer.put(data);
        buffer.flip();
        GL15.glBufferData(target, buffer, usage);
    }

    @Override
    public void bufferSubData(int target, long offset, ByteBuffer data) {
        GL15.glBufferSubData(target, offset, data);
    }

    @Override
    public void bufferSubData(int target, long offset, ShortBuffer data) {
        GL15.glBufferSubData(target, offset, data);
    }

    @Override
    public void bufferSubData(int target, long offset, IntBuffer data) {
        GL15.glBufferSubData(target, offset, data);
    }

    @Override
    public void bufferSubData(int target, long offset, FloatBuffer data) {
        GL15.glBufferSubData(target, offset, data);
    }

    @Override
    public void bufferSubData(int target, long offset, DoubleBuffer data) {
        GL15.glBufferSubData(target, offset, data);
    }

    @Override
    public ByteBuffer mapBuffer(int target, int access) {
        return GL15.glMapBuffer(target, access, null);
    }

    @Override
    public ByteBuffer mapBuffer(int target, int access, long length, ByteBuffer old_buffer) {
        return GL15.glMapBuffer(target, access, length, old_buffer);
    }

    @Override
    public boolean unmapBuffer(int target) {
        return GL15.glUnmapBuffer(target);
    }

    @Override
    public void bufferStorage(int target, ByteBuffer data, int flags) {
        GL44.glBufferStorage(target, data, flags);
    }

    @Override
    public void bufferStorage(int target, long size, int flags) {
        GL44.glBufferStorage(target, size, flags);
    }

    @Override
    public void getBufferSubData(int target, long offset, ByteBuffer data) {
        GL15.glGetBufferSubData(target, offset, data);
    }

    @Override
    public void getBufferSubData(int target, long offset, ShortBuffer data) {
        GL15.glGetBufferSubData(target, offset, data);
    }

    @Override
    public void getBufferSubData(int target, long offset, IntBuffer data) {
        GL15.glGetBufferSubData(target, offset, data);
    }

    @Override
    public void getBufferSubData(int target, long offset, FloatBuffer data) {
        GL15.glGetBufferSubData(target, offset, data);
    }

    @Override
    public void getBufferSubData(int target, long offset, DoubleBuffer data) {
        GL15.glGetBufferSubData(target, offset, data);
    }

    @Override
    public int getBufferParameteri(int target, int pname) {
        return GL15.glGetBufferParameteri(target, pname);
    }

    @Override
    public boolean isBuffer(int buffer) {
        return GL15.glIsBuffer(buffer);
    }

    @Override
    public ByteBuffer mapBufferRange(int target, long offset, long length, int access) {
        return GL30.glMapBufferRange(target, offset, length, access, null);
    }

    @Override
    public int genVertexArrays() {
        return GL30.glGenVertexArrays();
    }

    @Override
    public void deleteVertexArrays(int array) {
        GL30.glDeleteVertexArrays(array);
    }

    @Override
    public void bindVertexArray(int array) {
        GL30.glBindVertexArray(array);
    }

    @Override
    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        GL20.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }

    @Override
    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, ByteBuffer pointer) {
        GL20.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }

    @Override
    public void vertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        GL30.glVertexAttribIPointer(index, size, type, stride, pointer);
    }

    @Override
    public void vertexAttribIPointer(int index, int size, int type, int stride, ByteBuffer pointer) {
        GL30.glVertexAttribIPointer(index, size, type, stride, pointer);
    }

    @Override
    public void enableVertexAttribArray(int index) {
        GL20.glEnableVertexAttribArray(index);
    }

    @Override
    public void disableVertexAttribArray(int index) {
        GL20.glDisableVertexAttribArray(index);
    }

    @Override
    public void vertexAttribDivisor(int index, int divisor) {
        GL33.glVertexAttribDivisor(index, divisor);
    }

    @Override
    public void bindVertexBuffer(int bindingindex, int buffer, long offset, int stride) {
        GL43.glBindVertexBuffer(bindingindex, buffer, offset, stride);
    }

    @Override
    public void vertexAttribFormat(int attribindex, int size, int type, boolean normalized, int relativeoffset) {
        GL43.glVertexAttribFormat(attribindex, size, type, normalized, relativeoffset);
    }

    @Override
    public void vertexAttribIFormat(int attribindex, int size, int type, int relativeoffset) {
        GL43.glVertexAttribIFormat(attribindex, size, type, relativeoffset);
    }

    @Override
    public void vertexAttribBinding(int attribindex, int bindingindex) {
        GL43.glVertexAttribBinding(attribindex, bindingindex);
    }

    @Override
    public int createTextures(int target) {
        return ARBDirectStateAccess.glCreateTextures(target);
    }

    @Override
    public void bindTextureUnit(int unit, int texture) {
        ARBDirectStateAccess.glBindTextureUnit(unit, texture);
    }

    @Override
    public void textureParameteri(int texture, int target, int pname, int param) {
        ARBDirectStateAccess.glTextureParameteri(texture, pname, param);
    }

    @Override
    public void textureParameterf(int texture, int target, int pname, float param) {
        ARBDirectStateAccess.glTextureParameterf(texture, pname, param);
    }

    @Override
    public void textureParameteriv(int texture, int target, int pname, IntBuffer params) {
        ARBDirectStateAccess.glTextureParameter(texture, pname, params);
    }

    @Override
    public void texStorage2D(int target, int levels, int internalFormat, int width, int height) {
        GL42.glTexStorage2D(target, levels, internalFormat, width, height);
    }

    @Override
    public void textureStorage2D(int texture, int levels, int internalFormat, int width, int height) {
        ARBDirectStateAccess.glTextureStorage2D(texture, levels, internalFormat, width, height);
    }

    @Override
    public void texStorage1D(int target, int levels, int internalFormat, int width) {
        GL42.glTexStorage1D(target, levels, internalFormat, width);
    }

    @Override
    public void texStorage3D(int target, int levels, int internalFormat, int width, int height, int depth) {
        GL42.glTexStorage3D(target, levels, internalFormat, width, height, depth);
    }

    @Override
    public void textureStorage1D(int texture, int levels, int internalFormat, int width) {
        ARBDirectStateAccess.glTextureStorage1D(texture, levels, internalFormat, width);
    }

    @Override
    public void textureStorage3D(int texture, int levels, int internalFormat, int width, int height, int depth) {
        ARBDirectStateAccess.glTextureStorage3D(texture, levels, internalFormat, width, height, depth);
    }

    @Override
    public void generateTextureMipmap(int texture) {
        ARBDirectStateAccess.glGenerateTextureMipmap(texture);
    }

    @Override
    public void textureImage2DEXT(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        EXTDirectStateAccess.glTextureImage2DEXT(texture, target, level, internalformat, width, height, border, format, type, pixels);
    }

    @Override
    public void textureImage2DEXT(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        EXTDirectStateAccess.glTextureImage2DEXT(texture, target, level, internalformat, width, height, border, format, type, pixels);
    }

    @Override
    public void namedBufferData(int buffer, FloatBuffer data, int usage) {
        GL45.glNamedBufferData(buffer, data, usage);
    }

    @Override
    public void textureSubImage2D(int texture, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        ARBDirectStateAccess.glTextureSubImage2D(texture, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    @Override
    public void textureSubImage2D(int texture, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
        ARBDirectStateAccess.glTextureSubImage2D(texture, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    @Override
    public int createFramebuffers() {
        return ARBDirectStateAccess.glCreateFramebuffers();
    }

    @Override
    public void namedFramebufferTexture(int framebuffer, int attachment, int texture, int level) {
        ARBDirectStateAccess.glNamedFramebufferTexture(framebuffer, attachment, texture, level);
    }

    @Override
    public void namedFramebufferReadBuffer(int framebuffer, int mode) {
        ARBDirectStateAccess.glNamedFramebufferReadBuffer(framebuffer, mode);
    }

    @Override
    public void namedFramebufferDrawBuffers(int framebuffer, IntBuffer bufs) {
        ARBDirectStateAccess.glNamedFramebufferDrawBuffers(framebuffer, bufs);
    }

    @Override
    public void blitNamedFramebuffer(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1,
            int dstY1, int mask, int filter) {
        ARBDirectStateAccess.glBlitNamedFramebuffer(readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    @Override
    public int createBuffers() {
        return GL45.glCreateBuffers();
    }

    @Override
    public void namedBufferData(int buffer, ByteBuffer data, int usage) {
        GL45.glNamedBufferData(buffer, data, usage);
    }

    @Override
    public void namedBufferSubData(int buffer, long offset, ByteBuffer data) {
        GL45.glNamedBufferSubData(buffer, offset, data);
    }

    @Override
    public void copyTextureSubImage2D(int texture, int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        ARBDirectStateAccess.glCopyTextureSubImage2D(texture, level, xoffset, yoffset, x, y, width, height);
    }

    @Override
    public int getTextureParameteri(int texture, int target, int pname) {
        return ARBDirectStateAccess.glGetTextureParameteri(texture, pname);
    }

    @Override
    public int getTextureLevelParameteri(int texture, int level, int pname) {
        return ARBDirectStateAccess.glGetTextureLevelParameteri(texture, level, pname);
    }

    @Override
    public int getInteger(int pname) {
        return GL11.glGetInteger(pname);
    }

    @Override
    public void getInteger(int pname, IntBuffer params) {
        GL11.glGetInteger(pname, params);
    }

    @Override
    public float getFloat(int pname) {
        return GL11.glGetFloat(pname);
    }

    @Override
    public void getFloat(int pname, FloatBuffer params) {
        GL11.glGetFloat(pname, params);
    }

    @Override
    public boolean getBoolean(int pname) {
        return GL11.glGetBoolean(pname);
    }

    @Override
    public void getBoolean(int pname, ByteBuffer params) {
        GL11.glGetBoolean(pname, params);
    }

    @Override
    public String getString(int pname) {
        return GL11.glGetString(pname);
    }

    @Override
    public String getStringi(int name, int index) {
        return GL30.glGetStringi(name, index);
    }

    @Override
    public int getError() {
        return GL11.glGetError();
    }

    // LWJGL2 uses GLSync objects, not longs. The LWJGL service in celeritas already handles this, so we delegate
    @Override
    public long fenceSync(int condition, int flags) {
        return LWJGL.glFenceSync(condition, flags);
    }

    @Override
    public int clientWaitSync(long sync, int flags, long timeout) {
        return LWJGL.glClientWaitSync(sync, flags, timeout);
    }

    @Override
    public void deleteSync(long sync) {
        LWJGL.glDeleteSync(sync);
    }

    @Override
    public void clearBufferSubData(int target, int internalFormat, long offset, long size, int format, int type, ByteBuffer data) {
        GL43.glClearBufferSubData(target, internalFormat, offset, size, format, type, data);
    }

    @Override
    public void clearTexImage(int texture, int level, int format, int type) {
        if (caps.OpenGL44) {
            GL44.glClearTexImage(texture, level, format, type, (ByteBuffer) null);
        } else {
            ARBClearTexture.glClearTexImage(texture, level, format, type, (ByteBuffer) null);
        }
    }

    @Override
    public void bindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        GL42.glBindImageTexture(unit, texture, level, layered, layer, access, format);
    }

    @Override
    public void memoryBarrier(int barriers) {
        GL42.glMemoryBarrier(barriers);
    }

    @Override
    public void copyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ,
                                  int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ,
                                  int srcWidth, int srcHeight, int srcDepth) {
        GL43.glCopyImageSubData(srcName, srcTarget, srcLevel, srcX, srcY, srcZ,
            dstName, dstTarget, dstLevel, dstX, dstY, dstZ,
            srcWidth, srcHeight, srcDepth);
    }

    @Override
    public boolean supportsDebugOutput() {
        return caps.OpenGL43 || caps.GL_KHR_debug || caps.GL_ARB_debug_output || caps.GL_AMD_debug_output;
    }

    @Override
    public int setupDebugOutput(DebugMessageHandler handler) {
        if (caps.OpenGL43 || caps.GL_KHR_debug) {
            khrCallback = new KHRDebugCallback((source, type, id, severity, message) -> handler.handleMessage(source, type, id, severity, message));
            GL43.glDebugMessageControl(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_HIGH, null, true);
            GL43.glDebugMessageControl(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_MEDIUM, null, false);
            GL43.glDebugMessageControl(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_LOW, null, false);
            GL43.glDebugMessageControl(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_NOTIFICATION, null, false);
            GL43.glDebugMessageCallback(khrCallback);
            GL11.glEnable(GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
            activeDebugExtension = 1;

            if ((GL11.glGetInteger(GL30.GL_CONTEXT_FLAGS) & GL43.GL_CONTEXT_FLAG_DEBUG_BIT) == 0) {
                GL11.glEnable(GL43.GL_DEBUG_OUTPUT);
                return 2;
            }
            return 1;
        } else if (caps.GL_ARB_debug_output) {
            arbCallback = new ARBDebugOutputCallback((source, type, id, severity, message) -> handler.handleMessage(source, type, id, severity, message));
            ARBDebugOutput.glDebugMessageControlARB(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_HIGH, null, true);
            ARBDebugOutput.glDebugMessageControlARB(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_MEDIUM, null, false);
            ARBDebugOutput.glDebugMessageControlARB(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_LOW, null, false);
            ARBDebugOutput.glDebugMessageControlARB(GL11.GL_DONT_CARE, GL11.GL_DONT_CARE, GL43.GL_DEBUG_SEVERITY_NOTIFICATION, null, false);
            ARBDebugOutput.glDebugMessageCallbackARB(arbCallback);
            GL11.glEnable(ARBDebugOutput.GL_DEBUG_OUTPUT_SYNCHRONOUS_ARB);
            activeDebugExtension = 2;
            return 1;
        } else if (caps.GL_AMD_debug_output) {
            amdCallback = new AMDDebugOutputCallback((id, category, severity, message) -> {
                // Map AMD severity to GL43 severity constants (values are identical, but be explicit)
                int mappedSeverity = switch (severity) {
                    case AMDDebugOutput.GL_DEBUG_SEVERITY_HIGH_AMD -> GL43.GL_DEBUG_SEVERITY_HIGH;
                    case AMDDebugOutput.GL_DEBUG_SEVERITY_MEDIUM_AMD -> GL43.GL_DEBUG_SEVERITY_MEDIUM;
                    case AMDDebugOutput.GL_DEBUG_SEVERITY_LOW_AMD -> GL43.GL_DEBUG_SEVERITY_LOW;
                    default -> GL43.GL_DEBUG_SEVERITY_NOTIFICATION;
                };
                String categoryName = switch (category) {
                    case AMDDebugOutput.GL_DEBUG_CATEGORY_API_ERROR_AMD -> "API_ERROR";
                    case AMDDebugOutput.GL_DEBUG_CATEGORY_WINDOW_SYSTEM_AMD -> "WINDOW_SYSTEM";
                    case AMDDebugOutput.GL_DEBUG_CATEGORY_DEPRECATION_AMD -> "DEPRECATION";
                    case AMDDebugOutput.GL_DEBUG_CATEGORY_UNDEFINED_BEHAVIOR_AMD -> "UNDEFINED_BEHAVIOR";
                    case AMDDebugOutput.GL_DEBUG_CATEGORY_PERFORMANCE_AMD -> "PERFORMANCE";
                    case AMDDebugOutput.GL_DEBUG_CATEGORY_SHADER_COMPILER_AMD -> "SHADER_COMPILER";
                    case AMDDebugOutput.GL_DEBUG_CATEGORY_APPLICATION_AMD -> "APPLICATION";
                    case AMDDebugOutput.GL_DEBUG_CATEGORY_OTHER_AMD -> "OTHER";
                    default -> "UNKNOWN_" + category;
                };
                handler.handleMessage(
                    GL43.GL_DEBUG_SOURCE_OTHER, // AMD doesn't distinguish source
                    GL43.GL_DEBUG_TYPE_OTHER,   // AMD uses category instead of type
                    id,
                    mappedSeverity,
                    "[AMD cat=" + categoryName + "] " + message);
            });
            AMDDebugOutput.glDebugMessageEnableAMD(0, GL43.GL_DEBUG_SEVERITY_HIGH, null, true);
            AMDDebugOutput.glDebugMessageEnableAMD(0, GL43.GL_DEBUG_SEVERITY_MEDIUM, null, false);
            AMDDebugOutput.glDebugMessageEnableAMD(0, GL43.GL_DEBUG_SEVERITY_LOW, null, false);
            AMDDebugOutput.glDebugMessageEnableAMD(0, GL43.GL_DEBUG_SEVERITY_NOTIFICATION, null, false);
            AMDDebugOutput.glDebugMessageCallbackAMD(amdCallback);
            activeDebugExtension = 3;
            return 1;
        }
        return 0;
    }

    @Override
    public int disableDebugOutput() {
        switch (activeDebugExtension) {
            case 1 -> {
                if (caps.OpenGL43) {
                    GL43.glDebugMessageCallback(null);
                } else {
                    KHRDebug.glDebugMessageCallback(null);
                    if (caps.OpenGL30 && (GL11.glGetInteger(GL30.GL_CONTEXT_FLAGS) & GL43.GL_CONTEXT_FLAG_DEBUG_BIT) == 0) {
                        GL11.glDisable(GL43.GL_DEBUG_OUTPUT);
                    }
                }
                khrCallback = null;
            }
            case 2 -> {
                ARBDebugOutput.glDebugMessageCallbackARB(null);
                arbCallback = null;
            }
            case 3 -> {
                AMDDebugOutput.glDebugMessageCallbackAMD(null);
                amdCallback = null;
            }
            default -> {
                return 0;
            }
        }
        activeDebugExtension = 0;
        return 1;
    }

    @Override
    public void objectLabel(int identifier, int name, CharSequence label) {
        if (activeDebugExtension == 1) {
            KHRDebug.glObjectLabel(identifier, name, label);
        }
    }

    @Override
    public String getObjectLabel(int identifier, int name) {
        if (activeDebugExtension == 1) {
            int maxLen = GL11.glGetInteger(KHRDebug.GL_MAX_LABEL_LENGTH);
            return KHRDebug.glGetObjectLabel(identifier, name, maxLen);
        }
        return "";
    }

    @Override
    public void pushDebugGroup(int source, int id, CharSequence message) {
        if (activeDebugExtension == 1) {
            KHRDebug.glPushDebugGroup(source, id, message);
        }
    }

    @Override
    public void popDebugGroup() {
        if (activeDebugExtension == 1) {
            KHRDebug.glPopDebugGroup();
        }
    }

    @Override
    public void debugMessageInsert(int source, int type, int id, int severity, CharSequence message) {
        if (activeDebugExtension == 1) {
            KHRDebug.glDebugMessageInsert(source, type, id, severity, message);
        }
    }

}

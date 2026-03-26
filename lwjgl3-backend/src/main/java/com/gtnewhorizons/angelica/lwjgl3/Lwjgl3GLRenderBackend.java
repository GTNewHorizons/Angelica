package com.gtnewhorizons.angelica.lwjgl3;

import com.gtnewhorizons.angelica.glsm.backend.DebugMessageHandler;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import org.lwjgl.opengl.ARBClearTexture;
import org.lwjgl.opengl.EXTDirectStateAccess;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL40C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL44C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.ShortBuffer;

/**
 * LWJGL3 GL implementation of {@link RenderBackend}.
 *
 * NOTE: This is _not_ redirected by lwjgl3ify at runtime and targets lwjgl3 directly
 */
@Lwjgl3Aware
public final class Lwjgl3GLRenderBackend extends RenderBackend {
    private GLCapabilities caps;
    private GLDebugMessageCallback debugCallback;
    private boolean debugOutputActive;

    @Override
    public void init() {
        caps = GL.getCapabilities();
    }

    @Override
    public void shutdown() {
        // no-op
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("org.lwjgl.opengl.GL31C");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public String getName() {
        return "OpenGL (LWJGL3)";
    }

    @Override
    public int getPriority() { return 50; }

    @Override
    public int getMinGLSLVersion() {return 330;}

    @Override
    public void flush() {GL11C.glFlush();}

    @Override
    public void finish() {GL11C.glFinish();}

    @Override
    public void enable(int cap) {
        GL11C.glEnable(cap);
    }

    @Override
    public void enablei(int cap, int index) {
        GL30C.glEnablei(cap, index);
    }

    @Override
    public void disable(int cap) {
        GL11C.glDisable(cap);
    }

    @Override
    public void disablei(int cap, int index) {
        GL30C.glDisablei(cap, index);
    }

    @Override
    public void blendFuncSeparatei(int buf, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        GL40C.glBlendFuncSeparatei(buf, srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    @Override
    public void blendFunc(int sfactor, int dfactor) {
        GL11C.glBlendFunc(sfactor, dfactor);
    }

    @Override
    public void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        GL14C.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    @Override
    public void blendEquation(int mode) {
        GL14C.glBlendEquation(mode);
    }

    @Override
    public void blendEquationSeparate(int modeRGB, int modeAlpha) {
        GL20C.glBlendEquationSeparate(modeRGB, modeAlpha);
    }

    @Override
    public void blendColor(float red, float green, float blue, float alpha) {
        GL14C.glBlendColor(red, green, blue, alpha);
    }

    @Override
    public void depthFunc(int func) {
        GL11C.glDepthFunc(func);
    }

    @Override
    public void depthMask(boolean flag) {
        GL11C.glDepthMask(flag);
    }

    @Override
    public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GL11C.glColorMask(red, green, blue, alpha);
    }

    @Override
    public void cullFace(int mode) {
        GL11C.glCullFace(mode);
    }

    @Override
    public void frontFace(int mode) {
        GL11C.glFrontFace(mode);
    }

    @Override
    public void polygonMode(int face, int mode) {
        GL11C.glPolygonMode(face, mode);
    }

    @Override
    public void polygonOffset(float factor, float units) {
        GL11C.glPolygonOffset(factor, units);
    }

    @Override
    public void stencilFunc(int func, int ref, int mask) {
        GL11C.glStencilFunc(func, ref, mask);
    }

    @Override
    public void stencilOp(int sfail, int dpfail, int dppass) {
        GL11C.glStencilOp(sfail, dpfail, dppass);
    }

    @Override
    public void stencilMask(int mask) {
        GL11C.glStencilMask(mask);
    }

    @Override
    public void stencilFuncSeparate(int face, int func, int ref, int mask) {
        GL20C.glStencilFuncSeparate(face, func, ref, mask);
    }

    @Override
    public void stencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        GL20C.glStencilOpSeparate(face, sfail, dpfail, dppass);
    }

    @Override
    public void stencilMaskSeparate(int face, int mask) {
        GL20C.glStencilMaskSeparate(face, mask);
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        GL11C.glViewport(x, y, width, height);
    }

    @Override
    public void depthRange(double nearVal, double farVal) {
        GL11C.glDepthRange(nearVal, farVal);
    }

    @Override
    public void scissor(int x, int y, int width, int height) {
        GL11C.glScissor(x, y, width, height);
    }

    @Override
    public void clearColor(float red, float green, float blue, float alpha) {
        GL11C.glClearColor(red, green, blue, alpha);
    }

    @Override
    public void clearDepth(double depth) {
        GL11C.glClearDepth(depth);
    }

    @Override
    public void clearStencil(int s) {
        GL11C.glClearStencil(s);
    }

    @Override
    public void clear(int mask) {
        GL11C.glClear(mask);
    }

    @Override
    public void lineWidth(float width) {
        GL11C.glLineWidth(width);
    }

    @Override
    public void pointSize(float size) {
        GL11C.glPointSize(size);
    }

    @Override
    public void logicOp(int opcode) {
        GL11C.glLogicOp(opcode);
    }

    @Override
    public void hint(int target, int mode) {
        GL11C.glHint(target, mode);
    }

    @Override
    public void drawArrays(int mode, int first, int count) {
        GL11C.glDrawArrays(mode, first, count);
    }

    @Override
    public void drawElements(int mode, int count, int type, long indices) {
        GL11C.glDrawElements(mode, count, type, indices);
    }

    @Override
    public void drawElements(int mode, ByteBuffer indices) {
        GL11C.glDrawElements(mode, indices);
    }

    @Override
    public void drawElements(int mode, IntBuffer indices) {
        GL11C.glDrawElements(mode, indices);
    }

    @Override
    public void drawElements(int mode, ShortBuffer indices) {
        GL11C.glDrawElements(mode, indices);
    }

    @Override
    public void drawElements(int mode, int count, int type, ByteBuffer indices) {
        GL11C.glDrawElements(mode, count, type, org.lwjgl.system.MemoryUtil.memAddress(indices));
    }

    @Override
    public void drawElementsInstanced(int mode, int count, int type, long indices, int primcount) {
        GL31C.glDrawElementsInstanced(mode, count, type, indices, primcount);
    }

    @Override
    public void multiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride) {
        GL43C.glMultiDrawElementsIndirect(mode, type, indirect, drawcount, stride);
    }

    @Override
    public void copyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        GL31C.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }

    @Override
    public void drawElementsBaseVertex(int mode, int count, int type, long indices, int baseVertex) {
        GL32C.glDrawElementsBaseVertex(mode, count, type, indices, baseVertex);
    }

    @Override
    public void multiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawcount, long pBaseVertex) {
        GL32C.nglMultiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawcount, pBaseVertex);
    }

    @Override
    public void drawBuffer(int mode) {
        GL11C.glDrawBuffer(mode);
    }

    @Override
    public void dispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ) {
        GL43C.glDispatchCompute(numGroupsX, numGroupsY, numGroupsZ);
    }

    @Override
    public void dispatchComputeIndirect(long offset) {
        GL43C.glDispatchComputeIndirect(offset);
    }

    @Override
    public int genTextures() {
        return GL11C.glGenTextures();
    }

    @Override
    public void genTextures(IntBuffer textures) {
        GL11C.glGenTextures(textures);
    }

    @Override
    public void deleteTextures(int texture) {
        GL11C.glDeleteTextures(texture);
    }

    @Override
    public void bindTexture(int target, int texture) {
        GL11C.glBindTexture(target, texture);
    }

    @Override
    public void activeTexture(int texture) {
        GL13C.glActiveTexture(texture);
    }

    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        GL11C.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, DoubleBuffer pixels) {
        GL11C.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, FloatBuffer pixels) {
        GL11C.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        GL11C.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, long pixelBufferOffset) {
        GL11C.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixelBufferOffset);
    }

    @Override
    public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        GL11C.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    @Override
    public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
        GL11C.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    @Override
    public void copyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        GL11C.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }

    @Override
    public void texImage1D(int target, int level, int internalFormat, int width, int border, int format, int type, ByteBuffer pixels) {
        GL11C.glTexImage1D(target, level, internalFormat, width, border, format, type, pixels);
    }

    @Override
    public void texImage3D(int target, int level, int internalFormat, int width, int height, int depth, int border, int format, int type, ByteBuffer pixels) {
        GL12C.glTexImage3D(target, level, internalFormat, width, height, depth, border, format, type, pixels);
    }

    @Override
    public void texImage3D(int target, int level, int internalFormat, int width, int height, int depth, int border, int format, int type, IntBuffer pixels) {
        GL12C.glTexImage3D(target, level, internalFormat, width, height, depth, border, format, type, pixels);
    }

    @Override
    public void texSubImage1D(int target, int level, int xoffset, int width, int format, int type, ByteBuffer pixels) {
        GL11C.glTexSubImage1D(target, level, xoffset, width, format, type, pixels);
    }

    @Override
    public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, long pboOffset) {
        GL11C.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pboOffset);
    }

    @Override
    public void texSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type,
            ByteBuffer pixels) {
        GL12C.glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, pixels);
    }

    @Override
    public void copyTexImage1D(int target, int level, int internalFormat, int x, int y, int width, int border) {
        GL11C.glCopyTexImage1D(target, level, internalFormat, x, y, width, border);
    }

    @Override
    public void copyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
        GL11C.glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border);
    }

    @Override
    public void copyTexSubImage1D(int target, int level, int xoffset, int x, int y, int width) {
        GL11C.glCopyTexSubImage1D(target, level, xoffset, x, y, width);
    }

    @Override
    public void copyTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int x, int y, int width, int height) {
        GL12C.glCopyTexSubImage3D(target, level, xoffset, yoffset, zoffset, x, y, width, height);
    }

    @Override
    public void texParameteri(int target, int pname, int param) {
        GL11C.glTexParameteri(target, pname, param);
    }

    @Override
    public void texParameterf(int target, int pname, float param) {
        GL11C.glTexParameterf(target, pname, param);
    }

    @Override
    public void texParameteriv(int target, int pname, IntBuffer params) {
        GL11C.glTexParameteriv(target, pname, params);
    }

    @Override
    public void texParameterfv(int target, int pname, FloatBuffer params) {
        GL11C.glTexParameterfv(target, pname, params);
    }

    @Override
    public int getTexParameteri(int target, int pname) {
        return GL11C.glGetTexParameteri(target, pname);
    }

    @Override
    public float getTexParameterf(int target, int pname) {
        return GL11C.glGetTexParameterf(target, pname);
    }

    @Override
    public int getTexLevelParameteri(int target, int level, int pname) {
        return GL11C.glGetTexLevelParameteri(target, level, pname);
    }

    @Override
    public void generateMipmap(int target) {
        GL30C.glGenerateMipmap(target);
    }

    @Override
    public void pixelStorei(int pname, int param) {
        GL11C.glPixelStorei(pname, param);
    }

    @Override
    public void pixelStoref(int pname, float param) {
        GL11C.glPixelStoref(pname, param);
    }

    @Override
    public void sampleCoverage(float value, boolean invert) {
        GL13C.glSampleCoverage(value, invert);
    }

    @Override
    public int genSamplers() {
        return GL33C.glGenSamplers();
    }

    @Override
    public void deleteSamplers(int sampler) {
        GL33C.glDeleteSamplers(sampler);
    }

    @Override
    public void bindSampler(int unit, int sampler) {
        GL33C.glBindSampler(unit, sampler);
    }

    @Override
    public void samplerParameteri(int sampler, int pname, int param) {
        GL33C.glSamplerParameteri(sampler, pname, param);
    }

    @Override
    public void samplerParameterf(int sampler, int pname, float param) {
        GL33C.glSamplerParameterf(sampler, pname, param);
    }

    @Override
    public int genFramebuffers() {
        return GL30C.glGenFramebuffers();
    }

    @Override
    public void deleteFramebuffers(int framebuffer) {
        GL30C.glDeleteFramebuffers(framebuffer);
    }

    @Override
    public void bindFramebuffer(int target, int framebuffer) {
        GL30C.glBindFramebuffer(target, framebuffer);
    }

    @Override
    public void framebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        GL30C.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }

    @Override
    public void framebufferTexture(int target, int attachment, int texture, int level) {
        GL32C.glFramebufferTexture(target, attachment, texture, level);
    }

    @Override
    public int checkFramebufferStatus(int target) {
        return GL30C.glCheckFramebufferStatus(target);
    }

    @Override
    public void blitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        GL30C.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    @Override
    public void drawBuffers(int buffer) {
        GL20C.glDrawBuffers(buffer);
    }

    @Override
    public void drawBuffers(IntBuffer bufs) {
        GL20C.glDrawBuffers(bufs);
    }

    @Override
    public void readBuffer(int mode) {
        GL11C.glReadBuffer(mode);
    }

    @Override
    public void readPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        GL11C.glReadPixels(x, y, width, height, format, type, pixels);
    }

    @Override
    public void readPixels(int x, int y, int width, int height, int format, int type, FloatBuffer pixels) {
        GL11C.glReadPixels(x, y, width, height, format, type, pixels);
    }

    @Override
    public void readPixels(int x, int y, int width, int height, int format, int type, IntBuffer pixels) {
        GL11C.glReadPixels(x, y, width, height, format, type, pixels);
    }

    @Override
    public void getTexImage(int target, int level, int format, int type, ByteBuffer pixels) {
        GL11C.glGetTexImage(target, level, format, type, pixels);
    }

    @Override
    public void getTexImage(int target, int level, int format, int type, IntBuffer pixels) {
        GL11C.glGetTexImage(target, level, format, type, pixels);
    }

    @Override
    public int getFramebufferAttachmentParameteri(int target, int attachment, int pname) {
        return GL30C.glGetFramebufferAttachmentParameteri(target, attachment, pname);
    }

    @Override
    public int createShader(int type) {
        return GL20C.glCreateShader(type);
    }

    @Override
    public void deleteShader(int shader) {
        GL20C.glDeleteShader(shader);
    }

    @Override
    public void shaderSource(int shader, CharSequence source) {
        GL20C.glShaderSource(shader, source);
    }

    @Override
    public void compileShader(int shader) {
        GL20C.glCompileShader(shader);
    }

    @Override
    public int createProgram() {
        return GL20C.glCreateProgram();
    }

    @Override
    public void deleteProgram(int program) {
        GL20C.glDeleteProgram(program);
    }

    @Override
    public void attachShader(int program, int shader) {
        GL20C.glAttachShader(program, shader);
    }

    @Override
    public void detachShader(int program, int shader) {
        GL20C.glDetachShader(program, shader);
    }

    @Override
    public void linkProgram(int program) {
        GL20C.glLinkProgram(program);
    }

    @Override
    public void useProgram(int program) {
        GL20C.glUseProgram(program);
    }

    @Override
    public String getShaderInfoLog(int shader, int maxLength) {
        return GL20C.glGetShaderInfoLog(shader, maxLength);
    }

    @Override
    public void getShaderInfoLog(int shader, IntBuffer length, ByteBuffer infoLog) {
        GL20C.glGetShaderInfoLog(shader, length, infoLog);
    }

    @Override
    public String getProgramInfoLog(int program, int maxLength) {
        return GL20C.glGetProgramInfoLog(program, maxLength);
    }

    @Override
    public void getProgramInfoLog(int program, IntBuffer length, ByteBuffer infoLog) {
        GL20C.glGetProgramInfoLog(program, length, infoLog);
    }

    @Override
    public int getShaderi(int shader, int pname) {
        return GL20C.glGetShaderi(shader, pname);
    }

    @Override
    public int getProgrami(int program, int pname) {
        return GL20C.glGetProgrami(program, pname);
    }

    @Override
    public void getProgramiv(int program, int pname, IntBuffer params) {
        GL20C.glGetProgramiv(program, pname, params);
    }

    @Override
    public String getActiveUniform(int program, int index, int maxLength, IntBuffer sizeType) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final IntBuffer size = stack.mallocInt(1);
            final IntBuffer type = stack.mallocInt(1);
            final String name = GL20C.glGetActiveUniform(program, index, maxLength, size, type);
            sizeType.put(0, size.get(0));
            sizeType.put(1, type.get(0));
            return name;
        }
    }

    @Override
    public void getActiveUniform(int program, int index, IntBuffer length, IntBuffer size, IntBuffer type, ByteBuffer name) {
        GL20C.glGetActiveUniform(program, index, length, size, type, name);
    }

    @Override
    public void bindAttribLocation(int program, int index, CharSequence name) {
        GL20C.glBindAttribLocation(program, index, name);
    }

    @Override
    public int getAttribLocation(int program, CharSequence name) {
        return GL20C.glGetAttribLocation(program, name);
    }

    @Override
    public int getAttribLocation(int program, ByteBuffer name) {
        return GL20C.glGetAttribLocation(program, StandardCharsets.UTF_8.decode(name.slice()).toString().trim());
    }

    @Override
    public int getUniformLocation(int program, CharSequence name) {
        return GL20C.glGetUniformLocation(program, name);
    }

    @Override
    public int getUniformLocation(int program, ByteBuffer name) {
        return GL20C.glGetUniformLocation(program, StandardCharsets.UTF_8.decode(name.slice()).toString().trim());
    }

    @Override
    public boolean isShader(int obj) {
        return GL20C.glIsShader(obj);
    }

    @Override
    public boolean isProgram(int obj) {
        return GL20C.glIsProgram(obj);
    }

    @Override
    public void validateProgram(int program) {
        GL20C.glValidateProgram(program);
    }

    @Override
    public void getAttachedShaders(int program, IntBuffer count, IntBuffer shaders) {
        GL20C.glGetAttachedShaders(program, count, shaders);
    }

    @Override
    public String getShaderSource(int shader, int maxLength) {
        return GL20C.glGetShaderSource(shader, maxLength);
    }

    @Override
    public void getShaderSource(int shader, IntBuffer length, ByteBuffer source) {
        GL20C.glGetShaderSource(shader, length, source);
    }

    @Override
    public void uniform1i(int location, int v0) {
        GL20C.glUniform1i(location, v0);
    }

    @Override
    public void uniform1f(int location, float v0) {
        GL20C.glUniform1f(location, v0);
    }

    @Override
    public void uniform1fv(int location, FloatBuffer values) {
        GL20C.glUniform1fv(location, values);
    }

    @Override
    public void uniform2f(int location, float v0, float v1) {
        GL20C.glUniform2f(location, v0, v1);
    }

    @Override
    public void uniform2i(int location, int v0, int v1) {
        GL20C.glUniform2i(location, v0, v1);
    }

    @Override
    public void uniform3f(int location, float v0, float v1, float v2) {
        GL20C.glUniform3f(location, v0, v1, v2);
    }

    @Override
    public void uniform3i(int location, int v0, int v1, int v2) {
        GL20C.glUniform3i(location, v0, v1, v2);
    }

    @Override
    public void uniform4f(int location, float v0, float v1, float v2, float v3) {
        GL20C.glUniform4f(location, v0, v1, v2, v3);
    }

    @Override
    public void uniform4i(int location, int v0, int v1, int v2, int v3) {
        GL20C.glUniform4i(location, v0, v1, v2, v3);
    }

    @Override
    public void uniform3(int location, FloatBuffer value) {
        GL20C.glUniform3fv(location, value);
    }

    @Override
    public void uniform4(int location, FloatBuffer value) {
        GL20C.glUniform4fv(location, value);
    }

    @Override
    public void uniformMatrix3(int location, boolean transpose, FloatBuffer value) {
        GL20C.glUniformMatrix3fv(location, transpose, value);
    }

    @Override
    public void uniformMatrix4(int location, boolean transpose, FloatBuffer value) {
        GL20C.glUniformMatrix4fv(location, transpose, value);
    }

    @Override
    public void uniformMatrix2(int location, boolean transpose, FloatBuffer matrices) {
        GL20C.glUniformMatrix2fv(location, transpose, matrices);
    }

    @Override
    public void getUniformfv(int program, int location, FloatBuffer params) {
        GL20C.glGetUniformfv(program, location, params);
    }

    @Override
    public void getUniformiv(int program, int location, IntBuffer params) {
        GL20C.glGetUniformiv(program, location, params);
    }

    @Override
    public void vertexAttrib2f(int index, float v0, float v1) {
        GL20C.glVertexAttrib2f(index, v0, v1);
    }

    @Override
    public void vertexAttrib3f(int index, float v0, float v1, float v2) {
        GL20C.glVertexAttrib3f(index, v0, v1, v2);
    }

    @Override
    public void vertexAttrib4f(int index, float v0, float v1, float v2, float v3) {
        GL20C.glVertexAttrib4f(index, v0, v1, v2, v3);
    }

    @Override
    public int genBuffers() {
        return GL15C.glGenBuffers();
    }

    @Override
    public void deleteBuffers(int buffer) {
        GL15C.glDeleteBuffers(buffer);
    }

    @Override
    public void deleteBuffers(IntBuffer buffers) {
        GL15C.glDeleteBuffers(buffers);
    }

    @Override
    public void bindBuffer(int target, int buffer) {
        GL15C.glBindBuffer(target, buffer);
    }

    @Override
    public void bindBufferBase(int target, int index, int buffer) {
        GL30C.glBindBufferBase(target, index, buffer);
    }

    @Override
    public void bufferData(int target, long size, int usage) {
        GL15C.glBufferData(target, size, usage);
    }

    @Override
    public void bufferData(int target, ByteBuffer data, int usage) {
        GL15C.glBufferData(target, data, usage);
    }

    @Override
    public void bufferData(int target, FloatBuffer data, int usage) {
        GL15C.glBufferData(target, data, usage);
    }

    @Override
    public void bufferData(int target, ShortBuffer data, int usage) {
        GL15C.glBufferData(target, data, usage);
    }

    @Override
    public void bufferData(int target, IntBuffer data, int usage) {
        GL15C.glBufferData(target, data, usage);
    }

    @Override
    public void bufferData(int target, DoubleBuffer data, int usage) {
        GL15C.glBufferData(target, data, usage);
    }

    @Override
    public void bufferData(int target, int[] data, int usage) {
        GL15C.glBufferData(target, data, usage);
    }

    @Override
    public void bufferData(int target, float[] data, int usage) {
        GL15C.glBufferData(target, data, usage);
    }

    @Override
    public void bufferSubData(int target, long offset, ByteBuffer data) {
        GL15C.glBufferSubData(target, offset, data);
    }

    @Override
    public void bufferSubData(int target, long offset, ShortBuffer data) {
        GL15C.glBufferSubData(target, offset, data);
    }

    @Override
    public void bufferSubData(int target, long offset, IntBuffer data) {
        GL15C.glBufferSubData(target, offset, data);
    }

    @Override
    public void bufferSubData(int target, long offset, FloatBuffer data) {
        GL15C.glBufferSubData(target, offset, data);
    }

    @Override
    public void bufferSubData(int target, long offset, DoubleBuffer data) {
        GL15C.glBufferSubData(target, offset, data);
    }

    @Override
    public ByteBuffer mapBuffer(int target, int access) {
        return GL15C.glMapBuffer(target, access);
    }

    @Override
    public ByteBuffer mapBuffer(int target, int access, long length, ByteBuffer old_buffer) {
        return GL15C.glMapBuffer(target, access, length, old_buffer);
    }

    @Override
    public boolean unmapBuffer(int target) {
        return GL15C.glUnmapBuffer(target);
    }

    @Override
    public void bufferStorage(int target, ByteBuffer data, int flags) {
        GL44C.glBufferStorage(target, data, flags);
    }

    @Override
    public void bufferStorage(int target, long size, int flags) {
        GL44C.glBufferStorage(target, size, flags);
    }

    @Override
    public void getBufferSubData(int target, long offset, ByteBuffer data) {
        GL15C.glGetBufferSubData(target, offset, data);
    }

    @Override
    public void getBufferSubData(int target, long offset, ShortBuffer data) {
        GL15C.glGetBufferSubData(target, offset, data);
    }

    @Override
    public void getBufferSubData(int target, long offset, IntBuffer data) {
        GL15C.glGetBufferSubData(target, offset, data);
    }

    @Override
    public void getBufferSubData(int target, long offset, FloatBuffer data) {
        GL15C.glGetBufferSubData(target, offset, data);
    }

    @Override
    public void getBufferSubData(int target, long offset, DoubleBuffer data) {
        GL15C.glGetBufferSubData(target, offset, data);
    }

    @Override
    public int getBufferParameteri(int target, int pname) {
        return GL15C.glGetBufferParameteri(target, pname);
    }

    @Override
    public boolean isBuffer(int buffer) {
        return GL15C.glIsBuffer(buffer);
    }

    @Override
    public ByteBuffer mapBufferRange(int target, long offset, long length, int access) {
        return GL30C.glMapBufferRange(target, offset, length, access);
    }

    @Override
    public int genVertexArrays() {
        return GL30C.glGenVertexArrays();
    }

    @Override
    public void deleteVertexArrays(int array) {
        GL30C.glDeleteVertexArrays(array);
    }

    @Override
    public void bindVertexArray(int array) {
        GL30C.glBindVertexArray(array);
    }

    @Override
    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        GL20C.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }

    @Override
    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, ByteBuffer pointer) {
        GL20C.glVertexAttribPointer(index, size, type, normalized, stride, org.lwjgl.system.MemoryUtil.memAddress(pointer));
    }

    @Override
    public void vertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        GL30C.glVertexAttribIPointer(index, size, type, stride, pointer);
    }

    @Override
    public void vertexAttribIPointer(int index, int size, int type, int stride, ByteBuffer pointer) {
        GL30C.glVertexAttribIPointer(index, size, type, stride, org.lwjgl.system.MemoryUtil.memAddress(pointer));
    }

    @Override
    public void enableVertexAttribArray(int index) {
        GL20C.glEnableVertexAttribArray(index);
    }

    @Override
    public void disableVertexAttribArray(int index) {
        GL20C.glDisableVertexAttribArray(index);
    }

    @Override
    public void vertexAttribDivisor(int index, int divisor) {
        GL33C.glVertexAttribDivisor(index, divisor);
    }

    @Override
    public void bindVertexBuffer(int bindingindex, int buffer, long offset, int stride) {
        GL43C.glBindVertexBuffer(bindingindex, buffer, offset, stride);
    }

    @Override
    public void vertexAttribFormat(int attribindex, int size, int type, boolean normalized, int relativeoffset) {
        GL43C.glVertexAttribFormat(attribindex, size, type, normalized, relativeoffset);
    }

    @Override
    public void vertexAttribIFormat(int attribindex, int size, int type, int relativeoffset) {
        GL43C.glVertexAttribIFormat(attribindex, size, type, relativeoffset);
    }

    @Override
    public void vertexAttribBinding(int attribindex, int bindingindex) {
        GL43C.glVertexAttribBinding(attribindex, bindingindex);
    }

    @Override
    public int createTextures(int target) {
        return GL45C.glCreateTextures(target);
    }

    @Override
    public void bindTextureUnit(int unit, int texture) {
        GL45C.glBindTextureUnit(unit, texture);
    }

    @Override
    public void textureParameteri(int texture, int target, int pname, int param) {
        GL45C.glTextureParameteri(texture, pname, param);
    }

    @Override
    public void textureParameterf(int texture, int target, int pname, float param) {
        GL45C.glTextureParameterf(texture, pname, param);
    }

    @Override
    public void textureParameteriv(int texture, int target, int pname, IntBuffer params) {
        GL45C.glTextureParameteriv(texture, pname, params);
    }

    @Override
    public void texStorage2D(int target, int levels, int internalFormat, int width, int height) {
        GL42C.glTexStorage2D(target, levels, internalFormat, width, height);
    }

    @Override
    public void textureStorage2D(int texture, int levels, int internalFormat, int width, int height) {
        GL45C.glTextureStorage2D(texture, levels, internalFormat, width, height);
    }

    @Override
    public void texStorage1D(int target, int levels, int internalFormat, int width) {
        GL42C.glTexStorage1D(target, levels, internalFormat, width);
    }

    @Override
    public void texStorage3D(int target, int levels, int internalFormat, int width, int height, int depth) {
        GL42C.glTexStorage3D(target, levels, internalFormat, width, height, depth);
    }

    @Override
    public void textureStorage1D(int texture, int levels, int internalFormat, int width) {
        GL45C.glTextureStorage1D(texture, levels, internalFormat, width);
    }

    @Override
    public void textureStorage3D(int texture, int levels, int internalFormat, int width, int height, int depth) {
        GL45C.glTextureStorage3D(texture, levels, internalFormat, width, height, depth);
    }

    @Override
    public void generateTextureMipmap(int texture) {
        GL45C.glGenerateTextureMipmap(texture);
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
    public void textureSubImage2D(int texture, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        GL45C.glTextureSubImage2D(texture, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    @Override
    public void textureSubImage2D(int texture, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
        GL45C.glTextureSubImage2D(texture, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    @Override
    public int createFramebuffers() {
        return GL45C.glCreateFramebuffers();
    }

    @Override
    public void namedFramebufferTexture(int framebuffer, int attachment, int texture, int level) {
        GL45C.glNamedFramebufferTexture(framebuffer, attachment, texture, level);
    }

    @Override
    public void namedFramebufferReadBuffer(int framebuffer, int mode) {
        GL45C.glNamedFramebufferReadBuffer(framebuffer, mode);
    }

    @Override
    public void namedFramebufferDrawBuffers(int framebuffer, IntBuffer bufs) {
        GL45C.glNamedFramebufferDrawBuffers(framebuffer, bufs);
    }

    @Override
    public void blitNamedFramebuffer(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        GL45C.glBlitNamedFramebuffer(readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    @Override
    public int createBuffers() {
        return GL45C.glCreateBuffers();
    }

    @Override
    public void namedBufferData(int buffer, ByteBuffer data, int usage) {
        GL45C.glNamedBufferData(buffer, data, usage);
    }

    @Override
    public void namedBufferData(int buffer, FloatBuffer data, int usage) {
        GL45C.glNamedBufferData(buffer, data, usage);
    }

    @Override
    public void namedBufferSubData(int buffer, long offset, ByteBuffer data) {
        GL45C.glNamedBufferSubData(buffer, offset, data);
    }

    @Override
    public void copyTextureSubImage2D(int texture, int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        GL45C.glCopyTextureSubImage2D(texture, level, xoffset, yoffset, x, y, width, height);
    }

    @Override
    public int getTextureParameteri(int texture, int target, int pname) {
        return GL45C.glGetTextureParameteri(texture, pname);
    }

    @Override
    public int getTextureLevelParameteri(int texture, int level, int pname) {
        return GL45C.glGetTextureLevelParameteri(texture, level, pname);
    }

    @Override
    public int getInteger(int pname) {
        return GL11C.glGetInteger(pname);
    }

    @Override
    public void getInteger(int pname, IntBuffer params) {
        GL11C.glGetIntegerv(pname, params);
    }

    @Override
    public float getFloat(int pname) {
        return GL11C.glGetFloat(pname);
    }

    @Override
    public void getFloat(int pname, FloatBuffer params) {
        GL11C.glGetFloatv(pname, params);
    }

    @Override
    public boolean getBoolean(int pname) {
        return GL11C.glGetBoolean(pname);
    }

    @Override
    public void getBoolean(int pname, ByteBuffer params) {
        GL11C.glGetBooleanv(pname, params);
    }

    @Override
    public String getString(int pname) {
        return GL11C.glGetString(pname);
    }

    @Override
    public String getStringi(int name, int index) {
        return GL30C.glGetStringi(name, index);
    }

    @Override
    public int getError() {
        return GL11C.glGetError();
    }

    @Override
    public long fenceSync(int condition, int flags) {
        return GL32C.glFenceSync(condition, flags);
    }

    @Override
    public int clientWaitSync(long sync, int flags, long timeout) {
        return GL32C.glClientWaitSync(sync, flags, timeout);
    }

    @Override
    public void deleteSync(long sync) {
        GL32C.glDeleteSync(sync);
    }

    @Override
    public void clearBufferSubData(int target, int internalFormat, long offset, long size, int format, int type, ByteBuffer data) {
        GL43C.glClearBufferSubData(target, internalFormat, offset, size, format, type, data);
    }

    @Override
    public void clearTexImage(int texture, int level, int format, int type) {
        if (caps.OpenGL44) {
            GL44C.glClearTexImage(texture, level, format, type, (ByteBuffer) null);
        } else {
            ARBClearTexture.glClearTexImage(texture, level, format, type, (ByteBuffer) null);
        }
    }

    @Override
    public void bindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        GL42C.glBindImageTexture(unit, texture, level, layered, layer, access, format);
    }

    @Override
    public void memoryBarrier(int barriers) {
        GL42C.glMemoryBarrier(barriers);
    }

    @Override
    public void copyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ,
                                  int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ,
                                  int srcWidth, int srcHeight, int srcDepth) {
        GL43C.glCopyImageSubData(srcName, srcTarget, srcLevel, srcX, srcY, srcZ,
            dstName, dstTarget, dstLevel, dstX, dstY, dstZ,
            srcWidth, srcHeight, srcDepth);
    }

    @Override
    public boolean supportsDebugOutput() {
        return caps != null && caps.OpenGL43;
    }

    @Override
    public int setupDebugOutput(DebugMessageHandler handler) {
        debugCallback = GLDebugMessageCallback.create(
            (source, type, id, severity, length, message, userParam) -> {
                final String msg = GLDebugMessageCallback.getMessage(length, message);
                handler.handleMessage(source, type, id, severity, msg);
            });

        // Enable high severity only
        GL43C.glDebugMessageControl(GL11C.GL_DONT_CARE, GL11C.GL_DONT_CARE, GL43C.GL_DEBUG_SEVERITY_HIGH, (IntBuffer) null, true);
        GL43C.glDebugMessageControl(GL11C.GL_DONT_CARE, GL11C.GL_DONT_CARE, GL43C.GL_DEBUG_SEVERITY_MEDIUM, (IntBuffer) null, false);
        GL43C.glDebugMessageControl(GL11C.GL_DONT_CARE, GL11C.GL_DONT_CARE, GL43C.GL_DEBUG_SEVERITY_LOW, (IntBuffer) null, false);
        GL43C.glDebugMessageControl(GL11C.GL_DONT_CARE, GL11C.GL_DONT_CARE, GL43C.GL_DEBUG_SEVERITY_NOTIFICATION, (IntBuffer) null, false);

        GL43C.glDebugMessageCallback(debugCallback, 0L);
        GL11C.glEnable(GL43C.GL_DEBUG_OUTPUT_SYNCHRONOUS);

        final int contextFlags = GL11C.glGetInteger(GL30C.GL_CONTEXT_FLAGS);
        if ((contextFlags & GL43C.GL_CONTEXT_FLAG_DEBUG_BIT) == 0) {
            GL11C.glEnable(GL43C.GL_DEBUG_OUTPUT);
            debugOutputActive = true;
            return 2; // non-debug context
        }
        debugOutputActive = true;
        return 1;
    }

    @Override
    public int disableDebugOutput() {
        GL43C.glDebugMessageCallback(null, 0L);
        if (debugCallback != null) {
            debugCallback.free();
            debugCallback = null;
        }
        if (debugOutputActive) {
            GL11C.glDisable(GL43C.GL_DEBUG_OUTPUT);
            debugOutputActive = false;
        }
        return 1;
    }

    @Override
    public void objectLabel(int identifier, int name, CharSequence label) {
        GL43C.glObjectLabel(identifier, name, label);
    }

    @Override
    public String getObjectLabel(int identifier, int name) {
        return GL43C.glGetObjectLabel(identifier, name, 256);
    }

    @Override
    public void pushDebugGroup(int source, int id, CharSequence message) {
        GL43C.glPushDebugGroup(source, id, message);
    }

    @Override
    public void popDebugGroup() {
        GL43C.glPopDebugGroup();
    }

    @Override
    public void debugMessageInsert(int source, int type, int id, int severity, CharSequence message) {
        GL43C.glDebugMessageInsert(source, type, id, severity, message);
    }

}

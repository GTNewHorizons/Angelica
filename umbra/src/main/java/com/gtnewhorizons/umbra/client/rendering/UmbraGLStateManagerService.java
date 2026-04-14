package com.gtnewhorizons.umbra.client.rendering;

import com.gtnewhorizon.gtnhlib.client.opengl.UniversalVAO;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.mitchej123.glsm.GLStateManagerService;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.nio.IntBuffer;

public class UmbraGLStateManagerService implements GLStateManagerService {

    private IntBuffer intArrayBuffer = BufferUtils.createIntBuffer(16);

    private IntBuffer getIntArrayBuffer(int size) {
        if (intArrayBuffer.capacity() < size) {
            intArrayBuffer = BufferUtils.createIntBuffer(size);
        }
        intArrayBuffer.clear();
        return intArrayBuffer;
    }

    @Override public int getPriority() { return 100; }

    @Override public int glGetInteger(int pname) { return GLStateManager.glGetInteger(pname); }
    @Override public String glGetString(int pname) { return GLStateManager.glGetString(pname); }

    @Override public int glGenFramebuffers() { return GLStateManager.glGenFramebuffers(); }
    @Override public void glDeleteFramebuffers(int fb) { GLStateManager.glDeleteFramebuffers(fb); }
    @Override public void glBindFramebuffer(int target, int fb) { GLStateManager.glBindFramebuffer(target, fb); }
    @Override public int glCheckFramebufferStatus(int target) { return GLStateManager.glCheckFramebufferStatus(target); }
    @Override public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        GLStateManager.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }

    @Override public int glCreateShader(int type) { return GLStateManager.glCreateShader(type); }
    @Override public void glCompileShader(int shader) { GLStateManager.glCompileShader(shader); }
    @Override public int glGetShaderi(int shader, int pname) { return GLStateManager.glGetShaderi(shader, pname); }
    @Override public String glGetShaderInfoLog(int shader, int maxLen) { return GLStateManager.glGetShaderInfoLog(shader, maxLen); }
    @Override public void glDeleteShader(int shader) { GLStateManager.glDeleteShader(shader); }

    @Override public int glCreateProgram() { return GLStateManager.glCreateProgram(); }
    @Override public void glAttachShader(int program, int shader) { GLStateManager.glAttachShader(program, shader); }
    @Override public void glLinkProgram(int program) { GLStateManager.glLinkProgram(program); }
    @Override public int glGetProgrami(int program, int pname) { return GLStateManager.glGetProgrami(program, pname); }
    @Override public void glUseProgram(int program) { GLStateManager.glUseProgram(program); }
    @Override public void glDeleteProgram(int program) { GLStateManager.glDeleteProgram(program); }

    @Override public int glGetUniformLocation(int program, CharSequence name) { return GLStateManager.glGetUniformLocation(program, name); }
    @Override public void glUniform1i(int location, int value) { GLStateManager.glUniform1i(location, value); }
    @Override public int glGetAttribLocation(int program, CharSequence name) { return GLStateManager.glGetAttribLocation(program, name); }
    @Override public void glBindAttribLocation(int program, int index, CharSequence name) { GLStateManager.glBindAttribLocation(program, index, name); }

    @Override public int glGenVertexArrays() { return UniversalVAO.genVertexArrays(); }
    @Override public void glBindVertexArray(int array) { GLStateManager.glBindVertexArray(array); }
    @Override public void glDeleteVertexArrays(int array) { GLStateManager.glDeleteVertexArrays(array); }

    @Override public int glGenBuffers() { return GLStateManager.glGenBuffers(); }
    @Override public void glBindBuffer(int target, int buffer) { GLStateManager.glBindBuffer(target, buffer); }
    @Override public void glDeleteBuffers(int buffer) { GLStateManager.glDeleteBuffers(buffer); }

    @Override public int glGenTextures() { return GLStateManager.glGenTextures(); }
    @Override public void glGenTextures(int[] textures) {
        final IntBuffer buffer = getIntArrayBuffer(textures.length);
        GLStateManager.glGenTextures(buffer);
        buffer.get(textures);
    }
    @Override public void glDeleteTextures(int texture) { GLStateManager.glDeleteTextures(texture); }
    @Override public void glDeleteTextures(int[] textures) {
        final IntBuffer buffer = getIntArrayBuffer(textures.length);
        buffer.put(textures);
        buffer.flip();
        GLStateManager.glDeleteTextures(buffer);
    }
    @Override public void glActiveTexture(int texture) { GLStateManager.glActiveTexture(texture); }
    @Override public int glGetTexLevelParameteri(int target, int level, int pname) { return GLStateManager.glGetTexLevelParameteri(target, level, pname); }
    @Override public int glGetTexLevelParameter(int target, int level, int pname) { return GLStateManager.glGetTexLevelParameteri(target, level, pname); }
    @Override public void glCopyTexSubImage2D(int target, int level, int xo, int yo, int x, int y, int w, int h) {
        GLStateManager.glCopyTexSubImage2D(target, level, xo, yo, x, y, w, h);
    }
    @Override public void glPixelStorei(int pname, int param) { GLStateManager.glPixelStorei(pname, param); }

    @Override public void enableCullFace() { GLStateManager.enableCull(); }
    @Override public void disableCullFace() { GLStateManager.disableCull(); }
    @Override public void enableBlend() { GLStateManager.enableBlend(); }
    @Override public void disableBlend() { GLStateManager.disableBlend(); }
    @Override public void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcA, int dstA) { GLStateManager.tryBlendFuncSeparate(srcRGB, dstRGB, srcA, dstA); }
    @Override public void enableDepthTest() { GLStateManager.enableDepthTest(); }
    @Override public void disableDepthTest() { GLStateManager.disableDepthTest(); }
    @Override public void glDepthFunc(int func) { GLStateManager.glDepthFunc(func); }
    @Override public void glDepthMask(boolean flag) { GLStateManager.glDepthMask(flag); }
    @Override public void glViewport(int x, int y, int w, int h) { GLStateManager.glViewport(x, y, w, h); }
    @Override public void glColorMask(boolean r, boolean g, boolean b, boolean a) { GLStateManager.glColorMask(r, g, b, a); }
    @Override public void glClearColor(float r, float g, float b, float a) { GLStateManager.glClearColor(r, g, b, a); }
    @Override public void glClear(int mask) { GLStateManager.glClear(mask); }

    @Override public void clear(int mask, boolean checkError) { GLStateManager.glClear(mask); }
    @Override public void bindTexture(int texture) { GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture); }
    @Override public int getActiveTexture() { return GLStateManager.getActiveTextureUnit() + GL13.GL_TEXTURE0; }
    @Override public int getActiveTextureAccessor() { return GLStateManager.getActiveTextureUnit(); }
    @Override public int getBoundTexture(int internalUnit) { return GLStateManager.getBoundTextureForServerState(internalUnit); }
    @Override public int getActiveBoundTexture() { return GLStateManager.getBoundTextureForServerState(); }
    @Override public int getViewportWidth() { return GLStateManager.getViewportState().width; }
    @Override public int getViewportHeight() { return GLStateManager.getViewportState().height; }
    @Override public boolean getDepthStateMask() { return GLStateManager.getDepthState().isEnabled(); }
    @Override public boolean isBlendEnabled() { return GLStateManager.getBlendMode().isEnabled(); }
    @Override public void setBoundTexture(int unit, int texture) {
        final int currentUnit = GLStateManager.getActiveTextureUnit();
        if (currentUnit != unit) GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        if (currentUnit != unit) GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + currentUnit);
    }
    @Override public int getTextureBinding(int unit) { return GLStateManager.getBoundTextureForServerState(unit); }
}

package com.gtnewhorizons.angelica.glsm;

import com.mitchej123.glsm.GLStateManagerService;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.IntBuffer;

public class AngelicaGLStateManagerService implements GLStateManagerService {

    private IntBuffer intArrayBuffer = BufferUtils.createIntBuffer(16);

    private IntBuffer getIntArrayBuffer(int size) {
        if (intArrayBuffer.capacity() < size) {
            intArrayBuffer = BufferUtils.createIntBuffer(size);
        }
        intArrayBuffer.clear();
        return intArrayBuffer;
    }

    @Override
    public int getPriority() {
        return 100;
    }

    // ===================== QUERY OPERATIONS =====================

    @Override
    public int glGetInteger(int pname) {
        return GLStateManager.glGetInteger(pname);
    }

    @Override
    public String glGetString(int pname) {
        return GL11.glGetString(pname);
    }

    // ===================== FRAMEBUFFER OPERATIONS =====================

    @Override
    public int glGenFramebuffers() {
        return GL30.glGenFramebuffers();
    }

    @Override
    public void glDeleteFramebuffers(int framebuffer) {
        GL30.glDeleteFramebuffers(framebuffer);
    }

    @Override
    public void glBindFramebuffer(int target, int framebuffer) {
        GL30.glBindFramebuffer(target, framebuffer);
    }

    @Override
    public int glCheckFramebufferStatus(int target) {
        return GL30.glCheckFramebufferStatus(target);
    }

    @Override
    public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }

    // ===================== SHADER OPERATIONS =====================

    @Override
    public int glCreateShader(int type) {
        return GL20.glCreateShader(type);
    }

    @Override
    public void glCompileShader(int shader) {
        GL20.glCompileShader(shader);
    }

    @Override
    public int glGetShaderi(int shader, int pname) {
        return GL20.glGetShaderi(shader, pname);
    }

    @Override
    public String glGetShaderInfoLog(int shader, int maxLength) {
        return GL20.glGetShaderInfoLog(shader, maxLength);
    }

    @Override
    public void glDeleteShader(int shader) {
        GL20.glDeleteShader(shader);
    }

    @Override
    public int glCreateProgram() {
        return GL20.glCreateProgram();
    }

    @Override
    public void glAttachShader(int program, int shader) {
        GL20.glAttachShader(program, shader);
    }

    @Override
    public void glLinkProgram(int program) {
        GL20.glLinkProgram(program);
    }

    @Override
    public int glGetProgrami(int program, int pname) {
        return GL20.glGetProgrami(program, pname);
    }

    @Override
    public void glUseProgram(int program) {
        GLStateManager.glUseProgram(program);
    }

    @Override
    public void glDeleteProgram(int program) {
        GL20.glDeleteProgram(program);
    }

    // ===================== UNIFORM/ATTRIBUTE OPERATIONS =====================

    @Override
    public int glGetUniformLocation(int program, CharSequence name) {
        return GL20.glGetUniformLocation(program, name);
    }

    @Override
    public void glUniform1i(int location, int value) {
        GL20.glUniform1i(location, value);
    }

    @Override
    public int glGetAttribLocation(int program, CharSequence name) {
        return GL20.glGetAttribLocation(program, name);
    }

    @Override
    public void glBindAttribLocation(int program, int index, CharSequence name) {
        GL20.glBindAttribLocation(program, index, name);
    }

    // ===================== VAO OPERATIONS =====================

    @Override
    public int glGenVertexArrays() {
        return GL30.glGenVertexArrays();
    }

    @Override
    public void glBindVertexArray(int array) {
        GL30.glBindVertexArray(array);
    }

    @Override
    public void glDeleteVertexArrays(int array) {
        GL30.glDeleteVertexArrays(array);
    }

    // ===================== BUFFER OPERATIONS =====================

    @Override
    public int glGenBuffers() {
        return GL15.glGenBuffers();
    }

    @Override
    public void glBindBuffer(int target, int buffer) {
        GL15.glBindBuffer(target, buffer);
    }

    @Override
    public void glDeleteBuffers(int buffer) {
        GL15.glDeleteBuffers(buffer);
    }

    // ===================== TEXTURE OPERATIONS =====================

    @Override
    public int glGenTextures() {
        return GL11.glGenTextures();
    }

    @Override
    public void glGenTextures(int[] textures) {
        IntBuffer buffer = getIntArrayBuffer(textures.length);
        GL11.glGenTextures(buffer);
        buffer.get(textures);
    }

    @Override
    public void glDeleteTextures(int texture) {
        GLStateManager.glDeleteTextures(texture);
    }

    @Override
    public void glDeleteTextures(int[] textures) {
        IntBuffer buffer = getIntArrayBuffer(textures.length);
        buffer.put(textures);
        buffer.flip();
        GLStateManager.glDeleteTextures(buffer);
    }

    @Override
    public void glActiveTexture(int texture) {
        GLStateManager.glActiveTexture(texture);
    }

    @Override
    public int glGetTexLevelParameteri(int target, int level, int pname) {
        return GLStateManager.glGetTexLevelParameteri(target, level, pname);
    }

    @Override
    public int glGetTexLevelParameter(int target, int level, int pname) {
        return GLStateManager.glGetTexLevelParameteri(target, level, pname);
    }

    @Override
    public void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        GL11.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }

    @Override
    public void glPixelStorei(int pname, int param) {
        GLStateManager.glPixelStorei(pname, param);
    }

    // ===================== STATE OPERATIONS =====================

    @Override
    public void enableCullFace() {
        GLStateManager.enableCull();
    }

    @Override
    public void disableCullFace() {
        GLStateManager.disableCull();
    }

    @Override
    public void enableBlend() {
        GLStateManager.enableBlend();
    }

    @Override
    public void disableBlend() {
        GLStateManager.disableBlend();
    }

    @Override
    public void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        GLStateManager.tryBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    @Override
    public void enableDepthTest() {
        GLStateManager.enableDepthTest();
    }

    @Override
    public void disableDepthTest() {
        GLStateManager.disableDepthTest();
    }

    @Override
    public void glDepthFunc(int func) {
        GLStateManager.glDepthFunc(func);
    }

    @Override
    public void glDepthMask(boolean flag) {
        GLStateManager.glDepthMask(flag);
    }

    @Override
    public void glViewport(int x, int y, int width, int height) {
        GLStateManager.glViewport(x, y, width, height);
    }

    @Override
    public void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GLStateManager.glColorMask(red, green, blue, alpha);
    }

    @Override
    public void glClearColor(float red, float green, float blue, float alpha) {
        GLStateManager.glClearColor(red, green, blue, alpha);
    }

    @Override
    public void glClear(int mask) {
        GLStateManager.glClear(mask);
    }

    // ===================== MOJANG ADDITIONS =====================

    @Override
    public void clear(int mask, boolean checkError) {
        GLStateManager.glClear(mask);
    }

    @Override
    public void bindTexture(int texture) {
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    @Override
    public int getActiveTexture() {
        return GLStateManager.getActiveTextureUnit() + GL13.GL_TEXTURE0;
    }

    @Override
    public int getActiveTextureAccessor() {
        return GLStateManager.getActiveTextureUnit();
    }

    @Override
    public int getBoundTexture(int internalUnit) {
        return GLStateManager.getBoundTextureForServerState(internalUnit);
    }

    @Override
    public int getActiveBoundTexture() {
        return GLStateManager.getBoundTextureForServerState();
    }

    @Override
    public int getViewportWidth() {
        return GLStateManager.getViewportState().width;
    }

    @Override
    public int getViewportHeight() {
        return GLStateManager.getViewportState().height;
    }

    @Override
    public boolean getDepthStateMask() {
        return GLStateManager.getDepthState().isEnabled();
    }

    @Override
    public boolean isBlendEnabled() {
        return GLStateManager.getBlendMode().isEnabled();
    }

    @Override
    public void setBoundTexture(int unit, int texture) {
        int currentUnit = GLStateManager.getActiveTextureUnit();
        if (currentUnit != unit) {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        }
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        if (currentUnit != unit) {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + currentUnit);
        }
    }

    @Override
    public int getTextureBinding(int unit) {
        return GLStateManager.getBoundTextureForServerState(unit);
    }
}

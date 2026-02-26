package com.gtnewhorizons.angelica.glsm.dsa;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.GL45;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class DSAARB extends DSAUnsupported {

    @Override
    public void generateMipmaps(int texture, int target) {
        ARBDirectStateAccess.glGenerateTextureMipmap(texture);
    }

    @Override
    public void textureImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, long pixels_buffer_offset) {
        // Note: DSA glTextureImage2D doesn't exist in ARB_direct_state_access
        // We need to use glTextureStorage2D + glTextureSubImage2D, or fall back to bind-based approach
        // For simplicity, use the fallback which binds, uploads, and restores
        super.textureImage2D(texture, target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
    }

    @Override
    public void textureSubImage2D(int texture, int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, long pixels_buffer_offset) {
        ARBDirectStateAccess.glTextureSubImage2D(texture, level, xoffset, yoffset, width, height, format, type, pixels_buffer_offset);
    }

    @Override
    public void texParameteri(int texture, int target, int pname, int param) {
        param = GLStateManager.remapTexClamp(pname, param);
        if(!GLStateManager.updateTexParameteriCache(target, texture, pname, param)) return;

        ARBDirectStateAccess.glTextureParameteri(texture, pname, param);
    }

    @Override
    public void texParameterf(int texture, int target, int pname, float param) {
        param = GLStateManager.remapTexClamp(pname, param);
        if(!GLStateManager.updateTexParameterfCache(target, texture, pname, param)) return;

        ARBDirectStateAccess.glTextureParameterf(texture, pname, param);
    }

    @Override
    public void texParameteriv(int texture, int target, int pname, IntBuffer params) {
        GLStateManager.remapTexClampBuffer(pname, params);
        ARBDirectStateAccess.glTextureParameter(texture, pname, params);
    }

    @Override
    public void readBuffer(int framebuffer, int buffer) {
        ARBDirectStateAccess.glNamedFramebufferReadBuffer(framebuffer, buffer);
    }

    @Override
    public void drawBuffers(int framebuffer, IntBuffer buffers) {
        ARBDirectStateAccess.glNamedFramebufferDrawBuffers(framebuffer, buffers);
    }

    @Override
    public int getTexParameteri(int texture, int target, int pname) {
        return GLStateManager.getTexParameterOrDefault(texture, pname, () -> ARBDirectStateAccess.glGetTextureParameteri(texture, pname));
    }

    @Override
    public int getTexLevelParameteri(int texture, int level, int pname) {
        return ARBDirectStateAccess.glGetTextureLevelParameteri(texture, level, pname);
    }

    @Override
    public void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height) {
        ARBDirectStateAccess.glCopyTextureSubImage2D(destTexture, i, i1, i2, i3, i4, width, height);
    }

    @Override
    public void bindTextureToUnit(int unit, int texture) {
        if(GLStateManager.getBoundTextureForServerState(unit) == texture) return;

        if (texture == 0) {
            super.bindTextureToUnit(unit, texture);
        } else {
            ARBDirectStateAccess.glBindTextureUnit(unit, texture);
            GLStateManager.getTextures().getTextureUnitBindings(unit).setBinding(texture);
        }
    }

    @Override
    public void bindTextureToUnit(int target, int unit, int texture) {
        bindTextureToUnit(unit, texture);
    }

    @Override
    public int bufferStorage(int target, FloatBuffer data, int usage) {
        final int buffer = GL45.glCreateBuffers();
        GL45.glNamedBufferData(buffer, data, usage);
        return buffer;
    }

    @Override
    public void blitFramebuffer(int source, int dest, int offsetX, int offsetY, int width, int height, int offsetX2, int offsetY2, int width2, int height2,
        int bufferChoice, int filter) {
        ARBDirectStateAccess.glBlitNamedFramebuffer(source, dest, offsetX, offsetY, width, height, offsetX2, offsetY2, width2, height2, bufferChoice, filter);
    }

    @Override
    public void framebufferTexture2D(int fb, int fbtarget, int attachment, int target, int texture, int levels) {
        ARBDirectStateAccess.glNamedFramebufferTexture(fb, attachment, texture, levels);
    }

    @Override
    public int createFramebuffer() {
        return ARBDirectStateAccess.glCreateFramebuffers();
    }

    @Override
    public int createTexture(int target) {
        return ARBDirectStateAccess.glCreateTextures(target);
    }

    @Override
    public void textureStorage1D(int texture, int target, int levels, int internalFormat, int width) {
        ARBDirectStateAccess.glTextureStorage1D(texture, levels, internalFormat, width);
    }

    @Override
    public void textureStorage2D(int texture, int target, int levels, int internalFormat, int width, int height) {
        ARBDirectStateAccess.glTextureStorage2D(texture, levels, internalFormat, width, height);
    }

    @Override
    public void textureStorage3D(int texture, int target, int levels, int internalFormat, int width, int height, int depth) {
        ARBDirectStateAccess.glTextureStorage3D(texture, levels, internalFormat, width, height, depth);
    }
}

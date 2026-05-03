package com.gtnewhorizons.angelica.glsm.dsa;

import com.gtnewhorizons.angelica.glsm.GLStateManager;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public class DSAARB extends DSAUnsupported {

    @Override
    public void generateMipmaps(int texture, int target) {
        RENDER_BACKEND.generateTextureMipmap(texture);
    }

    @Override
    public void textureImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        // Note: DSA glTextureImage2D doesn't exist in ARB_direct_state_access
        // We need to use glTextureStorage2D + glTextureSubImage2D, or fall back to bind-based approach
        // For simplicity, use the fallback which binds, uploads, and restores
        super.textureImage2D(texture, target, level, internalformat, width, height, border, format, type, pixels);
    }

    @Override
    public void textureImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        super.textureImage2D(texture, target, level, internalformat, width, height, border, format, type, pixels);
    }

    @Override
    public void textureSubImage2D(int texture, int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        RENDER_BACKEND.textureSubImage2D(texture, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    @Override
    public void textureSubImage2D(int texture, int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
        RENDER_BACKEND.textureSubImage2D(texture, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    @Override
    public void texParameteri(int texture, int target, int pname, int param) {
        param = GLStateManager.remapTexClamp(pname, param);
        if(!GLStateManager.updateTexParameteriCache(target, texture, pname, param)) return;

        RENDER_BACKEND.textureParameteri(texture, target, pname, param);
    }

    @Override
    public void texParameterf(int texture, int target, int pname, float param) {
        param = GLStateManager.remapTexClamp(pname, param);
        if(!GLStateManager.updateTexParameterfCache(target, texture, pname, param)) return;

        RENDER_BACKEND.textureParameterf(texture, target, pname, param);
    }

    @Override
    public void texParameteriv(int texture, int target, int pname, IntBuffer params) {
        GLStateManager.remapTexClampBuffer(pname, params);
        RENDER_BACKEND.textureParameteriv(texture, target, pname, params);
    }

    @Override
    public void readBuffer(int framebuffer, int buffer) {
        RENDER_BACKEND.namedFramebufferReadBuffer(framebuffer, buffer);
    }

    @Override
    public void drawBuffers(int framebuffer, IntBuffer buffers) {
        RENDER_BACKEND.namedFramebufferDrawBuffers(framebuffer, buffers);
    }

    @Override
    public int getTexParameteri(int texture, int target, int pname) {
        return GLStateManager.getTexParameterOrDefault(texture, pname, () -> RENDER_BACKEND.getTextureParameteri(texture, target, pname));
    }

    @Override
    public int getTexLevelParameteri(int texture, int level, int pname) {
        return RENDER_BACKEND.getTextureLevelParameteri(texture, level, pname);
    }

    @Override
    public void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height) {
        RENDER_BACKEND.copyTextureSubImage2D(destTexture, target, i, i1, i2, i3, i4, width, height);
    }

    @Override
    public void bindTextureToUnit(int unit, int texture) {
        if(GLStateManager.getBoundTextureForServerState(unit) == texture) return;

        if (texture == 0) {
            super.bindTextureToUnit(unit, texture);
        } else {
            RENDER_BACKEND.bindTextureUnit(unit, texture);
            GLStateManager.getTextures().getTextureUnitBindings(unit).setBinding(texture);
        }
    }

    @Override
    public void bindTextureToUnit(int target, int unit, int texture) {
        bindTextureToUnit(unit, texture);
    }

    @Override
    public int bufferStorage(int target, FloatBuffer data, int usage) {
        final int buffer = RENDER_BACKEND.createBuffers();
        RENDER_BACKEND.namedBufferData(buffer, data, usage);
        return buffer;
    }

    @Override
    public void blitFramebuffer(int source, int dest, int offsetX, int offsetY, int width, int height, int offsetX2, int offsetY2, int width2, int height2,
        int bufferChoice, int filter) {
        RENDER_BACKEND.blitNamedFramebuffer(source, dest, offsetX, offsetY, width, height, offsetX2, offsetY2, width2, height2, bufferChoice, filter);
    }

    @Override
    public void framebufferTexture2D(int fb, int fbtarget, int attachment, int target, int texture, int levels) {
        RENDER_BACKEND.namedFramebufferTexture(fb, attachment, texture, levels);
    }

    @Override
    public int createFramebuffer() {
        return RENDER_BACKEND.createFramebuffers();
    }

    @Override
    public int createTexture(int target) {
        return RENDER_BACKEND.createTextures(target);
    }

    @Override
    public void textureStorage1D(int texture, int target, int levels, int internalFormat, int width) {
        RENDER_BACKEND.textureStorage1D(texture, levels, internalFormat, width);
    }

    @Override
    public void textureStorage2D(int texture, int target, int levels, int internalFormat, int width, int height) {
        RENDER_BACKEND.textureStorage2D(texture, levels, internalFormat, width, height);
    }

    @Override
    public void textureStorage3D(int texture, int target, int levels, int internalFormat, int width, int height, int depth) {
        RENDER_BACKEND.textureStorage3D(texture, levels, internalFormat, width, height, depth);
    }
}

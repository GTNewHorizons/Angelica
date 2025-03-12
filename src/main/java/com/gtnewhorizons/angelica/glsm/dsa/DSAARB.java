package com.gtnewhorizons.angelica.glsm.dsa;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.managers.GLTextureManager;
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
    public void texParameteri(int texture, int target, int pname, int param) {
        if(!GLTextureManager.updateTexParameteriCache(target, texture, pname, param)) return;

        ARBDirectStateAccess.glTextureParameteri(texture, pname, param);
    }

    @Override
    public void texParameterf(int texture, int target, int pname, float param) {
        if(!GLTextureManager.updateTexParameterfCache(target, texture, pname, param)) return;

        ARBDirectStateAccess.glTextureParameterf(texture, pname, param);
    }

    @Override
    public void texParameteriv(int texture, int target, int pname, IntBuffer params) {
        ARBDirectStateAccess.glTextureParameteriv(texture, pname, params);
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
        return GLTextureManager.getTexParameterOrDefault(texture, pname, () -> ARBDirectStateAccess.glGetTextureParameteri(texture, pname));
    }

    @Override
    public void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height) {
        ARBDirectStateAccess.glCopyTextureSubImage2D(destTexture, i, i1, i2, i3, i4, width, height);
    }

    @Override
    public void bindTextureToUnit(int unit, int texture) {
        if(GLTextureManager.getBoundTexture(unit) == texture) return;

        if (texture == 0) {
            super.bindTextureToUnit(unit, texture);
        } else {
            ARBDirectStateAccess.glBindTextureUnit(unit, texture);
            GLTextureManager.textures.getTextureUnitBindings(unit).setBinding(texture);
        }
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
}

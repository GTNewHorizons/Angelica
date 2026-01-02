package com.gtnewhorizons.angelica.glsm.dsa;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class DSAUnsupported implements DSAAccess {

    @Override
    public void generateMipmaps(int texture, int target) {
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL30.glGenerateMipmap(target);
    }

    @Override
    public void textureImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        // Get what cache thinks is bound - we'll restore to this to keep cache/GL in sync
        final int cachedBinding = GLStateManager.getBoundTextureForServerState();
        GL11.glBindTexture(target, texture);
        GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        GL11.glBindTexture(target, cachedBinding); // Restore to cache value
    }

    @Override
    public void textureImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        final int cachedBinding = GLStateManager.getBoundTextureForServerState();
        GL11.glBindTexture(target, texture);
        GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        GL11.glBindTexture(target, cachedBinding);
    }

    @Override
    public void textureSubImage2D(int texture, int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        final int cachedBinding = GLStateManager.getBoundTextureForServerState();
        GL11.glBindTexture(target, texture);
        GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
        GL11.glBindTexture(target, cachedBinding);
    }

    @Override
    public void textureSubImage2D(int texture, int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
        final int cachedBinding = GLStateManager.getBoundTextureForServerState();
        GL11.glBindTexture(target, texture);
        GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
        GL11.glBindTexture(target, cachedBinding);
    }

    @Override
    public void texParameteri(int texture, int target, int pname, int param) {
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLStateManager.glTexParameteri(target, pname, param);
    }

    @Override
    public void texParameterf(int texture, int target, int pname, float param) {
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLStateManager.glTexParameterf(target, pname, param);
    }

    @Override
    public void texParameteriv(int texture, int target, int pname, IntBuffer params) {
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLStateManager.glTexParameter(target, pname, params);
    }

    @Override
    public void readBuffer(int framebuffer, int buffer) {
        OpenGlHelper.func_153171_g/*glBindFramebuffer*/(GL30.GL_FRAMEBUFFER, framebuffer);
        GL11.glReadBuffer(buffer);
    }

    @Override
    public void drawBuffers(int framebuffer, IntBuffer buffers) {
        OpenGlHelper.func_153171_g/*glBindFramebuffer*/(GL30.GL_FRAMEBUFFER, framebuffer);
        GL20.glDrawBuffers(buffers);
    }

    @Override
    public int getTexParameteri(int texture, int target, int pname) {
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        return GLStateManager.glGetTexParameteri(target, pname);
    }

    @Override
    public int getTexLevelParameteri(int texture, int level, int pname) {
        final int previous = GLStateManager.getBoundTextureForServerState();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        final int result = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, pname);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        return result;
    }

    @Override
    public void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height) {
        final int previous = GLStateManager.getBoundTextureForServerState();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, destTexture);
        GL11.glCopyTexSubImage2D(target, i, i1, i2, i3, i4, width, height);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, previous);
    }

    @Override
    public void bindTextureToUnit(int unit, int texture) {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    @Override
    public int bufferStorage(int target, FloatBuffer data, int usage) {
        final int buffer = GL15.glGenBuffers();
        GL15.glBindBuffer(target, buffer);
        RenderSystem.bufferData(target, data, usage);
        GL15.glBindBuffer(target, 0);

        return buffer;
    }

    @Override
    public void blitFramebuffer(int source, int dest, int offsetX, int offsetY, int width, int height, int offsetX2, int offsetY2, int width2, int height2,
        int bufferChoice, int filter) {
        OpenGlHelper.func_153171_g/*glBindFramebuffer*/(GL30.GL_READ_FRAMEBUFFER, source);
        OpenGlHelper.func_153171_g/*glBindFramebuffer*/(GL30.GL_DRAW_FRAMEBUFFER, dest);
        GL30.glBlitFramebuffer(offsetX, offsetY, width, height, offsetX2, offsetY2, width2, height2, bufferChoice, filter);
    }

    @Override
    public void framebufferTexture2D(int fb, int fbtarget, int attachment, int target, int texture, int levels) {
        OpenGlHelper.func_153171_g/*glBindFramebuffer*/(fbtarget, fb);
        GL30.glFramebufferTexture2D(fbtarget, attachment, target, texture, levels);
    }

    @Override
    public int createFramebuffer() {
        final int framebuffer = OpenGlHelper.func_153165_e/*glGenFramebuffers*/();
        OpenGlHelper.func_153171_g/*glBindFramebuffer*/(GL30.GL_FRAMEBUFFER, framebuffer);
        return framebuffer;
    }

    @Override
    public int createTexture(int target) {
        final int texture = GL11.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        return texture;
    }
}

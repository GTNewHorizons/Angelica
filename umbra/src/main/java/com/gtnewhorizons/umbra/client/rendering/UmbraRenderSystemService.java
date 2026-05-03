package com.gtnewhorizons.umbra.client.rendering;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.mitchej123.glsm.RenderSystemService;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

public class UmbraRenderSystemService implements RenderSystemService {

    private final int[] shaderTextures = new int[16];
    private final float[] shaderColor = {1f, 1f, 1f, 1f};
    private final float[] shaderFogColor = {0f, 0f, 0f, 1f};
    private float shaderFogStart = 0f;
    private float shaderFogEnd = 0f;
    private int fogShape = 0;
    private float shaderLineWidth = 1f;
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final FloatBuffer projectionMatrixBuffer = BufferUtils.createFloatBuffer(16);

    @Override public int getPriority() { return 100; }

    @Override public void glActiveTexture(int texture) { GLStateManager.glActiveTexture(texture); }
    @Override public void bindTexture(int texture) { GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture); }

    @Override public void enableCullFace() { GLStateManager.enableCull(); }
    @Override public void disableCullFace() { GLStateManager.disableCull(); }
    @Override public void enableBlend() { GLStateManager.enableBlend(); }
    @Override public void disableBlend() { GLStateManager.disableBlend(); }
    @Override public void defaultBlendFunc() { GLStateManager.defaultBlendFunc(); }
    @Override public void blendFuncSeparate(int srcRGB, int dstRGB, int srcA, int dstA) { GLStateManager.tryBlendFuncSeparate(srcRGB, dstRGB, srcA, dstA); }
    @Override public void setUnknownBlendState() { }
    @Override public void enableDepthTest() { GLStateManager.enableDepthTest(); }
    @Override public void disableDepthTest() { GLStateManager.disableDepthTest(); }
    @Override public void depthFunc(int func) { GLStateManager.glDepthFunc(func); }
    @Override public void depthMask(boolean flag) { GLStateManager.glDepthMask(flag); }
    @Override public void glViewport(int x, int y, int w, int h) { GLStateManager.glViewport(x, y, w, h); }
    @Override public void glClearColor(float r, float g, float b, float a) { GLStateManager.glClearColor(r, g, b, a); }
    @Override public void clear(int mask, boolean checkError) { GLStateManager.glClear(mask); }

    @Override public void glUniform1i(int loc, int val) { GLStateManager.glUniform1i(loc, val); }
    @Override public void glUniformMatrix3(int loc, boolean transpose, FloatBuffer val) { GLStateManager.glUniformMatrix3(loc, transpose, val); }
    @Override public void glUniformMatrix4(int loc, boolean transpose, FloatBuffer val) { GLStateManager.glUniformMatrix4(loc, transpose, val); }

    @Override public void assertOnRenderThread() { }
    @Override public void assertOnRenderThreadOrInit() { }

    @Override public void setShaderTexture(int slot, int id) { if (slot >= 0 && slot < 16) shaderTextures[slot] = id; }
    @Override public int getShaderTexture(int slot) { return (slot >= 0 && slot < 16) ? shaderTextures[slot] : 0; }
    @Override public void setShaderColor(float r, float g, float b, float a) { shaderColor[0]=r; shaderColor[1]=g; shaderColor[2]=b; shaderColor[3]=a; }
    @Override public float[] getShaderColor() { return shaderColor.clone(); }
    @Override public void setShaderLineWidth(float w) { shaderLineWidth = w; }
    @Override public float getShaderLineWidth() { return shaderLineWidth; }

    @Override public void setShaderFogColor(float r, float g, float b, float a) { shaderFogColor[0]=r; shaderFogColor[1]=g; shaderFogColor[2]=b; shaderFogColor[3]=a; }
    @Override public float[] getShaderFogColor() { return shaderFogColor.clone(); }
    @Override public void setShaderFogStart(float start) { shaderFogStart = start; }
    @Override public float getShaderFogStart() { return shaderFogStart; }
    @Override public void setShaderFogEnd(float end) { shaderFogEnd = end; }
    @Override public float getShaderFogEnd() { return shaderFogEnd; }
    @Override public void setFogShape(int shape) { fogShape = shape; }
    @Override public int getFogShape() { return fogShape; }

    @Override public Matrix4f getProjectionMatrix() { return new Matrix4f(projectionMatrix); }
    @Override public void setProjectionMatrixOrth(Matrix4f m) {
        projectionMatrix.set(m);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        projectionMatrix.get(projectionMatrixBuffer);
        projectionMatrixBuffer.rewind();
        GLStateManager.glLoadMatrix(projectionMatrixBuffer);
    }
    @Override public void setProjectionMatrixOrigin(Matrix4f m) { setProjectionMatrixOrth(m); }
}

package com.gtnewhorizons.angelica.glsm;

import com.mitchej123.glsm.RenderSystemService;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

public class AngelicaRenderSystemService implements RenderSystemService {

    // Shader state (Mojang-specific, tracked here)
    private final int[] shaderTextures = new int[16];
    private final float[] shaderColor = {1f, 1f, 1f, 1f};
    private final float[] shaderFogColor = {0f, 0f, 0f, 1f};
    private float shaderFogStart = 0f;
    private float shaderFogEnd = 0f;
    private int fogShape = 0;
    private float shaderLineWidth = 1f;
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final FloatBuffer projectionMatrixBuffer = BufferUtils.createFloatBuffer(16);

    @Override
    public int getPriority() {
        return 100;
    }

    // ===================== TEXTURE OPERATIONS =====================

    @Override
    public void glActiveTexture(int texture) {
        GLStateManager.glActiveTexture(texture);
    }

    @Override
    public void bindTexture(int texture) {
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
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
    public void defaultBlendFunc() {
        GLStateManager.defaultBlendFunc();
    }

    @Override
    public void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        GLStateManager.tryBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    @Override
    public void setUnknownBlendState() {
        // No-op: Angelica tracks blend state accurately
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
    public void depthFunc(int depthFunc) {
        GLStateManager.glDepthFunc(depthFunc);
    }

    @Override
    public void depthMask(boolean flag) {
        GLStateManager.glDepthMask(flag);
    }

    @Override
    public void glViewport(int x, int y, int width, int height) {
        GLStateManager.glViewport(x, y, width, height);
    }

    @Override
    public void glClearColor(float red, float green, float blue, float alpha) {
        GLStateManager.glClearColor(red, green, blue, alpha);
    }

    @Override
    public void clear(int mask, boolean checkError) {
        GLStateManager.glClear(mask);
    }

    // ===================== UNIFORM OPERATIONS =====================

    @Override
    public void glUniform1i(int location, int value) {
        GL20.glUniform1i(location, value);
    }

    @Override
    public void glUniformMatrix3(int location, boolean transpose, FloatBuffer value) {
        GL20.glUniformMatrix3(location, transpose, value);
    }

    @Override
    public void glUniformMatrix4(int location, boolean transpose, FloatBuffer value) {
        GL20.glUniformMatrix4(location, transpose, value);
    }

    // ===================== THREAD ASSERTIONS =====================

    @Override
    public void assertOnRenderThread() {
        // No-op: 1.7.10 is single-threaded for rendering
    }

    @Override
    public void assertOnRenderThreadOrInit() {
        // No-op: 1.7.10 is single-threaded for rendering
    }

    // ===================== SHADER STATE =====================

    @Override
    public void setShaderTexture(int shaderTexture, int textureId) {
        if (shaderTexture >= 0 && shaderTexture < 16) {
            shaderTextures[shaderTexture] = textureId;
        }
    }

    @Override
    public int getShaderTexture(int shaderTexture) {
        return (shaderTexture >= 0 && shaderTexture < 16) ? shaderTextures[shaderTexture] : 0;
    }

    @Override
    public void setShaderColor(float red, float green, float blue, float alpha) {
        shaderColor[0] = red;
        shaderColor[1] = green;
        shaderColor[2] = blue;
        shaderColor[3] = alpha;
    }

    @Override
    public float[] getShaderColor() {
        return shaderColor.clone();
    }

    @Override
    public void setShaderLineWidth(float lineWidth) {
        shaderLineWidth = lineWidth;
    }

    @Override
    public float getShaderLineWidth() {
        return shaderLineWidth;
    }

    // ===================== FOG STATE =====================

    @Override
    public void setShaderFogColor(float red, float green, float blue, float alpha) {
        shaderFogColor[0] = red;
        shaderFogColor[1] = green;
        shaderFogColor[2] = blue;
        shaderFogColor[3] = alpha;
    }

    @Override
    public float[] getShaderFogColor() {
        return shaderFogColor.clone();
    }

    @Override
    public void setShaderFogStart(float start) {
        shaderFogStart = start;
    }

    @Override
    public float getShaderFogStart() {
        return shaderFogStart;
    }

    @Override
    public void setShaderFogEnd(float end) {
        shaderFogEnd = end;
    }

    @Override
    public float getShaderFogEnd() {
        return shaderFogEnd;
    }

    @Override
    public void setFogShape(int shape) {
        fogShape = shape;
    }

    @Override
    public int getFogShape() {
        return fogShape;
    }

    // ===================== PROJECTION MATRIX =====================

    @Override
    public Matrix4f getProjectionMatrix() {
        return new Matrix4f(projectionMatrix);
    }

    @Override
    public void setProjectionMatrixOrth(Matrix4f m) {
        projectionMatrix.set(m);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        projectionMatrix.get(projectionMatrixBuffer);
        projectionMatrixBuffer.rewind();
        GLStateManager.glLoadMatrix(projectionMatrixBuffer);
    }

    @Override
    public void setProjectionMatrixOrigin(Matrix4f m) {
        setProjectionMatrixOrth(m);
    }
}

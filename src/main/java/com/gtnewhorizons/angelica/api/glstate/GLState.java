package com.gtnewhorizons.angelica.api.glstate;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.stacks.FogStateStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.FloatBuffer;

/** Read-only access to the GL state Angelica tracks. Call from the render thread. */
public final class GLState {

    private GLState() {}

    public static boolean isFogEnabled() {
        return GLStateManager.getFogMode().isEnabled();
    }

    /** @return The fog mode: GL_EXP, GL_EXP2 or GL_LINEAR. */
    public static int getFogMode() {
        return GLStateManager.getFogState().getFogMode();
    }

    public static float getFogStart() {
        return GLStateManager.getFogState().getStart();
    }

    public static float getFogEnd() {
        return GLStateManager.getFogState().getEnd();
    }

    public static float getFogDensity() {
        return GLStateManager.getFogState().getDensity();
    }

    public static Vector4f getFogColor(Vector4f dest) {
        final FogStateStack fog = GLStateManager.getFogState();
        return dest.set((float) fog.getFogColor().x, (float) fog.getFogColor().y, (float) fog.getFogColor().z, fog.getFogAlpha());
    }

    /** Writes the fog color as RGBA at the buffer's position. The position is not changed. */
    public static void getFogColor(FloatBuffer rgba) {
        final FogStateStack fog = GLStateManager.getFogState();
        final int pos = rgba.position();
        rgba.put(pos, (float) fog.getFogColor().x);
        rgba.put(pos + 1, (float) fog.getFogColor().y);
        rgba.put(pos + 2, (float) fog.getFogColor().z);
        rgba.put(pos + 3, fog.getFogAlpha());
    }

    public static Matrix4f getModelViewMatrix(Matrix4f dest) {
        return dest.set(GLStateManager.getModelViewMatrix());
    }

    public static Matrix4f getProjectionMatrix(Matrix4f dest) {
        return dest.set(GLStateManager.getProjectionMatrix());
    }

    public static String getRenderBackendName() {
        return GLStateManager.getRenderBackendName();
    }

    public static boolean isRenderThread() {
        return GLStateManager.isMainThread();
    }
}

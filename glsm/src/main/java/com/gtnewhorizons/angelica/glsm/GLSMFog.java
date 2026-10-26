package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.DisplayListManager.RecordMode;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.FogStateStack;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;
import java.nio.FloatBuffer;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.HAS_MULTIPLE_SET;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.beforeUncapturedStateChange;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.ctx;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.isCachingEnabled;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.mod;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.rejectExitCapture;

public final class GLSMFog {

    private GLSMFog() {}

    public static void enableFog() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_FOG);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        enableFog(ctx());
    }

    static void enableFog(GLContextState glCtx) {
        glCtx.fogMode.enable();
        if (GLSMHooks.FOG_STATE_CHANGE.hasListeners()) {
            GLSMHooks.FOG_STATE_CHANGE.post(GLSMHooks.fogStateChangeEvent);
        }
    }

    public static void disableFog() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_FOG);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        disableFog(ctx());
    }

    static void disableFog(GLContextState glCtx) {
        glCtx.fogMode.disable();
        if (GLSMHooks.FOG_STATE_CHANGE.hasListeners()) {
            GLSMHooks.FOG_STATE_CHANGE.post(GLSMHooks.fogStateChangeEvent);
        }
    }

    public static void glFog(int pname, FloatBuffer param) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordFog(pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (HAS_MULTIPLE_SET.contains(pname)) {
            if (pname == GL11.GL_FOG_COLOR && isCachingEnabled()) {
                final float red = param.get(0);
                final float green = param.get(1);
                final float blue = param.get(2);

                beforeUncapturedStateChange();
                mod(glCtx.fogState);
                glCtx.fogState.getFogColor().set(red, green, blue);
                glCtx.fogState.setFogAlpha(param.get(3));
                glCtx.fogState.getFogColorBuffer().clear();
                glCtx.fogState.getFogColorBuffer().put((FloatBuffer) param.position(0)).flip();
                glCtx.fragmentGeneration++;
                if (GLSMHooks.FOG_STATE_CHANGE.hasListeners()) {
                    GLSMHooks.FOG_STATE_CHANGE.post(GLSMHooks.fogStateChangeEvent);
                }
            }
        } else {
            glFogf(pname, param.get(0));
        }
    }

    public static Vector3d getFogColor() {
        return ctx().fogState.getFogColor();
    }

    public static void fogColor(float red, float green, float blue, float alpha) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final boolean caching = isCachingEnabled();
        if (!caching || red != glCtx.fogState.getFogColor().x || green != glCtx.fogState.getFogColor().y || blue != glCtx.fogState.getFogColor().z || alpha != glCtx.fogState.getFogAlpha()) {
            beforeUncapturedStateChange();
            mod(glCtx.fogState);
            glCtx.fogState.getFogColor().set(red, green, blue);
            glCtx.fogState.setFogAlpha(alpha);
            glCtx.fogState.getFogColorBuffer().clear();
            glCtx.fogState.getFogColorBuffer().put(red).put(green).put(blue).put(alpha).flip();
            glCtx.fragmentGeneration++;
            if (GLSMHooks.FOG_STATE_CHANGE.hasListeners()) {
                GLSMHooks.FOG_STATE_CHANGE.post(GLSMHooks.fogStateChangeEvent);
            }
        }
    }

    public static void glFogf(int pname, float param) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordFogf(pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        // Update cached state (FFP shader reads from cache)
        if (isCachingEnabled()) {
            boolean changed = false;
            switch (pname) {
                case GL11.GL_FOG_DENSITY -> { if (!isCachingEnabled() || glCtx.fogState.getDensity() != param) { beforeUncapturedStateChange(); mod(glCtx.fogState).setDensity(param); changed = true; } }
                case GL11.GL_FOG_START -> { if (!isCachingEnabled() || glCtx.fogState.getStart() != param) { beforeUncapturedStateChange(); mod(glCtx.fogState).setStart(param); changed = true; } }
                case GL11.GL_FOG_END -> { if (!isCachingEnabled() || glCtx.fogState.getEnd() != param) { beforeUncapturedStateChange(); mod(glCtx.fogState).setEnd(param); changed = true; } }
                case GL11.GL_FOG_MODE -> { if (!isCachingEnabled() || glCtx.fogState.getFogMode() != (int) param) { beforeUncapturedStateChange(); mod(glCtx.fogState).setFogMode((int) param); changed = true; } }
                default -> {}
            }
            if (changed) {
                glCtx.fragmentGeneration++;
                if (GLSMHooks.FOG_STATE_CHANGE.hasListeners()) {
                    GLSMHooks.FOG_STATE_CHANGE.post(GLSMHooks.fogStateChangeEvent);
                }
            }
        }
    }

    public static void glFogi(int pname, int param) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordFogi(pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        // Update cached state (FFP shader reads from cache)
        if (isCachingEnabled() && pname == GL11.GL_FOG_MODE && (!isCachingEnabled() || glCtx.fogState.getFogMode() != param)) {
            beforeUncapturedStateChange();
            mod(glCtx.fogState).setFogMode(param);
            glCtx.fragmentGeneration++;
            if (GLSMHooks.FOG_STATE_CHANGE.hasListeners()) {
                GLSMHooks.FOG_STATE_CHANGE.post(GLSMHooks.fogStateChangeEvent);
            }
        }
    }

    public static FogStateStack getFogState() {
        return ctx().fogState;
    }

    public static BooleanStateStack getFogMode() {
        return ctx().fogMode;
    }
}

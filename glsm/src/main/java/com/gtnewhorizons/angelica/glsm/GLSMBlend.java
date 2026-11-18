package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.DisplayListManager.RecordMode;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredBlendHandler;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredDepthColorHandler;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.stacks.BlendStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.Color4Stack;
import com.gtnewhorizons.angelica.glsm.stacks.ColorMaskStack;
import com.gtnewhorizons.angelica.glsm.stacks.IntegerStateStack;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.glsm.states.ColorMask;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.beforeUncapturedStateChange;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.ctx;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.isCachingEnabled;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.mod;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.rejectExitCapture;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public final class GLSMBlend {

    private GLSMBlend() {}

    public static void glBlendColor(float red, float green, float blue, float alpha) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordBlendColor(red, green, blue, alpha);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        final boolean bypass = !caching;
        if (bypass || red != glCtx.blendState.getBlendColorR() || green != glCtx.blendState.getBlendColorG() || blue != glCtx.blendState.getBlendColorB() || alpha != glCtx.blendState.getBlendColorA()) {
            mod(glCtx.blendState);
            beforeUncapturedStateChange();
            glCtx.blendState.setBlendColorR(red);
            glCtx.blendState.setBlendColorG(green);
            glCtx.blendState.setBlendColorB(blue);
            glCtx.blendState.setBlendColorA(alpha);
            RENDER_BACKEND.blendColor(red, green, blue, alpha);
        }
    }

    public static void glBlendFuncSeparatei(int buf, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        rejectExitCapture();
        RENDER_BACKEND.blendFuncSeparatei(buf, srcRGB, dstRGB, srcAlpha, dstAlpha);
        ctx().blendState.setFuncUnknownState();
    }

    public static void enableBlend() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_BLEND);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        enableBlend(ctx());
    }

    static void enableBlend(GLContextState glCtx) {
        final BooleanStateStack blend = glCtx.blendMode;
        final boolean wasEnabled = blend.isEffectivelyEnabled();
        final DeferredBlendHandler bh = GLSMHooks.blendHandler;
        if (bh != null && bh.isBlendLocked()) {
            glCtx.exitCapture.capability(GL11.GL_BLEND, -1);
            blend.beforeModify();
            bh.deferBlendModeToggle(true);
        } else {
            blend.enable();
        }
        if (!wasEnabled && blend.isEffectivelyEnabled()) postVanillaBlendChange();
    }

    public static void disableBlend() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_BLEND);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        disableBlend(ctx());
    }

    static void disableBlend(GLContextState glCtx) {
        final BooleanStateStack blend = glCtx.blendMode;
        final boolean wasEnabled = blend.isEffectivelyEnabled();
        final DeferredBlendHandler bh = GLSMHooks.blendHandler;
        if (bh != null && bh.isBlendLocked()) {
            glCtx.exitCapture.capability(GL11.GL_BLEND, -1);
            blend.beforeModify();
            bh.deferBlendModeToggle(false);
        } else {
            blend.disable();
        }
        if (wasEnabled && !blend.isEffectivelyEnabled()) postVanillaBlendChange();
    }

    private static void postVanillaBlendChange() {
        if (GLSMHooks.VANILLA_BLEND_CHANGE.hasListeners()) {
            GLSMHooks.VANILLA_BLEND_CHANGE.post(GLSMHooks.vanillaBlendChangeEvent);
        }
    }

    static boolean snapshotVanillaBlendFunc() {
        if (!GLSMHooks.VANILLA_BLEND_CHANGE.hasListeners()) return false;
        final GLContextState glCtx = ctx();
        glCtx.blendState.readEffective(glCtx.vanillaBlendBefore);
        glCtx.vanillaBlendEnabledBefore = glCtx.blendMode.isEffectivelyEnabled();
        return true;
    }

    static void postVanillaBlendChangeIfMoved(boolean snapshotted) {
        if (!snapshotted) return;
        final GLContextState glCtx = ctx();
        final BlendState before = glCtx.vanillaBlendBefore;
        final BlendState after = glCtx.blendState.readEffective(glCtx.vanillaBlendAfter);
        if (glCtx.blendMode.isEffectivelyEnabled() != glCtx.vanillaBlendEnabledBefore
            || after.getSrcRgb() != before.getSrcRgb()
            || after.getDstRgb() != before.getDstRgb()
            || after.getSrcAlpha() != before.getSrcAlpha()
            || after.getDstAlpha() != before.getDstAlpha()) {
            postVanillaBlendChange();
        }
    }

    public static boolean isEffectiveBlendEnabled() {
        return ctx().blendMode.isEffectivelyEnabled();
    }

    public static BlendState getEffectiveBlendState(BlendState out) {
        return ctx().blendState.readEffective(out);
    }

    public static ColorMask getEffectiveColorMask(ColorMask out) {
        return ctx().colorMask.readEffective(out);
    }

    public static void glBlendFunc(int srcFactor, int dstFactor) {
        tryBlendFuncSeparate(srcFactor, dstFactor, srcFactor, dstFactor);
    }

    static void issueBlendFunc(BlendStateStack blend) {
        if (GLSMConfig.hudCacheOverride) {
            RENDER_BACKEND.blendFuncSeparate(blend.getSrcRgb(), blend.getDstRgb(), GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            RENDER_BACKEND.blendFuncSeparate(blend.getSrcRgb(), blend.getDstRgb(), blend.getSrcAlpha(), blend.getDstAlpha());
        }
    }

    public static void setHudCacheOverride(boolean enabled) {
        if (GLSMConfig.hudCacheOverride == enabled) return;
        GLSMConfig.hudCacheOverride = enabled;
        final DeferredBlendHandler bh = GLSMHooks.blendHandler;
        if (bh != null && bh.isOverrideHeld()) return;
        final BlendStateStack blend = ctx().blendState;
        issueBlendFunc(blend);
        blend.clearFuncUnknownState();
    }

    public static void glBlendEquation(int mode) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glBlendEquation");
        }
        final boolean caching = isCachingEnabled();
        final boolean bypass = !caching;
        if (bypass || glCtx.blendState.getEquationRgb() != mode || glCtx.blendState.getEquationAlpha() != mode) {
            mod(glCtx.blendState);
            beforeUncapturedStateChange();
            glCtx.blendState.setEquationRgb(mode);
            glCtx.blendState.setEquationAlpha(mode);
            RENDER_BACKEND.blendEquation(mode);
        }
    }

    public static void glBlendEquationSeparate(int modeRGB, int modeAlpha) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glBlendEquationSeparate");
        }
        final boolean caching = isCachingEnabled();
        final boolean bypass = !caching;
        if (bypass || glCtx.blendState.getEquationRgb() != modeRGB || glCtx.blendState.getEquationAlpha() != modeAlpha) {
            mod(glCtx.blendState);
            beforeUncapturedStateChange();
            glCtx.blendState.setEquationRgb(modeRGB);
            glCtx.blendState.setEquationAlpha(modeAlpha);
            RENDER_BACKEND.blendEquationSeparate(modeRGB, modeAlpha);
        }
    }

    public static void tryBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.writeBlend();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean snapshotted = snapshotVanillaBlendFunc();
        final DeferredBlendHandler bh = GLSMHooks.blendHandler;
        if (bh != null && bh.isBlendLocked()) {
            mod(glCtx.blendState);
            bh.deferBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
            postVanillaBlendChangeIfMoved(snapshotted);
            return;
        }
        final boolean caching = isCachingEnabled();
        final boolean bypass = !caching;
        if (bypass || glCtx.blendState.getSrcRgb() != srcRgb || glCtx.blendState.getDstRgb() != dstRgb || glCtx.blendState.getSrcAlpha() != srcAlpha || glCtx.blendState.getDstAlpha() != dstAlpha || glCtx.blendState.isFuncUnknown()) {
            mod(glCtx.blendState);
            glCtx.blendState.setAll(srcRgb, dstRgb, srcAlpha, dstAlpha);
            glCtx.blendState.clearFuncUnknownState();
            issueBlendFunc(glCtx.blendState);
            if (GLSMHooks.BLEND_FUNC_CHANGE.hasListeners()) {
                final boolean hudCache = GLSMConfig.hudCacheOverride;
                GLSMHooks.blendFuncChangeEvent.srcRgb = srcRgb;
                GLSMHooks.blendFuncChangeEvent.dstRgb = dstRgb;
                GLSMHooks.blendFuncChangeEvent.srcAlpha = hudCache ? GL11.GL_ONE : srcAlpha;
                GLSMHooks.blendFuncChangeEvent.dstAlpha = hudCache ? GL11.GL_ONE_MINUS_SRC_ALPHA : dstAlpha;
                GLSMHooks.BLEND_FUNC_CHANGE.post(GLSMHooks.blendFuncChangeEvent);
            }
        }
        postVanillaBlendChangeIfMoved(snapshotted);
    }

    public static void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        tryBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    public static void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.writeDepthColor(ExitStateCapture.COLOR_MASK);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordColorMask(red, green, blue, alpha);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final DeferredDepthColorHandler dch = GLSMHooks.depthColorHandler;
        if (dch != null && dch.isDepthColorLocked()) {
            mod(glCtx.colorMask);
            dch.deferColorMask(red, green, blue, alpha);
            return;
        }
        final boolean caching = isCachingEnabled();
        final boolean bypass = !caching;
        if (bypass || red != glCtx.colorMask.red || green != glCtx.colorMask.green || blue != glCtx.colorMask.blue || alpha != glCtx.colorMask.alpha) {
            mod(glCtx.colorMask);
            glCtx.colorMask.setAll(red, green, blue, alpha);
            RENDER_BACKEND.colorMask(red, green, blue, alpha);
        }
    }

    public static void glClearColor(float red, float green, float blue, float alpha) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordClearColor(red, green, blue, alpha);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (!caching || red != glCtx.clearColor.getRed() || green != glCtx.clearColor.getGreen() || blue != glCtx.clearColor.getBlue() || alpha != glCtx.clearColor.getAlpha()) {
            mod(glCtx.clearColor);
            glCtx.clearColor.setRed(red);
            glCtx.clearColor.setGreen(green);
            glCtx.clearColor.setBlue(blue);
            glCtx.clearColor.setAlpha(alpha);
            RENDER_BACKEND.clearColor(red, green, blue, alpha);
        }
    }

    public static void glLogicOp(int opcode) {
        rejectExitCapture();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLogicOp(opcode);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (!isCachingEnabled() || ctx().logicOpMode.getValue() != opcode) beforeUncapturedStateChange();
        if (isCachingEnabled()) mod(ctx().logicOpMode).setValue(opcode);
        RENDER_BACKEND.logicOp(opcode);
    }

    public static void defaultBlendFunc() {
        tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }

    public static BlendStateStack getBlendState() {
        return ctx().blendState;
    }

    public static BooleanStateStack getBlendMode() {
        return ctx().blendMode;
    }

    public static Color4Stack getClearColor() {
        return ctx().clearColor;
    }

    public static ColorMaskStack getColorMask() {
        return ctx().colorMask;
    }

    public static IntegerStateStack getLogicOpMode() {
        return ctx().logicOpMode;
    }

    public static BooleanStateStack getColorLogicOpState() {
        return ctx().colorLogicOpState;
    }

    public static BooleanStateStack getIndexLogicOpState() {
        return ctx().indexLogicOpState;
    }
}

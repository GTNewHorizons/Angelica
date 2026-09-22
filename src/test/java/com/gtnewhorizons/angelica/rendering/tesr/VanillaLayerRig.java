package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredAlphaHandler;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredBlendHandler;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredDepthColorHandler;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import net.coderbot.iris.gl.blending.AlphaTestStorage;
import net.coderbot.iris.gl.blending.BlendModeStorage;
import net.coderbot.iris.gl.blending.DepthColorStorage;

public final class VanillaLayerRig {

    private VanillaLayerRig() {}

    private static final class RealBlendHandler implements DeferredBlendHandler {

        @Override
        public boolean isBlendLocked() {
            return BlendModeStorage.isBlendLocked();
        }

        @Override
        public boolean isOverrideHeld() {
            return BlendModeStorage.isOverrideHeld();
        }

        @Override
        public void deferBlendModeToggle(boolean enabled) {
            BlendModeStorage.deferBlendModeToggle(enabled);
        }

        @Override
        public void deferBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
            BlendModeStorage.deferBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }

        @Override
        public void flushDeferredBlend() {
            BlendModeStorage.flushDeferredBlend();
        }
    }

    private static final class RealAlphaHandler implements DeferredAlphaHandler {

        @Override
        public boolean isAlphaTestLocked() {
            return AlphaTestStorage.isAlphaTestLocked();
        }

        @Override
        public boolean deferAlphaTestToggle(boolean enabled) {
            return AlphaTestStorage.deferAlphaTestToggle(enabled);
        }

        @Override
        public boolean deferAlphaFunc(int function, float reference) {
            return AlphaTestStorage.deferAlphaFunc(function, reference);
        }
    }

    private static final class RealDepthColorHandler implements DeferredDepthColorHandler {

        @Override
        public boolean isDepthColorLocked() {
            return DepthColorStorage.isDepthColorLocked();
        }

        @Override
        public boolean isOverrideHeld() {
            return DepthColorStorage.isOverrideHeld();
        }

        @Override
        public void deferDepthEnable(boolean enabled) {
            DepthColorStorage.deferDepthEnable(enabled);
        }

        @Override
        public void deferColorMask(boolean r, boolean g, boolean b, boolean a) {
            DepthColorStorage.deferColorMask(r, g, b, a);
        }
    }

    public static void installBlend() {
        GLStateManager.getBlendMode().setVanillaLayer(BlendModeStorage.ENABLE_LAYER);
        GLStateManager.getBlendState().setVanillaLayer(BlendModeStorage.FUNC_LAYER);
        GLSMHooks.blendHandler = new RealBlendHandler();
    }

    public static void installAlpha() {
        GLStateManager.getAlphaTest().setVanillaLayer(AlphaTestStorage.ENABLE_LAYER);
        GLStateManager.getAlphaState().setVanillaLayer(AlphaTestStorage.FUNC_LAYER);
        GLSMHooks.alphaHandler = new RealAlphaHandler();
    }

    public static void installDepthColor() {
        GLStateManager.getDepthState().setVanillaLayer(DepthColorStorage.DEPTH_LAYER);
        GLStateManager.getColorMask().setVanillaLayer(DepthColorStorage.COLOR_LAYER);
        GLSMHooks.depthColorHandler = new RealDepthColorHandler();
    }

    public static void installAll() {
        installBlend();
        installAlpha();
        installDepthColor();
    }

    public static void clear() {
        GLStateManager.getBlendMode().setVanillaLayer(null);
        GLStateManager.getBlendState().setVanillaLayer(null);
        GLStateManager.getAlphaTest().setVanillaLayer(null);
        GLStateManager.getAlphaState().setVanillaLayer(null);
        GLStateManager.getDepthState().setVanillaLayer(null);
        GLStateManager.getColorMask().setVanillaLayer(null);
        GLSMHooks.blendHandler = null;
        GLSMHooks.alphaHandler = null;
        GLSMHooks.depthColorHandler = null;
    }
}

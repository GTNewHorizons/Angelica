package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import com.gtnewhorizons.angelica.mixins.interfaces.IGameSettingsExt;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.FramePacer;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.embeddedt.embeddium.impl.render.frame.RenderAheadManager;
import org.lwjgl.input.Keyboard;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Shadow
    public GameSettings gameSettings;

    @Shadow
    public abstract boolean isFramerateLimitBelowMax();

    @Shadow
    public abstract int getLimitFramerate();

    @Shadow(remap = false)
    private static int max_texture_size;

    @Unique
    private final RenderAheadManager celeritas$renderAheadManager = new RenderAheadManager();

    /**
     * @author mitchej123
     * @reason Avoid GL_PROXY_TEXTURE_2D which doesn't work with GLSM's texture binding.
     *         Uses the standard GL_MAX_TEXTURE_SIZE query instead.
     */
    @Overwrite
    public static int getGLMaximumTextureSize() {
        if (max_texture_size == -1) {
            max_texture_size = GLStateManager.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        }
        return max_texture_size;
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lcpw/mods/fml/common/FMLCommonHandler;onRenderTickEnd(F)V", shift = At.Shift.AFTER, remap = false)
    )
    private void angelica$injectLightingFixPostRenderTick(CallbackInfo ci) {
        GLStateManager.glEnable(GL11.GL_LIGHTING);
    }

    @Inject(method = "func_147120_f", at = @At("RETURN"))
    private void angelica$limitFPS(CallbackInfo ci) {
        if (AngelicaMod.proxy == null) return;

        final int capHz = isFramerateLimitBelowMax() ? getLimitFramerate() : 0;
        AngelicaMod.proxy.putFrametime(FramePacer.pace(FramePacer.updateCeiling(capHz)));
    }

    @Redirect(method = {"startGame", "toggleFullscreen"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;setVSyncEnabled(Z)V", remap = false))
    private void angelica$redirectVSync(boolean sync) {
        ClientProxy.applyVSyncMode(sync);
    }

    @Inject(method = "startGame", at = @At("RETURN"))
    private void angelica$markSplashCompleteOnStartGame(CallbackInfo ci) {
        GLStateManager.markSplashComplete("startGame");
        GLStateManager.applyRenderAheadLimit(ClientProxy.options().performance.cpuRenderAheadLimit);
    }

    @WrapWithCondition(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;sync(I)V", remap = false))
    private boolean angelica$noopFPSLimiter(int fps) {
        return false;
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;isShiftKeyDown()Z", shift = At.Shift.AFTER))
    private void angelica$setShowFpsGraph(CallbackInfo ci) {
        ((IGameSettingsExt) gameSettings).angelica$setShowFpsGraph(Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU));
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void celeritas$renderAheadStartFrame(CallbackInfo ci) {
        final int limit = GLStateManager.renderAheadLimit(ClientProxy.options().performance.cpuRenderAheadLimit);
        if (limit > 0) {
            celeritas$renderAheadManager.startFrame(limit);
        }
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void celeritas$renderAheadEndFrame(CallbackInfo ci) {
        if (GLStateManager.renderAheadLimit(ClientProxy.options().performance.cpuRenderAheadLimit) > 0) {
            celeritas$renderAheadManager.endFrame();
        }
    }

    @Inject(method = "runGameLoop", at = @At("RETURN"))
    private void angelica$streamingBufferEndFrame(CallbackInfo ci) {
        TessellatorStreamingDrawer.endFrame();
        BatchingFontRenderer.endFrame();
        ShaderManager.endFrame();
    }
}

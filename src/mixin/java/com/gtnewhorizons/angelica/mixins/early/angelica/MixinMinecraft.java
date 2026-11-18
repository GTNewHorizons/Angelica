package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.debug.DebugKeyHandler;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import com.gtnewhorizons.angelica.mixins.interfaces.IGameSettingsExt;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.FpsReducer;
import com.gtnewhorizons.angelica.rendering.FramePacer;
import com.gtnewhorizons.angelica.rendering.tesr.TesrBatchRenderer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import org.embeddedt.embeddium.impl.render.frame.RenderAheadManager;
import org.lwjgl.input.Keyboard;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL11;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
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
    public WorldClient theWorld;

    @Shadow
    public GuiScreen currentScreen;

    @Unique
    private final RenderAheadManager celeritas$renderAheadManager = new RenderAheadManager();

    @Unique
    private static boolean angelica$hadWorld;

    @Unique private static final Tracy.ZoneId angelica$Z_STREAMING_END_FRAME = Tracy.zoneId("streamingEndFrame", Tracy.COLOR_CLIENT);

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lcpw/mods/fml/common/FMLCommonHandler;onRenderTickEnd(F)V", shift = At.Shift.AFTER, remap = false)
    )
    private void angelica$injectLightingFixPostRenderTick(CallbackInfo ci) {
        GLSMEnable.glEnable(GL11.GL_LIGHTING);
    }

    @Unique
    private final Runnable angelica$renderAheadWait = () -> {
        final int limit = ClientProxy.options().performance.cpuRenderAheadLimit;
        if (limit > 0 && !GLStateManager.hasSwapchainBackpressure()) {
            celeritas$renderAheadManager.startFrame(limit);
        }
    };

    @Inject(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;updateCameraAndRender(F)V"))
    private void angelica$freshenMouseInput(CallbackInfo ci) {
        GLStateManager.pumpDisplayMessages();
    }

    @Inject(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;func_147120_f()V"))
    private void angelica$beforePresent(CallbackInfo ci) {
        if (ClientProxy.options().performance.cpuRenderAheadLimit > 0 && !GLStateManager.hasSwapchainBackpressure()) {
            celeritas$renderAheadManager.endFrame();
        }
        FramePacer.beforePresent();
    }

    @WrapWithCondition(method = "runGameLoop", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;yield()V", remap = false))
    private boolean angelica$limitFPS() {
        if (AngelicaMod.proxy == null) return true;

        final boolean hasWorld = theWorld != null;
        if (hasWorld != angelica$hadWorld) {
            angelica$hadWorld = hasWorld;
            FramePacer.invalidate();
        }

        FpsReducer.evaluateFrame();
        final int capHz = FpsReducer.effectiveCap(gameSettings.limitFramerate, theWorld == null && currentScreen != null);
        AngelicaMod.proxy.putFrametime(FramePacer.endFrame(capHz, angelica$renderAheadWait));
        return !FramePacer.pacedLastFrame();
    }

    @Redirect(method = {"startGame", "toggleFullscreen"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;setVSyncEnabled(Z)V", remap = false))
    private void angelica$redirectVSync(boolean sync) {
        GLStateManager.setVSyncEnabled(sync);
    }

    @Inject(method = "startGame", at = @At("RETURN"))
    private void angelica$markSplashCompleteOnStartGame(CallbackInfo ci) {
        GLStateManager.markSplashComplete("startGame");
    }

    @WrapWithCondition(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;sync(I)V", remap = false))
    private boolean angelica$noopFPSLimiter(int fps) {
        return false;
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;isShiftKeyDown()Z", shift = At.Shift.AFTER))
    private void angelica$setShowFpsGraph(CallbackInfo ci) {
        if (AngelicaConfig.modernizeF3Screen && DebugKeyHandler.F3_TOGGLE.isArmed()) return;
        ((IGameSettingsExt) gameSettings).angelica$setShowFpsGraph(Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU));
    }

    @Inject(method = "func_152348_aa", at = @At("HEAD"), cancellable = true, require = 1)
    private void angelica$disableStreamKeys(CallbackInfo ci) {
        if (!Keyboard.getEventKeyState() || !Keyboard.isKeyDown(Keyboard.KEY_F3)) return;
        final int key = Keyboard.getEventKey();
        if (key == gameSettings.field_152396_an.getKeyCode() || key == gameSettings.field_152397_ao.getKeyCode()
            || key == gameSettings.field_152398_ap.getKeyCode() || key == gameSettings.field_152399_aq.getKeyCode()) {
            ci.cancel();
        }
    }

    @Inject(method = "runGameLoop", at = @At("RETURN"))
    private void angelica$streamingBufferEndFrame(CallbackInfo ci) {
        if (Tracy.ENABLED) Tracy.beginZone(angelica$Z_STREAMING_END_FRAME);
        try {
            TessellatorStreamingDrawer.endFrame();
            BatchingFontRenderer.endFrame();
            TesrBatchRenderer.INSTANCE.instanceRing().endFrame();
            ShaderManager.endFrame();
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    @ModifyExpressionValue(
        method = "runGameLoop",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;fancyGraphics:Z", opcode = Opcodes.GETFIELD))
    private boolean angelica$applyGrassQuality(boolean fancyGraphics) {
        return ClientProxy.options().quality.grassQuality.isFancy();
    }
}

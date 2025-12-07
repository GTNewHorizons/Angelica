package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.mixins.interfaces.IGameSettingsExt;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
    private long angelica$lastFrameTime = 0;

    /**
     * @author mitchej123
     * @reason Avoid GL_PROXY_TEXTURE_2D which doesn't work with GLSM's texture binding.
     *         Uses the standard GL_MAX_TEXTURE_SIZE query instead.
     */
    @Overwrite
    public static int getGLMaximumTextureSize() {
        if (max_texture_size == -1) {
            max_texture_size = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        }
        return max_texture_size;
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lcpw/mods/fml/common/FMLCommonHandler;onRenderTickEnd(F)V", shift = At.Shift.AFTER, remap = false)
    )
    private void angelica$injectLightingFixPostRenderTick(CallbackInfo ci) {
        GL11.glEnable(GL11.GL_LIGHTING);
    }

    @Inject(
        method = "func_147120_f",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;update()V", shift = At.Shift.BEFORE, remap = false)
    )
    private void angelica$limitFPS(CallbackInfo ci) {
        if (!AngelicaConfig.sleepBeforeSwap) return;
        if (isFramerateLimitBelowMax()) {
            final long target = angelica$lastFrameTime + (long) (1.0 / getLimitFramerate() * 1_000_000) * 1_000;
            while (target - System.nanoTime() > 100) {
                Thread.yield();
            }
        }
    }

    @Inject(
        method = "func_147120_f",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;update()V", shift = At.Shift.AFTER, remap = false)
    )
    private void angelica$trackFrametimes(CallbackInfo ci) {
        if (AngelicaMod.proxy == null) return;

        final long time = System.nanoTime();
        AngelicaMod.proxy.putFrametime(time - angelica$lastFrameTime);
        angelica$lastFrameTime = time;
    }

    @WrapOperation(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;sync(I)V", remap = false))
    private void angelica$noopFPSLimiter(int fps, Operation<Void> original) {
        if (AngelicaConfig.sleepBeforeSwap) return;
        original.call(fps);
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;isShiftKeyDown()Z", shift = At.Shift.AFTER))
    private void angelica$setShowFpsGraph(CallbackInfo ci) {
        ((IGameSettingsExt) gameSettings).angelica$setShowFpsGraph(Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU));
    }
}

package com.gtnewhorizons.angelica.mixins.early.angelica.hudcaching;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiIngame;

@Mixin(GuiIngame.class)
public class MixinGuiIngame_HUDCaching {
	@Inject(method = "renderGameOverlay", at = @At("HEAD"))
    private void angelica$resetCaptures(CallbackInfo ci) {
        if (HUDCaching.renderingCacheOverride) {
        	HUDCaching.renderVignetteCaptured = false;
        	HUDCaching.renderHelmetCaptured = false;
        	HUDCaching.renderPortalCapturedTicks = -1;
        	// GuiIngame doesn't allow a clean way to capture crosshair
        }
    }
	
    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void angelica$captureRenderVignette(CallbackInfo ci) {
        if (HUDCaching.renderingCacheOverride) {
        	HUDCaching.renderVignetteCaptured = true;
        	ci.cancel();
        }
    }
    
    @Inject(method = "renderPumpkinBlur", at = @At("HEAD"), cancellable = true)
    private void angelica$captureRenderPumpkimBlur(CallbackInfo ci) {
    	if (HUDCaching.renderingCacheOverride) {
    		HUDCaching.renderHelmetCaptured = true;
        	ci.cancel();
        }
    }
    
    @Inject(method = "func_130015_b", at = @At("HEAD"), cancellable = true)
    private void angelica$captureRenderPortal(float partialTicks, int width, int height, CallbackInfo ci) {
    	if (HUDCaching.renderingCacheOverride) {
    		HUDCaching.renderPortalCapturedTicks = partialTicks;
        	ci.cancel();
        }
    }
    
    @WrapOperation(method = "func_96136_a", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I"))
    private int angelica$fixScoreboardTextAlpha(FontRenderer fontRenderer, String text, int x, int y, int color, Operation<Integer> op) {
    	// Vanilla uses 0x20 for the alpha but it looks like 0xFF for some reason
    	// here we just make it 0xFF so it doesn't cause issues
    	if (HUDCaching.renderingCacheOverride) {
    		color |= 0xFF000000;
    	}
    	return op.call(fontRenderer, text, x, y, color);
    }
    
}

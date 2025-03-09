package com.gtnewhorizons.angelica.mixins.early.angelica.hudcaching;

import com.gtnewhorizons.angelica.mixins.interfaces.GuiIngameForgeAccessor;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.angelica.hudcaching.HUDCaching;

import net.minecraft.client.gui.Gui;
import net.minecraftforge.client.GuiIngameForge;

@Mixin(GuiIngameForge.class)
public class MixinGuiIngameForge_HUDCaching {
	@Inject(method = "renderGameOverlay", at = @At("HEAD"))
    private void angelica$resetCaptures(CallbackInfo ci) {
        if (HUDCaching.INSTANCE.renderingCacheOverride) {
        	HUDCaching.INSTANCE.renderVignetteCaptured = false;
        	HUDCaching.INSTANCE.renderHelmetCaptured = false;
        	HUDCaching.INSTANCE.renderPortalCapturedTicks = -1;
        	HUDCaching.INSTANCE.renderCrosshairsCaptured = false;
        }
    }

    @Inject(method = "renderCrosshairs", at = @At("HEAD"), cancellable = true, remap = false)
    private void angelica$captureRenderCrosshair(CallbackInfo ci) {
        if (HUDCaching.INSTANCE.renderingCacheOverride) {
        	HUDCaching.INSTANCE.renderCrosshairsCaptured = true;
        	HUDCaching.fixGLStateBeforeRenderingCache();
        	ci.cancel();
        }
    }

    @Inject(method = "renderHelmet", at = @At("HEAD"), cancellable = true, remap = false)
    private void angelica$captureRenderHelmet(ScaledResolution res, float partialTicks, boolean hasScreen, int mouseX, int mouseY, CallbackInfo ci) {
    	if (HUDCaching.INSTANCE.renderingCacheOverride) {
    		HUDCaching.INSTANCE.renderHelmetCaptured = true;
        	ci.cancel();
        }

        HUDCaching.disableHoloInventory();
    }

    @Inject(method = "renderPortal", at = @At("HEAD"), cancellable = true, remap = false)
    private void angelica$captureRenderPortal(int width, int height, float partialTicks, CallbackInfo ci) {
    	if (HUDCaching.INSTANCE.renderingCacheOverride) {
    		HUDCaching.INSTANCE.renderPortalCapturedTicks = partialTicks;
        	ci.cancel();
        }
    }

    @Inject(method = "renderBossHealth", at = @At("HEAD"))
    private void angelica$bindBossHealthTexture(CallbackInfo ci) {
    	// boss health texture is bind in renderCrosshairs
    	// but HUD caching skips rendering crosshairs when rendering into cache
    	if (HUDCaching.INSTANCE.renderingCacheOverride) {
    		((GuiIngameForgeAccessor) this).callBind(Gui.icons);
    	}
    }
}

package com.gtnewhorizons.angelica.mixins.early.angelica.archaic;

import cpw.mods.fml.client.SplashProgress;

import net.minecraft.client.gui.FontRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.angelica.client.font.SplashFontRendererRef;

@SuppressWarnings("deprecation")
@Mixin(SplashProgress.class)
public class MixinSplashProgress {

    @Inject(at = @At("HEAD"), method = "access$302", remap = false)
    private static void captureFontRenderer(@Coerce FontRenderer fontRenderer, CallbackInfoReturnable<? extends FontRenderer> cir) {
        SplashFontRendererRef.fontRenderer = fontRenderer;
    }
}

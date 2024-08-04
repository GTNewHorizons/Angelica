package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cc.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.enchantment.EnchantmentHelper;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.prupe.mcpatcher.cc.ColorizeWorld;
import com.prupe.mcpatcher.cc.Colorizer;
import com.prupe.mcpatcher.cc.Lightmap;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

    @Shadow
    private Minecraft mc;

    @Shadow
    float fogColorRed;

    @Shadow
    float fogColorGreen;

    @Shadow
    float fogColorBlue;

    @Shadow
    @Final
    private DynamicTexture lightmapTexture;

    @Shadow
    @Final
    private int[] lightmapColors;

    @Shadow
    private boolean lightmapUpdateNeeded;

    @Inject(method = "updateLightmap(F)V", at = @At("HEAD"), cancellable = true)
    private void modifyUpdateLightMap(float partialTick, CallbackInfo ci) {
        if (Lightmap
            .computeLightmap((EntityRenderer) (Object) this, this.mc.theWorld, this.lightmapColors, partialTick)) {
            this.lightmapTexture.updateDynamicTexture();
            this.lightmapUpdateNeeded = false;
            ci.cancel();
        }
    }

    @Inject(
        method = "updateFogColor(F)V",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;fogColorBlue:F",
            ordinal = 11,
            shift = At.Shift.AFTER))
    private void modifyUpdateFogColor1(float p_78466_1_, CallbackInfo ci) {
        float n11 = (float) EnchantmentHelper.getRespiration(this.mc.renderViewEntity) * 0.2F;
        if (ColorizeWorld.computeUnderwaterColor()) {
            this.fogColorRed = Colorizer.setColor[0] + n11;
            this.fogColorGreen = Colorizer.setColor[1] + n11;
            this.fogColorBlue = Colorizer.setColor[2] + n11;
        }
    }

    @Inject(
        method = "updateFogColor(F)V",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;fogColorBlue:F",
            ordinal = 12,
            shift = At.Shift.AFTER))
    private void modifyUpdateFogColor2(float p_78466_1_, CallbackInfo ci) {
        if (ColorizeWorld.computeUnderlavaColor()) {
            this.fogColorRed = Colorizer.setColor[0];
            this.fogColorGreen = Colorizer.setColor[1];
            this.fogColorBlue = Colorizer.setColor[2];
        }
    }
}

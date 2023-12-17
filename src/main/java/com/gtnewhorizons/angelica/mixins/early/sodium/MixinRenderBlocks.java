package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderBlocks.class)
public abstract class MixinRenderBlocks {
    @Redirect(method = "renderStandardBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isAmbientOcclusionEnabled()Z"))
    private boolean checkAOEnabled() {
        if(AngelicaConfig.enableIris && BlockRenderingSettings.INSTANCE.shouldUseSeparateAo()) {
            return false; // force Sodium pipeline
        }

        return Minecraft.isAmbientOcclusionEnabled();
    }
}

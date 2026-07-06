package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.hd;

import net.minecraft.client.renderer.texture.TextureManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.prupe.mcpatcher.hd.CustomAnimation;

@Mixin(TextureManager.class)
public abstract class MixinTextureManager {

    @Inject(method = "tick()V", at = @At("RETURN"))
    private void modifyTick(CallbackInfo ci) {
        CustomAnimation.updateAll();
    }
}

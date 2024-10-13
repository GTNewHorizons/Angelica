package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.AngelicaMod;
import net.minecraft.client.renderer.texture.TextureMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureMap.class)
public class MixinTextureMap {

    @Inject(method = "registerIcons", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getMinecraft()Lnet/minecraft/client/Minecraft;"))
    private void angelica$registerErrorBlockIcon(CallbackInfo ci) {
        TextureMap thisObject = (TextureMap)(Object)this;
        AngelicaMod.blockError.registerBlockIcons(thisObject);
    }
}

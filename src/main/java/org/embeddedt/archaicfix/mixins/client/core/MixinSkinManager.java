package org.embeddedt.archaicfix.mixins.client.core;

import net.minecraft.client.resources.SkinManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = { "net/minecraft/client/resources/SkinManager$2" })
public class MixinSkinManager {
    @Shadow private SkinManager.SkinAvailableCallback field_152636_b;

    /**
     * Avoid leaking an EntityClientPlayerMP instance.
     */
    @Inject(method = "func_152634_a", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/SkinManager$SkinAvailableCallback;func_152121_a(Lcom/mojang/authlib/minecraft/MinecraftProfileTexture$Type;Lnet/minecraft/util/ResourceLocation;)V", shift = At.Shift.AFTER))
    private void onMakeCallback(CallbackInfo ci) {
        field_152636_b = null;
    }
}
